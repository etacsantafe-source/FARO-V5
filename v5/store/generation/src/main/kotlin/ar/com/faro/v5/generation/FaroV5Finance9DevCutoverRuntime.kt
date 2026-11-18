package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.BootstrapTransitionPolicy
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationActivationResult
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

/**
 * Exclusive maintenance lease for a private, isolated Finance DEV root.
 * It keeps the original request durable without retaining a store graph.
 */
internal class FaroV5Finance9DevCutoverRuntime private constructor(
    private val root: Path,
    private val gate: SingleProcessMaterialWriteGate,
    private val planVerifier: PreparedPlanEnvelopeVerificationPort,
    private val channel: FileChannel,
    private val lock: FileLock,
    private val fault: (GenerationSwitchFaultPoint) -> Unit,
    private val afterAuthorization: () -> Unit,
) : AutoCloseable {
    enum class Status { ORIGIN_RETAINED, ACTIVE_NINE, ROLLED_BACK }

    data class Selection(val status: Status, val pointer: ActiveGenerationPointer)

    private data class Intent(
        val operationId: MaintenanceOperationId,
        val origin: ActiveGenerationPointer,
        val target: StorageGenerationDescriptor,
        val sourceFingerprint: String? = null,
    )

    private val ticket = root.resolve("bootstrap").resolve("finance9-dev-cutover-v1.bin")
    private var closed = false

    fun start(
        operationId: MaintenanceOperationId,
        target: StorageGenerationDescriptor,
        origin: ActiveGenerationPointer,
        finishedAtEpochMillis: Long,
        confirmInForeground: () -> Boolean,
    ): Selection = gate.withQuiescedMaterialWrites {
        requireLive()
        check(confirmInForeground()) { "Finance9 cutover requires explicit foreground confirmation" }
        check(recover()?.status != Status.ACTIVE_NINE) { "Finance9 root already finished cutover" }
        val pointer = FileActiveGenerationPointer(root)
        check(pointer.read() == origin && target.generationId != origin.generationId)
        val journal = FileBootstrapJournal(root)
        check(journal.pending().isEmpty() && journal.read(operationId) == null)

        val intent = Intent(operationId, origin, target)
        publish(intent)
        val preparation = stagePreparation()
        val staged = preparation.prepare(operationId, target, origin, finishedAtEpochMillis)
        preparation.requireSourceUnchanged(staged)
        if (!confirmInForeground()) return@withQuiescedMaterialWrites retainOrigin(intent)

        val authorized = intent.copy(sourceFingerprint = staged.sourceFingerprint)
        check(readIntent() == intent) { "Finance9 PREPARE record changed" }
        publish(authorized)
        afterAuthorization()
        activate(authorized, recovery = false)
    }

    fun recover(): Selection? = gate.withQuiescedMaterialWrites {
        requireLive()
        val intent = readIntent() ?: return@withQuiescedMaterialWrites null
        if (intent.sourceFingerprint == null) retainOrigin(intent)
        else activate(intent, recovery = true)
    }

    private fun retainOrigin(intent: Intent): Selection {
        check(FileActiveGenerationPointer(root).read() == intent.origin)
        val journal = FileBootstrapJournal(root)
        val entry = journal.read(intent.operationId)
        check(journal.pending().all { it == entry }) { "Finance9 PREPARE found foreign maintenance" }
        if (entry != null) {
            check(entry.kind == MaintenanceOperationKind.RESTORE &&
                entry.previousGenerationId == intent.origin.generationId &&
                entry.targetGenerationId == intent.target.generationId &&
                !entry.materialWritesAccepted)
            if (entry.phase != BootstrapPhase.ABORTED_PRE_SWITCH) {
                val aborted = BootstrapTransitionPolicy.transition(entry, BootstrapPhase.ABORTED_PRE_SWITCH)
                check(journal.compareAndSet(entry, aborted))
                forceDirectory(ticket.parent.resolve("journal"))
            }
        }
        return Selection(Status.ORIGIN_RETAINED, intent.origin)
    }

    private fun activate(intent: Intent, recovery: Boolean): Selection {
        val pointer = FileActiveGenerationPointer(root)
        val staged = FileStorageGenerationLayout(root).requireStaged(intent.target.generationId)
        check(staged.descriptor == intent.target)
        val prepared = FaroV5Finance9DevStagePreparation.Prepared(
            intent.operationId, intent.origin, staged, checkNotNull(intent.sourceFingerprint),
        )
        if (pointer.read() == intent.origin) {
            stagePreparation().requireSourceUnchanged(prepared, resumeSwitchBeforePublish = recovery)
        }
        val cutover = FaroV5Finance9DevCutoverOwner(root, gate, planVerifier, ::requireLive, fault)
        val result = if (recovery) cutover.recover(intent.operationId, staged, intent.origin)
            else cutover.activateValidated(intent.operationId, staged, intent.origin)
        val status = when (result) {
            is GenerationActivationResult.Activated -> Status.ACTIVE_NINE
            is GenerationActivationResult.RolledBack -> Status.ROLLED_BACK
            is GenerationActivationResult.Blocked -> error("Finance9 exact recovery blocked: ${result.reason}")
        }
        return Selection(status, checkNotNull(pointer.read()))
    }

    private fun stagePreparation() = FaroV5Finance9DevStagePreparation(root, gate, ::requireLive)

    private fun publish(intent: Intent) {
        requireLive()
        check(Files.isDirectory(ticket.parent, NOFOLLOW_LINKS))
        val payload = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(RECORD_VERSION)
                out.writeUTF(intent.operationId.value)
                out.writeUTF(intent.origin.generationId.value)
                out.writeLong(intent.origin.sequence)
                out.writeUTF(intent.target.generationId.value)
                out.writeUTF(intent.target.schemaSetFingerprint)
                out.writeLong(intent.target.createdAtEpochMillis)
                out.writeUTF(intent.sourceFingerprint.orEmpty())
            }
            buffer.toByteArray()
        }
        val bytes = payload + sha256(payload)
        check(bytes.size <= RECORD_LIMIT)
        val temporary = Files.createTempFile(ticket.parent, ".finance9-dev-cutover-", ".tmp")
        try {
            FileChannel.open(temporary, WRITE, NOFOLLOW_LINKS).use { output ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) output.write(buffer)
                output.force(true)
            }
            Files.move(temporary, ticket, ATOMIC_MOVE, REPLACE_EXISTING)
            forceDirectory(ticket.parent)
        } finally {
            Files.deleteIfExists(temporary)
        }
        check(readIntent() == intent)
    }

    private fun readIntent(): Intent? {
        if (Files.notExists(ticket, NOFOLLOW_LINKS)) return null
        check(Files.isDirectory(ticket.parent, NOFOLLOW_LINKS) &&
            Files.isRegularFile(ticket, NOFOLLOW_LINKS) && Files.size(ticket) in 33L..RECORD_LIMIT)
        val bytes = Files.readAllBytes(ticket)
        val offset = bytes.size - 32
        val payload = bytes.copyOfRange(0, offset)
        check(MessageDigest.isEqual(sha256(payload), bytes.copyOfRange(offset, bytes.size))) {
            "Finance9 DEV cutover record checksum mismatch"
        }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            check(input.readUTF() == RECORD_VERSION)
            val operationId = MaintenanceOperationId(input.readUTF())
            val originId = StorageGenerationId(input.readUTF())
            val origin = ActiveGenerationPointer(input.readLong(), originId)
            val target = StorageGenerationDescriptor(
                StorageGenerationId(input.readUTF()), input.readUTF(), input.readLong(),
            )
            val fingerprint = input.readUTF().ifEmpty { null }
            check(input.available() == 0 && target.generationId != origin.generationId &&
                (fingerprint == null || fingerprint.matches(Regex("[0-9a-f]{64}"))))
            Intent(operationId, origin, target, fingerprint)
        }
    }

    private fun requireLive() {
        check(!closed && lock.isValid && channel.isOpen) { "Finance9 DEV root lease has ended" }
    }

    override fun close() = gate.withQuiescedMaterialWrites {
        synchronized(heldRoots) {
            if (!closed) {
                closed = true
                try {
                    lock.release()
                } finally {
                    try {
                        channel.close()
                    } finally {
                        heldRoots.remove(root)
                    }
                }
            }
        }
    }

    companion object {
        private const val RECORD_LIMIT = 8192L
        private const val RECORD_VERSION = "FARO_FINANCE9_DEV_CUTOVER_V1"
        private val heldRoots = mutableSetOf<Path>()

        fun acquire(
            storageRoot: Path,
            gate: SingleProcessMaterialWriteGate,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            fault: (GenerationSwitchFaultPoint) -> Unit = {},
            afterAuthorization: () -> Unit = {},
        ): FaroV5Finance9DevCutoverRuntime = synchronized(heldRoots) {
            check(Files.isDirectory(storageRoot, NOFOLLOW_LINKS))
            val root = storageRoot.toRealPath()
            check(root !in heldRoots) { "Finance9 DEV root is already held in this process" }
            val channel = FileChannel.open(root.resolve("finance9-dev-root.lock"), CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                val lock = checkNotNull(channel.tryLock()) { "Finance9 DEV root is already held by another process" }
                heldRoots.add(root)
                FaroV5Finance9DevCutoverRuntime(root, gate, planVerifier, channel, lock, fault, afterAuthorization)
            } catch (failure: Throwable) {
                runCatching { channel.close() }
                throw failure
            }
        }

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun forceDirectory(directory: Path) {
            FileChannel.open(directory, READ, NOFOLLOW_LINKS).use { it.force(true) }
        }
    }
}
