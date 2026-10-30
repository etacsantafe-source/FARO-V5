package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/**
 * A1.3b read-only discriminator for an EXISTING, validated staged generation.
 *
 * The legacy descriptor is byte-for-byte V1 and cannot be changed. An opt-in V2
 * durable record (sequence exactly 1) binds the generation identity, fingerprint
 * and creation timestamp through the immutable V1 descriptor codec.
 *
 * This reader does NOT stage, initialize SQLite, activate or migrate anything.
 * A later owner must publish the V2 record BEFORE creating its fifth DB so
 * interrupted V2 staging can never be mistaken for an implicit V1 upgrade.
 */
internal object GenerationFormatMarkerCodec {
    private val prefix = "FARO-GENERATION-FORMAT-V2\n".toByteArray(Charsets.US_ASCII)

    fun encode(descriptor: StorageGenerationDescriptor): ByteArray =
        prefix + MessageDigest.getInstance("SHA-256")
            .digest(StorageGenerationDescriptorCodec.encode(descriptor))

    fun matches(bytes: ByteArray, descriptor: StorageGenerationDescriptor): Boolean =
        MessageDigest.isEqual(bytes, encode(descriptor))
}

object ExistingGenerationFormatReader {
    const val RECORD_PREFIX = "generation-format"

    /**
     * Pre-open guard: V1 may create its original four stores only when the
     * staged directory has no V2 format claim or Workspace SQLite residue.
     * A legitimate historical empty V1 generation remains eligible.
     */
    fun requireLegacyUnmarkedCandidate(directory: java.nio.file.Path) {
        val normalized = directory.toAbsolutePath().normalize()
        check(!Files.isSymbolicLink(normalized) &&
            Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            "legacy generation must be an existing real directory"
        }
        val marker = DurableRecordDirectory(normalized).readLatest(RECORD_PREFIX)
        check(marker == null) { "V2-marked generation cannot be opened or staged as V1" }
        Files.newDirectoryStream(normalized).use { entries ->
            for (path in entries) {
                val name = path.fileName.toString()
                check(!name.startsWith(GenerationStoreFiles.WORKSPACE)) {
                    "unversioned Workspace database/residue cannot be treated as V1"
                }
            }
        }
    }

    fun requireAndRead(staged: StagedStorageGeneration): GenerationStoreFormat {
        val directory = staged.directory.toAbsolutePath().normalize()
        check(!Files.isSymbolicLink(directory) &&
            Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            "existing generation must be a real directory"
        }

        // This call never creates a directory because existence was proved above.
        val marker = DurableRecordDirectory(directory).readLatest(RECORD_PREFIX)
        if (marker == null) {
            requireLegacyUnmarkedCandidate(directory)
            GenerationStoreFiles.requireMaterialized(directory, GenerationStoreFormat.LEGACY_V1)
            return GenerationStoreFormat.LEGACY_V1
        }

        check(marker.sequence == 1L) {
            "generation format record must be immutable at sequence 1"
        }
        check(GenerationFormatMarkerCodec.matches(marker.bytes, staged.descriptor)) {
            "generation V2 marker is corrupt or bound to another descriptor"
        }
        GenerationStoreFiles.requireMaterialized(directory, GenerationStoreFormat.WORKSPACE_V2)
        return GenerationStoreFormat.WORKSPACE_V2
    }
}
