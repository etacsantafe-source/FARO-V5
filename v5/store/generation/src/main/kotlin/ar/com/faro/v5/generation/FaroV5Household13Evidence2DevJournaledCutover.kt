package ar.com.faro.v5.generation

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

/** Journaled isolated DEV cutover: verified SQL12 ACTIVE -> authenticated SQL13 STAGED.
 * No stock Apply, N-016 Undo, productive stock, or Android capability is granted here.
 */
internal class FaroV5Household13Evidence2DevJournaledCutover private constructor(
    private val root: Path,
    private val gate: SingleProcessMaterialWriteGate,
    private val channel: FileChannel,
    private val lease: FileLock,
    private val closedDevHost: () -> Boolean,
    private val fault: (GenerationSwitchFaultPoint) -> Unit,
    private val runtimeHealthy: () -> Boolean,
    private val afterSwitchPrepared: () -> Unit,
) : AutoCloseable {
    enum class Status { AWAITING_FOREGROUND, ACTIVE_THIRTEEN, ROLLED_BACK, NO_TICKET, BLOCKED }
    data class Outcome(val status: Status, val pointer: ActiveGenerationPointer?)

    private data class Ticket(
        val operation: MaintenanceOperationId,
        val origin: ActiveGenerationPointer,
        val target: StorageGenerationId,
        val source: StorageGenerationId,
        val archiveHash: String,
        val originHash: String,
        val targetHash: String,
    ) {
        init {
            require(origin.generationId != target && source != target)
            require(listOf(archiveHash, originHash, targetHash).all { it.matches(Regex("[0-9a-f]{64}")) })
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
        check(FaroV5Household12Evidence2DevJournaledCutover.hasCompletedSelection(root, origin)) {
            "SQL13 origin needs a completed SQL12 Evidence2 selection"
        }
        requireOriginHealthy(old)
        val ticket = Ticket(operation, origin, target.descriptor.generationId, source, hash(bytes),
            fingerprint(old, true), fingerprint(target, false))
        check(confirmForeground())
        check(verifyStage(operation, target, origin, bytes, passphrase) == source)
        requireBackingFiles(ticket, null)
        check(confirmForeground())
        val existing = readTicket(root)
        if (existing == null) {
            check(journal.pending().isEmpty() && journal.read(operation) == null)
            publishTicket(root, ticket)
        } else check(existing == ticket) { "SQL13 ticket cannot be replaced" }
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
        check(FaroV5Household12Evidence2DevJournaledCutover.hasCompletedSelection(root, origin)) {
            "SQL13 origin selection changed"
        }
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
    ) = FaroV5Household13V10StagedReplay(root, quiescence, ::requireLive)
        .verifyForEvidence2Cutover(op, target, origin, archive, passphrase)

    private fun requireEntry(ticket: Ticket): BootstrapJournalEntry =
        checkNotNull(journal.read(ticket.operation)).also { entry ->
            check(entry.kind == RESTORE && entry.targetGenerationId == ticket.target &&
                entry.previousGenerationId == ticket.origin.generationId &&
                journal.pending().all { it.operationId == ticket.operation })
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
        check(allowed && (entry == null || requireEntry(ticket) == entry)) { "SQL13 ticket full pointer or journal mismatch" }
        if (phase == BootstrapPhase.COMPLETED && entry?.materialWritesAccepted == true) return
        val target = layout.requireStaged(ticket.target)
        val old = layout.requireStaged(ticket.origin.generationId)
        // A readonly SQL13 reopen may leave an empty WAL after a verified rollback.
        // The full pointer and journal above prove that this target was selected.
        val targetWasSelected = actual == ticket.selected || actual == ticket.rolledBack
        check(fingerprint(target, targetWasSelected) == ticket.targetHash)
        check(fingerprint(old, true) == ticket.originHash)
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
                        FaroV5GenerationStoreGraph.openActiveHousehold13ForHeldDevHost(
                            layout.requireStaged(ticket.target), gate,
                            { currentLease() && pointer.read() == ticket.selected }, null,
                        ).use { graph ->
                            graph.reality.snapshotActiveHousehold13RowsForTrustedHost()
                            graph.verifyHealthy() && runtimeHealthy()
                        }
                    }
                    ticket.origin.generationId -> {
                        check(pointer.read() == ticket.rolledBack)
                        requireOriginHealthy(layout.requireStaged(id))
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
                Outcome(Status.ACTIVE_THIRTEEN, ticket.selected)
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
    private fun requireLive() = check(currentLease()) { "SQL13 cutover requires an exclusive closed DEV host" }

    override fun close() = gate.withQuiescedMaterialWrites { channel.close() }

    companion object {
        private val RESTORE = MaintenanceOperationKind.RESTORE
        private const val TICKET_VERSION = "FARO_SQL13_EVIDENCE2_CUTOVER_V1"
        private fun ticketPath(root: Path) = root.resolve("bootstrap/sql13-evidence2-cutover.ticket")

        fun acquire(
            storageRoot: Path,
            gate: SingleProcessMaterialWriteGate,
            closedDevHost: () -> Boolean,
            fault: (GenerationSwitchFaultPoint) -> Unit = {},
            runtimeHealthy: () -> Boolean = { true },
            afterSwitchPrepared: () -> Unit = {},
        ): FaroV5Household13Evidence2DevJournaledCutover {
            check(Files.isDirectory(storageRoot, NOFOLLOW_LINKS) && closedDevHost())
            val root = storageRoot.toRealPath()
            val path = root.resolve("finance9-dev-root.lock")
            val channel = FileChannel.open(path, CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                check(singleLink(path))
                val lease = checkNotNull(channel.tryLock())
                return FaroV5Household13Evidence2DevJournaledCutover(
                    root, gate, channel, lease, closedDevHost, fault, runtimeHealthy, afterSwitchPrepared,
                )
            } catch (failure: Throwable) {
                channel.close()
                throw failure
            }
        }

        fun reopenCompleted(
            storageRoot: Path, selected: ActiveGenerationPointer,
            gate: SingleProcessMaterialWriteGate, ownerAdmission: () -> Boolean,
        ): FaroV5Household13ActiveDevReadHost {
            val root = storageRoot.toRealPath()
            val ticket = checkNotNull(readTicket(root))
            check(selected == ticket.selected)
            return FaroV5Household13ActiveDevReadHost.reopen(root, selected, gate, {
                ownerAdmission() && runCatching {
                    checkCompleted(root, ticket)
                    true
                }.getOrDefault(false)
            })
        }

        internal fun hasCompletedSelection(root: Path, selected: ActiveGenerationPointer): Boolean =
            runCatching {
                val ticket = checkNotNull(readTicket(root))
                check(ticket.selected == selected)
                checkCompleted(root, ticket)
                true
            }.getOrDefault(false)

        private fun checkCompleted(root: Path, ticket: Ticket) {
            val entry = checkNotNull(FileBootstrapJournal(root).read(ticket.operation))
            val fence = FileGenerationReplayFence(root)
            check(readTicket(root) == ticket && entry.kind == RESTORE &&
                entry.previousGenerationId == ticket.origin.generationId && entry.targetGenerationId == ticket.target &&
                entry.phase == BootstrapPhase.COMPLETED && entry.materialWritesAccepted &&
                FileActiveGenerationPointer(root).read() == ticket.selected &&
                fence.isInstalled(RESTORE, ticket.target) &&
                !fence.isInstalled(MaintenanceOperationKind.MIGRATION, ticket.target))
        }

        private fun requireOriginHealthy(origin: StagedStorageGeneration) {
            ActiveSqlitePreflight.requireAll(origin.directory, listOf(
                StagedSqlitePreflight.Schema(GenerationStoreFiles.REALITY, 12,
                    listOf("household_stock_receipt", "household_confirmed_purchase", "household_need", "finance_state")),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.COMMERCIAL_EVIDENCE, 2,
                    listOf("commercial_evidence", "commercial_evidence_quote_terms")),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.WEB_EVIDENCE, 2, listOf("web_evidence")),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.AUXILIARY, 4, listOf("notice_attention_receipt")),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.WORKSPACE, 4, listOf("workspace_conversation")),
            ))
        }

        private fun fingerprint(stage: StagedStorageGeneration, allowEmptyActiveWal: Boolean): String {
            val dir = stage.directory
            check(dir.toRealPath() == dir)
            val digest = MessageDigest.getInstance("SHA-256")
            for (name in GenerationStoreFiles.WORKSPACE_V2) {
                val file = dir.resolve(name)
                check(singleLink(file))
                for (suffix in listOf("-wal", "-shm", "-journal")) {
                    val sidecar = dir.resolve(name + suffix)
                    if (Files.notExists(sidecar, NOFOLLOW_LINKS)) continue
                    check(allowEmptyActiveWal && suffix != "-journal" && singleLink(sidecar) &&
                        (suffix != "-wal" || Files.size(sidecar) <= 32L)) { "SQL13 cutover rejects unverified WAL frames" }
                }
                val member = MessageDigest.getInstance("SHA-256")
                Files.newInputStream(file, NOFOLLOW_LINKS).use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        member.update(buffer, 0, n)
                    }
                }
                digest.update(member.digest())
            }
            return hex(digest.digest())
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
            }
            val payload = buffer.toByteArray()
            val tmp = Files.createTempFile(path.parent, ".sql13-ticket-", ".tmp")
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
                    input.readUTF(), input.readUTF(), input.readUTF()).also { check(input.available() == 0) }
            }
        }

        private fun sha(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
        private fun hash(bytes: ByteArray): String = hex(sha(bytes))
        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
