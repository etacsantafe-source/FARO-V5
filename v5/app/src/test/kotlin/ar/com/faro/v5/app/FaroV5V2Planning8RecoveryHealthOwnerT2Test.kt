package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5WorkspaceV2StageOwner
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.RealityStore
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Real SQLite five-owner Planning8 health; never a production runtime grant. */
class FaroV5V2Planning8RecoveryHealthOwnerT2Test {
    @TempDir lateinit var root: Path
    private val op = MaintenanceOperationId("c2b3-health-operation")
    private val old = StorageGenerationId("c2b3-previous")
    private val target = StorageGenerationId("c2b3-planning-eight")
    private val gate = SingleProcessMaterialWriteGate()
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val journal by lazy { FileBootstrapJournal(root) }
    private val quiescence by lazy { MaterialWriteGateQuiescence(gate) }

    private fun prepare(
        planning8: Boolean = true,
        phase: BootstrapPhase = BootstrapPhase.SWITCHED,
        switched: Boolean = true,
        accepted: Boolean = false,
    ): StagedStorageGeneration {
        assertNotNull(pointer.compareAndSet(null, old))
        val stage = FaroV5WorkspaceV2StageOwner(root).stageFreshAndVerify(
            StorageGenerationDescriptor(target, "n033c2b3-five-owner-test", 2026L),
        )
        if (planning8) {
            RealityStore(
                stage.directory.resolve(GenerationStoreFiles.REALITY).toString(),
                planningStage8 = true,
            ).use { assertEquals(8, it.maintenance.inspect().schemaVersion) }
        }
        if (switched) assertNotNull(pointer.compareAndSet(old, target))
        journal.create(BootstrapJournalEntry(
            op, MaintenanceOperationKind.RESTORE, old, target, phase,
            materialWritesAccepted = accepted,
        ))
        return stage
    }

    private fun canonical(certified: Boolean = true) =
        FaroV5V2Planning8RecoveryHealthOwner.canonical(
            root, layout, journal, pointer, quiescence, gate, certified,
        )

    private fun withProbe(callback: (StagedStorageGeneration) -> Boolean) =
        FaroV5V2Planning8RecoveryHealthOwner(
            layout, journal, pointer, quiescence,
            Planning8RecoveryReadinessProbe(callback),
        )

    @Test fun healthyActiveSchemaEightIsVerifiedWithStablePointerAndJournal() {
        prepare()
        val beforePointer = pointer.read()
        val beforeJournal = journal.read(op)
        assertEquals(V2RuntimeHealth.Verified(target.value), canonical().verify(op))
        assertEquals(beforePointer, pointer.read())
        assertEquals(beforeJournal, journal.read(op))
        assertFalse(journal.read(op)!!.materialWritesAccepted)
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }

    @Test fun schemaSevenAndUncertifiedRealityEightBothFailClosedWithoutUpgrade() {
        val staged = prepare(planning8 = false)
        val realityFile = staged.directory.resolve(GenerationStoreFiles.REALITY)
        val before = Files.readAllBytes(realityFile)
        assertEquals(V2RuntimeHealth.Blocked, canonical().verify(op))
        assertArrayEquals(before, Files.readAllBytes(realityFile))
        assertEquals(7, RealityStore(realityFile.toString()).use {
            it.maintenance.inspect().schemaVersion
        })
        assertEquals(V2RuntimeHealth.Blocked, canonical(certified = false).verify(op))
    }

    @Test fun pointerAbaDuringHealthProbeBlocksEvenAfterReturningToSameId() {
        prepare()
        val before = checkNotNull(pointer.read())
        val foreign = StorageGenerationId("c2b3-foreign")
        val checked = withProbe {
            assertNotNull(pointer.compareAndSet(target, foreign))
            assertNotNull(pointer.compareAndSet(foreign, target))
            true
        }
        assertEquals(V2RuntimeHealth.Blocked, checked.verify(op))
        assertEquals(target, pointer.read()!!.generationId)
        assertTrue(pointer.read()!!.sequence > before.sequence)
    }

    @Test fun journalTransitionDuringHealthProbeRevokesPriorAdmission() {
        prepare()
        val before = checkNotNull(journal.read(op))
        val owner = withProbe {
            val after = BootstrapTransitionPolicy.transition(before, BootstrapPhase.HEALTH_VERIFIED)
            assertTrue(journal.compareAndSet(before, after))
            true
        }
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(BootstrapPhase.HEALTH_VERIFIED, journal.read(op)!!.phase)
    }

    @Test fun competingPendingJournalBlocksBeforeProbe() {
        prepare()
        journal.create(BootstrapJournalEntry(
            MaintenanceOperationId("c2b3-foreign-operation"),
            MaintenanceOperationKind.RESTORE,
            old,
            StorageGenerationId("c2b3-foreign-target"),
            BootstrapPhase.STAGED,
        ))
        var invocations = 0
        val owner = withProbe { invocations++; true }
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(0, invocations)
    }

    @Test fun alreadyAcceptedMaterialWritesCannotReenterRecoveryHealth() {
        prepare(accepted = true)
        var invocations = 0
        val owner = withProbe { invocations++; true }
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(0, invocations)
    }

    @Test fun unswitchedAndInvalidBootstrapPhaseBlockBeforeProbe() {
        prepare(phase = BootstrapPhase.VALIDATED, switched = false)
        var invocations = 0
        val owner = withProbe { invocations++; true }
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(old, pointer.read()!!.generationId)
        assertEquals(0, invocations)
    }

    @Test fun failingReadinessProbeBlocksWithoutChangingDurableState() {
        prepare()
        val pinned = pointer.read()
        val persisted = journal.read(op)
        val owner = withProbe { error("injected-failed-readiness") }
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(pinned, pointer.read())
        assertEquals(persisted, journal.read(op))
    }
}
