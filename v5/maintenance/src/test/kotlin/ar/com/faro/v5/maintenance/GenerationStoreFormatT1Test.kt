package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** No native SQLite is opened. This freezes historical generation-file semantics. */
class GenerationStoreFormatT1Test {
    @TempDir lateinit var root: Path

    private fun materialize(names: List<String>): Path {
        val directory = Files.createDirectory(root.resolve("generation"))
        names.forEach { name -> Files.createFile(directory.resolve(name)) }
        return directory
    }

    @Test fun legacyFormatPreservesExactFourAuthoritativeFileNames() {
        assertEquals(
            listOf("reality.db", "commercial-evidence.db", "web-evidence.db", "auxiliary.db"),
            GenerationStoreFiles.LEGACY_V1,
        )
        assertEquals(GenerationStoreFiles.LEGACY_V1, GenerationStoreFiles.ALL)
        assertEquals(
            GenerationStoreFiles.LEGACY_V1,
            GenerationStoreFiles.names(GenerationStoreFormat.LEGACY_V1),
        )
        assertFalse(GenerationStoreFiles.ALL.contains(GenerationStoreFiles.WORKSPACE))
    }

    @Test fun workspaceFormatAddsExactlyOneNonDuplicateFifthFile() {
        assertEquals(5, GenerationStoreFiles.WORKSPACE_V2.size)
        assertEquals(5, GenerationStoreFiles.WORKSPACE_V2.distinct().size)
        assertEquals(
            GenerationStoreFiles.LEGACY_V1 + "workspace.db",
            GenerationStoreFiles.WORKSPACE_V2,
        )
    }

    @Test fun legacyOpenAllowsExistingFourFilesWithoutCreatingWorkspace() {
        val directory = materialize(GenerationStoreFiles.ALL)
        GenerationStoreFiles.requireMaterialized(directory)
        assertFalse(Files.exists(directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }

    @Test fun workspaceV2ValidationRejectsMissingFifthFileWithoutMutation() {
        val directory = materialize(GenerationStoreFiles.LEGACY_V1)
        assertThrows(IllegalStateException::class.java) {
            GenerationStoreFiles.requireMaterialized(
                directory, GenerationStoreFormat.WORKSPACE_V2
            )
        }
        assertFalse(Files.exists(directory.resolve(GenerationStoreFiles.WORKSPACE)))
        assertEquals(4L, Files.list(directory).use { it.count() })
    }

    @Test fun v2ValidationAcceptsAllFiveButNeverWritesAnything() {
        val directory = materialize(GenerationStoreFiles.WORKSPACE_V2)
        GenerationStoreFiles.requireMaterialized(
            directory, GenerationStoreFormat.WORKSPACE_V2
        )
        assertEquals(5L, Files.list(directory).use { it.count() })
    }

    @Test fun workspaceSymlinkCannotMasqueradeAsValidV2Store() {
        val directory = materialize(GenerationStoreFiles.LEGACY_V1)
        val foreign = Files.createFile(root.resolve("outside.db"))
        Files.createSymbolicLink(directory.resolve(GenerationStoreFiles.WORKSPACE), foreign)
        assertTrue(Files.isSymbolicLink(directory.resolve(GenerationStoreFiles.WORKSPACE)))
        assertThrows(IllegalStateException::class.java) {
            GenerationStoreFiles.requireMaterialized(
                directory, GenerationStoreFormat.WORKSPACE_V2
            )
        }
    }
}
