package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Pure JVM safety tests; no SQLite library is initialized in these paths. */
class GenerationWorkspaceV2StagingT1Test {
    @TempDir lateinit var root: Path

    private fun descriptor(id: String) =
        StorageGenerationDescriptor(
            generationId = StorageGenerationId(id),
            schemaSetFingerprint = "v2-bound",
            createdAtEpochMillis = 1200L,
        )

    private fun v1Files(directory: Path) {
        GenerationStoreFiles.LEGACY_V1.forEach { Files.createFile(directory.resolve(it)) }
    }

    @Test fun freshV2StageCreatesOnlyImmutableMarkerAndDescriptor() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("fresh-v2")
        val staged = layout.stageWorkspaceV2(d)
        assertEquals(d, layout.requireStaged(d.generationId).descriptor)
        assertEquals(2L, Files.list(staged.directory).use { it.count() })
        assertTrue(Files.exists(staged.directory.resolve(
            "generation-format-00000000000000000001.bin")))
        assertFalse(Files.exists(staged.directory.resolve(GenerationStoreFiles.WORKSPACE)))
        assertFalse(Files.exists(staged.directory.resolve(GenerationStoreFiles.REALITY)))
    }

    @Test fun explicitV2IsRecognizedOnlyAfterAllFiveFilesAreStaged() {
        val layout = FileStorageGenerationLayout(root)
        val staged = layout.stageWorkspaceV2(descriptor("complete-v2"))
        assertThrows(IllegalStateException::class.java) {
            ExistingGenerationFormatReader.requireAndRead(staged)
        }
        GenerationStoreFiles.WORKSPACE_V2.forEach {
            Files.createFile(staged.directory.resolve(it))
        }
        assertEquals(GenerationStoreFormat.WORKSPACE_V2,
            ExistingGenerationFormatReader.requireAndRead(staged))
    }

    @Test fun existingV1DirectoryCannotBeUpgradedInPlace() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("legacy-v1")
        val legacy = layout.stage(d)
        v1Files(legacy.directory)
        val before = Files.list(legacy.directory).use { it.count() }
        assertThrows(IllegalStateException::class.java) { layout.stageWorkspaceV2(d) }
        assertEquals(before, Files.list(legacy.directory).use { it.count() })
        assertFalse(Files.exists(legacy.directory.resolve(
            "generation-format-00000000000000000001.bin")))
    }

    @Test fun existingV2DirectoryCannotBeAdoptedByLegacyStage() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("no-v2-to-v1")
        val staged = layout.stageWorkspaceV2(d)
        assertThrows(IllegalStateException::class.java) { layout.stage(d) }
        assertEquals(d, layout.requireStaged(d.generationId).descriptor)
        assertEquals(2L, Files.list(staged.directory).use { it.count() })
    }

    @Test fun interruptedMarkerBeforeDescriptorBlocksLegacyStaging() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("interrupted-before-descriptor")
        val directory = Files.createDirectory(layout.expectedDirectory(d.generationId))
        DurableRecordDirectory(directory).publish(
            ExistingGenerationFormatReader.RECORD_PREFIX, 1L,
            GenerationFormatMarkerCodec.encode(d)
        )
        assertThrows(IllegalStateException::class.java) { layout.stage(d) }
        assertThrows(IllegalStateException::class.java) {
            layout.requireStaged(d.generationId)
        }
        assertEquals(1L, Files.list(directory).use { it.count() })
    }

    @Test fun duplicateV2StageFailsClosedWithoutClobberingDescriptor() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("duplicate-v2")
        val staged = layout.stageWorkspaceV2(d)
        val descriptorPath = staged.directory.resolve(
            "descriptor-00000000000000000001.bin")
        val before = Files.readAllBytes(descriptorPath)
        assertThrows(IllegalStateException::class.java) { layout.stageWorkspaceV2(d) }
        assertTrue(before.contentEquals(Files.readAllBytes(descriptorPath)))
    }

    @Test fun orphanWorkspaceSqliteCompanionBlocksLegacyResume() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("orphan-wal")
        val staged = layout.stage(d)
        Files.createFile(staged.directory.resolve("workspace.db-wal"))
        assertThrows(IllegalStateException::class.java) { layout.stage(d) }
        assertFalse(Files.exists(staged.directory.resolve(
            GenerationStoreFiles.WORKSPACE), LinkOption.NOFOLLOW_LINKS))
    }

    @Test fun directLegacyGraphOpenRefusesIncompleteV2BeforeCreatingDatabases() {
        val layout = FileStorageGenerationLayout(root)
        val staged = layout.stageWorkspaceV2(descriptor("unsafe-open"))
        assertThrows(IllegalStateException::class.java) {
            GenerationAuthorityStoreGraph.open(staged)
        }
        assertEquals(2L, Files.list(staged.directory).use { it.count() })
    }

    @Test fun directLegacyGraphOpenRefusesUnmarkedFifthStore() {
        val layout = FileStorageGenerationLayout(root)
        val staged = layout.stage(descriptor("unmarked-fifth"))
        Files.createFile(staged.directory.resolve(GenerationStoreFiles.WORKSPACE))
        assertThrows(IllegalStateException::class.java) {
            GenerationAuthorityStoreGraph.open(staged)
        }
        assertEquals(2L, Files.list(staged.directory).use { it.count() })
    }

    @Test fun legacyStageStillReusesItsOwnDescriptorWithoutWritingV2() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("legacy-repeat")
        val staged = layout.stage(d)
        assertEquals(staged, layout.stage(d))
        assertEquals(1L, Files.list(staged.directory).use { it.count() })
        assertFalse(Files.exists(staged.directory.resolve(
            "generation-format-00000000000000000001.bin")))
    }
    @Test fun resumeMarkerOnlyAfterCrashCompletesDescriptor() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("marker-only-crash")
        val directory = Files.createDirectory(layout.expectedDirectory(d.generationId))
        DurableRecordDirectory(directory).publish(
            ExistingGenerationFormatReader.RECORD_PREFIX, 1L,
            GenerationFormatMarkerCodec.encode(d)
        )
        val recovered = layout.resumeWorkspaceV2(d)
        assertEquals(d, layout.requireStaged(d.generationId).descriptor)
        assertEquals(directory, recovered.directory)
        assertEquals(2L, Files.list(directory).use { it.count() })
    }

    @Test fun resumeAlreadyCompletedV2MetadataDoesNotRewriteFiles() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("v2-replay")
        val staged = layout.stageWorkspaceV2(d)
        val markerPath = staged.directory.resolve(
            "generation-format-00000000000000000001.bin")
        val descriptorPath = staged.directory.resolve(
            "descriptor-00000000000000000001.bin")
        val beforeMarker = Files.readAllBytes(markerPath)
        val beforeDescriptor = Files.readAllBytes(descriptorPath)
        assertEquals(staged, layout.resumeWorkspaceV2(d))
        assertEquals(staged, layout.resumeWorkspaceV2(d))
        assertTrue(beforeMarker.contentEquals(Files.readAllBytes(markerPath)))
        assertTrue(beforeDescriptor.contentEquals(Files.readAllBytes(descriptorPath)))
        assertEquals(2L, Files.list(staged.directory).use { it.count() })
    }

    @Test fun resumeCannotAdoptAValidLegacyV1Descriptor() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("legacy-isolation")
        val legacy = layout.stage(d)
        assertThrows(IllegalStateException::class.java) { layout.resumeWorkspaceV2(d) }
        assertEquals(1L, Files.list(legacy.directory).use { it.count() })
    }

    @Test fun resumeCannotClaimPreviouslyUnusedDirectory() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("nonexistent")
        val directory = layout.expectedDirectory(d.generationId)
        assertThrows(IllegalStateException::class.java) { layout.resumeWorkspaceV2(d) }
        assertFalse(Files.exists(directory, LinkOption.NOFOLLOW_LINKS))
    }

    @Test fun resumeCannotConvertUnmarkedEmptyDirectory() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("empty-unmarked")
        val directory = Files.createDirectory(layout.expectedDirectory(d.generationId))
        assertThrows(IllegalStateException::class.java) { layout.resumeWorkspaceV2(d) }
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    @Test fun resumeRejectsMarkerFromDifferentDescriptor() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("marker-mismatch")
        val directory = Files.createDirectory(layout.expectedDirectory(d.generationId))
        DurableRecordDirectory(directory).publish(
            ExistingGenerationFormatReader.RECORD_PREFIX, 1L,
            GenerationFormatMarkerCodec.encode(d.copy(createdAtEpochMillis = 5L))
        )
        assertThrows(IllegalStateException::class.java) { layout.resumeWorkspaceV2(d) }
        assertEquals(1L, Files.list(directory).use { it.count() })
    }

    @Test fun resumeRejectsConflictingDescriptorWithoutOverwriting() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("descriptor-mismatch")
        val directory = Files.createDirectory(layout.expectedDirectory(d.generationId))
        DurableRecordDirectory(directory).publish(
            ExistingGenerationFormatReader.RECORD_PREFIX, 1L,
            GenerationFormatMarkerCodec.encode(d)
        )
        DurableRecordDirectory(directory).publish("descriptor", 1L,
            StorageGenerationDescriptorCodec.encode(d.copy(schemaSetFingerprint = "foreign")))
        assertThrows(IllegalStateException::class.java) { layout.resumeWorkspaceV2(d) }
        assertEquals(2L, Files.list(directory).use { it.count() })
    }

    @Test fun resumeRejectsUnexpectedCrashTempWithoutDeletion() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("stranded-temp")
        val staged = layout.stageWorkspaceV2(d)
        val residue = Files.writeString(staged.directory.resolve(".pending-remnant.tmp"), "data")
        assertThrows(IllegalStateException::class.java) { layout.resumeWorkspaceV2(d) }
        assertEquals("data", Files.readString(residue))
    }

    @Test fun resumeRefusesGenerationAfterAnyDbMaterialization() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("already-materialized")
        val staged = layout.stageWorkspaceV2(d)
        Files.createFile(staged.directory.resolve(GenerationStoreFiles.REALITY))
        assertThrows(IllegalStateException::class.java) { layout.resumeWorkspaceV2(d) }
        assertEquals(3L, Files.list(staged.directory).use { it.count() })
    }

    @Test fun resumeRefusesDirectorySymlinkAndKeepsExternalTarget() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("symbolic-directory")
        val target = Files.createDirectory(root.resolve("foreign"))
        Files.createSymbolicLink(layout.expectedDirectory(d.generationId), target)
        assertThrows(IllegalStateException::class.java) { layout.resumeWorkspaceV2(d) }
        assertEquals(0L, Files.list(target).use { it.count() })
    }

    @Test fun resumeRefusesMarkerSymlinkWithoutTouchingExternalFile() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("symbolic-marker")
        val dir = Files.createDirectory(layout.expectedDirectory(d.generationId))
        val outside = Files.write(root.resolve("external-marker"),
            GenerationFormatMarkerCodec.encode(d))
        Files.createSymbolicLink(dir.resolve(
            "generation-format-00000000000000000001.bin"), outside)
        assertThrows(IllegalStateException::class.java) { layout.resumeWorkspaceV2(d) }
        assertTrue(Files.isRegularFile(outside))
    }

    @Test fun resumeRefusesHigherMarkerSequence() {
        val layout = FileStorageGenerationLayout(root)
        val d = descriptor("higher-marker")
        val dir = Files.createDirectory(layout.expectedDirectory(d.generationId))
        DurableRecordDirectory(dir).publish(
            ExistingGenerationFormatReader.RECORD_PREFIX, 2L,
            GenerationFormatMarkerCodec.encode(d))
        assertThrows(IllegalStateException::class.java) { layout.resumeWorkspaceV2(d) }
        assertEquals(1L, Files.list(dir).use { it.count() })
    }


}
