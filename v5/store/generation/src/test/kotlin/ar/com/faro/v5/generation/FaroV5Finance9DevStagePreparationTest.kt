package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Finance9DevStagePreparationTest {
    @TempDir lateinit var root: Path

    private val gate = SingleProcessMaterialWriteGate()
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val journal by lazy { FileBootstrapJournal(root) }
    private val operation = MaintenanceOperationId("d1b1c2a-prepare")
    private val sourceId = StorageGenerationId("d1b1c2a-source")
    private val targetId = StorageGenerationId("d1b1c2a-target")
    private val target = StorageGenerationDescriptor(targetId, "d1b1c2a-stage", 100L)

    private fun source(): Pair<ActiveGenerationPointer, StagedStorageGeneration> {
        val staged = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(sourceId, "d1b1c2a-planning8", 10L),
        )
        BundledSQLiteDriver().open(staged.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.execSQL("INSERT INTO finance_account VALUES ('cash', 'Efectivo', 'ARS', 'CASH')")
            it.execSQL(
                "INSERT INTO finance_entry VALUES " +
                    "('entry', 'cash', 1200, 'INCOME', 'ARS', 'Cobro local', 'Ingreso', 10, 11, NULL)",
            )
            it.execSQL("UPDATE finance_state SET revision = 2 WHERE singleton_id = 1")
        }
        return checkNotNull(pointer.compareAndSet(null, sourceId)) to staged
    }

    private fun preparation(afterCopy: () -> Unit = {}, lease: () -> Unit = {}) =
        FaroV5Finance9DevStagePreparation(root, gate, lease, afterCopy)

    private fun prepare(origin: ActiveGenerationPointer) =
        preparation().prepare(operation, target, origin, 120L)

    private fun requireUnpublished(origin: ActiveGenerationPointer) {
        assertEquals(origin, pointer.read())
        assertNull(journal.read(operation))
    }

    @Test fun copiesFiveStoresAndPreservesAccountAndMovementBeforeCutover() {
        val (origin, source) = source()
        val initial = GenerationStoreFiles.WORKSPACE_V2.associateWith { Files.readAllBytes(source.directory.resolve(it)) }
        val prepared = prepare(origin)
        preparation().requireSourceUnchanged(prepared)
        assertEquals(origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
        assertTrue(prepared.sourceFingerprint.matches(Regex("[0-9a-f]{64}")))
        assertEquals(
            FaroV5Finance9DevCutoverPreflight.Observation.OBSERVED_SEALED_STAGED9_ONLY,
            FaroV5Finance9DevCutoverPreflight(root, MaterialWriteGateQuiescence(gate))
                .inspect(operation, prepared.target, origin),
        )
        for (name in GenerationStoreFiles.WORKSPACE_V2) {
            assertArrayEquals(initial.getValue(name), Files.readAllBytes(source.directory.resolve(name)))
            if (name != GenerationStoreFiles.REALITY) {
                assertArrayEquals(initial.getValue(name), Files.readAllBytes(prepared.target.directory.resolve(name)))
            }
        }
        BundledSQLiteDriver().open(prepared.target.directory.resolve(GenerationStoreFiles.REALITY).toString()).use { db ->
            db.prepare(
                "SELECT a.display_name, e.amount_minor, e.currency_code " +
                    "FROM finance_entry e JOIN finance_account a USING (account_id) WHERE e.entry_id = 'entry'",
            ).use {
                assertTrue(it.step())
                assertEquals("Efectivo", it.getText(0))
                assertEquals(1200L, it.getLong(1))
                assertEquals("ARS", it.getText(2))
            }
        }
    }

    @Test fun samePointerWithNewSourceBytesInvalidatesPreparedCopy() {
        val (origin, source) = source()
        val prepared = prepare(origin)
        BundledSQLiteDriver().open(source.directory.resolve(GenerationStoreFiles.REALITY).toString()).use {
            it.execSQL("UPDATE finance_state SET revision = 3 WHERE singleton_id = 1")
        }
        assertThrows(IllegalStateException::class.java) {
            preparation().requireSourceUnchanged(prepared)
        }
        assertEquals(origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
    }

    @Test fun activePointerAbaDeniesBeforeStageCreation() {
        val (origin, _) = source()
        val other = StorageGenerationId("d1b1c2a-other")
        checkNotNull(pointer.compareAndSet(sourceId, other))
        val newOrigin = checkNotNull(pointer.compareAndSet(other, sourceId))
        assertThrows(IllegalStateException::class.java) { prepare(origin) }
        requireUnpublished(newOrigin)
        assertThrows(IllegalStateException::class.java) { layout.requireStaged(targetId) }
    }

    @Test fun danglingWalPreventsAnyStageCopy() {
        val (origin, source) = source()
        Files.createSymbolicLink(source.directory.resolve("workspace.db-wal"), root.resolve("missing"))
        assertThrows(IllegalStateException::class.java) { prepare(origin) }
        requireUnpublished(origin)
        assertThrows(IllegalStateException::class.java) { layout.requireStaged(targetId) }
    }

    @Test fun interruptedCopyDoesNotPublishAValidatedJournal() {
        val (origin, source) = source()
        val file = source.directory.resolve(GenerationStoreFiles.REALITY)
        val original = Files.readAllBytes(file)
        assertThrows(IllegalStateException::class.java) {
            preparation(afterCopy = { error("interrupted after copying") }).prepare(operation, target, origin, 120L)
        }
        requireUnpublished(origin)
        assertArrayEquals(original, Files.readAllBytes(file))
        assertEquals(target, layout.requireStaged(targetId).descriptor)
        assertThrows(IllegalStateException::class.java) { prepare(origin) }
    }

    @Test fun foreignPendingJournalPreventsPreparation() {
        val (origin, _) = source()
        val competing = BootstrapJournalEntry(
            MaintenanceOperationId("d1b1c2a-competing"), MaintenanceOperationKind.RESTORE, sourceId,
            StorageGenerationId("d1b1c2a-competing-target"), BootstrapPhase.PREPARING,
        )
        journal.create(competing)
        assertThrows(IllegalStateException::class.java) { prepare(origin) }
        requireUnpublished(origin)
        assertEquals(competing, journal.pending().single())
    }

    @Test fun missingExclusiveLeasePreventsPreparation() {
        val (origin, _) = source()
        assertThrows(SecurityException::class.java) {
            preparation(lease = { throw SecurityException("missing exclusive root lease") })
                .prepare(operation, target, origin, 120L)
        }
        requireUnpublished(origin)
        assertThrows(IllegalStateException::class.java) { layout.requireStaged(targetId) }
    }
}
