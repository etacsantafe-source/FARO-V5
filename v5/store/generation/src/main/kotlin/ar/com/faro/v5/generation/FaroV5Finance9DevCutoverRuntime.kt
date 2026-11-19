package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.BootstrapTransitionPolicy
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
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
 * Maintenance keeps the original request durable. A subsequent mount can
 * retain one committed graph for this lease.
 */
class FaroV5Finance9DevCutoverRuntime private constructor(
    private val root: Path,
    private val gate: SingleProcessMaterialWriteGate,
    private val planVerifier: PreparedPlanEnvelopeVerificationPort,
    private val channel: FileChannel,
    private val lock: FileLock,
    private val fault: (GenerationSwitchFaultPoint) -> Unit,
    private val afterAuthorization: () -> Unit,
    private val targetRuntimeHealthy: () -> Boolean,
) : AutoCloseable {
    enum class Status { ORIGIN_RETAINED, ACTIVE_NINE, ROLLED_BACK }

    data class Selection(
        val status: Status,
        val pointer: ActiveGenerationPointer,
        val auxiliary4: Boolean = false,
    )

    private data class Intent(
        val operationId: MaintenanceOperationId,
        val origin: ActiveGenerationPointer,
        val target: StorageGenerationDescriptor,
        val sourceFingerprint: String? = null,
        val auxiliary4: Boolean = false,
    )

    private val ticket = root.resolve("bootstrap").resolve("finance9-dev-cutover-v1.bin")
    private val auxiliaryTicket = root.resolve("bootstrap").resolve("auxiliary4-dev-cutover-v1.bin")
    private var closed = false
    private var mountedGraph: FaroV5GenerationStoreGraph? = null

    fun start(
        operationId: MaintenanceOperationId,
        target: StorageGenerationDescriptor,
        origin: ActiveGenerationPointer,
        finishedAtEpochMillis: Long,
        confirmInForeground: () -> Boolean,
    ): Selection = startUpgrade(
        operationId, target, origin, finishedAtEpochMillis, confirmInForeground, auxiliary4 = false,
    )

    fun startAuxiliary4(
        operationId: MaintenanceOperationId,
        target: StorageGenerationDescriptor,
        origin: ActiveGenerationPointer,
        finishedAtEpochMillis: Long,
        confirmInForeground: () -> Boolean,
    ): Selection = startUpgrade(
        operationId, target, origin, finishedAtEpochMillis, confirmInForeground, auxiliary4 = true,
    )

    private fun startUpgrade(
        operationId: MaintenanceOperationId,
        target: StorageGenerationDescriptor,
        origin: ActiveGenerationPointer,
        finishedAtEpochMillis: Long,
        confirmInForeground: () -> Boolean,
        auxiliary4: Boolean,
    ): Selection = gate.withQuiescedMaterialWrites {
        requireLive()
        check(mountedGraph == null) { "Close the DEV graph before maintenance" }
        check(confirmInForeground()) { "Finance9 cutover requires explicit foreground confirmation" }
        val previousAuxiliary = readIntent(auxiliary4 = true)
        if (auxiliary4) {
            if (previousAuxiliary != null) check(recover()?.status != Status.ACTIVE_NINE)
        } else {
            check(previousAuxiliary == null && recover()?.status != Status.ACTIVE_NINE) {
                "Finance9 root already finished cutover"
            }
        }
        val pointer = FileActiveGenerationPointer(root)
        check(pointer.read() == origin && target.generationId != origin.generationId)
        val journal = FileBootstrapJournal(root)
        check(journal.pending().isEmpty() && journal.read(operationId) == null)

        val intent = Intent(operationId, origin, target, auxiliary4 = auxiliary4)
        publish(intent)
        val preparation = stagePreparation()
        val staged = preparation.prepare(operationId, target, origin, finishedAtEpochMillis, auxiliary4)
        preparation.requireSourceUnchanged(staged)
        if (!confirmInForeground()) return@withQuiescedMaterialWrites retainOrigin(intent)

        val authorized = intent.copy(sourceFingerprint = staged.sourceFingerprint)
        check(readIntent(auxiliary4) == intent) { "DEV cutover PREPARE record changed" }
        publish(authorized)
        afterAuthorization()
        activate(authorized, recovery = false)
    }

    fun recover(): Selection? = gate.withQuiescedMaterialWrites {
        requireLive()
        check(mountedGraph == null) { "Close the DEV graph before recovery" }
        val intent = readIntent(auxiliary4 = true) ?: readIntent() ?: return@withQuiescedMaterialWrites null
        if (intent.sourceFingerprint == null) retainOrigin(intent)
        else activate(intent, recovery = true)
    }

    /** Opens only a committed target under this lease and retains ownership until close. */
    fun openCommittedFinance9(selection: Selection): FaroV5GenerationStoreGraph =
        gate.withQuiescedMaterialWrites {
            requireLive()
            check(mountedGraph == null) { "This DEV root already has a mounted graph" }
            check((selection.status == Status.ACTIVE_NINE || selection.auxiliary4) && isCurrent(selection))
            val intent = checkNotNull(readIntent(selection.auxiliary4))
            val stage = FileStorageGenerationLayout(root).requireStaged(selection.pointer.generationId)
            val selectedTarget = selection.status == Status.ACTIVE_NINE
            if (selectedTarget) check(stage.descriptor == intent.target)
            val graph = if (selectedTarget && selection.auxiliary4) {
                FaroV5GenerationStoreGraph.reopenVerifiedActiveFinance9Auxiliary4Dev(
                    root, stage, selection.pointer.sequence, planVerifier, gate,
                )
            } else {
                FaroV5GenerationStoreGraph.reopenVerifiedActiveFinance9Dev(
                    root, stage, selection.pointer.sequence, planVerifier, gate,
                )
            }
            try {
                check(isCurrent(selection)) { "Finance9 selection changed during mount" }
                mountedGraph = graph
                graph
            } catch (failure: Throwable) {
                graph.close()
                throw failure
            }
        }

    /** Rechecks session readiness from durable metadata without opening another graph. */
    fun isCurrent(selection: Selection): Boolean = runCatching {
        gate.withMaterialWrite {
            requireLive()
            val intent = checkNotNull(readIntent(selection.auxiliary4))
            val bootstrap = ticket.parent
            check(listOf("active", "journal").all { Files.isDirectory(bootstrap.resolve(it), NOFOLLOW_LINKS) })
            val pointer = FileActiveGenerationPointer(root)
            val journal = FileBootstrapJournal(root)
            val record = journal.read(intent.operationId)
            check(pointer.read() == selection.pointer && journal.pending().isEmpty())
            check(record == null || (record.kind == MaintenanceOperationKind.RESTORE &&
                record.previousGenerationId == intent.origin.generationId &&
                record.targetGenerationId == intent.target.generationId))

            when (selection.status) {
                Status.ACTIVE_NINE -> intent.sourceFingerprint != null &&
                    selection.pointer == ActiveGenerationPointer(
                        Math.addExact(intent.origin.sequence, 1L), intent.target.generationId,
                    ) && record != null && record.phase == BootstrapPhase.COMPLETED && record.materialWritesAccepted &&
                    hasReplayFence(MaintenanceOperationKind.RESTORE, intent.target.generationId)
                Status.ORIGIN_RETAINED -> intent.sourceFingerprint == null &&
                    selection.pointer == intent.origin &&
                    (record == null || record.phase == BootstrapPhase.ABORTED_PRE_SWITCH)
                Status.ROLLED_BACK -> intent.sourceFingerprint != null &&
                    selection.pointer == ActiveGenerationPointer(
                        Math.addExact(intent.origin.sequence, 2L), intent.origin.generationId,
                    ) && record != null && record.phase == BootstrapPhase.ROLLBACK_VERIFIED && !record.materialWritesAccepted &&
                    !hasReplayFence(MaintenanceOperationKind.RESTORE, intent.target.generationId) &&
                    !hasReplayFence(MaintenanceOperationKind.MIGRATION, intent.target.generationId)
            }
        }
    }.getOrDefault(false)

    private fun hasReplayFence(kind: MaintenanceOperationKind, targetId: StorageGenerationId): Boolean {
        val directory = ticket.parent.resolve("replay-fences")
        if (Files.notExists(directory, NOFOLLOW_LINKS)) return false
        check(Files.isDirectory(directory, NOFOLLOW_LINKS))
        return FileGenerationReplayFence(root).isInstalled(kind, targetId)
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
        return Selection(Status.ORIGIN_RETAINED, intent.origin, intent.auxiliary4)
    }

    private fun activate(intent: Intent, recovery: Boolean): Selection {
        val pointer = FileActiveGenerationPointer(root)
        val staged = FileStorageGenerationLayout(root).requireStaged(intent.target.generationId)
        check(staged.descriptor == intent.target)
        val prepared = FaroV5Finance9DevStagePreparation.Prepared(
            intent.operationId, intent.origin, staged, checkNotNull(intent.sourceFingerprint), intent.auxiliary4,
        )
        if (pointer.read() == intent.origin) {
            stagePreparation().requireSourceUnchanged(prepared, resumeSwitchBeforePublish = recovery)
        }
        val cutover = FaroV5Finance9DevCutoverOwner(
            root, gate, planVerifier, ::requireLive, fault,
            targetRuntimeHealthy = targetRuntimeHealthy, auxiliary4 = intent.auxiliary4,
        )
        val result = if (recovery) cutover.recover(intent.operationId, staged, intent.origin)
            else cutover.activateValidated(intent.operationId, staged, intent.origin)
        val status = when (result) {
            is GenerationActivationResult.Activated -> Status.ACTIVE_NINE
            is GenerationActivationResult.RolledBack -> Status.ROLLED_BACK
            is GenerationActivationResult.Blocked -> error("Finance9 exact recovery blocked: ${result.reason}")
        }
        return Selection(status, checkNotNull(pointer.read()), intent.auxiliary4)
    }

    private fun stagePreparation() = FaroV5Finance9DevStagePreparation(root, gate, ::requireLive)

    private fun publish(intent: Intent) {
        requireLive()
        val destination = if (intent.auxiliary4) auxiliaryTicket else ticket
        check(Files.isDirectory(destination.parent, NOFOLLOW_LINKS))
        val payload = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(if (intent.auxiliary4) AUXILIARY_RECORD_VERSION else RECORD_VERSION)
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
        val temporary = Files.createTempFile(destination.parent, ".faro-dev-cutover-", ".tmp")
        try {
            FileChannel.open(temporary, WRITE, NOFOLLOW_LINKS).use { output ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) output.write(buffer)
                output.force(true)
            }
            Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
            forceDirectory(destination.parent)
        } finally {
            Files.deleteIfExists(temporary)
        }
        check(readIntent(intent.auxiliary4) == intent)
    }

    private fun readIntent(auxiliary4: Boolean = false): Intent? {
        val file = if (auxiliary4) auxiliaryTicket else ticket
        if (Files.notExists(file, NOFOLLOW_LINKS)) return null
        check(Files.isDirectory(file.parent, NOFOLLOW_LINKS) &&
            Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file) in 33L..RECORD_LIMIT)
        val bytes = Files.readAllBytes(file)
        val offset = bytes.size - 32
        val payload = bytes.copyOfRange(0, offset)
        check(MessageDigest.isEqual(sha256(payload), bytes.copyOfRange(offset, bytes.size))) {
            "Finance9 DEV cutover record checksum mismatch"
        }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            check(input.readUTF() == (if (auxiliary4) AUXILIARY_RECORD_VERSION else RECORD_VERSION))
            val operationId = MaintenanceOperationId(input.readUTF())
            val originId = StorageGenerationId(input.readUTF())
            val origin = ActiveGenerationPointer(input.readLong(), originId)
            val target = StorageGenerationDescriptor(
                StorageGenerationId(input.readUTF()), input.readUTF(), input.readLong(),
            )
            val fingerprint = input.readUTF().ifEmpty { null }
            check(input.available() == 0 && target.generationId != origin.generationId &&
                (fingerprint == null || fingerprint.matches(Regex("[0-9a-f]{64}"))))
            Intent(operationId, origin, target, fingerprint, auxiliary4)
        }
    }

    private fun requireLive() {
        check(!closed && lock.isValid && channel.isOpen) { "Finance9 DEV root lease has ended" }
    }

    override fun close() = gate.withQuiescedMaterialWrites {
        synchronized(heldRoots) {
            if (!closed) {
                mountedGraph?.close()
                mountedGraph = null
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
        private const val AUXILIARY_RECORD_VERSION = "FARO_AUXILIARY4_DEV_CUTOVER_V1"
        private val heldRoots = mutableSetOf<Path>()

        fun acquire(
            storageRoot: Path,
            gate: SingleProcessMaterialWriteGate,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
        ): FaroV5Finance9DevCutoverRuntime = acquire(storageRoot, gate, planVerifier, {}, {})

        internal fun acquire(
            storageRoot: Path,
            gate: SingleProcessMaterialWriteGate,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            fault: (GenerationSwitchFaultPoint) -> Unit,
            afterAuthorization: () -> Unit,
            targetRuntimeHealthy: () -> Boolean = { true },
        ): FaroV5Finance9DevCutoverRuntime = synchronized(heldRoots) {
            check(Files.isDirectory(storageRoot, NOFOLLOW_LINKS))
            val root = storageRoot.toRealPath()
            check(root !in heldRoots) { "Finance9 DEV root is already held in this process" }
            val channel = FileChannel.open(root.resolve("finance9-dev-root.lock"), CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                val lock = checkNotNull(channel.tryLock()) { "Finance9 DEV root is already held by another process" }
                heldRoots.add(root)
                FaroV5Finance9DevCutoverRuntime(
                    root, gate, planVerifier, channel, lock, fault, afterAuthorization, targetRuntimeHealthy,
                )
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
