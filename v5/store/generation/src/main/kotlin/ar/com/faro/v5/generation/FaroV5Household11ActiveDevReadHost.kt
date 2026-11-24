package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.ActiveSqlitePreflight
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

/**
 * W05-A1B3C1C: exclusively DEV closed-host read-only reopening of an ALREADY
 * committed SQL11 cutover. Never creates a database, switches a pointer or
 * publishes a Reality/Workspace graph or an Apply capability.
 */
class FaroV5Household11ActiveDevReadHost private constructor() {
    enum class Status { ACTIVE_ELEVEN, AWAITING_FOREGROUND, NO_TICKET, ROLLED_BACK, BLOCKED }
    data class OpenResult(val status: Status, val session: Session? = null)
    data class AuthorizedRows(
        val needRevision: Long, val needCount: Int,
        val receiptRevision: Long, val receiptCount: Int,
    )
    internal data class Ticket(
        val operation: MaintenanceOperationId,
        val origin: ActiveGenerationPointer,
        val source: StorageGenerationId,
        val target: StorageGenerationId,
        val ciphertextSha256: String,
        val originSha256: String,
        val targetSha256: String,
        val expected: AuthorizedRows,
    ) {
        val selected: ActiveGenerationPointer
            get() = ActiveGenerationPointer(Math.addExact(origin.sequence, 1L), target)
    }

    class Session internal constructor(
        private val root: Path,
        private val gate: SingleProcessMaterialWriteGate,
        private val channel: FileChannel,
        private val lock: FileLock,
        private val pinned: ActiveGenerationPointer,
        private val ticket: Ticket,
        private val hostClosed: () -> Boolean,
    ) : AutoCloseable {
        @Volatile private var closed = false

        fun expectedRows(): AuthorizedRows = ticket.expected

        /** The callback belongs ONLY to the trusted DEV Android read-only adapter. */
        @Synchronized
        fun <T> readPinnedReality(readOnlyReader: (Path) -> T): T =
            gate.withQuiescedMaterialWrites {
                requireLive()
                requireComplete()
                val staged = FileStorageGenerationLayout(root).requireStaged(pinned.generationId)
                check(ExistingGenerationFormatReader.requireAndRead(staged) ==
                    GenerationStoreFormat.WORKSPACE_V2)
                ActiveSqlitePreflight.requireAll(staged.directory, sql11Schemas())
                check(FileActiveGenerationPointer(root).read() == pinned)
                val result = readOnlyReader(staged.directory.resolve(GenerationStoreFiles.REALITY))
                requireLive()
                requireComplete()
                result
            }

        private fun requireComplete() = verifyCompleted(root, pinned, ticket)

        private fun requireLive() {
            check(!closed && channel.isOpen && lock.isValid && hostClosed()) {
                "SQL11 DEV read lease revoked or host no longer closed"
            }
        }

        @Synchronized
        override fun close() {
            if (!closed) {
                closed = true
                try { lock.release() } finally { channel.close() }
            }
        }
    }

