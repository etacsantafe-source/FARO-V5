package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityHousehold11StagedFilePort
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

/**
 * W05-A1B0: upgrades ONLY a sealed five-store copy to Reality11.
 * A trusted closed DEV host must own the exclusive generation lease.
 * Never performs active cutover, archive/restore or receipt Apply.
 */
internal class FaroV5Household11StagedMigration(
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
            "Household11 target must not be active"
        }
        val target = layout.requireStaged(staged.descriptor.generationId)
        check(target.descriptor == staged.descriptor &&
            target.directory == staged.directory.toAbsolutePath().normalize()) {
            "Household11 target descriptor or directory is noncanonical"
        }
        val source = layout.requireStaged(expectedActive.generationId)
        val sourceSnapshot = sealedFingerprints(source.directory)
        val targetSnapshot = sealedFingerprints(target.directory)
        check(sourceSnapshot == targetSnapshot) {
            "Household11 stage must be an exact sealed copy of its pinned source"
        }
        for (name in GenerationStoreFiles.WORKSPACE_V2) {
            check(!Files.isSameFile(source.directory.resolve(name), target.directory.resolve(name))) {
                "Household11 staged store aliases source: $name"
            }
        }
        FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(source)
        FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(target)

        requirePinned(expectedActive)
        val reality = target.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(reality)
        afterMigration()
        requirePinned(expectedActive)
        FileChannel.open(reality, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
        FileChannel.open(target.directory, READ, NOFOLLOW_LINKS).use { it.force(true) }

        check(sealedFingerprints(source.directory) == sourceSnapshot) {
            "Household11 migrated source changed"
        }
        check(sealedFingerprints(target.directory) - GenerationStoreFiles.REALITY ==
            targetSnapshot - GenerationStoreFiles.REALITY) {
            "Household11 migration changed another store"
        }
        StagedSqlitePreflight.requireAll(
            target.directory,
            listOf(StagedSqlitePreflight.Schema(
                GenerationStoreFiles.REALITY,
                11,
                listOf(
                    "household_need_state",
                    "household_need",
                    "household_stock_receipt_state",
                    "household_stock_receipt",
                ),
            )),
        )
        requirePinned(expectedActive)
        target
    }

    private fun requirePinned(expected: ActiveGenerationPointer) {
        assertExclusiveClosedDevHost()
        check(pointer.read() == expected) {
            "Household11 pinned active generation changed"
        }
    }

    private fun sealedFingerprints(directory: Path): Map<String, List<Byte>> {
        requireNoSidecars(directory)
        val hashes = GenerationStoreFiles.WORKSPACE_V2.associateWith { name ->
            val file = directory.resolve(name)
            check(Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)) {
                "Household11 stage requires a sealed regular store: $name"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file, NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(64 * 1024)
                var read = input.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            digest.digest().toList()
        }
        requireNoSidecars(directory)
        return hashes
    }

    private fun requireNoSidecars(directory: Path) {
        for (name in GenerationStoreFiles.WORKSPACE_V2) {
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(directory.resolve(name + suffix), NOFOLLOW_LINKS)) {
                    "Household11 stage contains an unsafe SQLite sidecar: $name$suffix"
                }
            }
        }
    }
}
