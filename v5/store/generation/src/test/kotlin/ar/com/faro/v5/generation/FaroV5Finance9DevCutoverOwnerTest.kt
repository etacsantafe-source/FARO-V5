package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.GenerationActivationResult
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Finance9DevCutoverOwnerTest {
    @TempDir lateinit var root: Path

    private val gate = SingleProcessMaterialWriteGate()
    private val verifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val journal by lazy { FileBootstrapJournal(root) }
    private val replay by lazy { FileGenerationReplayFence(root) }
    private val operation = MaintenanceOperationId("w01-d1b1c1-restore")
    private val sourceId = StorageGenerationId("w01-dev-source8")
    private val targetId = StorageGenerationId("w01-dev-target9")

    private data class Fixture(
        val origin: ActiveGenerationPointer,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
    )

    private fun fixture(): Fixture {
        val stageOwner = FaroV5WorkspaceV2StageOwner(root)
        val source = stageOwner.stageFreshPlanning8AndVerify(StorageGenerationDescriptor(sourceId, "source8", 10L))
        val target = stageOwner.stageFreshPlanning8AndVerify(StorageGenerationDescriptor(targetId, "target9", 20L))
        for (stage in listOf(source, target)) {
            BundledSQLiteDriver().open(reality(stage).toString()).use {
                it.execSQL("UPDATE finance_state SET revision = 17 WHERE singleton_id = 1")
            }
        }
        val origin = checkNotNull(pointer.compareAndSet(null, sourceId))
        FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate))
            .migrate(operation, target, origin, "a".repeat(64), 30L)
        journal.create(BootstrapJournalEntry(
            operation, MaintenanceOperationKind.RESTORE, sourceId, targetId, BootstrapPhase.VALIDATED,
        ))
        return Fixture(origin, source, target)
    }

    private fun owner(
        host: () -> Unit = {},
        fault: (GenerationSwitchFaultPoint) -> Unit = {},
        healthy: () -> Boolean = { true },
    ) = FaroV5Finance9DevCutoverOwner(root, gate, verifier, host, fault, healthy)

    private fun reality(staged: StagedStorageGeneration): Path =
        staged.directory.resolve(GenerationStoreFiles.REALITY)

    private fun hasReplay() = replay.isInstalled(MaintenanceOperationKind.RESTORE, targetId)

    @Test fun exactFinanceNineActivatesAndExistingEightBytesRemainIntact() {
        val f = fixture()
        val original = Files.readAllBytes(reality(f.source))
        val result = owner().activateValidated(operation, f.target, f.origin)
        assertTrue(result is GenerationActivationResult.Activated)
        assertEquals(ActiveGenerationPointer(f.origin.sequence + 1, targetId), pointer.read())
        assertEquals(BootstrapPhase.COMPLETED, journal.read(operation)?.phase)
        assertTrue(journal.read(operation)?.materialWritesAccepted == true)
        assertTrue(hasReplay())
        assertArrayEquals(original, Files.readAllBytes(reality(f.source)))
        gate.withQuiescedMaterialWrites {
            FaroV5GenerationStoreGraph.reopenVerifiedActiveFinance9Dev(
                root, f.target, f.origin.sequence + 1, verifier, gate,
            ).use { graph ->
                assertEquals(9, graph.reality.maintenance.inspect().schemaVersion)
                assertEquals(17L, graph.reality.financePersistence.read().revision)
                assertTrue(graph.verifyHealthy())
                assertThrows(IllegalStateException::class.java) {
                    graph.reality.finance9TrustedStageGrantPortForDevOnly()
                }
                assertThrows(IllegalStateException::class.java) {
                    graph.reality.finance9ObservedSourceReadForDevOnly()
                }
            }
        }
    }

    @Test fun restartAfterPointerPublicationConvergesWithoutSecondSwitch() {
        val f = fixture()
        val interrupted = owner(fault = { point ->
            if (point == GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE) {
                error("interrupted before journal advance")
            }
        })
        assertThrows(IllegalStateException::class.java) {
            interrupted.activateValidated(operation, f.target, f.origin)
        }
        val switched = ActiveGenerationPointer(f.origin.sequence + 1, targetId)
        assertEquals(switched, pointer.read())
        assertEquals(BootstrapPhase.SWITCH_PREPARED, journal.read(operation)?.phase)
        assertFalse(hasReplay())
        assertTrue(owner().recover(operation, f.target, f.origin) is GenerationActivationResult.Activated)
        assertTrue(owner().recover(operation, f.target, f.origin) is GenerationActivationResult.Activated)
        assertEquals(switched, pointer.read())
        assertEquals(BootstrapPhase.COMPLETED, journal.read(operation)?.phase)
        assertTrue(hasReplay())
    }

    @Test fun unhealthyTargetRollsBackToExactPlanningEightBeforeWriteAdmission() {
        val f = fixture()
        val result = owner(healthy = { false }).activateValidated(operation, f.target, f.origin)
        assertTrue(result is GenerationActivationResult.RolledBack)
        assertEquals(ActiveGenerationPointer(f.origin.sequence + 2, sourceId), pointer.read())
        assertEquals(BootstrapPhase.ROLLBACK_VERIFIED, journal.read(operation)?.phase)
        assertFalse(hasReplay())
        assertTrue(owner().recover(operation, f.target, f.origin) is GenerationActivationResult.RolledBack)
    }

    @Test fun uncertifiedHostDoesNotSwitchOrAdvanceTheJournal() {
        val f = fixture()
        assertThrows(SecurityException::class.java) {
            owner(host = { throw SecurityException("not the exclusive DEV host") })
                .activateValidated(operation, f.target, f.origin)
        }
        assertEquals(f.origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
        assertFalse(hasReplay())
    }

    @Test fun sameIdWithChangedSequenceIsDeniedBeforeAnySwitch() {
        val f = fixture()
        val other = StorageGenerationId("w01-replaced")
        checkNotNull(pointer.compareAndSet(sourceId, other))
        val newer = checkNotNull(pointer.compareAndSet(other, sourceId))
        assertThrows(IllegalStateException::class.java) {
            owner().activateValidated(operation, f.target, f.origin)
        }
        assertEquals(newer, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
        assertFalse(hasReplay())
    }

    @Test fun schemaDowngradeIsDeniedBeforeAnySwitch() {
        val f = fixture()
        BundledSQLiteDriver().open(reality(f.target).toString()).use {
            it.execSQL("PRAGMA user_version=8")
        }
        assertThrows(IllegalStateException::class.java) {
            owner().activateValidated(operation, f.target, f.origin)
        }
        assertEquals(f.origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
        assertFalse(hasReplay())
    }
}
