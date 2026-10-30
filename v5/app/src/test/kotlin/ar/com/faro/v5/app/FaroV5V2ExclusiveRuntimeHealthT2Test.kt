package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5V2ExclusiveRuntimeHealthT2Test {
    @TempDir lateinit var root: Path
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val journal by lazy { FileBootstrapJournal(root) }
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val gate = SingleProcessMaterialWriteGate()
    private val quiescence = MaterialWriteGateQuiescence(gate)
    private val operation = MaintenanceOperationId("c123-exclusive")
    private val oldId = StorageGenerationId("c123-old")
    private val target = StorageGenerationId("c123-v2")
    private var opened = 0
    private var closed = 0
    private var throwOnHealth = false
    private val opener = V2RuntimeOpener { staged ->
        opened++
        object : V2RuntimeGraph {
            override val generationId = staged.descriptor.generationId.value
            override val hasWorkspace = true
            override fun isHealthy(): Boolean =
                gate.withMaterialWrite {
                    if (throwOnHealth) error("health fail")
                    true
                }
            override fun close() { closed++ }
        }
    }
    private val health by lazy {
        FaroV5V2RecoveryRuntimeHealthOwner(layout, journal, pointer, quiescence, opener)
    }
    private fun descriptor(id: StorageGenerationId) =
        StorageGenerationDescriptor(id, "c123-gated", 1L)
    private fun stage() {
        val old = layout.stage(descriptor(oldId))
        GenerationStoreFiles.LEGACY_V1.forEach {
            Files.write(old.directory.resolve(it), byteArrayOf(1))
        }
        checkNotNull(pointer.compareAndSet(null, oldId))
        val targetStage = layout.stageWorkspaceV2(descriptor(target))
        GenerationStoreFiles.WORKSPACE_V2.forEach {
            Files.write(targetStage.directory.resolve(it), byteArrayOf(1))
        }
        checkNotNull(pointer.compareAndSet(oldId, target))
        journal.create(BootstrapJournalEntry(
            operation, MaintenanceOperationKind.RESTORE,
            oldId, target, BootstrapPhase.SWITCHED,
        ))
    }

    @Test fun maintenanceLeaseAllowsCanonicalOwnerReadEvenWhenWriteAdmissionClosed() {
        stage()
        gate.closeMaterialWriteAdmission()
        assertEquals(V2RuntimeHealth.Verified(target.value), health.verify(operation))
        assertEquals(1, opened)
        assertEquals(1, closed)
        assertFalse(gate.isMaterialWriteAdmissionOpen())
    }

    @Test fun ordinaryMaterialWriteDeniedButPostSwitchHealthRemainsAvailable() {
        stage()
        gate.closeMaterialWriteAdmission()
        assertThrows(IllegalStateException::class.java) {
            gate.withMaterialWrite { 17 }
        }
        assertEquals(V2RuntimeHealth.Verified(target.value), health.verify(operation))
        assertFalse(gate.isMaterialWriteAdmissionOpen())
    }

    @Test fun healthExceptionClosesGraphAndLeavesPointerAndJournalUntouched() {
        stage()
        val beforePointer = pointer.read()
        val beforeJournal = journal.read(operation)
        throwOnHealth = true
        gate.closeMaterialWriteAdmission()
        assertEquals(V2RuntimeHealth.Blocked, health.verify(operation))
        assertEquals(1, opened)
        assertEquals(1, closed)
        assertEquals(beforePointer, pointer.read())
        assertEquals(beforeJournal, journal.read(operation))
        assertFalse(gate.isMaterialWriteAdmissionOpen())
    }
}
