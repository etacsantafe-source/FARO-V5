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

    class Session internal constructor(
        private val root: Path,
        private val gate: SingleProcessMaterialWriteGate,
        private val channel: FileChannel,
        private val lock: FileLock,
        private val pinned: ActiveGenerationPointer,
        private val operation: MaintenanceOperationId,
        private val hostClosed: () -> Boolean,
    ) : AutoCloseable {
        @Volatile private var closed = false

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

        private fun requireComplete() {
            check(FileActiveGenerationPointer(root).read() == pinned) { "SQL11 full pointer drift" }
            val journal = checkNotNull(FileBootstrapJournal(root).read(operation))
            check(journal.phase == BootstrapPhase.COMPLETED &&
                journal.materialWritesAccepted && journal.targetGenerationId == pinned.generationId &&
                FileBootstrapJournal(root).pending().isEmpty() &&
                FileGenerationReplayFence(root).isInstalled(
                    MaintenanceOperationKind.RESTORE, pinned.generationId
                )) { "SQL11 active journal/fence not complete" }
            check(readPinnedOperation(root) == operation) { "SQL11 durable ticket changed" }
        }

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
            // The certified C1B coordinator owns all crash recovery. Do not
            // invent an independent pointer/journal/CAS algorithm here.
            val prior = try {
                FaroV5Household11DevJournaledCutover.acquire(root, gate, hostClosed).use {
                    it.recover()
                }
            } catch (_: Exception) {
                return OpenResult(Status.BLOCKED)
            }
            when (prior.status) {
                FaroV5Household11DevJournaledCutover.Status.NO_TICKET ->
                    return OpenResult(Status.NO_TICKET)
                FaroV5Household11DevJournaledCutover.Status.AWAITING_FOREGROUND ->
                    return OpenResult(Status.AWAITING_FOREGROUND)
                FaroV5Household11DevJournaledCutover.Status.ROLLED_BACK ->
                    return OpenResult(Status.ROLLED_BACK)
                FaroV5Household11DevJournaledCutover.Status.BLOCKED ->
                    return OpenResult(Status.BLOCKED)
                FaroV5Household11DevJournaledCutover.Status.ACTIVE_ELEVEN -> Unit
            }
            // Take the SAME per-root lock following the C1B recovery. Recheck
            // full pointer, ticket and journal after acquiring it (no TOCTOU).
            val channel = try {
                FileChannel.open(root.resolve("finance9-dev-root.lock"), CREATE, WRITE, NOFOLLOW_LINKS)
            } catch (_: Exception) { return OpenResult(Status.BLOCKED) }
            var lock: FileLock? = null
            try {
                lock = checkNotNull(channel.tryLock()) { "SQL11 host root is occupied" }
                val pinned = checkNotNull(prior.pointer)
                check(FileActiveGenerationPointer(root).read() == pinned)
                val operation = readPinnedOperation(root)
                val journal = checkNotNull(FileBootstrapJournal(root).read(operation))
                check(journal.phase == BootstrapPhase.COMPLETED &&
                    journal.materialWritesAccepted && journal.targetGenerationId == pinned.generationId &&
                    FileBootstrapJournal(root).pending().isEmpty() &&
                    FileGenerationReplayFence(root).isInstalled(
                        MaintenanceOperationKind.RESTORE, pinned.generationId))
                val staged = FileStorageGenerationLayout(root).requireStaged(pinned.generationId)
                check(ExistingGenerationFormatReader.requireAndRead(staged) ==
                    GenerationStoreFormat.WORKSPACE_V2)
                gate.withQuiescedMaterialWrites {
                    check(hostClosed())
                    ActiveSqlitePreflight.requireAll(staged.directory, sql11Schemas())
                    check(FileActiveGenerationPointer(root).read() == pinned)
                }
                return OpenResult(Status.ACTIVE_ELEVEN,
                    Session(root, gate, channel, lock, pinned, operation, hostClosed))
            } catch (_: Exception) {
                try { lock?.release() } finally { channel.close() }
                return OpenResult(Status.BLOCKED)
            }
        }

        private fun readPinnedOperation(root: Path): MaintenanceOperationId {
            val ticket = root.resolve("bootstrap/household11-dev-cutover-v1.bin")
            check(Files.isRegularFile(ticket, NOFOLLOW_LINKS) && !Files.isSymbolicLink(ticket) &&
                Files.size(ticket) in 65..4096)
            val bytes = Files.readAllBytes(ticket)
            val body = bytes.copyOfRange(0, bytes.size - 32)
            check(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(body),
                bytes.copyOfRange(body.size, bytes.size)))
            return DataInputStream(ByteArrayInputStream(body)).use {
                check(it.readUTF() == "FARO_H11_SQL11_PRE_CUTOVER_AUTHORIZATION_V1")
                MaintenanceOperationId(it.readUTF())
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
