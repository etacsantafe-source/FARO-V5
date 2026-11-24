package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationActivationResult
import ar.com.faro.v5.maintenance.GenerationProjectionReconciliationPort
import ar.com.faro.v5.maintenance.GenerationRuntimeActivationPort
import ar.com.faro.v5.maintenance.GenerationPreSwitchAdmissionPort
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationSwitchCoordinator
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.maintenance.ValidatedGenerationActivationCoordinator
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import ar.com.faro.v5.store.reality.RealityHousehold11NeedsRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRowsPort
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
 * W05-A1B3C1B: DEV-only SQL11 journaled generation switch + crash recovery.
 *
 * Consumes C1A's existing durable ticket and VALIDATED journal; never creates
 * new authorization, FAROBAK2 archive, SQLITE data, or an app production host.
 * The target is examined as sealed immutable five-store SQLite after the
 * source is closed and the global gate/root lock are held.
 */
internal class FaroV5Household11DevJournaledCutover private constructor(
    private val root: Path,
    private val gate: SingleProcessMaterialWriteGate,
    private val channel: FileChannel,
    private val fileLock: FileLock,
    private val hostClosed: () -> Boolean,
    private val fault: (GenerationSwitchFaultPoint) -> Unit,
    private val supportsSealedSql11: () -> Boolean,
    private val targetRuntimeHealthy: () -> Boolean,
) : AutoCloseable {
    enum class Status { ACTIVE_ELEVEN, ROLLED_BACK, AWAITING_FOREGROUND, NO_TICKET, BLOCKED }
    data class Selection(val status: Status, val pointer: ActiveGenerationPointer?)

    private data class Ticket(
        val operation: MaintenanceOperationId,
        val origin: ActiveGenerationPointer,
        val sourceId: StorageGenerationId,
        val targetId: StorageGenerationId,
        val ciphertextSha256: String,
        val originSha256: String,
        val targetSha256: String,
        val needRevision: Long,
        val needCount: Int,
        val receiptRevision: Long,
        val receiptCount: Int,
    ) {
        init {
            require(sourceId != targetId && origin.generationId != targetId)
            require(listOf(ciphertextSha256, originSha256, targetSha256).all {
                it.matches(Regex("[0-9a-f]{64}"))
            })
            require(needRevision >= 0L && receiptRevision >= 0L &&
                needCount in 0..8192 && receiptCount in 0..8192)
        }

        val selected: ActiveGenerationPointer
            get() = ActiveGenerationPointer(Math.addExact(origin.sequence, 1L), targetId)
        val rolledBack: ActiveGenerationPointer
            get() = ActiveGenerationPointer(Math.addExact(origin.sequence, 2L), origin.generationId)
    }

    private val pointer = FileActiveGenerationPointer(root)
    private val journal = FileBootstrapJournal(root)
    private val layout = FileStorageGenerationLayout(root)
    private val fence = FileGenerationReplayFence(root)
    private val archiveIdentity = FileRestoreArchiveIdentity(root)
    private val ticketPath = root.resolve("bootstrap/household11-dev-cutover-v1.bin")
    private val quiescence = MaterialWriteGateQuiescence(gate)
    private var closed = false

    fun start(
        operation: MaintenanceOperationId,
        target: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
        confirmInForeground: () -> Boolean,
    ): Selection = guarded {
        requireLive()
        check(confirmInForeground()) { "SQL11 cutover requires a new foreground switch authorization" }
        val ticket = checkNotNull(readTicket()) { "SQL11 cutover requires prior C1A durable ticket" }
        check(ticket.operation == operation && ticket.origin == origin &&
            ticket.targetId == target.descriptor.generationId &&
            layout.requireStaged(ticket.targetId) == target) {
            "SQL11 cutover attempted operation/target/pointer substitution"
        }
        val immutableBytes = encryptedArchive.copyOf()
        try {
            authenticateOriginal(ticket, immutableBytes, passphrase)
        } finally {
            immutableBytes.fill(0)
        }
        val record = requireJournal(ticket)
        check(record.phase in setOf(
            BootstrapPhase.VALIDATED, BootstrapPhase.SWITCH_PREPARED,
            BootstrapPhase.SWITCHED, BootstrapPhase.HEALTH_VERIFIED,
            BootstrapPhase.COMPLETED, BootstrapPhase.ROLLBACK_PREPARED,
            BootstrapPhase.ROLLED_BACK_POST_SWITCH, BootstrapPhase.ROLLBACK_VERIFIED,
        )) { "SQL11 cutover cannot activate an unvalidated journal" }
        requireSealed(ticket)
        val current = pointer.read()
        check(current == allowedPointer(ticket, record)) {
            "SQL11 cutover pointer/journal is not exact"
        }
        check(confirmInForeground()) { "SQL11 switch foreground authorization revoked" }
        requireLive()
        check(pointer.read() == current && requireJournal(ticket) == record) {
            "SQL11 cutover authority changed before switch"
        }
        activate(ticket, recovery = record.phase != BootstrapPhase.VALIDATED)
    }

    /**
     * Startup recovery DOES NOT autoactivate a merely VALIDATED C1A ticket:
     * the actual cutover requires start() and a separate foreground approval.
     * Once SWITCH_PREPARED is durably persisted by start(), restart may finish
     * the authorized switch or rollback without asking for a stored passphrase.
     */
    fun recover(): Selection = guarded {
        val ticket = readTicket() ?: run {
            check(journal.pending().isEmpty()) {
                "SQL11 cutover missing durable ticket with pending journal"
            }
            return@guarded Selection(Status.NO_TICKET, pointer.read())
        }
        val record = requireJournal(ticket)
        requireSealed(ticket)
        check(pointer.read() == allowedPointer(ticket, record)) {
            "SQL11 cutover recover found pointer or journal mismatch"
        }
        if (record.phase in setOf(BootstrapPhase.PREPARING, BootstrapPhase.STAGED,
            BootstrapPhase.VALIDATED)) {
            return@guarded Selection(Status.AWAITING_FOREGROUND, pointer.read())
        }
        activate(ticket, recovery = true)
    }

    private fun authenticateOriginal(ticket: Ticket, bytes: ByteArray, key: CharArray) {
        requireLive()
        check(bytes.isNotEmpty() && sha256Hex(bytes) == ticket.ciphertextSha256) {
            "SQL11 cutover ciphertext differs from authorized ticket"
        }
        archiveIdentity.requireExistingPinnedArchive(ticket.operation, ticket.targetId, bytes)
        val payload = when (val opened = FaroV5FiveStorePortableBackupCrypto()
            .openWithHouseholdReceipts11(bytes, key)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error(
                "SQL11 cutover rejected FBS2v8: " + opened.reason
            )
        }
        check(payload.base.base.base.fourStore.manifest.sourceGenerationId == ticket.sourceId &&
            payload.base.base.base.fourStore.commercialEvidence.schemaVersion == 1 &&
            payload.base.base.base.fourStore.commercialEvidence.stagedTyped == null &&
            payload.base.base.base.finance9 != null &&
            payload.base.household.revision == ticket.needRevision &&
            payload.base.household.needs.size == ticket.needCount &&
            payload.receipts.revision == ticket.receiptRevision &&
            payload.receipts.receipts.size == ticket.receiptCount) {
            "SQL11 cutover authenticated archive diverges from C1A ticket"
        }
    }

    private fun allowedPointer(ticket: Ticket, entry: BootstrapJournalEntry): ActiveGenerationPointer =
        when (entry.phase) {
            BootstrapPhase.PREPARING, BootstrapPhase.STAGED, BootstrapPhase.VALIDATED -> ticket.origin
            BootstrapPhase.SWITCH_PREPARED -> {
                val actual = pointer.read()
                check(actual == ticket.origin || actual == ticket.selected) {
                    "SQL11 SWITCH_PREPARED full pointer mismatch"
                }
                checkNotNull(actual)
            }
            BootstrapPhase.SWITCHED, BootstrapPhase.HEALTH_VERIFIED, BootstrapPhase.COMPLETED ->
                ticket.selected
            BootstrapPhase.ROLLBACK_PREPARED -> {
                val actual = pointer.read()
                check(actual == ticket.selected || actual == ticket.rolledBack) {
                    "SQL11 ROLLBACK_PREPARED full pointer mismatch"
                }
                checkNotNull(actual)
            }
            BootstrapPhase.ROLLED_BACK_POST_SWITCH, BootstrapPhase.ROLLBACK_VERIFIED ->
                ticket.rolledBack
            else -> error("SQL11 cutover journal phase unsupported")
        }

    private fun requireJournal(ticket: Ticket): BootstrapJournalEntry {
        val record = checkNotNull(journal.read(ticket.operation)) {
            "SQL11 cutover requires C1A durable journal"
        }
        check(record.kind == MaintenanceOperationKind.RESTORE &&
            record.previousGenerationId == ticket.origin.generationId &&
            record.targetGenerationId == ticket.targetId &&
            journal.pending().all { it == record }) {
            "SQL11 cutover foreign pending journal or identity mismatch"
        }
        return record
    }

    private fun requireSealed(ticket: Ticket) {
        requireLive()
        val origin = layout.requireStaged(ticket.origin.generationId)
        val target = layout.requireStaged(ticket.targetId)
        FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(
            origin, allowAuxiliary3 = false,
        )
        check(FaroV5Household10DevCutoverOwner.fingerprint(origin) == ticket.originSha256) {
            "SQL11 original five-store generation changed"
        }
        requireExactReality11FiveStores(target)
        check(FaroV5Household10DevCutoverOwner.fingerprint(target) == ticket.targetSha256) {
            "SQL11 selected five-store stage changed"
        }
        val reality = target.directory.resolve(GenerationStoreFiles.REALITY)
        val needs = RealityHousehold11NeedsRowsPort.readTrustedSealedStageEleven(reality)
        val receipts = RealityHousehold11ReceiptRowsPort.readTrustedSealedStageEleven(reality)
        check(needs.revision == ticket.needRevision && needs.needs.size == ticket.needCount &&
            receipts.revision == ticket.receiptRevision &&
            receipts.receipts.size == ticket.receiptCount) {
            "SQL11 selected H10/H11R revision/count changed"
        }
        requireLive()
    }

    private fun requireExactReality11FiveStores(stage: StagedStorageGeneration) {
        check(ExistingGenerationFormatReader.requireAndRead(stage) ==
            GenerationStoreFormat.WORKSPACE_V2) {
            "SQL11 DEV requires V2 five-store generation"
        }
        val canonical = layout.requireStaged(stage.descriptor.generationId)
        val dir = stage.directory.toAbsolutePath().normalize()
        check(canonical.descriptor == stage.descriptor &&
            canonical.directory == dir && dir.toRealPath() == dir) {
            "SQL11 DEV stage directory or descriptor changed"
        }
        for (fileName in GenerationStoreFiles.WORKSPACE_V2) {
            val file = dir.resolve(fileName)
            check(Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(file) &&
                (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L) {
                "SQL11 DEV store is not single-linked and sealed"
            }
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(dir.resolve(fileName + suffix), NOFOLLOW_LINKS)) {
                    "SQL11 DEV store has unsafe SQLite sidecar"
                }
            }
            val header = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(20) }
            check(header.size == 20 && header[18] == 2.toByte() && header[19] == 2.toByte()) {
                "SQL11 DEV store is not sealed WAL format"
            }
        }
        val realityTables = listOf(
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
        )
        StagedSqlitePreflight.requireAll(dir, listOf(
            StagedSqlitePreflight.Schema(GenerationStoreFiles.REALITY, 11, realityTables),
            StagedSqlitePreflight.Schema(
                GenerationStoreFiles.COMMERCIAL_EVIDENCE, 1, listOf("commercial_evidence"),
            ),
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
        ))
    }

    private fun activate(ticket: Ticket, recovery: Boolean): Selection {
        requireSealed(ticket)
        val existing = requireJournal(ticket)
        check(pointer.read() == allowedPointer(ticket, existing)) {
            "SQL11 DEV cannot activate with stale full pointer"
        }
        val coordinator = ValidatedGenerationActivationCoordinator(
            journal, pointer, layout, quiescence,
            GenerationSwitchCoordinator(journal, pointer, layout, fault),
            runtime = GenerationRuntimeActivationPort { kind, id ->
                if (kind != MaintenanceOperationKind.RESTORE) false else when (id) {
                    ticket.targetId -> {
                        requireSealed(ticket)
                        pointer.read() == ticket.selected &&
                            supportsSealedSql11() && targetRuntimeHealthy()
                    }
                    ticket.origin.generationId -> {
                        requireSealed(ticket)
                        pointer.read() == ticket.rolledBack
                    }
                    else -> false
                }
            },
            replayFence = fence,
            projections = GenerationProjectionReconciliationPort { kind, id ->
                // DEV only. No OS/Android projection should execute here.
                check(kind == MaintenanceOperationKind.RESTORE && id == ticket.targetId)
                requireSealed(ticket)
            },
            preSwitchAdmission = GenerationPreSwitchAdmissionPort { kind, id ->
                if (kind != MaintenanceOperationKind.RESTORE || id != ticket.targetId) false
                else {
                    requireSealed(ticket)
                    val current = requireJournal(ticket)
                    val actual = pointer.read()
                    supportsSealedSql11() && when (actual) {
                        ticket.origin -> current.phase in setOf(
                            BootstrapPhase.VALIDATED, BootstrapPhase.SWITCH_PREPARED,
                        ) && !current.materialWritesAccepted
                        ticket.selected -> current.phase in setOf(
                            BootstrapPhase.SWITCH_PREPARED, BootstrapPhase.SWITCHED,
                            BootstrapPhase.HEALTH_VERIFIED, BootstrapPhase.COMPLETED,
                        )
                        else -> false
                    }
                }
            },
        )
        val result = if (recovery) coordinator.recover(ticket.operation)
            else coordinator.activate(ticket.operation)
        val completed = checkNotNull(journal.read(ticket.operation))
        return when (result) {
            is GenerationActivationResult.Activated -> {
                check(pointer.read() == ticket.selected &&
                    completed.phase == BootstrapPhase.COMPLETED &&
                    completed.materialWritesAccepted &&
                    fence.isInstalled(MaintenanceOperationKind.RESTORE, ticket.targetId) &&
                    !fence.isInstalled(MaintenanceOperationKind.MIGRATION, ticket.targetId)) {
                    "SQL11 cutover did not finish with durable replay fence"
                }
                requireSealed(ticket)
                Selection(Status.ACTIVE_ELEVEN, pointer.read())
            }
            is GenerationActivationResult.RolledBack -> {
                check(pointer.read() == ticket.rolledBack &&
                    completed.phase == BootstrapPhase.ROLLBACK_VERIFIED &&
                    !completed.materialWritesAccepted &&
                    !fence.isInstalled(MaintenanceOperationKind.RESTORE, ticket.targetId)) {
                    "SQL11 DEV rollback did not converge before target writes"
                }
                requireSealed(ticket)
                Selection(Status.ROLLED_BACK, pointer.read())
            }
            is GenerationActivationResult.Blocked -> Selection(Status.BLOCKED, pointer.read())
        }
    }

    private fun readTicket(): Ticket? {
        if (Files.notExists(ticketPath, NOFOLLOW_LINKS)) return null
        check(ticketPath.parent.toRealPath() == ticketPath.parent &&
            Files.isRegularFile(ticketPath, NOFOLLOW_LINKS) && !Files.isSymbolicLink(ticketPath) &&
            (Files.getAttribute(ticketPath, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L &&
            Files.size(ticketPath) in 65..4096) {
            "SQL11 journaled switch rejects noncanonical C1A ticket"
        }
        val bytes = Files.readAllBytes(ticketPath)
        val payload = bytes.copyOfRange(0, bytes.size - 32)
        check(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(payload),
            bytes.copyOfRange(payload.size, bytes.size))) {
            "SQL11 journaled switch ticket checksum mismatch"
        }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            check(input.readUTF() == "FARO_H11_SQL11_PRE_CUTOVER_AUTHORIZATION_V1") {
                "SQL11 journaled switch ticket has unknown version"
            }
            val op = MaintenanceOperationId(input.readUTF())
            val origin = ActiveGenerationPointer(
                input.readLong(), StorageGenerationId(input.readUTF()),
            )
            val ticket = Ticket(
                op, origin, StorageGenerationId(input.readUTF()),
                StorageGenerationId(input.readUTF()),
                input.readUTF(), input.readUTF(), input.readUTF(),
                input.readLong(), input.readInt(), input.readLong(), input.readInt(),
            )
            check(input.available() == 0) { "SQL11 journaled ticket has trailing bytes" }
            ticket
        }
    }

    private fun guarded(block: () -> Selection): Selection = try {
        gate.withQuiescedMaterialWrites {
            requireLive()
            block()
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        Selection(Status.BLOCKED, pointer.read())
    } catch (_: Exception) {
        Selection(Status.BLOCKED, pointer.read())
    }

    private fun requireLive() {
        check(!closed && channel.isOpen && fileLock.isValid && hostClosed()) {
            "SQL11 journaled DEV cutover requires exclusive closed host"
        }
    }

    override fun close() = gate.withQuiescedMaterialWrites {
        if (!closed) {
            closed = true
            try {
                fileLock.release()
            } finally {
                channel.close()
            }
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    companion object {
        fun acquire(
            storageRoot: Path,
            gate: SingleProcessMaterialWriteGate,
            hostClosed: () -> Boolean,
            fault: (GenerationSwitchFaultPoint) -> Unit = {},
            supportsSealedSql11: () -> Boolean = { true },
            targetRuntimeHealthy: () -> Boolean = { true },
        ): FaroV5Household11DevJournaledCutover {
            check(Files.isDirectory(storageRoot, NOFOLLOW_LINKS) && hostClosed()) {
                "SQL11 DEV cutover needs existing closed root"
            }
            val root = storageRoot.toRealPath()
            val channel = FileChannel.open(
                root.resolve("finance9-dev-root.lock"), CREATE, WRITE, NOFOLLOW_LINKS,
            )
            return try {
                val lock = checkNotNull(channel.tryLock()) {
                    "SQL11 DEV cutover root lock is already held"
                }
                FaroV5Household11DevJournaledCutover(
                    root, gate, channel, lock, hostClosed, fault,
                    supportsSealedSql11, targetRuntimeHealthy,
                )
            } catch (failure: Exception) {
                channel.close()
                throw IllegalStateException("SQL11 DEV cutover could not obtain root lock", failure)
            }
        }
    }
}
