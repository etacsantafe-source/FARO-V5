package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** One-shot SQL15 ACTIVE inspection on DEV synthetic selection, no C2 cutover. */
class FaroV5Household15ActiveDevReadTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val quiescence = MaterialWriteGateQuiescence(gate)
    private val pointer get() = FileActiveGenerationPointer(root)

    private fun prepare(to15: Boolean = true, typedEvidence: Boolean = true): ActiveGenerationPointer {
        val original = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("a2c1b-source")))
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(
                StorageGenerationId("a2c1b-target"), "a2c1b-isolated", 100L,
            ),
        )
        val stage = FaroV5Finance9StagedMigration(root, quiescence).migrate(
            MaintenanceOperationId("a2c1b-finance"), fresh, original, "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        val reality = stage.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(reality)
        RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(reality) {}
        RealityHousehold13StagedFilePort.migrateTrustedStagedTwelve(reality)
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(reality) {}
        if (to15) RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(reality) {}
        if (typedEvidence) CommercialEvidenceStore(
            fileName = stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
            stagedTypedTermsV2 = true, stagedSemanticBackupV2 = true,
        ).close()
        return checkNotNull(pointer.compareAndSet(original.generationId, stage.descriptor.generationId))
    }

    private fun reality(selected: ActiveGenerationPointer): Path =
        FileStorageGenerationLayout(root).requireStaged(selected.generationId)
            .directory.resolve(GenerationStoreFiles.REALITY)

    private fun inspect(
        selected: ActiveGenerationPointer,
        owner: () -> Boolean = { true },
        completedFixtureSelection: () -> Boolean = { true },
    ) = FaroV5GenerationStoreGraph.inspectActiveHousehold15DevReadOnly(
        root, selected, gate, owner, completedFixtureSelection,
    )

    @Test
    fun readsFiveStoreSql15ProfileAndReturnsOnlyTypedSnapshot() {
        val selected = prepare()
        val path = reality(selected)
        val original = Files.readAllBytes(path)
        assertTrue(inspect(selected).consumptions.isEmpty())
        assertTrue(inspect(selected).historical.reservations.isEmpty())
        assertArrayEquals(original, Files.readAllBytes(path))
        assertEquals(selected, pointer.read())
    }

    @Test
    fun readsCommittedWalAndDoesNotUseImmutableActiveMode() {
        val selected = prepare()
        val file = reality(selected)
        val base = Files.readAllBytes(file)
        BundledSQLiteDriver().open(file.toString()).use { writer ->
            writer.execSQL("PRAGMA wal_autocheckpoint=0")
            writer.execSQL("INSERT INTO household_confirmed_purchase VALUES " +
                "('purchase','item',3000,'UNIT','human',1,'MANUAL_EXPLICIT',1)")
            writer.execSQL("INSERT INTO household_stock_receipt VALUES " +
                "('receipt','intent','purchase','item',3000,'UNIT',0,20,1)")
            writer.execSQL("UPDATE household_stock_receipt_state SET revision=1")
            writer.execSQL("INSERT INTO household_stock_consumption_event VALUES " +
                "('consume','consume-intent','CONSUME','consume','item','UNIT'," +
                "1000,'human','manual-use',NULL,50,51,2)")
            assertTrue(Files.size(file.resolveSibling("reality.db-wal")) > 32L)
            assertArrayEquals(base, Files.readAllBytes(file))
            assertEquals("consume", inspect(selected).consumptions.single().eventId)
        }
        assertEquals(selected, pointer.read())
    }

    @Test
    fun deniesUnselectedGenerationOwnerRevocationAndIncompleteProof() {
        val selected = prepare()
        assertThrows(IllegalStateException::class.java) {
            inspect(selected, owner = { false })
        }
        assertThrows(IllegalStateException::class.java) {
            inspect(selected, completedFixtureSelection = { false })
        }
        assertThrows(IllegalStateException::class.java) {
            inspect(ActiveGenerationPointer(selected.sequence + 1, selected.generationId))
        }
        assertEquals(selected, pointer.read())
    }

    @Test
    fun rejectsSchema14Evidence1AndPointerAba() {
        val selected = prepare(to15 = false, typedEvidence = false)
        assertThrows(IllegalStateException::class.java) { inspect(selected) }
        val other = checkNotNull(pointer.compareAndSet(
            selected.generationId, StorageGenerationId("a2c1b-other")))
        val back = checkNotNull(pointer.compareAndSet(other.generationId, selected.generationId))
        assertEquals(selected.sequence + 2, back.sequence)
        assertThrows(IllegalStateException::class.java) { inspect(selected) }
    }

    @Test
    fun rejectsRootLeaseContentionAndSymlinkedActiveWal() {
        val selected = prepare()
        val lockPath = root.resolve("finance9-dev-root.lock")
        FileChannel.open(lockPath, CREATE, WRITE).use { channel ->
            channel.lock().use {
                assertThrows(IllegalStateException::class.java) { inspect(selected) }
            }
        }
        val file = reality(selected)
        val wal = file.resolveSibling("reality.db-wal")
        assertTrue(Files.notExists(wal))
        val bait = Files.writeString(root.resolve("bait-wal"), "not-sqlite")
        Files.createSymbolicLink(wal, bait)
        assertThrows(IllegalStateException::class.java) { inspect(selected) }
    }
}