    companion object {
        fun reopen(
            storageRoot: Path,
            gate: SingleProcessMaterialWriteGate,
            hostClosed: () -> Boolean,
        ): OpenResult {
            if (!hostClosed() || Files.isSymbolicLink(storageRoot) ||
                !Files.isDirectory(storageRoot, NOFOLLOW_LINKS)) return OpenResult(Status.BLOCKED)
            val root = storageRoot.toRealPath()
            // A COMPLETED/accepted generation is ACTIVE, not a sealed stage.
            // Re-running C1B's immutable stage fingerprint after a read-only WAL
            // open would reject legitimate -wal/-shm sidecars. All INCOMPLETE
            // cutover recovery continues to use C1B unchanged.
            val ticket = try { readPinnedTicket(root) } catch (_: Exception) {
                return OpenResult(Status.BLOCKED)
            }
            val completed = try {
                ticket != null && FileBootstrapJournal(root).read(ticket.operation)?.phase ==
                    BootstrapPhase.COMPLETED
            } catch (_: Exception) { return OpenResult(Status.BLOCKED) }
            val selected = if (completed) {
                try { FileActiveGenerationPointer(root).read() }
                catch (_: Exception) { return OpenResult(Status.BLOCKED) }
            } else {
                val prior = try {
                    FaroV5Household11DevJournaledCutover.acquire(root, gate, hostClosed).use {
                        it.recover()
                    }
                } catch (_: Exception) { return OpenResult(Status.BLOCKED) }
                when (prior.status) {
                    FaroV5Household11DevJournaledCutover.Status.NO_TICKET ->
                        return OpenResult(Status.NO_TICKET)
                    FaroV5Household11DevJournaledCutover.Status.AWAITING_FOREGROUND ->
                        return OpenResult(Status.AWAITING_FOREGROUND)
                    FaroV5Household11DevJournaledCutover.Status.ROLLED_BACK ->
                        return OpenResult(Status.ROLLED_BACK)
                    FaroV5Household11DevJournaledCutover.Status.BLOCKED ->
                        return OpenResult(Status.BLOCKED)
                    FaroV5Household11DevJournaledCutover.Status.ACTIVE_ELEVEN -> prior.pointer
                }
            }
            // Re-acquire the SAME per-root lock. No pointer/journal action is
            // performed by C1C, and every durable field is rechecked under lock.
            val channel = try {
                FileChannel.open(root.resolve("finance9-dev-root.lock"), CREATE, WRITE, NOFOLLOW_LINKS)
            } catch (_: Exception) { return OpenResult(Status.BLOCKED) }
            var lock: FileLock? = null
            try {
                lock = checkNotNull(channel.tryLock()) { "SQL11 host root is occupied" }
                val pinned = checkNotNull(selected)
                val trusted = checkNotNull(readPinnedTicket(root))
                check(ticket == null || ticket == trusted) { "SQL11 ticket replaced before lease" }
                verifyCompleted(root, pinned, trusted)
                val staged = FileStorageGenerationLayout(root).requireStaged(pinned.generationId)
                check(ExistingGenerationFormatReader.requireAndRead(staged) ==
                    GenerationStoreFormat.WORKSPACE_V2)
                gate.withQuiescedMaterialWrites {
                    check(hostClosed())
                    ActiveSqlitePreflight.requireAll(staged.directory, sql11Schemas())
                    verifyCompleted(root, pinned, trusted)
                }
                return OpenResult(Status.ACTIVE_ELEVEN,
                    Session(root, gate, channel, lock, pinned, trusted, hostClosed))
            } catch (_: Exception) {
                try { lock?.release() } finally { channel.close() }
                return OpenResult(Status.BLOCKED)
            }
        }

        private fun verifyCompleted(root: Path, pinned: ActiveGenerationPointer, ticket: Ticket) {
            check(pinned == ticket.selected && FileActiveGenerationPointer(root).read() == pinned)
            check(readPinnedTicket(root) == ticket) { "SQL11 durable ticket has drifted" }
            val journal = FileBootstrapJournal(root)
            val entry = checkNotNull(journal.read(ticket.operation))
            check(entry.kind == MaintenanceOperationKind.RESTORE &&
                entry.phase == BootstrapPhase.COMPLETED &&
                entry.previousGenerationId == ticket.origin.generationId &&
                entry.targetGenerationId == ticket.target &&
                entry.materialWritesAccepted && journal.pending().isEmpty()) {
                "SQL11 ACTIVE journal not durably complete"
            }
            val fence = FileGenerationReplayFence(root)
            check(fence.isInstalled(MaintenanceOperationKind.RESTORE, ticket.target) &&
                !fence.isInstalled(MaintenanceOperationKind.MIGRATION, ticket.target)) {
                "SQL11 ACTIVE restore fence mismatch"
            }
        }

        private fun readPinnedTicket(root: Path): Ticket? {
            val file = root.resolve("bootstrap/household11-dev-cutover-v1.bin")
            if (Files.notExists(file, NOFOLLOW_LINKS)) return null
            check(file.parent.toRealPath() == file.parent &&
                Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
                (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L &&
                Files.size(file) in 65..4096) { "SQL11 durable ticket not canonical" }
            val bytes = Files.readAllBytes(file)
            val body = bytes.copyOfRange(0, bytes.size - 32)
            check(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(body),
                bytes.copyOfRange(body.size, bytes.size))) { "SQL11 ticket digest mismatch" }
            return DataInputStream(ByteArrayInputStream(body)).use { input ->
                check(input.readUTF() == "FARO_H11_SQL11_PRE_CUTOVER_AUTHORIZATION_V1")
                val operation = MaintenanceOperationId(input.readUTF())
                val origin = ActiveGenerationPointer(
                    input.readLong(), StorageGenerationId(input.readUTF()))
                val source = StorageGenerationId(input.readUTF())
                val target = StorageGenerationId(input.readUTF())
                val ciphertextSha256 = input.readUTF()
                val originSha256 = input.readUTF()
                val targetSha256 = input.readUTF()
                val expected = AuthorizedRows(
                    input.readLong(), input.readInt(), input.readLong(), input.readInt())
                check(input.available() == 0 && source != target &&
                    origin.generationId != target &&
                    listOf(ciphertextSha256, originSha256, targetSha256).all {
                        it.matches(Regex("[0-9a-f]{64}"))
                    } && expected.needRevision >= 0L && expected.receiptRevision >= 0L &&
                    expected.needCount in 0..8192 && expected.receiptCount in 0..8192) {
                    "SQL11 ticket identity and expected rows invalid"
                }
                Ticket(operation, origin, source, target, ciphertextSha256,
                    originSha256, targetSha256, expected)
            }
        }

        private fun sql11Schemas(): List<StagedSqlitePreflight.Schema> = listOf(
            StagedSqlitePreflight.Schema(GenerationStoreFiles.REALITY, 11, listOf(
                "profile_state", "apply_attempt", "execution_receipt", "reality_outbox",
                "finance_state", "finance_account", "finance_account_opening", "finance_entry",
                "objective_state", "objective_record", "matter_state", "matter_record",
                "reminder_state", "reminder_rule", "reminder_occurrence",
                "monthly_plan_state", "monthly_plan_item",
                "finance_acl_state", "finance_acl_policy", "finance_apply_grant",
                "finance_opening_confirmation", "finance_transfer", "finance_debt",
                "finance_schedule", "finance_plan_item", "finance_plan_item_payment",
                "finance_budget_limit", "finance_schema_migration_audit",
                "household_need_state", "household_need",
                "household_stock_receipt_state", "household_stock_receipt",
            )),
            StagedSqlitePreflight.Schema(GenerationStoreFiles.COMMERCIAL_EVIDENCE, 1,
                listOf("commercial_evidence")),
            StagedSqlitePreflight.Schema(GenerationStoreFiles.WEB_EVIDENCE, 2,
                listOf("web_evidence", "web_search_evidence")),
            StagedSqlitePreflight.Schema(GenerationStoreFiles.AUXILIARY, 4,
                listOf("delivery_notice", "delivery_intent", "bookmark_scope",
                    "bookmark_folder", "bookmark_item", "bookmark_receipt",
                    "notice_attention_receipt")),
            StagedSqlitePreflight.Schema(GenerationStoreFiles.WORKSPACE, 4,
                listOf("workspace_conversation", "workspace_meta", "workspace_receipt",
                    "workspace_draft", "workspace_authority", "workspace_message",
                    "workspace_plan_review")),
        )
    }
}
