package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.*
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5V2RecoveryRuntimeHealthOwnerT2Test {
    @TempDir lateinit var root: Path
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val journal by lazy { FileBootstrapJournal(root) }
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val op = MaintenanceOperationId("c123-v2-health")
    private val before = StorageGenerationId("c123-before")
    private val target = StorageGenerationId("c123-target")
    private var opens = 0
    private var closes = 0
    private var leases = 0
    private var healthy = true
    private var workspace = true
    private var reportedId: String? = null
    private var failOpen = false

    private val quiescence = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId, block: () -> T,
        ): T {
            assertEquals(op, operationId)
            leases++
            return block()
        }
    }
    private val opener = V2RuntimeOpener { staged ->
        if (failOpen) error("failed to open")
        opens++
        object : V2RuntimeGraph {
            override val generationId = reportedId ?: staged.descriptor.generationId.value
            override val hasWorkspace get() = workspace
            override fun isHealthy() = healthy
            override fun close() { closes++ }
        }
    }
    private val owner by lazy {
        FaroV5V2RecoveryRuntimeHealthOwner(layout, journal, pointer, quiescence, opener)
    }
    private fun descriptor(id: StorageGenerationId) =
        StorageGenerationDescriptor(id, "c123-five", 1L)

    private fun prepare(
        phase: BootstrapPhase = BootstrapPhase.SWITCHED,
        v2: Boolean = true,
        complete: Boolean = true,
        switched: Boolean = true,
        accepted: Boolean = false,
    ) {
        val old = layout.stage(descriptor(before))
        GenerationStoreFiles.LEGACY_V1.forEach {
            Files.write(old.directory.resolve(it), byteArrayOf(1))
        }
        checkNotNull(pointer.compareAndSet(null, before))
        val staged = if (v2) layout.stageWorkspaceV2(descriptor(target))
            else layout.stage(descriptor(target))
        val names = if (v2 && complete) GenerationStoreFiles.WORKSPACE_V2
            else GenerationStoreFiles.LEGACY_V1
        names.forEach { Files.write(staged.directory.resolve(it), byteArrayOf(1)) }
        if (switched) checkNotNull(pointer.compareAndSet(before, target))
        journal.create(BootstrapJournalEntry(
            op, MaintenanceOperationKind.RESTORE, before, target, phase,
            materialWritesAccepted = accepted,
        ))
    }

    @Test fun switchedHealthyV2IsVerifiedClosedAndLeavesJournalAndPointerUnchanged() {
        prepare()
        val p = pointer.read()
        val j = journal.read(op)
        assertEquals(V2RuntimeHealth.Verified(target.value), owner.verify(op))
        assertEquals(1, leases)
        assertEquals(1, opens)
        assertEquals(1, closes)
        assertEquals(p, pointer.read())
        assertEquals(j, journal.read(op))
    }

    @Test fun healthVerifiedPhaseIsAllowedWithoutWriteReadmission() {
        prepare(phase = BootstrapPhase.HEALTH_VERIFIED)
        assertEquals(V2RuntimeHealth.Verified(target.value), owner.verify(op))
        assertEquals(1, closes)
        assertFalse(journal.read(op)!!.materialWritesAccepted)
    }

    @Test fun validatedPreSwitchCannotOpen() {
        prepare(phase = BootstrapPhase.VALIDATED, switched = false)
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(0, opens)
    }

    @Test fun nonV2TargetCannotOpen() {
        prepare(v2 = false)
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(0, opens)
    }

    @Test fun interruptedFiveStoreTargetCannotOpen() {
        prepare(complete = false)
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(0, opens)
    }

    @Test fun acceptedWritesCannotReenterHealth() {
        prepare(accepted = true)
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(0, opens)
    }

    @Test fun unhealthyOrMissingWorkspaceClosesAndBlocks() {
        prepare()
        healthy = false
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        healthy = true
        workspace = false
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        reportedId = "wrong-generation"
        workspace = true
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(3, opens)
        assertEquals(3, closes)
    }

    @Test fun graphOpenExceptionBlocksWithoutMovingPointer() {
        prepare()
        val active = pointer.read()
        failOpen = true
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(active, pointer.read())
        assertEquals(0, opens)
    }

    @Test fun competingPendingJournalDeniesOpen() {
        prepare()
        journal.create(BootstrapJournalEntry(
            MaintenanceOperationId("other-operation"), MaintenanceOperationKind.RESTORE,
            before, StorageGenerationId("other-target"), BootstrapPhase.STAGED,
        ))
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(0, opens)
    }

    @Test fun missingPointerSwitchDeniesOpen() {
        prepare(switched = false)
        assertEquals(V2RuntimeHealth.Blocked, owner.verify(op))
        assertEquals(0, opens)
    }
}
