package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.BootstrapTransitionPolicy
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationActivationResult
import ar.com.faro.v5.maintenance.GenerationPreSwitchAdmissionPort
import ar.com.faro.v5.maintenance.GenerationProjectionReconciliationPort
import ar.com.faro.v5.maintenance.GenerationRuntimeActivationPort
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationSwitchCoordinator
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.maintenance.ValidatedGenerationActivationCoordinator
import ar.com.faro.v5.store.reality.ActiveSqlitePreflight
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
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

/** Journaled isolated DEV cutover: certified SQL14 ACTIVE -> authenticated SQL15 STAGED.
 * No stock Apply, N-016 Undo, productive stock, or Android capability is granted here.
 * This DEV-only selection has separate real ticket provenance (not the C1B fixture seam).
 */
internal class FaroV5Household15Evidence2DevJournaledCutover private constructor(
    private val root: Path,
    private val gate: SingleProcessMaterialWriteGate,
    private val channel: FileChannel,
    private val lease: FileLock,
    private val closedDevHost: () -> Boolean,
    private val fault: (GenerationSwitchFaultPoint) -> Unit,
    private val runtimeHealthy: () -> Boolean,
    private val afterSwitchPrepared: () -> Unit,
    private val planVerifier: PreparedPlanEnvelopeVerificationPort?,
) : AutoCloseable {
    enum class Status { AWAITING_FOREGROUND, ACTIVE_FIFTEEN, ROLLED_BACK, NO_TICKET, BLOCKED }
    data class Outcome(val status: Status, val pointer: ActiveGenerationPointer?)

    private data class Ticket(
        val operation: MaintenanceOperationId,
        val origin: ActiveGenerationPointer,
        val target: StorageGenerationId,
        val source: StorageGenerationId,
        val archiveHash: String,
        val originHash: String,
        val targetHash: String,
        val priorTicketHash: String,
        val priorOperation: MaintenanceOperationId,
    ) {
        init {
            require(origin.generationId != target && source == origin.generationId)
            require(listOf(archiveHash, originHash, targetHash, priorTicketHash).all {
                it.matches(Regex("[0-9a-f]{64}"))
            })
        }

        val selected get() = ActiveGenerationPointer(Math.addExact(origin.sequence, 1), target)
        val rolledBack get() = ActiveGenerationPointer(Math.addExact(origin.sequence, 2), origin.generationId)
    }

    private val pointer = FileActiveGenerationPointer(root)
    private val layout = FileStorageGenerationLayout(root)
    private val journal = FileBootstrapJournal(root)
    private val fence = FileGenerationReplayFence(root)
    private val quiescence = MaterialWriteGateQuiescence(gate)

    fun authorize(
        operation: MaintenanceOperationId,
        target: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
        confirmForeground: () -> Boolean,
    ): Outcome = guarded {
        check(confirmForeground() && pointer.read() == origin && layout.requireStaged(target.descriptor.generationId) == target)
        val bytes = archive.copyOf()
        val source = verifyStage(operation, target, origin, bytes, passphrase)
        val old = layout.requireStaged(origin.generationId)
        check(FaroV5Household14Evidence2DevJournaledCutover.hasCompletedSelection(root, origin)) {
            "SQL15 origin needs a completed SQL14 Evidence2 selection"
        }
        requireHealthyGeneration(old, realityVersion = 14)
        val prior = readPriorEvidence(origin)
        val ticket = Ticket(operation, origin, target.descriptor.generationId, source, hash(bytes),
            FaroV5Household13Evidence2DevJournaledCutover.fingerprint(old, true),
            FaroV5Household13Evidence2DevJournaledCutover.fingerprint(target, false),
            prior.second, prior.first)
        check(confirmForeground())
        check(verifyStage(operation, target, origin, bytes, passphrase) == source)
        requireBackingFiles(ticket, null)
        check(confirmForeground())
        val existing = readTicket(root)
        if (existing == null) {
            check(journal.pending().isEmpty() && journal.read(operation) == null)
            publishTicket(root, ticket)
        } else check(existing == ticket) { "SQL15 ticket cannot be replaced" }
        advanceValidation(ticket)
        Outcome(Status.AWAITING_FOREGROUND, origin)
    }

    fun start(
        operation: MaintenanceOperationId,
        target: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
        confirmForeground: () -> Boolean,
    ): Outcome = guarded {
        val ticket = checkNotNull(readTicket(root))
        check(confirmForeground() && ticket.operation == operation && ticket.origin == origin &&
            target.descriptor.generationId == ticket.target && layout.requireStaged(ticket.target) == target)
        // A C2 VALIDATED journal is pending, so C14's global "no pending journals"
        // check intentionally no longer applies. Revalidate the exact prior
        // completed-ticket digest, its journal and its replay fence instead.
        requirePriorEvidence(ticket)
        val entry = requireEntry(ticket)
        check(entry.phase == BootstrapPhase.VALIDATED && !entry.materialWritesAccepted)
        val bytes = archive.copyOf()
        check(hash(bytes) == ticket.archiveHash && verifyStage(operation, target, origin, bytes, passphrase) == ticket.source)
        check(confirmForeground())
        requireBackingFiles(ticket, entry)
        activate(ticket, recovery = false)
    }

    fun recover(): Outcome = guarded {
        val ticket = readTicket(root) ?: run {
            check(journal.pending().isEmpty())
            return@guarded Outcome(Status.NO_TICKET, pointer.read())
        }
        val entry = journal.read(ticket.operation)
        if (entry == null || entry.phase in setOf(BootstrapPhase.PREPARING, BootstrapPhase.STAGED, BootstrapPhase.VALIDATED)) {
            requireBackingFiles(ticket, entry)
            return@guarded Outcome(Status.AWAITING_FOREGROUND, pointer.read())
        }
        requireBackingFiles(ticket, entry)
        activate(ticket, recovery = true)
    }

    private fun verifyStage(
        op: MaintenanceOperationId,
        target: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): StorageGenerationId = FaroV5Household15PreCutoverReadback(root, quiescence, ::requireLive)
        .verifyOnly(op, target, origin, archive, passphrase)
        .also { evidence ->
            check(evidence.sourceGenerationId == origin.generationId) {
                "SQL15 FBS2v12 source must equal certified selected SQL14 origin"
            }
            check(evidence.stageGenerationId == target.descriptor.generationId)
        }
        .sourceGenerationId

    private fun requireEntry(ticket: Ticket): BootstrapJournalEntry =
        checkNotNull(journal.read(ticket.operation)).also { entry ->
            check(entry.kind == RESTORE && entry.targetGenerationId == ticket.target &&
                entry.previousGenerationId == ticket.origin.generationId &&
                journal.pending().all { it.operationId == ticket.operation })
        }

    /** Reattest the actual C14 COMPLETED ticket even while our C2 journal is pending. */
    private fun readPriorEvidence(selected14: ActiveGenerationPointer):
        Pair<MaintenanceOperationId, String> {
        val path = root.resolve("bootstrap/sql14-evidence2-cutover.ticket")
        check(singleLink(path) && path.parent.toRealPath() == path.parent) {
            "Missing regular certified SQL14 selection ticket"
        }
        val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(4097) }
        check(bytes.size in 65..4096)
        val payload = bytes.copyOfRange(0, bytes.size - 32)
        check(MessageDigest.isEqual(sha(payload), bytes.copyOfRange(payload.size, bytes.size))) {
            "Prior SQL14 ticket digest changed"
        }
        val prior = DataInputStream(ByteArrayInputStream(payload)).use { input ->
            check(input.readUTF() == "FARO_SQL14_EVIDENCE2_CUTOVER_V1")
            val operation = MaintenanceOperationId(input.readUTF())
            val origin = ActiveGenerationPointer(input.readLong(), StorageGenerationId(input.readUTF()))
            val target = StorageGenerationId(input.readUTF())
            val source = StorageGenerationId(input.readUTF())
            val hashes = List(3) { input.readUTF() }
            check(source != target && hashes.all { it.matches(Regex("[0-9a-f]{64}")) } &&
                input.available() == 0)
            check(selected14 == ActiveGenerationPointer(Math.addExact(origin.sequence, 1), target)) {
                "Prior SQL14 ticket not bound to exact original selected pointer"
            }
            Triple(operation, origin, target)
        }
        val entry = checkNotNull(journal.read(prior.first))
        check(entry.kind == RESTORE &&
            entry.previousGenerationId == prior.second.generationId &&
            entry.targetGenerationId == prior.third &&
            entry.phase == BootstrapPhase.COMPLETED && entry.materialWritesAccepted &&
            fence.isInstalled(RESTORE, prior.third) &&
            !fence.isInstalled(MaintenanceOperationKind.MIGRATION, prior.third)) {
            "Prior SQL14 completed journal or replay fence no longer valid"
        }
        return prior.first to hash(bytes)
    }

    private fun requirePriorEvidence(ticket: Ticket) {
        val actual = readPriorEvidence(ticket.origin)
        check(actual.first == ticket.priorOperation && actual.second == ticket.priorTicketHash) {
            "Prior completed SQL14 selection ticket was replaced"
        }
    }

    private fun requireBackingFiles(ticket: Ticket, entry: BootstrapJournalEntry?) {
        requireLive()
        val actual = pointer.read()
        val phase = entry?.phase
        val allowed = when (phase) {
            null, BootstrapPhase.PREPARING, BootstrapPhase.STAGED, BootstrapPhase.VALIDATED -> actual == ticket.origin
            BootstrapPhase.SWITCH_PREPARED -> actual == ticket.origin || actual == ticket.selected
            BootstrapPhase.SWITCHED, BootstrapPhase.HEALTH_VERIFIED, BootstrapPhase.COMPLETED -> actual == ticket.selected
            BootstrapPhase.ROLLBACK_PREPARED -> actual == ticket.selected || actual == ticket.rolledBack
            BootstrapPhase.ROLLED_BACK_POST_SWITCH, BootstrapPhase.ROLLBACK_VERIFIED -> actual == ticket.rolledBack
            else -> false
        }
        check(allowed && (entry == null || requireEntry(ticket) == entry)) { "SQL15 ticket full pointer or journal mismatch" }
        // C14's public completed-selection check rejects *any* pending entry,
        // including this C2 operation. Preserve the genuine prior ticket hash.
        requirePriorEvidence(ticket)
        if (actual == ticket.origin && entry == null) {
            check(FaroV5Household14Evidence2DevJournaledCutover.hasCompletedSelection(root, ticket.origin))
        }
        if (phase == BootstrapPhase.COMPLETED && entry?.materialWritesAccepted == true) return
        if (phase == BootstrapPhase.ROLLBACK_VERIFIED) {
            check(entry != null && !entry.materialWritesAccepted && !fence.isInstalled(RESTORE, ticket.target))
            return
        }
        val target = layout.requireStaged(ticket.target)
        val old = layout.requireStaged(ticket.origin.generationId)
        // A readonly SQL14 reopen may leave an empty WAL after a verified rollback.
        // The full pointer and journal above prove that this target was selected.
        val targetWasSelected = actual == ticket.selected || actual == ticket.rolledBack
        check(FaroV5Household13Evidence2DevJournaledCutover.fingerprint(target, targetWasSelected) == ticket.targetHash)
        check(FaroV5Household13Evidence2DevJournaledCutover.fingerprint(old, true) == ticket.originHash)
        check(pointer.read() == actual && (entry == null || journal.read(ticket.operation) == entry))
        requireLive()
    }

    private fun advanceValidation(ticket: Ticket) {
        var current = journal.read(ticket.operation) ?: BootstrapJournalEntry(
            ticket.operation, RESTORE, ticket.origin.generationId, ticket.target, BootstrapPhase.PREPARING,
        ).also(journal::create)
        requireBackingFiles(ticket, current)
        for ((from, to) in listOf(
            BootstrapPhase.PREPARING to BootstrapPhase.STAGED,
            BootstrapPhase.STAGED to BootstrapPhase.VALIDATED,
        )) {
            if (current.phase == from) {
                requireBackingFiles(ticket, current)
                val next = BootstrapTransitionPolicy.transition(current, to)
                check(journal.compareAndSet(current, next))
                current = next
            }
        }
        requireBackingFiles(ticket, current)
        check(current.phase == BootstrapPhase.VALIDATED && !current.materialWritesAccepted)
    }

    private fun activate(ticket: Ticket, recovery: Boolean): Outcome {
        val coordinator = ValidatedGenerationActivationCoordinator(
            journal, pointer, layout, quiescence, GenerationSwitchCoordinator(journal, pointer, layout, fault),
            runtime = GenerationRuntimeActivationPort { kind, id ->
                check(kind == RESTORE)
                val entry = requireEntry(ticket)
                requireBackingFiles(ticket, entry)
                when (id) {
                    ticket.target -> {
                        check(pointer.read() == ticket.selected)
                        // Stage semantics were authenticated before CAS. Reopen READONLY with WAL support.
                        requireHealthyGeneration(layout.requireStaged(ticket.target), realityVersion = 15)
                        requireBackingFiles(ticket, entry)
                        runtimeHealthy()
                    }
                    ticket.origin.generationId -> {
                        check(pointer.read() == ticket.rolledBack)
                        requireHealthyGeneration(layout.requireStaged(id), realityVersion = 14)
                        true
                    }
                    else -> false
                }
            },
            replayFence = fence,
            projections = GenerationProjectionReconciliationPort { kind, id ->
                check(kind == RESTORE)
                val entry = requireEntry(ticket)
                requireBackingFiles(ticket, entry)
                check((id == ticket.target && pointer.read() == ticket.selected) ||
                    (id == ticket.origin.generationId && pointer.read() == ticket.rolledBack))
            },
            preSwitchAdmission = GenerationPreSwitchAdmissionPort { kind, id ->
                if (kind != RESTORE || id != ticket.target) false else {
                    val entry = requireEntry(ticket)
                    if (!recovery && entry.phase == BootstrapPhase.SWITCH_PREPARED && pointer.read() == ticket.origin) {
                        afterSwitchPrepared()
                    }
                    requireBackingFiles(ticket, entry)
                    true
                }
            },
        )
        return when (if (recovery) coordinator.recover(ticket.operation) else coordinator.activate(ticket.operation)) {
            is GenerationActivationResult.Activated -> {
                checkCompleted(root, ticket)
                Outcome(Status.ACTIVE_FIFTEEN, ticket.selected)
            }
            is GenerationActivationResult.RolledBack -> {
                val entry = requireEntry(ticket)
                requireBackingFiles(ticket, entry)
                check(entry.phase == BootstrapPhase.ROLLBACK_VERIFIED && !entry.materialWritesAccepted &&
                    !fence.isInstalled(RESTORE, ticket.target))
                Outcome(Status.ROLLED_BACK, ticket.rolledBack)
            }
            is GenerationActivationResult.Blocked -> Outcome(Status.BLOCKED, null)
        }
    }

    private fun guarded(block: () -> Outcome): Outcome = try {
        gate.withQuiescedMaterialWrites {
            requireLive()
            block()
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        Outcome(Status.BLOCKED, null)
    } catch (_: Exception) {
        Outcome(Status.BLOCKED, null)
    }

    private fun currentLease(): Boolean = channel.isOpen && lease.isValid && closedDevHost()
    private fun requireLive() = check(currentLease()) { "SQL15 cutover requires an exclusive closed DEV host" }

    override fun close() = gate.withQuiescedMaterialWrites { channel.close() }

    companion object {
        private val RESTORE = MaintenanceOperationKind.RESTORE
        private const val TICKET_VERSION = "FARO_SQL15_EVIDENCE2_CUTOVER_V1"
        private fun ticketPath(root: Path) = root.resolve("bootstrap/sql15-evidence2-cutover.ticket")

        fun acquire(
            storageRoot: Path,
            gate: SingleProcessMaterialWriteGate,
            closedDevHost: () -> Boolean,
            fault: (GenerationSwitchFaultPoint) -> Unit = {},
            runtimeHealthy: () -> Boolean = { true },
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
            afterSwitchPrepared: () -> Unit = {},
        ): FaroV5Household15Evidence2DevJournaledCutover {
            check(Files.isDirectory(storageRoot, NOFOLLOW_LINKS) && closedDevHost())
            val root = storageRoot.toRealPath()
            val path = root.resolve("finance9-dev-root.lock")
            val channel = FileChannel.open(path, CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                check(singleLink(path))
                val lease = checkNotNull(channel.tryLock())
                return FaroV5Household15Evidence2DevJournaledCutover(
                    root, gate, channel, lease, closedDevHost, fault, runtimeHealthy, afterSwitchPrepared,
                    planVerifier,
                )
            } catch (failure: Throwable) {
                channel.close()
                throw failure
            }
        }

        internal fun hasVerifiedRollbackTo(
            root: Path,
            certifiedOrigin: ActiveGenerationPointer,
            selected: ActiveGenerationPointer,
        ): Boolean = runCatching {
            val ticket = checkNotNull(readTicket(root))
            val journal = FileBootstrapJournal(root)
            val entry = checkNotNull(journal.read(ticket.operation))
            val fence = FileGenerationReplayFence(root)
            check(ticket.origin == certifiedOrigin && ticket.rolledBack == selected &&
                FileActiveGenerationPointer(root).read() == selected && journal.pending().isEmpty() &&
                entry.kind == RESTORE && entry.previousGenerationId == certifiedOrigin.generationId &&
                entry.targetGenerationId == ticket.target && entry.phase == BootstrapPhase.ROLLBACK_VERIFIED &&
                !entry.materialWritesAccepted && !fence.isInstalled(RESTORE, ticket.target))
            true
        }.getOrDefault(false)

        internal fun hasCompletedSelection(root: Path, selected: ActiveGenerationPointer): Boolean =
            runCatching {
                val ticket = checkNotNull(readTicket(root))
                check(ticket.selected == selected)
                checkCompleted(root, ticket)
                true
            }.getOrDefault(false)

        private fun checkCompleted(root: Path, ticket: Ticket) {
            val journal = FileBootstrapJournal(root)
            val entry = checkNotNull(journal.read(ticket.operation))
            val fence = FileGenerationReplayFence(root)
            check(readTicket(root) == ticket && entry.kind == RESTORE &&
                entry.previousGenerationId == ticket.origin.generationId && entry.targetGenerationId == ticket.target &&
                entry.phase == BootstrapPhase.COMPLETED && entry.materialWritesAccepted &&
                journal.pending().isEmpty() && FileActiveGenerationPointer(root).read() == ticket.selected &&
                fence.isInstalled(RESTORE, ticket.target) &&
                !fence.isInstalled(MaintenanceOperationKind.MIGRATION, ticket.target))
        }

        private fun requireHealthyGeneration(stage: StagedStorageGeneration, realityVersion: Int) {
            check(realityVersion == 14 || realityVersion == 15)
            val historical = listOf("household_stock_receipt", "household_confirmed_purchase",
                "household_stock_compensation", "household_need", "finance_state",
                "household_stock_reservation_event")
            val realityTables = if (realityVersion == 15)
                historical + "household_stock_consumption_event" else historical
            ActiveSqlitePreflight.requireAll(stage.directory, listOf(
                StagedSqlitePreflight.Schema(GenerationStoreFiles.REALITY, realityVersion, realityTables),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.COMMERCIAL_EVIDENCE, 2,
                    listOf("commercial_evidence", "commercial_evidence_quote_terms")),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.WEB_EVIDENCE, 2, listOf("web_evidence")),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.AUXILIARY, 4, listOf("notice_attention_receipt")),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.WORKSPACE, 4, listOf("workspace_conversation")),
            ))
        }

        private fun singleLink(file: Path): Boolean = Files.isRegularFile(file, NOFOLLOW_LINKS) &&
            (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L

        private fun publishTicket(root: Path, ticket: Ticket) {
            val path = ticketPath(root)
            check(path.parent.toRealPath() == path.parent && Files.notExists(path, NOFOLLOW_LINKS))
            val buffer = ByteArrayOutputStream()
            DataOutputStream(buffer).use { out ->
                out.writeUTF(TICKET_VERSION)
                out.writeUTF(ticket.operation.value)
                out.writeLong(ticket.origin.sequence)
                out.writeUTF(ticket.origin.generationId.value)
                out.writeUTF(ticket.target.value)
                out.writeUTF(ticket.source.value)
                out.writeUTF(ticket.archiveHash)
                out.writeUTF(ticket.originHash)
                out.writeUTF(ticket.targetHash)
                out.writeUTF(ticket.priorTicketHash)
                out.writeUTF(ticket.priorOperation.value)
            }
            val payload = buffer.toByteArray()
            val tmp = Files.createTempFile(path.parent, ".sql15-ticket-", ".tmp")
            try {
                Files.write(tmp, payload + sha(payload), WRITE, NOFOLLOW_LINKS)
                FileChannel.open(tmp, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
                Files.move(tmp, path, ATOMIC_MOVE)
                FileChannel.open(path.parent, READ, NOFOLLOW_LINKS).use { it.force(true) }
                check(readTicket(root) == ticket)
            } finally {
                Files.deleteIfExists(tmp)
            }
        }

        private fun readTicket(root: Path): Ticket? {
            val path = ticketPath(root)
            if (Files.notExists(path, NOFOLLOW_LINKS)) return null
            check(singleLink(path) && path.parent.toRealPath() == path.parent)
            val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(4097) }
            check(bytes.size in 65..4096)
            val payload = bytes.copyOfRange(0, bytes.size - 32)
            check(MessageDigest.isEqual(sha(payload), bytes.copyOfRange(payload.size, bytes.size)))
            return DataInputStream(ByteArrayInputStream(payload)).use { input ->
                check(input.readUTF() == TICKET_VERSION)
                val operation = MaintenanceOperationId(input.readUTF())
                val origin = ActiveGenerationPointer(input.readLong(), StorageGenerationId(input.readUTF()))
                Ticket(operation, origin, StorageGenerationId(input.readUTF()), StorageGenerationId(input.readUTF()),
                    input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(),
                    MaintenanceOperationId(input.readUTF())).also { check(input.available() == 0) }
            }
        }

        private fun sha(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
        private fun hash(bytes: ByteArray): String = hex(sha(bytes))
        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
