package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

data class StagedStorageGeneration(
    val descriptor: StorageGenerationDescriptor,
    val directory: Path,
)

class FileStorageGenerationLayout(
    root: Path,
) {
    private val generationsRoot: Path =
        root.toAbsolutePath().normalize().resolve("generations")

    init {
        Files.createDirectories(generationsRoot)
        check(!Files.isSymbolicLink(generationsRoot)) {
            "generations root must not be a symbolic link"
        }
        check(Files.isDirectory(generationsRoot, LinkOption.NOFOLLOW_LINKS)) {
            "generations root must be a directory"
        }
    }

    fun stage(
        descriptor: StorageGenerationDescriptor,
    ): StagedStorageGeneration {
        val directory = directoryFor(descriptor.generationId)
        ensureGenerationDirectory(directory)
        // Legacy stage is reusable ONLY for genuinely unmarked V1. It must
        // never adopt a partially staged V2 directory after interrupted work.
        ExistingGenerationFormatReader.requireLegacyUnmarkedCandidate(directory)

        val records = DurableRecordDirectory(directory)
        val existing = records.readLatest(DESCRIPTOR_PREFIX)
        if (existing == null) {
            records.publish(
                prefix = DESCRIPTOR_PREFIX,
                sequence = 1L,
                bytes = StorageGenerationDescriptorCodec.encode(descriptor),
            )
        } else {
            check(existing.sequence == 1L) {
                "generation descriptor must remain immutable"
            }
            check(StorageGenerationDescriptorCodec.decode(existing.bytes) == descriptor) {
                "generation descriptor conflicts with existing staged generation"
            }
        }

        return StagedStorageGeneration(
            descriptor = descriptor,
            directory = directory,
        )
    }

    /**
     * Explicit V2 stage in a NEW generation directory, never an in-place
     * upgrade of an existing V1 generation. No SQLite stores are opened.
     *
     * The format marker is durably published BEFORE the descriptor: after a
     * crash following marker publication, legacy stage/open refuses adoption.
     * The caller owns cleanup of abandoned unactivated generations.
     */
    fun stageWorkspaceV2(
        descriptor: StorageGenerationDescriptor,
    ): StagedStorageGeneration {
        val directory = directoryFor(descriptor.generationId)
        check(!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            "Workspace V2 stage requires a never-used generation directory"
        }
        Files.createDirectory(directory)
        val records = DurableRecordDirectory(directory)
        records.publish(
            prefix = ExistingGenerationFormatReader.RECORD_PREFIX,
            sequence = 1L,
            bytes = GenerationFormatMarkerCodec.encode(descriptor),
        )
        records.publish(
            prefix = DESCRIPTOR_PREFIX,
            sequence = 1L,
            bytes = StorageGenerationDescriptorCodec.encode(descriptor),
        )
        return StagedStorageGeneration(descriptor = descriptor, directory = directory)
    }

    /**
     * Resume ONLY a previously explicit V2 pre-database stage. The V2 marker
     * must already exist and match this exact immutable descriptor.
     *
     * Never upgrades V1, creates an unmarked directory, deletes crash residue,
     * touches SQLite, or mutates a generation that has started store creation.
     * Safe repeated calls after marker+descriptor publication return no-op.
     */
    fun resumeWorkspaceV2(
        descriptor: StorageGenerationDescriptor,
    ): StagedStorageGeneration {
        val directory = directoryFor(descriptor.generationId)
        check(!Files.isSymbolicLink(directory) &&
            Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            "V2 recovery requires an existing real generation directory"
        }

        // Recovery is intentionally restricted to the two durable records.
        // Any pending temp, SQLite store, symlink or unexpected file needs an
        // explicit higher-level recovery decision, not silent destruction.
        val markerFile = "${ExistingGenerationFormatReader.RECORD_PREFIX}-00000000000000000001.bin"
        val descriptorFile = "$DESCRIPTOR_PREFIX-00000000000000000001.bin"
        Files.newDirectoryStream(directory).use { entries ->
            for (entry in entries) {
                check(entry.fileName.toString() == markerFile ||
                    entry.fileName.toString() == descriptorFile) {
                    "V2 descriptor recovery found unexpected generation content"
                }
                check(!Files.isSymbolicLink(entry) &&
                    Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    "V2 descriptor recovery requires regular durable records"
                }
            }
        }

        val records = DurableRecordDirectory(directory)
        val marker = checkNotNull(
            records.readLatest(ExistingGenerationFormatReader.RECORD_PREFIX)
        ) { "V2 recovery requires an explicit format marker" }
        check(marker.sequence == 1L &&
            GenerationFormatMarkerCodec.matches(marker.bytes, descriptor)) {
            "V2 recovery format marker does not match immutable descriptor"
        }
        val existingDescriptor = records.readLatest(DESCRIPTOR_PREFIX)
        if (existingDescriptor == null) {
            records.publish(
                prefix = DESCRIPTOR_PREFIX,
                sequence = 1L,
                bytes = StorageGenerationDescriptorCodec.encode(descriptor),
            )
        } else {
            check(existingDescriptor.sequence == 1L &&
                StorageGenerationDescriptorCodec.decode(existingDescriptor.bytes) == descriptor) {
                "V2 recovery descriptor conflicts with existing generation"
            }
        }
        return StagedStorageGeneration(descriptor, directory)
    }

    fun requireStaged(
        generationId: StorageGenerationId,
    ): StagedStorageGeneration {
        val directory = directoryFor(generationId)
        check(Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            "staged generation is missing"
        }
        check(!Files.isSymbolicLink(directory)) {
            "staged generation must not be a symbolic link"
        }
        check(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            "staged generation path must be a directory"
        }

        val records = DurableRecordDirectory(directory)
        val durable =
            checkNotNull(records.readLatest(DESCRIPTOR_PREFIX)) {
                "staged generation descriptor is missing"
            }
        check(durable.sequence == 1L) {
            "generation descriptor must remain immutable"
        }
        val descriptor = StorageGenerationDescriptorCodec.decode(durable.bytes)
        check(descriptor.generationId == generationId) {
            "staged generation descriptor identity mismatch"
        }

        return StagedStorageGeneration(
            descriptor = descriptor,
            directory = directory,
        )
    }

    /**
     * Physically removes one obsolete generation without following symbolic links.
     *
     * Authority/pointer checks belong to the lifecycle coordinator; this helper only owns the
     * bounded generations/ filesystem primitive.
     */
    fun deleteGeneration(
        generationId: StorageGenerationId,
    ) {
        val directory = directoryFor(generationId)
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return
        }
        check(!Files.isSymbolicLink(directory)) {
            "generation cleanup must not follow a symbolic link"
        }
        check(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            "generation cleanup target must be a directory"
        }

        Files.walk(directory).use { paths ->
            paths
                .sorted(java.util.Comparator.reverseOrder())
                .forEach { path ->
                    val normalized = path.toAbsolutePath().normalize()
                    check(normalized.startsWith(generationsRoot)) {
                        "generation cleanup escaped generations root"
                    }
                    Files.deleteIfExists(path)
                }
        }
    }

    internal fun expectedDirectory(
        generationId: StorageGenerationId,
    ): Path =
        directoryFor(generationId)

    private fun directoryFor(
        generationId: StorageGenerationId,
    ): Path =
        generationsRoot.resolve(
            "generation-" + MaintenancePathIdentity.sha256Hex(generationId.value)
        )

    private fun ensureGenerationDirectory(directory: Path) {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            check(!Files.isSymbolicLink(directory)) {
                "generation directory must not be a symbolic link"
            }
            check(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                "generation path must be a directory"
            }
            return
        }
        Files.createDirectory(directory)
    }

    private companion object {
        const val DESCRIPTOR_PREFIX = "descriptor"
    }
}
