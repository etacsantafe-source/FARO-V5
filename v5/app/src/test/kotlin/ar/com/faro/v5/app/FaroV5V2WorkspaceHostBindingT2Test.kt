package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Android JVM T2 is deliberately non-native; real five-owner SQLite T2 is in :store:generation. */
class FaroV5V2WorkspaceHostBindingT2Test {
    @TempDir lateinit var root: Path

    private fun staged(id: String) =
        FileStorageGenerationLayout(root).stageWorkspaceV2(
            StorageGenerationDescriptor(StorageGenerationId(id), "schema-five-store-1", 4L)
        )

    @Test fun incompleteFiveOwnerCandidateCannotExposeWorkspacePort() {
        val candidate = staged("incomplete-five")
        val active = Files.createDirectory(root.resolve("other-active"))
        assertThrows(IllegalStateException::class.java) {
            FaroV5V2WorkspaceHostBinding.openQuarantined(
                candidate, SingleProcessMaterialWriteGate(), Supplier { active }
            )
        }
        assertTrue(Files.notExists(candidate.directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }

    @Test fun activeGenerationCannotBeMountedAsQuarantinedUiHost() {
        val candidate = staged("active-refused")
        assertThrows(IllegalStateException::class.java) {
            FaroV5V2WorkspaceHostBinding.openQuarantined(
                candidate, SingleProcessMaterialWriteGate(), Supplier { candidate.directory }
            )
        }
        assertTrue(Files.notExists(candidate.directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }
}
