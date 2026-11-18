package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.BootstrapTransitionPolicy
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

/**
 * Prepares a local Finance9 generation from five closed Planning8 stores.
 * The W01 maintenance caller must hold the exclusive root lease and close its
 * previous runtime before calling. The durable active pointer stays with the source.
 */
internal class FaroV5Finance9DevStagePreparation(
    storageRoot: Path,
    private val gate: SingleProcessMaterialWriteGate,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val afterCopy: () -> Unit = {},
) {
    data class Prepared(
        val operationId: MaintenanceOperationId,
        val origin: ActiveGenerationPointer,
        val target: StagedStorageGeneration,
        val sourceFingerprint: String,
    )

    private val root = storageRoot.toAbsolutePath().normalize()

    fun prepare(
        operationId: MaintenanceOperationId,
        targetDescriptor: StorageGenerationDescriptor,
        origin: ActiveGenerationPointer,
        finishedAtEpochMillis: Long,
    ): Prepared = gate.withQuiescedMaterialWrites {
        assertExclusiveClosedDevHost()
        requireExistingRoot()
        check(targetDescriptor.generationId != origin.generationId)
        val pointer = FileActiveGenerationPointer(root)
        check(pointer.read() == origin) { "Finance9 stage origin pointer changed" }
        val layout = FileStorageGenerationLayout(root)
        val source = layout.requireStaged(origin.generationId)
        check(ExistingGenerationFormatReader.requireAndRead(source) == GenerationStoreFormat.WORKSPACE_V2)
        val journal = FileBootstrapJournal(root)
        check(journal.read(operationId) == null && journal.pending().isEmpty()) {
            "Finance9 stage preparation found prior or competing maintenance"
        }
        val sealedSource = readSealedFiles(source.directory)
        val sourceFingerprint = fingerprint(origin, sealedSource)

        val target = layout.stageWorkspaceV2(targetDescriptor)
        for (file in GenerationStoreFiles.WORKSPACE_V2) {
            val copy = target.directory.resolve(file)
            Files.copy(source.directory.resolve(file), copy, NOFOLLOW_LINKS)
            force(copy, directory = false)
        }
        afterCopy()
        check(readSealedFiles(target.directory) == sealedSource) { "Finance9 stage copy differs from source" }
        force(target.directory, directory = true)
        force(target.directory.parent, directory = true)
        assertExclusiveClosedDevHost()

        val prepared = FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate))
            .migrate(operationId, target, origin, sourceFingerprint, finishedAtEpochMillis)
        assertExclusiveClosedDevHost()
        check(pointer.read() == origin && readSealedFiles(source.directory) == sealedSource) {
            "Finance9 stage source changed during preparation"
        }
        force(prepared.directory, directory = true)

        val preparing = BootstrapJournalEntry(
            operationId, MaintenanceOperationKind.RESTORE, origin.generationId,
            targetDescriptor.generationId, BootstrapPhase.PREPARING,
        )
        check(journal.pending().isEmpty())
        journal.create(preparing)
        val staged = BootstrapTransitionPolicy.transition(preparing, BootstrapPhase.STAGED)
        check(journal.compareAndSet(preparing, staged))
        val validated = BootstrapTransitionPolicy.transition(staged, BootstrapPhase.VALIDATED)
        check(journal.compareAndSet(staged, validated))
        force(root.resolve("bootstrap").resolve("journal"), directory = true)
        force(root.resolve("bootstrap"), directory = true)
        check(pointer.read() == origin && journal.pending().singleOrNull() == validated)
        Prepared(operationId, origin, prepared, sourceFingerprint)
    }

    /**
     * A source can change without changing the active pointer. Recheck a
     * prepared copy under the same lease immediately before starting cutover.
     */
    fun requireSourceUnchanged(prepared: Prepared) = gate.withQuiescedMaterialWrites {
        assertExclusiveClosedDevHost()
        requireExistingRoot()
        val pointer = FileActiveGenerationPointer(root)
        check(pointer.read() == prepared.origin)
        val layout = FileStorageGenerationLayout(root)
        val source = layout.requireStaged(prepared.origin.generationId)
        check(layout.requireStaged(prepared.target.descriptor.generationId) == prepared.target)
        val journal = FileBootstrapJournal(root)
        val expected = BootstrapJournalEntry(
            prepared.operationId, MaintenanceOperationKind.RESTORE, prepared.origin.generationId,
            prepared.target.descriptor.generationId, BootstrapPhase.VALIDATED,
        )
        check(journal.read(prepared.operationId) == expected && journal.pending().singleOrNull() == expected)
        check(ExistingGenerationFormatReader.requireAndRead(source) == GenerationStoreFormat.WORKSPACE_V2)
        check(fingerprint(prepared.origin, readSealedFiles(source.directory)) == prepared.sourceFingerprint &&
            pointer.read() == prepared.origin) { "Finance9 stage source fingerprint is stale" }
        assertExclusiveClosedDevHost()
    }

    private fun requireExistingRoot() {
        val bootstrap = root.resolve("bootstrap")
        check(listOf(root, bootstrap, bootstrap.resolve("active"), root.resolve("generations")).all {
            Files.isDirectory(it, NOFOLLOW_LINKS)
        }) { "Finance9 requires a preexisting authority layout" }
    }

    private fun readSealedFiles(directory: Path): Map<String, String> {
        check(Files.isDirectory(directory, NOFOLLOW_LINKS))
        requireNoSidecars(directory)
        val files = GenerationStoreFiles.WORKSPACE_V2.associateWith { name ->
            val path = directory.resolve(name)
            check(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) >= 100L)
            val hash = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    hash.update(buffer, 0, size)
                }
            }
            hash.digest().toHex()
        }
        requireNoSidecars(directory)
        return files
    }

    private fun requireNoSidecars(directory: Path) {
        for (file in GenerationStoreFiles.WORKSPACE_V2) {
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(directory.resolve(file + suffix), NOFOLLOW_LINKS)) {
                    "Finance9 preparation requires five sealed stores"
                }
            }
        }
    }

    private fun fingerprint(origin: ActiveGenerationPointer, files: Map<String, String>): String {
        val canonical = buildString {
            appendLine("FARO_FINANCE9_DEV_SOURCE_V1")
            appendLine(origin.generationId.value)
            appendLine(origin.sequence)
            for (name in GenerationStoreFiles.WORKSPACE_V2) appendLine(name + " " + files.getValue(name))
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun force(path: Path, directory: Boolean) {
        FileChannel.open(path, if (directory) READ else WRITE, NOFOLLOW_LINKS).use { it.force(true) }
    }
}
