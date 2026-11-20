package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

/** Validates and upgrades a sealed copy while the caller holds the exclusive closed-root lease. */
internal class FaroV5Household10StagedMigration(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val afterMigration: () -> Unit = {},
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val layout = FileStorageGenerationLayout(root)
    private val pointer = FileActiveGenerationPointer(root)

    fun migrate(
        operationId: MaintenanceOperationId,
        staged: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ): StagedStorageGeneration = quiescence.withMaterialWritesQuiesced(operationId) {
        requirePinned(expectedActive)
        check(staged.descriptor.generationId != expectedActive.generationId) {
            "Household10 migration target must not be active"
        }
        val target = layout.requireStaged(staged.descriptor.generationId)
        check(target.descriptor == staged.descriptor &&
            target.directory == staged.directory.toAbsolutePath().normalize()) {
            "Household10 stage descriptor or directory is not canonical"
        }
        val source = layout.requireStaged(expectedActive.generationId)

        val sourceSnapshot = sealedFingerprints(source.directory)
        val targetSnapshot = sealedFingerprints(target.directory)
        check(sourceSnapshot == targetSnapshot) { "Household10 stage must be a sealed copy of the pinned source" }
        for (file in GenerationStoreFiles.WORKSPACE_V2) {
            check(!Files.isSameFile(source.directory.resolve(file), target.directory.resolve(file))) {
                "Household10 stage file aliases the pinned source: $file"
            }
        }
        FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(source, allowAuxiliary3 = false)
        FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(target, allowAuxiliary3 = false)

        requirePinned(expectedActive)
        val reality = target.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
        afterMigration()
        requirePinned(expectedActive)
        FileChannel.open(reality, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
        FileChannel.open(target.directory, READ, NOFOLLOW_LINKS).use { it.force(true) }

        check(sealedFingerprints(source.directory) == sourceSnapshot) {
            "Household10 source changed during migration"
        }
        check(sealedFingerprints(target.directory) - GenerationStoreFiles.REALITY ==
            targetSnapshot - GenerationStoreFiles.REALITY) {
            "Household10 stage changed a store other than Reality"
        }
        StagedSqlitePreflight.requireAll(
            target.directory,
            listOf(StagedSqlitePreflight.Schema(
                GenerationStoreFiles.REALITY, 10,
                listOf("household_need_state", "household_need"),
            )),
        )
        requirePinned(expectedActive)
        target
    }

    private fun requirePinned(expected: ActiveGenerationPointer) {
        assertExclusiveClosedDevHost()
        check(pointer.read() == expected) { "Household10 pinned active generation changed" }
    }

    private fun sealedFingerprints(directory: Path): Map<String, List<Byte>> {
        requireNoSidecars(directory)
        val fingerprints = GenerationStoreFiles.WORKSPACE_V2.associateWith { name ->
            val file = directory.resolve(name)
            check(Files.isRegularFile(file, NOFOLLOW_LINKS)) {
                "Household10 requires a regular sealed store: $name"
            }
            val hash = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file, NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(64 * 1024)
                var count = input.read(buffer)
                while (count != -1) {
                    hash.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
            hash.digest().toList()
        }
        requireNoSidecars(directory)
        return fingerprints
    }

    private fun requireNoSidecars(directory: Path) {
        for (file in GenerationStoreFiles.WORKSPACE_V2) {
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(directory.resolve(file + suffix), NOFOLLOW_LINKS)) {
                    "Household10 requires sealed SQLite stores: $file$suffix"
                }
            }
        }
    }
}
