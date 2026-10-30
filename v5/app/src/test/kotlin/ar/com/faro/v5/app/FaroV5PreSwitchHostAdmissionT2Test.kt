package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.*
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Pre-switch admission tests are intentionally SQLite-free; real host preflight
 * is bound to the canonical five-store graph in FaroV5MaintenanceRuntime.
 */
class FaroV5PreSwitchHostAdmissionT2Test {
    @TempDir lateinit var root: Path
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val id = StorageGenerationId("c127-target")
    private val previous = StorageGenerationId("c128-previous")
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private fun descriptor() = StorageGenerationDescriptor(id, "c127-five", 3L)

    private fun stageV1() {
        val stage = layout.stage(descriptor())
        GenerationStoreFiles.LEGACY_V1.forEach {
            Files.createFile(stage.directory.resolve(it))
        }
    }
    private fun stageV2() {
        val stage = layout.stageWorkspaceV2(descriptor())
        GenerationStoreFiles.WORKSPACE_V2.forEach {
            Files.createFile(stage.directory.resolve(it))
        }
        checkNotNull(pointer.compareAndSet(null, previous))
    }

    @Test fun V1RestoreAndMigrationPreserveLegacyHostAdmission() {
        stageV1()
        val admission = FaroV5PreSwitchHostAdmission(layout, pointer, false) { error("V2 probe forbidden") }
        assertTrue(admission.canOpenAsActive(MaintenanceOperationKind.RESTORE, id))
        assertTrue(admission.canOpenAsActive(MaintenanceOperationKind.MIGRATION, id))
        assertFalse(admission.canOpenAsActive(MaintenanceOperationKind.RESET, id))
    }

    @Test fun disabledV2CannotReachEvenAQuarantinedHealthProbe() {
        stageV2()
        var called = false
        val admission = FaroV5PreSwitchHostAdmission(layout, pointer, false) {
            called = true
            true
        }
        assertFalse(admission.canOpenAsActive(MaintenanceOperationKind.RESTORE, id))
        assertFalse(called)
    }

    @Test fun futureV2RestoreRequiresExactStagedIdentityAndHealth() {
        val stage = layout.stageWorkspaceV2(descriptor())
        GenerationStoreFiles.WORKSPACE_V2.forEach {
            Files.createFile(stage.directory.resolve(it))
        }
        checkNotNull(pointer.compareAndSet(null, previous))
        var checked = 0
        val admission = FaroV5PreSwitchHostAdmission(layout, pointer, true) { staged ->
            checked++
            assertEquals(stage.directory, staged.directory)
            assertEquals(id, staged.descriptor.generationId)
            true
        }
        assertTrue(admission.canOpenAsActive(MaintenanceOperationKind.RESTORE, id))
        assertEquals(1, checked)
    }

    @Test fun unhealthyOrThrowingV2ProbeFailsClosed() {
        stageV2()
        assertFalse(FaroV5PreSwitchHostAdmission(layout, pointer, true) { false }
            .canOpenAsActive(MaintenanceOperationKind.RESTORE, id))
        assertFalse(FaroV5PreSwitchHostAdmission(layout, pointer, true) { error("bad five-store health") }
            .canOpenAsActive(MaintenanceOperationKind.RESTORE, id))
    }

    @Test fun V2MigrationAndResetNeverInheritRestorePermit() {
        stageV2()
        var called = false
        val admission = FaroV5PreSwitchHostAdmission(layout, pointer, true) {
            called = true
            true
        }
        assertFalse(admission.canOpenAsActive(MaintenanceOperationKind.MIGRATION, id))
        assertFalse(admission.canOpenAsActive(MaintenanceOperationKind.RESET, id))
        assertFalse(called)
    }
    @Test fun postSwitchV2AdmissionDoesNotIncorrectlyRunQuarantinedOpener() {
        stageV2()
        checkNotNull(pointer.compareAndSet(previous, id))
        var quarantinedOpenCount = 0
        val admission = FaroV5PreSwitchHostAdmission(layout, pointer, true) {
            quarantinedOpenCount++
            error("quarantined opener must not access active SQLite")
        }
        assertTrue(admission.canOpenAsActive(MaintenanceOperationKind.RESTORE, id))
        assertEquals(0, quarantinedOpenCount)
        assertEquals(id, pointer.read()!!.generationId)
    }

    @Test fun pointerSwitchDuringStageHealthProbeFailsClosed() {
        stageV2()
        val admission = FaroV5PreSwitchHostAdmission(layout, pointer, true) {
            checkNotNull(pointer.compareAndSet(previous, id))
            true
        }
        assertFalse(admission.canOpenAsActive(MaintenanceOperationKind.RESTORE, id))
        assertEquals(id, pointer.read()!!.generationId)
    }

}
