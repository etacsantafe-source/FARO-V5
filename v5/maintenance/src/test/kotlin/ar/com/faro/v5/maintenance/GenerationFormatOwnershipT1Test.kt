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

/** Nine pure T1 oracles. No SQLite driver, Android runtime or generation writes. */
class GenerationFormatOwnershipT1Test {
    @TempDir lateinit var root: Path

    private val descriptor = StorageGenerationDescriptor(
        generationId = StorageGenerationId("test-generation-v2"),
        schemaSetFingerprint = "schema-v2-bound",
        createdAtEpochMillis = 1234L,
    )

    private fun staged(names: List<String> = GenerationStoreFiles.LEGACY_V1):
        StagedStorageGeneration {
        val directory = Files.createDirectory(root.resolve("generation"))
        names.forEach { Files.createFile(directory.resolve(it)) }
        return StagedStorageGeneration(descriptor, directory)
    }

    private fun publishMarker(staged: StagedStorageGeneration, bytes: ByteArray =
        GenerationFormatMarkerCodec.encode(staged.descriptor), sequence: Long = 1L) {
        DurableRecordDirectory(staged.directory).publish(
            ExistingGenerationFormatReader.RECORD_PREFIX, sequence, bytes
        )
    }

    @Test fun historicFourFileGenerationReadsV1WithoutMutation() {
        val s = staged()
        assertEquals(GenerationStoreFormat.LEGACY_V1,
            ExistingGenerationFormatReader.requireAndRead(s))
        assertEquals(4L, Files.list(s.directory).use { it.count() })
        assertFalse(Files.exists(s.directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }

    @Test fun explicitFiveFileGenerationReadsV2WithoutMutation() {
        val s = staged(GenerationStoreFiles.WORKSPACE_V2)
        publishMarker(s)
        assertEquals(GenerationStoreFormat.WORKSPACE_V2,
            ExistingGenerationFormatReader.requireAndRead(s))
        assertEquals(6L, Files.list(s.directory).use { it.count() })
    }

    @Test fun orphanFifthStoreWithoutMarkerFailsClosed() {
        val s = staged(GenerationStoreFiles.WORKSPACE_V2)
        assertThrows(IllegalStateException::class.java) {
            ExistingGenerationFormatReader.requireAndRead(s)
        }
        assertEquals(5L, Files.list(s.directory).use { it.count() })
    }

    @Test fun markedV2MissingWorkspaceFailsClosedWithoutCreatingFile() {
        val s = staged()
        publishMarker(s)
        assertThrows(IllegalStateException::class.java) {
            ExistingGenerationFormatReader.requireAndRead(s)
        }
        assertFalse(Files.exists(s.directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }

    @Test fun tamperedMarkerFailsClosed() {
        val s = staged(GenerationStoreFiles.WORKSPACE_V2)
        val altered = GenerationFormatMarkerCodec.encode(s.descriptor).clone()
        altered[3] = (altered[3].toInt() xor 1).toByte()
        publishMarker(s, altered)
        assertThrows(IllegalStateException::class.java) {
            ExistingGenerationFormatReader.requireAndRead(s)
        }
    }

    @Test fun markerFromAnotherDescriptorFailsClosed() {
        val s = staged(GenerationStoreFiles.WORKSPACE_V2)
        publishMarker(s, GenerationFormatMarkerCodec.encode(s.descriptor.copy(
            generationId = StorageGenerationId("different-id")
        )))
        assertThrows(IllegalStateException::class.java) {
            ExistingGenerationFormatReader.requireAndRead(s)
        }
    }

    @Test fun symlinkMarkerCannotMasqueradeAsDurableV2Record() {
        val s = staged(GenerationStoreFiles.WORKSPACE_V2)
        val external = Files.write(root.resolve("external.bin"),
            GenerationFormatMarkerCodec.encode(s.descriptor))
        val name = "generation-format-00000000000000000001.bin"
        Files.createSymbolicLink(s.directory.resolve(name), external)
        assertTrue(Files.isSymbolicLink(s.directory.resolve(name)))
        assertThrows(IllegalStateException::class.java) {
            ExistingGenerationFormatReader.requireAndRead(s)
        }
    }

    @Test fun laterRecordSequenceCannotOverrideImmutableFormat() {
        val s = staged(GenerationStoreFiles.WORKSPACE_V2)
        publishMarker(s, sequence = 2L)
        assertThrows(IllegalStateException::class.java) {
            ExistingGenerationFormatReader.requireAndRead(s)
        }
    }

    @Test fun missingGenerationDirectoryIsNeverMaterializedByRead() {
        val absent = root.resolve("never-created")
        val s = StagedStorageGeneration(descriptor, absent)
        assertThrows(IllegalStateException::class.java) {
            ExistingGenerationFormatReader.requireAndRead(s)
        }
        assertFalse(Files.exists(absent, LinkOption.NOFOLLOW_LINKS))
    }
}
