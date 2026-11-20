package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationActivationResult
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

/** Isolated Household10 cutover. The live lease gates every maintenance operation. */
internal class FaroV5Household10DevCutoverRuntime private constructor(
    private val root: Path,
    private val gate: SingleProcessMaterialWriteGate,
    private val channel: FileChannel,
    private val lock: FileLock,
    private val hostClosed: () -> Boolean,
    private val fault: (GenerationSwitchFaultPoint) -> Unit,
    private val afterAuthorization: () -> Unit,
    private val afterReplayFence: () -> Unit,
    private val targetRuntimeHealthy: () -> Boolean,
) : AutoCloseable {
    enum class Status { ACTIVE_TEN, ROLLED_BACK, ORIGIN_RETAINED, BLOCKED }
    data class Selection(val status: Status, val pointer: ActiveGenerationPointer?)

    private var closed = false
    private val ticket = root.resolve("bootstrap").resolve("household10-dev-cutover-v1.bin")
    private val quiescence = MaterialWriteGateQuiescence(gate)

    fun start(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
        confirmInForeground: () -> Boolean,
    ): Selection = checkNotNull(guarded {
        check(confirmInForeground()) { "Household10 cutover requires foreground authorization" }
        requireLive()
        require(FaroV5FiveStorePortableBackupCrypto().admitsArchiveSize(encryptedArchive.size))
        val archive = encryptedArchive.copyOf()
        val archiveDigest = sha256(archive).joinToString("") { "%02x".format(it) }
        val layout = FileStorageGenerationLayout(root)
        val targetId = target.descriptor.generationId
        val staged = layout.requireStaged(targetId)
        check(target.descriptor == staged.descriptor && target.directory.toAbsolutePath().normalize() == staged.directory)

        val previous = readTicket()
        if (previous != null) {
            check(previous.operationId == operationId && previous.origin == origin &&
                previous.targetId == targetId && previous.archiveDigest == archiveDigest)
            return@guarded resume(previous, recovery = true)
        }

        val pointer = FileActiveGenerationPointer(root)
        check(pointer.read() == origin && targetId != origin.generationId)
        val originStage = layout.requireStaged(origin.generationId)
        FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(originStage, allowAuxiliary3 = false)
        val originDigest = FaroV5Household10DevCutoverOwner.fingerprint(originStage)
        val journal = FileBootstrapJournal(root)
        val preparing = BootstrapJournalEntry(
            operationId, MaintenanceOperationKind.RESTORE, origin.generationId, targetId, BootstrapPhase.PREPARING,
        )
        val pending = journal.read(operationId)
        check((pending == null || pending == preparing) && journal.pending().all { it == preparing })
        if (pending == null) journal.create(preparing)

        val restored = FaroV5Household10ArchiveCoordinator(root, quiescence, ::requireLive)
            .restoreAuthenticated(operationId, staged, origin, archive, passphrase)
        check(restored == FiveStoreStagedRestoreResult.StagedVerified(targetId.value))
        check(pointer.read() == origin && FaroV5Household10DevCutoverOwner.fingerprint(originStage) == originDigest)
        val targetDigest = FaroV5Household10DevCutoverOwner.fingerprint(staged)
        if (!confirmInForeground()) return@guarded Selection(Status.ORIGIN_RETAINED, origin)
        requireLive()
        check(pointer.read() == origin && journal.read(operationId) == preparing)
        val intent = Household10CutoverIntent(operationId, origin, targetId, archiveDigest, originDigest, targetDigest)
        publishTicket(intent)
        afterAuthorization()
        resume(intent, recovery = false)
    })

    fun recover(): Selection? = guarded { readTicket()?.let { resume(it, recovery = true) } }

    private fun resume(intent: Household10CutoverIntent, recovery: Boolean): Selection {
        val result = FaroV5Household10DevCutoverOwner(
            root, quiescence, ::requireLive, fault, afterReplayFence, targetRuntimeHealthy,
        ).activate(intent, recovery)
        val status = when (result) {
            is GenerationActivationResult.Activated -> Status.ACTIVE_TEN
            is GenerationActivationResult.RolledBack -> Status.ROLLED_BACK
            is GenerationActivationResult.Blocked -> Status.BLOCKED
        }
        return Selection(status, FileActiveGenerationPointer(root).read())
    }

    private fun guarded(action: () -> Selection?): Selection? = try {
        gate.withQuiescedMaterialWrites {
            requireLive()
            action()
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        Selection(Status.BLOCKED, null)
    } catch (_: Exception) {
        Selection(Status.BLOCKED, null)
    }

    private fun requireLive() {
        check(!closed && channel.isOpen && lock.isValid && hostClosed()) {
            "Household10 cutover requires the exclusive closed DEV host"
        }
    }

    private fun readTicket(): Household10CutoverIntent? {
        if (Files.notExists(ticket, NOFOLLOW_LINKS)) return null
        check(ticket.parent.toRealPath() == ticket.parent &&
            Files.isRegularFile(ticket, NOFOLLOW_LINKS) && Files.size(ticket) in 33..4096)
        val bytes = Files.readAllBytes(ticket)
        val payload = bytes.copyOfRange(0, bytes.size - 32)
        check(MessageDigest.isEqual(sha256(payload), bytes.copyOfRange(payload.size, bytes.size)))
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            check(input.readUTF() == TICKET_VERSION)
            val operationId = MaintenanceOperationId(input.readUTF())
            val origin = ActiveGenerationPointer(input.readLong(), StorageGenerationId(input.readUTF()))
            val result = Household10CutoverIntent(
                operationId, origin, StorageGenerationId(input.readUTF()),
                input.readUTF(), input.readUTF(), input.readUTF(),
            )
            check(input.read() == -1)
            result
        }
    }

    private fun publishTicket(intent: Household10CutoverIntent) {
        check(ticket.parent.toRealPath() == ticket.parent && Files.notExists(ticket, NOFOLLOW_LINKS))
        val payload = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeUTF(TICKET_VERSION)
                output.writeUTF(intent.operationId.value)
                output.writeLong(intent.origin.sequence)
                output.writeUTF(intent.origin.generationId.value)
                output.writeUTF(intent.targetId.value)
                output.writeUTF(intent.archiveDigest)
                output.writeUTF(intent.originDigest)
                output.writeUTF(intent.targetDigest)
            }
        }.toByteArray()
        val temporary = Files.createTempFile(ticket.parent, ".household10-cutover-", ".tmp")
        try {
            Files.write(temporary, payload + sha256(payload), WRITE, NOFOLLOW_LINKS)
            FileChannel.open(temporary, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
            Files.move(temporary, ticket, ATOMIC_MOVE)
            FileChannel.open(ticket.parent, READ, NOFOLLOW_LINKS).use { it.force(true) }
            check(readTicket() == intent)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    override fun close() = gate.withQuiescedMaterialWrites {
        if (!closed) {
            closed = true
            try {
                lock.release()
            } finally {
                channel.close()
            }
        }
    }

    companion object {
        private const val TICKET_VERSION = "FARO_HOUSEHOLD10_DEV_CUTOVER_V1"

        fun acquire(
            storageRoot: Path,
            gate: SingleProcessMaterialWriteGate,
            hostClosed: () -> Boolean,
            fault: (GenerationSwitchFaultPoint) -> Unit = {},
            afterAuthorization: () -> Unit = {},
            afterReplayFence: () -> Unit = {},
            targetRuntimeHealthy: () -> Boolean = { true },
        ): FaroV5Household10DevCutoverRuntime {
            check(Files.isDirectory(storageRoot, NOFOLLOW_LINKS) && hostClosed())
            val root = storageRoot.toRealPath()
            // Share the existing Finance DEV root lease across both runtime owners.
            val channel = FileChannel.open(root.resolve("finance9-dev-root.lock"), CREATE, WRITE, NOFOLLOW_LINKS)
            return try {
                val lock = checkNotNull(channel.tryLock()) { "DEV root lease is occupied" }
                FaroV5Household10DevCutoverRuntime(
                    root, gate, channel, lock, hostClosed, fault, afterAuthorization, afterReplayFence, targetRuntimeHealthy,
                )
            } catch (failure: Exception) {
                channel.close()
                throw IllegalStateException("DEV root lease could not be acquired", failure)
            }
        }

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
