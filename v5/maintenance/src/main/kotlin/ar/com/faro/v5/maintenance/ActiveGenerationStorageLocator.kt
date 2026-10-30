package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Java-friendly application bootstrap facade for the active StorageGeneration.
 *
 * Pointer encoding, hashed directory names and descriptor validation remain private to Maintenance.
 */
class ActiveGenerationStorageLocator(
    root: Path,
) {
    private val storageRoot = root.toAbsolutePath().normalize()
    private val pointer = FileActiveGenerationPointer(storageRoot)
    private val layout = FileStorageGenerationLayout(storageRoot)

    @Synchronized
    fun ensureInitialGenerationAndGetDirectory(
        initialGenerationId: String,
        schemaSetFingerprint: String,
    ): Path {
        val existing = pointer.read()
        if (existing != null) {
            return layout.requireStaged(existing.generationId).directory
        }

        val initialId = StorageGenerationId(initialGenerationId)
        recoverInterruptedInitialBootstrap(initialId, schemaSetFingerprint)?.let { recovered ->
            return recovered
        }

        requirePristineInitialBootstrapStorage()

        val staged =
            layout.stage(
                StorageGenerationDescriptor(
                    generationId = initialId,
                    schemaSetFingerprint = schemaSetFingerprint,
                    createdAtEpochMillis = 0L,
                )
            )
        GenerationAuthorityStoreGraph.open(staged).use { graph ->
            check(graph.verifyFreshForReset()) {
                "initial StorageGeneration must be healthy and empty before activation"
            }
        }

        val published =
            pointer.compareAndSet(
                expectedGenerationId = null,
                targetGenerationId = initialId,
            )
        if (published != null) {
            return staged.directory
        }

        val observed =
            checkNotNull(pointer.read()) {
                "active generation pointer remained absent after bootstrap race"
            }
        return layout.requireStaged(observed.generationId).directory
    }

    fun activeGenerationId(): String =
        checkNotNull(pointer.read()) {
            "active generation pointer is missing"
        }.generationId.value

    fun activeGenerationDirectory(): Path {
        val active =
            checkNotNull(pointer.read()) {
                "active generation pointer is missing"
            }
        return layout.requireStaged(active.generationId).directory
    }

    private fun recoverInterruptedInitialBootstrap(
        initialGenerationId: StorageGenerationId,
        schemaSetFingerprint: String,
    ): Path? {
        val expectedDirectory = layout.expectedDirectory(initialGenerationId)
        if (!Files.exists(expectedDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return null
        }

        requireOnlyInitialGenerationExists(expectedDirectory)
        requireNoMaintenanceStateBeyondActivePointerTransport()

        val descriptor =
            StorageGenerationDescriptor(
                generationId = initialGenerationId,
                schemaSetFingerprint = schemaSetFingerprint,
                createdAtEpochMillis = 0L,
            )
        val staged =
            runCatching {
                layout.requireStaged(initialGenerationId)
            }.getOrElse {
                requireRecoverableIncompleteInitialGeneration(expectedDirectory)
                layout.stage(descriptor)
            }

        check(staged.descriptor == descriptor) {
            "interrupted initial generation descriptor does not match requested bootstrap"
        }

        GenerationAuthorityStoreGraph.open(staged).use { graph ->
            check(graph.verifyFreshForReset()) {
                "interrupted initial StorageGeneration must remain healthy and empty"
            }
        }

        val published =
            pointer.compareAndSet(
                expectedGenerationId = null,
                targetGenerationId = initialGenerationId,
            )
        if (published != null) {
            return staged.directory
        }

        val observed =
            checkNotNull(pointer.read()) {
                "active generation pointer remained absent after interrupted-bootstrap recovery race"
            }
        return layout.requireStaged(observed.generationId).directory
    }

    private fun requireRecoverableIncompleteInitialGeneration(expectedDirectory: Path) {
        check(!Files.isSymbolicLink(expectedDirectory)) {
            "incomplete initial generation must not be a symbolic link"
        }
        check(Files.isDirectory(expectedDirectory, LinkOption.NOFOLLOW_LINKS)) {
            "incomplete initial generation must be a directory"
        }
        Files.newDirectoryStream(expectedDirectory).use { entries ->
            for (entry in entries) {
                val name = entry.fileName.toString()
                check(
                    name.startsWith(".pending-") &&
                        name.endsWith(".tmp") &&
                        !Files.isSymbolicLink(entry) &&
                        Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
                ) {
                    "incomplete initial generation contains unexpected state"
                }
            }
        }
    }

    private fun requireOnlyInitialGenerationExists(expectedDirectory: Path) {
        val generationsRoot = storageRoot.resolve("generations")
        val expected = expectedDirectory.toAbsolutePath().normalize()
        val children = mutableListOf<Path>()
        Files.newDirectoryStream(generationsRoot).use { entries ->
            for (entry in entries) {
                children.add(entry.toAbsolutePath().normalize())
            }
        }
        check(children.size == 1 && children.single() == expected) {
            "pointerless storage contains competing generations"
        }
    }

    private fun requireNoMaintenanceStateBeyondActivePointerTransport() {
        val bootstrapRoot = storageRoot.resolve("bootstrap")
        val activeRoot = bootstrapRoot.resolve("active")

        val bootstrapChildren = mutableListOf<Path>()
        Files.newDirectoryStream(bootstrapRoot).use { entries ->
            for (entry in entries) {
                bootstrapChildren.add(entry.toAbsolutePath().normalize())
            }
        }
        check(bootstrapChildren.all { it == activeRoot }) {
            "pointerless storage contains Maintenance state"
        }

        Files.newDirectoryStream(activeRoot).use { entries ->
            for (entry in entries) {
                val name = entry.fileName.toString()
                check(
                    name.startsWith(".pending-") &&
                        name.endsWith(".tmp") &&
                        !Files.isSymbolicLink(entry) &&
                        Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
                ) {
                    "pointerless active-pointer directory contains unexpected state"
                }
            }
        }
    }

    private fun requirePristineInitialBootstrapStorage() {
        check(!Files.isSymbolicLink(storageRoot)) {
            "storage root must not be a symbolic link"
        }
        check(Files.isDirectory(storageRoot, LinkOption.NOFOLLOW_LINKS)) {
            "storage root must be a directory"
        }

        val generationsRoot = storageRoot.resolve("generations")
        val bootstrapRoot = storageRoot.resolve("bootstrap")
        val activeRoot = bootstrapRoot.resolve("active")
        val allowed =
            setOf(
                storageRoot,
                generationsRoot,
                bootstrapRoot,
                activeRoot,
            )

        Files.walk(storageRoot).use { paths ->
            val unexpected =
                paths
                    .map { it.toAbsolutePath().normalize() }
                    .filter { it !in allowed }
                    .findFirst()
            check(unexpected.isEmpty) {
                "active generation pointer is missing but storage is not pristine"
            }
        }
    }
}
