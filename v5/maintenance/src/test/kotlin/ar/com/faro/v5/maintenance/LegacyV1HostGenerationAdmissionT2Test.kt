package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** No SQLite JNI: prove exact durable marker/Workspace claim via filesystem. */
class LegacyV1HostGenerationAdmissionT2Test {
    @TempDir lateinit var root: Path
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val gate by lazy { LegacyV1HostGenerationAdmission(layout) }

    private fun descriptor(id: String) = StorageGenerationDescriptor(
        StorageGenerationId(id), "schema-exact-test", 120L
    )
    private fun stores(directory: Path, files: List<String>) {
        files.forEach { Files.write(directory.resolve(it), byteArrayOf(1)) }
    }

    @Test fun supportedLegacyRestorePermittedOnlyWithAllFourOwnerFiles() {
        val d = descriptor("c120-real-legacy")
        val stage = layout.stage(d)
        stores(stage.directory, GenerationStoreFiles.LEGACY_V1)
        assertTrue(gate.canOpenAsActive(MaintenanceOperationKind.RESTORE, d.generationId))
    }

    @Test fun supportedLegacyMigrationKeepsExistingFourStorePath() {
        val d = descriptor("c120-migrate-v1")
        val stage = layout.stage(d)
        stores(stage.directory, GenerationStoreFiles.LEGACY_V1)
        assertTrue(gate.canOpenAsActive(MaintenanceOperationKind.MIGRATION, d.generationId))
    }

    @Test fun markedV2WithFiveFilesIsDeniedEvenThoughComplete() {
        val d = descriptor("c120-marked-v2")
        val stage = layout.stageWorkspaceV2(d)
        stores(stage.directory, GenerationStoreFiles.WORKSPACE_V2)
        assertFalse(gate.canOpenAsActive(MaintenanceOperationKind.RESTORE, d.generationId))
        assertTrue(Files.exists(stage.directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }

    @Test fun interruptedMarkedV2IsDeniedWithoutCreatingMissingStore() {
        val d = descriptor("c120-partial-v2")
        val stage = layout.stageWorkspaceV2(d)
        stores(stage.directory, GenerationStoreFiles.LEGACY_V1)
        assertFalse(gate.canOpenAsActive(MaintenanceOperationKind.RESTORE, d.generationId))
        assertFalse(Files.exists(stage.directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }

    @Test fun unmarkedWorkspaceResidueIsNeverAdoptedAsV1() {
        val d = descriptor("c120-orphan-workspace")
        val stage = layout.stage(d)
        stores(stage.directory, GenerationStoreFiles.WORKSPACE_V2)
        assertFalse(gate.canOpenAsActive(MaintenanceOperationKind.RESTORE, d.generationId))
    }

    @Test fun missingGenerationAndResetKindAreNotPermitted() {
        assertFalse(gate.canOpenAsActive(
            MaintenanceOperationKind.RESTORE, StorageGenerationId("nonexistent-target")
        ))
        val d = descriptor("c120-reset-forbidden")
        val stage = layout.stage(d)
        stores(stage.directory, GenerationStoreFiles.LEGACY_V1)
        assertFalse(gate.canOpenAsActive(MaintenanceOperationKind.RESET, d.generationId))
    }
}
