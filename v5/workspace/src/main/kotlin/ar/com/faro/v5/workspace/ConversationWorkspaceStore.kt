package ar.com.faro.v5.workspace

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.ConversationReviewPort
import ar.com.faro.v5.kernel.contracts.TrustedConversationApplyReviewPort
import ar.com.faro.v5.kernel.contracts.TrustedApprovedConversationPlan
import ar.com.faro.v5.kernel.contracts.ApplyAttemptLookupPort
import ar.com.faro.v5.kernel.contracts.ExecutionReceiptLookupPort
import ar.com.faro.v5.kernel.contracts.ApplyAttemptJournalState
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewContext
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewTransition
import ar.com.faro.v5.kernel.contracts.ConversationReviewDecision
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewIntent
import ar.com.faro.v5.kernel.contracts.ConversationComposerPort
import ar.com.faro.v5.kernel.contracts.ConversationDraftSendRequest
import ar.com.faro.v5.kernel.contracts.ConversationDraftPort
import ar.com.faro.v5.kernel.contracts.ConversationTimelinePort
import ar.com.faro.v5.kernel.contracts.ConversationTimelineWriteRequest
import ar.com.faro.v5.kernel.contracts.ConversationTimelineWriteResult
import ar.com.faro.v5.kernel.contracts.ConversationTimelineSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationTimelineIntent
import ar.com.faro.v5.kernel.contracts.ConversationTimelinePolicy
import ar.com.faro.v5.kernel.contracts.ConversationTimelineTransition
import ar.com.faro.v5.kernel.contracts.ConversationMessage
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
import ar.com.faro.v5.kernel.contracts.ConversationMessageRole
import ar.com.faro.v5.kernel.contracts.ConversationMessageState
import ar.com.faro.v5.kernel.contracts.ConversationDraftPolicy
import ar.com.faro.v5.kernel.contracts.ConversationDraftSaveRequest
import ar.com.faro.v5.kernel.contracts.ConversationDraftSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationDraftWriteResult
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceIntent
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceReadPort
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceSelectionPort
import ar.com.faro.v5.kernel.contracts.ConversationSelectionResult
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceRejection
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceTransition
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceTransitions
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID

/** Staged A4 owner: no user-facing Apply wiring, no Reality writes. */
data class WorkspacePlanReviewCreateRequest(
    val conversationId: ConversationId,
    val reviewId: String,
    val authorityToken: String,
    val expectedWorkspaceRevision: Long,
    val planFingerprint: ar.com.faro.v5.kernel.contracts.PlanFingerprint,
    val reviewText: String,
    val planEnvelope: ByteArray,
    val idempotencyKey: IdempotencyKey,
)

data class WorkspacePlanReviewTransitionRequest(
    val intent: ar.com.faro.v5.kernel.contracts.ConversationPlanReviewIntent,
    val idempotencyKey: IdempotencyKey,
)

enum class WorkspacePlanReviewRejection {
    UNKNOWN_CONVERSATION, ARCHIVED, INVALID_PLAN, DUPLICATE_REVIEW,
    NOT_FOUND, INVALID_TRANSITION, CAPACITY_EXCEEDED
}

sealed interface WorkspacePlanReviewWriteResult {
    data class Saved(
        val review: ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot,
        val replayed: Boolean,
    ) : WorkspacePlanReviewWriteResult

    data object Stale : WorkspacePlanReviewWriteResult
    data object Unavailable : WorkspacePlanReviewWriteResult
    data object IdentityConflict : WorkspacePlanReviewWriteResult
    data class Rejected(val reason: WorkspacePlanReviewRejection) : WorkspacePlanReviewWriteResult
}

/** Private Workspace authority; no Reality/Apply tables or cross-store transaction. */
data class WorkspaceRevisionSnapshot(
    val revision: Long,
    val workspace: ConversationWorkspaceSnapshot,
)

data class WorkspaceCreateRequest(
    val entry: ConversationWorkspaceEntry,
    val expectedRevision: Long,
    val idempotencyKey: IdempotencyKey,
    val selectOnCreate: Boolean = false,
)

data class WorkspaceTransitionRequest(
    val intent: ConversationWorkspaceIntent,
    val expectedRevision: Long,
    val idempotencyKey: IdempotencyKey,
)

enum class WorkspaceWriteRejection {
    ID_ALREADY_EXISTS,
    UNKNOWN_CONVERSATION,
    ARCHIVED_CONVERSATION,
    INVALID_TITLE,
    PENDING_REVIEW,
}

sealed interface WorkspaceWriteResult {
    data class Committed(
        /** Observed CURRENT store state, not a historical snapshot at committedRevision. */
        val snapshot: WorkspaceRevisionSnapshot,
        val committedRevision: Long,
        val changed: Boolean,
        val replayed: Boolean,
    ) : WorkspaceWriteResult

    data class Stale(
        val current: WorkspaceRevisionSnapshot,
    ) : WorkspaceWriteResult

    data object IdentityConflict : WorkspaceWriteResult

    data class Rejected(
        val reason: WorkspaceWriteRejection,
    ) : WorkspaceWriteResult
}

internal enum class WorkspaceFaultPoint {
    AFTER_AUTHORITY_UPDATE,
}

/**
 * Staged, generation-scoped SQLite implementation. Not wired to composition or
 * backup yet: consuming this as the active authority before full lifecycle
 * integration is prohibited by the S2.A1 checkpoint.
 *
 * A single mutex protects the connection and each mutation uses BEGIN IMMEDIATE.
 * Idempotency receipts live in the SAME SQLite transaction as metadata.
 */
class ConversationWorkspaceStore private constructor(
    fileName: String,
    driver: SQLiteDriver,
    private val faultInjector: (WorkspaceFaultPoint) -> Unit,
    private val requireExistingSchema: Boolean,
    private val planEnvelopeVerifier: PreparedPlanEnvelopeVerificationPort,
    private val materialWriteGate: MaterialWriteGate? = null,
) : ConversationWorkspaceReadPort, ConversationComposerPort, ConversationTimelinePort, ConversationReviewPort, ConversationWorkspaceSelectionPort, TrustedConversationApplyReviewPort, AutoCloseable {
    constructor(
        fileName: String,
        driver: SQLiteDriver = BundledSQLiteDriver(),
        materialWriteGate: MaterialWriteGate? = null,
    ) : this(fileName, driver, {}, false, PreparedPlanEnvelopeVerificationPort { _, _ -> false }, materialWriteGate)

    internal constructor(fileName: String, faultInjector: (WorkspaceFaultPoint) -> Unit) :
        this(fileName, BundledSQLiteDriver(), faultInjector, false,
            PreparedPlanEnvelopeVerificationPort { _, _ -> false })

    private val mutex = Any()
    private fun <T> withMaterialAdmission(block: () -> T): T =
        materialWriteGate?.withMaterialWrite(block) ?: block()
    private val connection: SQLiteConnection = driver.open(fileName)
    private var closed = false

    init {
        try {
            withMaterialAdmission {
                synchronized(mutex) {
                    configure(fileName)
                    initialize(requireExistingSchema)
                    inspect()
                }
            }
        } catch (failure: Throwable) {
            // A half-opened or corrupt V2 file must never retain a live handle.
            runCatching { connection.close() }
            throw failure
        }
    }

    override suspend fun readSnapshot(): ConversationWorkspaceSnapshot = snapshot().workspace

    fun snapshot(): WorkspaceRevisionSnapshot = synchronized(mutex) {
        checkOpen()
        readCurrent()
    }

    override suspend fun readTimeline(
        conversationId: ConversationId,
    ): ConversationTimelineSnapshot? = timelineSnapshot(conversationId)

    internal fun timelineSnapshot(
        conversationId: ConversationId,
    ): ConversationTimelineSnapshot? = synchronized(mutex) {
        checkOpen()
        readTimelineInternal(conversationId)
    }

    private fun readTimelineInternal(id: ConversationId): ConversationTimelineSnapshot? {
        val draft = readDraftInternal(id) ?: return null
        val entries = mutableListOf<ConversationMessage>()
        connection.prepare(
            """SELECT id, turn_id, role, state, text, created_ms, reply_id
               FROM workspace_message WHERE conversation_id = ?
               ORDER BY sequence ASC"""
        ).use { statement ->
            statement.bindText(1, id.value)
            while (statement.step()) {
                entries += ConversationMessage(
                    id = ConversationMessageId(statement.getText(0)),
                    conversationId = id,
                    turnId = ConversationTurnId(statement.getText(1)),
                    role = ConversationMessageRole.valueOf(statement.getText(2)),
                    state = ConversationMessageState.valueOf(statement.getText(3)),
                    text = statement.getText(4),
                    createdAtEpochMillis = statement.getLong(5),
                    replyToMessageId = if (statement.isNull(6)) null
                        else ConversationMessageId(statement.getText(6)),
                )
            }
        }
        return ConversationTimelineSnapshot(
            id, draft.authorityToken, readCurrent().revision,
            Collections.unmodifiableList(entries.toList()),
        )
    }

    override suspend fun writeTimeline(
        request: ConversationTimelineWriteRequest,
    ): ConversationTimelineWriteResult = saveTimeline(request)

    internal fun saveTimeline(
        request: ConversationTimelineWriteRequest,
    ): ConversationTimelineWriteResult = transaction {
        validateIdentity(request.conversationId)
        validateIdempotencyKey(request.idempotencyKey)
        require(request.expectedRevision >= 0)
        require(request.authorityToken.isNotBlank())

        val before = readTimelineInternal(request.conversationId)
            ?: return@transaction ConversationTimelineWriteResult.NotFound
        // Restored receipts are intentionally kept; never replay old generation writes.
        if (before.authorityToken != request.authorityToken)
            return@transaction ConversationTimelineWriteResult.Unavailable

        val parts = when (val intent = request.intent) {
            is ConversationTimelineIntent.AcceptUser ->
                listOf("user", intent.message.id.value, intent.message.turnId.value,
                    intent.message.role.name, intent.message.state.name, intent.message.text,
                    intent.message.createdAtEpochMillis.toString(),
                    intent.message.replyToMessageId?.value.orEmpty(),
                    intent.message.conversationId.value)
            is ConversationTimelineIntent.StartFaro ->
                listOf("start", intent.message.id.value, intent.message.turnId.value,
                    intent.message.role.name, intent.message.state.name, intent.message.text,
                    intent.message.createdAtEpochMillis.toString(),
                    intent.message.conversationId.value)
            is ConversationTimelineIntent.ResolveFaro ->
                listOf("resolve", intent.messageId.value, intent.turnId.value,
                    intent.outcome.name, intent.text)
        }
        val fingerprint = requestFingerprint(
            "timeline.v3", request.conversationId.value, request.expectedRevision,
            listOf(request.authorityToken) + parts,
        )
        connection.prepare(
            "SELECT fingerprint FROM workspace_receipt WHERE key = ?"
        ).use { statement ->
            statement.bindText(1, request.idempotencyKey.value)
            if (statement.step()) {
                if (statement.getText(0) != fingerprint)
                    return@transaction ConversationTimelineWriteResult.IdentityConflict
                return@transaction ConversationTimelineWriteResult.Saved(before, true)
            }
        }
        if (before.revision != request.expectedRevision)
            return@transaction ConversationTimelineWriteResult.Stale(before)

        val archived = connection.prepare(
            "SELECT archived FROM workspace_conversation WHERE id = ?"
        ).use { statement ->
            statement.bindText(1, request.conversationId.value)
            check(statement.step())
            statement.getLong(0) == 1L
        }
        // Already-running replies may finish in the original archived conversation.
        if (archived && request.intent !is ConversationTimelineIntent.ResolveFaro)
            return@transaction ConversationTimelineWriteResult.Unavailable

        val evaluated = ConversationTimelinePolicy.evaluate(before, request.intent)
        if (evaluated is ConversationTimelineTransition.Rejected)
            return@transaction ConversationTimelineWriteResult.Rejected(evaluated.reason)
        val accepted = evaluated as ConversationTimelineTransition.Accepted

        val updated = accepted.changedMessage
        val previousText = before.messages.firstOrNull { it.id == updated.id }?.text.orEmpty()
        val additionalChars = (updated.text.length - previousText.length).toLong()
        if (additionalChars > 0) {
            var existingChars = 0L
            connection.prepare("SELECT text FROM workspace_message").use { statement ->
                while (statement.step()) {
                    existingChars += statement.getText(0).length
                }
            }
            check(existingChars + additionalChars <= WorkspaceBackupSnapshot.MAX_TOTAL_MESSAGE_CHARS) {
                "Workspace timeline would exceed restorable content limit"
            }
        }
        if (request.intent !is ConversationTimelineIntent.ResolveFaro) {
            val totalRows = connection.prepare(
                "SELECT COUNT(*) FROM workspace_message"
            ).use { statement ->
                check(statement.step())
                statement.getLong(0)
            }
            if (totalRows >= WorkspaceBackupSnapshot.MAX_RECORDS) {
                return@transaction ConversationTimelineWriteResult.Rejected(
                    ar.com.faro.v5.kernel.contracts.ConversationTimelineRejection.TOO_MANY_MESSAGES
                )
            }
        }
        if (request.intent is ConversationTimelineIntent.ResolveFaro) {
            connection.prepare(
                """UPDATE workspace_message SET state = ?, text = ?
                   WHERE conversation_id = ? AND id = ? AND turn_id = ? AND state = 'PROCESSING'"""
            ).use { statement ->
                statement.bindText(1, updated.state.name)
                statement.bindText(2, updated.text)
                statement.bindText(3, updated.conversationId.value)
                statement.bindText(4, updated.id.value)
                statement.bindText(5, updated.turnId.value)
                statement.step()
            }
        } else {
            connection.prepare(
                """INSERT INTO workspace_message
                   (id, conversation_id, turn_id, role, state, text, created_ms, reply_id)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { statement ->
                statement.bindText(1, updated.id.value)
                statement.bindText(2, updated.conversationId.value)
                statement.bindText(3, updated.turnId.value)
                statement.bindText(4, updated.role.name)
                statement.bindText(5, updated.state.name)
                statement.bindText(6, updated.text)
                statement.bindLong(7, updated.createdAtEpochMillis)
                val quoted = updated.replyToMessageId
                if (quoted == null) statement.bindNull(8)
                else statement.bindText(8, quoted.value)
                statement.step()
            }
        }
        updateMeta(before.revision + 1, readCurrent().workspace.activeConversationId)
        faultInjector(WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE)
        saveReceipt(request.idempotencyKey, fingerprint, before.revision + 1, true)
        ConversationTimelineWriteResult.Saved(
            readTimelineInternal(request.conversationId)!!, false,
        )
    }

    override suspend fun read(conversationId: ConversationId): ConversationDraftSnapshot? =
        draftSnapshot(conversationId)

    internal fun draftSnapshot(conversationId: ConversationId): ConversationDraftSnapshot? =
        synchronized(mutex) {
            checkOpen()
            readDraftInternal(conversationId)
        }

    override suspend fun save(request: ConversationDraftSaveRequest): ConversationDraftWriteResult =
        saveDraft(request)

    internal fun saveDraft(request: ConversationDraftSaveRequest): ConversationDraftWriteResult =
        transaction {
            validateIdentity(request.conversationId)
            validateIdempotencyKey(request.idempotencyKey)
            require(request.expectedRevision >= 0)
            require(request.text.length <= ConversationDraftPolicy.MAX_TEXT_CHARS)
            require(request.authorityToken.isNotBlank())

            // A restored generation has new authority even when old receipts
            // were restored for forensic/idempotency continuity. Check that
            // authority BEFORE looking up a matching legacy receipt.
            val current = readDraftInternal(request.conversationId)
                ?: return@transaction ConversationDraftWriteResult.NotFound
            if (current.authorityToken != request.authorityToken) {
                return@transaction ConversationDraftWriteResult.Unavailable
            }

            val fingerprint = requestFingerprint(
                "draft.set.v2", request.conversationId.value,
                request.expectedRevision, listOf(request.authorityToken, request.text),
            )
            connection.prepare(
                "SELECT fingerprint FROM workspace_receipt WHERE key = ?"
            ).use { statement ->
                statement.bindText(1, request.idempotencyKey.value)
                if (statement.step()) {
                    if (statement.getText(0) != fingerprint) {
                        return@transaction ConversationDraftWriteResult.IdentityConflict
                    }
                    val existing = readDraftInternal(request.conversationId)
                        ?: return@transaction ConversationDraftWriteResult.NotFound
                    return@transaction ConversationDraftWriteResult.Saved(existing, replayed = true)
                }
            }

            if (current.revision != request.expectedRevision) {
                return@transaction ConversationDraftWriteResult.Stale(current)
            }
            val archived = connection.prepare(
                "SELECT archived FROM workspace_conversation WHERE id = ?"
            ).use { statement ->
                statement.bindText(1, request.conversationId.value)
                check(statement.step())
                statement.getLong(0) == 1L
            }
            if (archived) return@transaction ConversationDraftWriteResult.Unavailable

            val changed = current.text != request.text
            if (changed) {
                // Stronger than the backup's Kotlin UTF-16 character ceiling:
                // UTF-8 byte count never underestimates Kotlin String.length.
                // Keep admission inside this transaction; no receipt on failure.
                val otherBytes = connection.prepare(
                    """SELECT COALESCE(SUM(length(CAST(text AS BLOB))), 0)
                       FROM workspace_draft WHERE conversation_id <> ?"""
                ).use { statement ->
                    statement.bindText(1, request.conversationId.value)
                    check(statement.step())
                    statement.getLong(0)
                }
                val incomingBytes = request.text.toByteArray(Charsets.UTF_8).size.toLong()
                if (otherBytes + incomingBytes > WorkspaceBackupSnapshot.MAX_TOTAL_DRAFT_CHARS) {
                    return@transaction ConversationDraftWriteResult.CapacityExceeded
                }
            }
            val before = readCurrent()
            val nextRevision = if (changed) before.revision + 1 else before.revision
            if (changed) {
                connection.prepare(
                    """INSERT INTO workspace_draft(conversation_id, revision, text)
                       VALUES (?, ?, ?)
                       ON CONFLICT(conversation_id)
                       DO UPDATE SET revision=excluded.revision, text=excluded.text"""
                ).use { statement ->
                    statement.bindText(1, request.conversationId.value)
                    statement.bindLong(2, nextRevision)
                    statement.bindText(3, request.text)
                    statement.step()
                }
                updateMeta(nextRevision, before.workspace.activeConversationId)
            }
            faultInjector(WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE)
            saveReceipt(request.idempotencyKey, fingerprint, nextRevision, changed)
            ConversationDraftWriteResult.Saved(
                readDraftInternal(request.conversationId)!!, replayed = false,
            )
        }

    /**
     * F1: actual send is ONE Workspace transaction: persisted draft + accepted
     * user turn + cleared draft + durable idempotency receipt. A failed or
     * interrupted send leaves both draft and timeline unchanged. No AI side
     * effect or implicit Apply is started from this method.
     */
    override suspend fun sendDraft(request: ConversationDraftSendRequest): ConversationTimelineWriteResult =
        sendCommittedDraft(request)

    internal fun sendCommittedDraft(
        request: ConversationDraftSendRequest,
    ): ConversationTimelineWriteResult = transaction {
        val candidate = request.candidate
        validateIdentity(candidate.conversationId)
        validateIdempotencyKey(request.idempotencyKey)
        val before = readTimelineInternal(candidate.conversationId)
            ?: return@transaction ConversationTimelineWriteResult.NotFound
        // Authority is checked before receipts, including receipts imported in a restore.
        if (before.authorityToken != candidate.authorityToken)
            return@transaction ConversationTimelineWriteResult.Unavailable
        val fingerprint = requestFingerprint(
            "composer.send.v1", candidate.conversationId.value, candidate.draftRevision,
            listOf(candidate.authorityToken, candidate.text, request.messageId.value,
                request.turnId.value, request.createdAtEpochMillis.toString()),
        )
        connection.prepare("SELECT fingerprint FROM workspace_receipt WHERE key = ?").use { stmt ->
            stmt.bindText(1, request.idempotencyKey.value)
            if (stmt.step()) {
                if (stmt.getText(0) != fingerprint)
                    return@transaction ConversationTimelineWriteResult.IdentityConflict
                val original = before.messages.firstOrNull { it.id == request.messageId }
                if (original == null || original.turnId != request.turnId ||
                    original.role != ConversationMessageRole.USER ||
                    original.text != candidate.text ||
                    original.createdAtEpochMillis != request.createdAtEpochMillis)
                    return@transaction ConversationTimelineWriteResult.IdentityConflict
                return@transaction ConversationTimelineWriteResult.Saved(before, replayed = true)
            }
        }
        val persisted = readDraftInternal(candidate.conversationId)
            ?: return@transaction ConversationTimelineWriteResult.NotFound
        if (persisted.revision != candidate.draftRevision || persisted.text != candidate.text)
            return@transaction ConversationTimelineWriteResult.Stale(before)
        if (persisted.text.isBlank())
            return@transaction ConversationTimelineWriteResult.Unavailable
        val archived = connection.prepare(
            "SELECT archived FROM workspace_conversation WHERE id = ?"
        ).use { stmt ->
            stmt.bindText(1, candidate.conversationId.value)
            check(stmt.step())
            stmt.getLong(0) == 1L
        }
        if (archived) return@transaction ConversationTimelineWriteResult.Unavailable
        val message = ConversationMessage(
            id = request.messageId,
            conversationId = candidate.conversationId,
            turnId = request.turnId,
            role = ConversationMessageRole.USER,
            state = ConversationMessageState.ACCEPTED,
            text = candidate.text,
            createdAtEpochMillis = request.createdAtEpochMillis,
        )
        val transition = ConversationTimelinePolicy.evaluate(
            before, ConversationTimelineIntent.AcceptUser(message),
        )
        if (transition is ConversationTimelineTransition.Rejected)
            return@transaction ConversationTimelineWriteResult.Rejected(transition.reason)
        val rows = connection.prepare("SELECT COUNT(*) FROM workspace_message").use { stmt ->
            check(stmt.step())
            stmt.getLong(0)
        }
        if (rows >= WorkspaceBackupSnapshot.MAX_RECORDS)
            return@transaction ConversationTimelineWriteResult.Rejected(
                ar.com.faro.v5.kernel.contracts.ConversationTimelineRejection.TOO_MANY_MESSAGES
            )
        var existingChars = 0L
        connection.prepare("SELECT text FROM workspace_message").use { stmt ->
            while (stmt.step()) existingChars += stmt.getText(0).length
        }
        check(existingChars + message.text.length <= WorkspaceBackupSnapshot.MAX_TOTAL_MESSAGE_CHARS) {
            "Workspace send would exceed restorable message capacity"
        }
        connection.prepare(
            """INSERT INTO workspace_message
               (id, conversation_id, turn_id, role, state, text, created_ms, reply_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, NULL)"""
        ).use { stmt ->
            stmt.bindText(1, message.id.value)
            stmt.bindText(2, message.conversationId.value)
            stmt.bindText(3, message.turnId.value)
            stmt.bindText(4, message.role.name)
            stmt.bindText(5, message.state.name)
            stmt.bindText(6, message.text)
            stmt.bindLong(7, message.createdAtEpochMillis)
            stmt.step()
        }
        val nextRevision = readCurrent().revision + 1
        connection.prepare(
            "UPDATE workspace_draft SET text = '', revision = ? WHERE conversation_id = ?"
        ).use { stmt ->
            stmt.bindLong(1, nextRevision)
            stmt.bindText(2, candidate.conversationId.value)
            stmt.step()
        }
        updateMeta(nextRevision, readCurrent().workspace.activeConversationId)
        faultInjector(WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE)
        saveReceipt(request.idempotencyKey, fingerprint, nextRevision, true)
        ConversationTimelineWriteResult.Saved(
            readTimelineInternal(candidate.conversationId)!!, replayed = false,
        )
    }

    private fun readDraftInternal(id: ConversationId): ConversationDraftSnapshot? {
        val exists = connection.prepare(
            "SELECT 1 FROM workspace_conversation WHERE id = ?"
        ).use { statement ->
            statement.bindText(1, id.value)
            statement.step()
        }
        if (!exists) return null
        val token = connection.prepare(
            "SELECT token FROM workspace_authority WHERE singleton = 1"
        ).use { statement ->
            check(statement.step()) { "Workspace authority token missing" }
            val value = statement.getText(0)
            check(value.isNotBlank() && !statement.step())
            value
        }
        var revision = 0L
        var text = ""
        connection.prepare(
            "SELECT revision, text FROM workspace_draft WHERE conversation_id = ?"
        ).use { statement ->
            statement.bindText(1, id.value)
            if (statement.step()) {
                revision = statement.getLong(0)
                text = statement.getText(1)
                check(!statement.step()) { "duplicate Workspace draft row" }
            }
        }
        return ConversationDraftSnapshot(id, token, revision, text)
    }

    /**
     * Staged native review authority. Only creates a pending plan after verifying
     * the exact canonical envelope via the kernel:runtime verification port.
     * No network, model call, approval, Reality mutation or UI attachment.
     */
    fun createPlanReview(
        request: WorkspacePlanReviewCreateRequest,
    ): WorkspacePlanReviewWriteResult = transaction {
        validateIdentity(request.conversationId)
        validateIdempotencyKey(request.idempotencyKey)
        require(request.expectedWorkspaceRevision >= 0)
        require(request.reviewId.length in 1..128 &&
            request.reviewId.none { it.code < 32 || it.code == 127 })
        val token = readAuthorityToken()
        if (request.authorityToken != token) return@transaction WorkspacePlanReviewWriteResult.Unavailable
        val bytes = request.planEnvelope.copyOf()
        val digest = sha256(bytes)
        if (bytes.isEmpty() || bytes.size > MAX_PLAN_BYTES ||
            !planEnvelopeVerifier.verify(bytes.copyOf(), request.planFingerprint))
            return@transaction WorkspacePlanReviewWriteResult.Rejected(
                WorkspacePlanReviewRejection.INVALID_PLAN
            )
        val pending = ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot(
            conversationId = request.conversationId, reviewId = request.reviewId,
            authorityToken = token, revision = 0,
            planFingerprint = request.planFingerprint, reviewText = request.reviewText,
        )
        val fingerprint = requestFingerprint(
            "plan.create.v4", request.conversationId.value,
            request.expectedWorkspaceRevision,
            listOf(token, request.reviewId, request.planFingerprint.value,
                request.reviewText, digest),
        )
        val existing = lookupPlanReceipt(request.idempotencyKey, fingerprint)
        if (existing != null) {
            if (!existing) return@transaction WorkspacePlanReviewWriteResult.IdentityConflict
            val replayed = readPlanReviewInternal(request.reviewId)
                ?: return@transaction WorkspacePlanReviewWriteResult.IdentityConflict
            return@transaction WorkspacePlanReviewWriteResult.Saved(replayed, true)
        }
        val before = readCurrent()
        if (before.revision != request.expectedWorkspaceRevision)
            return@transaction WorkspacePlanReviewWriteResult.Stale
        val chat = before.workspace.conversations.find { it.id == request.conversationId }
            ?: return@transaction WorkspacePlanReviewWriteResult.Rejected(
                WorkspacePlanReviewRejection.UNKNOWN_CONVERSATION
            )
        if (chat.archived)
            return@transaction WorkspacePlanReviewWriteResult.Rejected(WorkspacePlanReviewRejection.ARCHIVED)
        if (readPlanReviewInternal(request.reviewId) != null)
            return@transaction WorkspacePlanReviewWriteResult.Rejected(
                WorkspacePlanReviewRejection.DUPLICATE_REVIEW
            )
        val totals = connection.prepare(
            "SELECT COUNT(*), COALESCE(SUM(length(envelope)), 0) FROM workspace_plan_review"
        ).use { statement ->
            check(statement.step())
            statement.getLong(0) to statement.getLong(1)
        }
        if (totals.first >= WorkspaceBackupSnapshot.MAX_PLAN_REVIEWS ||
            totals.second + bytes.size > WorkspaceBackupSnapshot.MAX_TOTAL_PLAN_BYTES) {
            // Never evict an immutable plan or receipt automatically. A6 must
            // later provide a consented archival/resolution path.
            return@transaction WorkspacePlanReviewWriteResult.Rejected(
                WorkspacePlanReviewRejection.CAPACITY_EXCEEDED
            )
        }
        connection.prepare(
            """INSERT INTO workspace_plan_review
               (review_id, conversation_id, fingerprint, review_text, review_revision,
                state, envelope, envelope_sha256, transaction_id, receipt_id)
               VALUES (?, ?, ?, ?, 0, 'PENDING', ?, ?, NULL, NULL)"""
        ).use { statement ->
            statement.bindText(1, pending.reviewId)
            statement.bindText(2, pending.conversationId.value)
            statement.bindText(3, pending.planFingerprint.value)
            statement.bindText(4, pending.reviewText)
            statement.bindBlob(5, bytes)
            statement.bindText(6, digest)
            statement.step()
        }
        connection.prepare(
            "UPDATE workspace_conversation SET pending_review = 1 WHERE id = ?"
        ).use {
            it.bindText(1, request.conversationId.value)
            it.step()
        }
        updateMeta(before.revision + 1, before.workspace.activeConversationId)
        faultInjector(WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE)
        saveReceipt(request.idempotencyKey, fingerprint, before.revision + 1, true)
        WorkspacePlanReviewWriteResult.Saved(readPlanReviewInternal(request.reviewId)!!, false)
    }

    /** A4 staged review allows only human Approve/Discard. Apply outcomes remain
     * inaccessible until trusted Reality receipt integration in the A6 host.
     */
    internal fun transitionPlanReview(
        request: WorkspacePlanReviewTransitionRequest,
    ): WorkspacePlanReviewWriteResult = transaction {
        validateIdempotencyKey(request.idempotencyKey)
        val intent = request.intent
        if (intent !is ar.com.faro.v5.kernel.contracts.ConversationPlanReviewIntent.Approve &&
            intent !is ar.com.faro.v5.kernel.contracts.ConversationPlanReviewIntent.Discard)
            return@transaction WorkspacePlanReviewWriteResult.Rejected(
                WorkspacePlanReviewRejection.INVALID_TRANSITION
            )
        val current = readPlanReviewInternal(intent.context.reviewId)
            ?: return@transaction WorkspacePlanReviewWriteResult.Rejected(
                WorkspacePlanReviewRejection.NOT_FOUND
            )
        val token = readAuthorityToken()
        if (intent.context.authorityToken != token)
            return@transaction WorkspacePlanReviewWriteResult.Unavailable
        val code = if (intent is ar.com.faro.v5.kernel.contracts.ConversationPlanReviewIntent.Approve)
            "plan.approve.v4" else "plan.discard.v4"
        val fingerprint = requestFingerprint(
            code, intent.context.conversationId.value, intent.context.expectedRevision,
            listOf(token, intent.context.reviewId, intent.context.planFingerprint.value),
        )
        val existing = lookupPlanReceipt(request.idempotencyKey, fingerprint)
        if (existing != null) {
            if (!existing) return@transaction WorkspacePlanReviewWriteResult.IdentityConflict
            return@transaction WorkspacePlanReviewWriteResult.Saved(current, true)
        }
        val evaluated = ar.com.faro.v5.kernel.contracts.ConversationPlanReviewPolicy.evaluate(
            current, intent
        )
        if (evaluated !is ar.com.faro.v5.kernel.contracts.ConversationPlanReviewTransition.Accepted) {
            return@transaction WorkspacePlanReviewWriteResult.Rejected(
                WorkspacePlanReviewRejection.INVALID_TRANSITION
            )
        }
        val newState = evaluated.snapshot
        val before = readCurrent()
        connection.prepare(
            """UPDATE workspace_plan_review SET state = ?, review_revision = ?
               WHERE review_id = ? AND review_revision = ?"""
        ).use { statement ->
            statement.bindText(1, newState.state.name)
            statement.bindLong(2, newState.revision)
            statement.bindText(3, newState.reviewId)
            statement.bindLong(4, current.revision)
            statement.step()
        }
        refreshPendingReviewFlag(newState.conversationId)
        updateMeta(before.revision + 1, before.workspace.activeConversationId)
        faultInjector(WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE)
        saveReceipt(request.idempotencyKey, fingerprint, before.revision + 1, true)
        WorkspacePlanReviewWriteResult.Saved(readPlanReviewInternal(newState.reviewId)!!, false)
    }

    override suspend fun reviews(
        conversationId: ConversationId,
    ): List<ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot> =
        synchronized(mutex) {
            checkOpen()
            validateIdentity(conversationId)
            val reviewIds = mutableListOf<String>()
            connection.prepare(
                """SELECT review_id FROM workspace_plan_review
                   WHERE conversation_id = ? ORDER BY review_id LIMIT 128"""
            ).use { stmt ->
                stmt.bindText(1, conversationId.value)
                while (stmt.step()) reviewIds += stmt.getText(0)
            }
            // readPlanReviewInternal checks sealed envelope and plan fingerprint.
            reviewIds.map { requireNotNull(readPlanReviewInternal(it)) }
        }

    override suspend fun decide(
        intent: ConversationPlanReviewIntent,
        idempotencyKey: IdempotencyKey,
    ): ConversationReviewDecision {
        // UI cannot pass BeginApply, MarkRecovering or fabricate a Reality receipt.
        if (intent !is ConversationPlanReviewIntent.Approve &&
            intent !is ConversationPlanReviewIntent.Discard) return ConversationReviewDecision.Rejected
        return when (val result = transitionPlanReview(
            WorkspacePlanReviewTransitionRequest(intent, idempotencyKey),
        )) {
            is WorkspacePlanReviewWriteResult.Saved ->
                ConversationReviewDecision.Saved(result.review, result.replayed)
            WorkspacePlanReviewWriteResult.Stale,
            WorkspacePlanReviewWriteResult.Unavailable ->
                ConversationReviewDecision.StaleOrUnavailable
            else -> ConversationReviewDecision.Rejected
        }
    }

    /**
     * Host-only: returns the canonical sealed plan ONLY for the exact
     * human-approved context and generation. No unchecked caller envelope.
     */
    override fun load(context: ConversationPlanReviewContext): TrustedApprovedConversationPlan? =
        synchronized(mutex) {
            checkOpen()
            val review = readPlanReviewInternal(context.reviewId) ?: return@synchronized null
            if (review.context() != context ||
                review.state !in setOf(
                    ConversationPlanReviewState.APPROVED,
                    ConversationPlanReviewState.APPLYING,
                    ConversationPlanReviewState.RECOVERING_UNKNOWN,
                )
            ) return@synchronized null
            connection.prepare(
                "SELECT envelope FROM workspace_plan_review WHERE review_id = ?"
            ).use { statement ->
                statement.bindText(1, context.reviewId)
                check(statement.step())
                val bytes = statement.getBlob(0).copyOf()
                check(planEnvelopeVerifier.verify(bytes.copyOf(), review.planFingerprint))
                TrustedApprovedConversationPlan(review, bytes)
            }
        }

    /**
     * Host-only reconciliation. An intent is accepted only when Reality's
     * actual journal or canonical durable receipt independently confirms it.
     * The UI-only decide() still rejects these transitions.
     */
    override fun advance(
        intent: ConversationPlanReviewIntent,
        key: IdempotencyKey,
        attempts: ApplyAttemptLookupPort,
        receipts: ExecutionReceiptLookupPort,
    ): ConversationReviewDecision = transaction {
        if (intent !is ConversationPlanReviewIntent.BeginApply &&
            intent !is ConversationPlanReviewIntent.MarkRecovering &&
            intent !is ConversationPlanReviewIntent.ObserveCommitted)
            return@transaction ConversationReviewDecision.Rejected
        validateIdempotencyKey(key)
        val context = intent.context
        val current = readPlanReviewInternal(context.reviewId)
            ?: return@transaction ConversationReviewDecision.Rejected
        if (current.context() != context)
            return@transaction ConversationReviewDecision.StaleOrUnavailable
        val identity = when (intent) {
            is ConversationPlanReviewIntent.BeginApply -> intent.identity
            else -> {
                val tx = current.transactionId
                    ?: return@transaction ConversationReviewDecision.Rejected
                ar.com.faro.v5.kernel.contracts.ApplyAttemptIdentity(tx, current.planFingerprint)
            }
        }
        if (identity.planFingerprint != current.planFingerprint)
            return@transaction ConversationReviewDecision.Rejected
        val attempt = attempts.find(identity.transactionId)
            ?: return@transaction ConversationReviewDecision.Rejected
        if (attempt.transactionId != identity.transactionId ||
            attempt.planFingerprint != identity.planFingerprint)
            return@transaction ConversationReviewDecision.Rejected
        if (intent is ConversationPlanReviewIntent.ObserveCommitted) {
            if (attempt.state != ApplyAttemptJournalState.COMMITTED ||
                receipts.find(identity.transactionId) != intent.receipt)
                return@transaction ConversationReviewDecision.Rejected
        }
        val code = when (intent) {
            is ConversationPlanReviewIntent.BeginApply -> "plan.begin.reality.v1"
            is ConversationPlanReviewIntent.MarkRecovering -> "plan.recover.reality.v1"
            is ConversationPlanReviewIntent.ObserveCommitted -> "plan.commit.reality.v1"
            else -> error("trusted intent denied")
        }
        val fingerprint = requestFingerprint(
            code, context.conversationId.value, context.expectedRevision,
            listOf(context.reviewId, context.authorityToken,
                current.planFingerprint.value, identity.transactionId.value,
                if (intent is ConversationPlanReviewIntent.ObserveCommitted)
                    intent.receipt.receiptId.value else ""),
        )
        val replay = lookupPlanReceipt(key, fingerprint)
        if (replay != null) {
            if (!replay) return@transaction ConversationReviewDecision.Rejected
            return@transaction ConversationReviewDecision.Saved(current, true)
        }
        val evaluated = ar.com.faro.v5.kernel.contracts.ConversationPlanReviewPolicy.evaluate(
            current, intent,
        )
        if (evaluated !is ConversationPlanReviewTransition.Accepted)
            return@transaction ConversationReviewDecision.Rejected
        val next = evaluated.snapshot
        val before = readCurrent()
        connection.prepare(
            """UPDATE workspace_plan_review SET state = ?, review_revision = ?,
                      transaction_id = ?, receipt_id = ?
               WHERE review_id = ? AND review_revision = ?"""
        ).use { statement ->
            statement.bindText(1, next.state.name)
            statement.bindLong(2, next.revision)
            val committedTransactionId = next.transactionId
            if (committedTransactionId == null) statement.bindNull(3)
            else statement.bindText(3, committedTransactionId.value)
            val verifiedReceipt = next.receipt
            if (verifiedReceipt == null) statement.bindNull(4)
            else statement.bindText(4, verifiedReceipt.receiptId.value)
            statement.bindText(5, next.reviewId)
            statement.bindLong(6, current.revision)
            statement.step()
        }
        refreshPendingReviewFlag(next.conversationId)
        updateMeta(before.revision + 1, before.workspace.activeConversationId)
        faultInjector(WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE)
        saveReceipt(key, fingerprint, before.revision + 1, true)
        ConversationReviewDecision.Saved(requireNotNull(readPlanReviewInternal(next.reviewId)), false)
    }

    internal fun planReviewSnapshot(
        reviewId: String,
    ): ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot? = synchronized(mutex) {
        checkOpen()
        readPlanReviewInternal(reviewId)
    }

    private fun readAuthorityToken(): String =
        connection.prepare("SELECT token FROM workspace_authority WHERE singleton = 1").use {
            check(it.step()) { "Workspace authority token missing" }
            it.getText(0).also { token -> check(token.isNotBlank()) }
        }

    private fun lookupPlanReceipt(key: IdempotencyKey, fingerprint: String): Boolean? =
        connection.prepare("SELECT fingerprint FROM workspace_receipt WHERE key = ?").use {
            it.bindText(1, key.value)
            if (!it.step()) null else it.getText(0) == fingerprint
        }

    private fun readPlanReviewInternal(
        reviewId: String,
    ): ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot? {
        connection.prepare(
            """SELECT conversation_id, fingerprint, review_text, review_revision,
                      state, envelope, envelope_sha256, transaction_id, receipt_id
               FROM workspace_plan_review WHERE review_id = ?"""
        ).use { statement ->
            statement.bindText(1, reviewId)
            if (!statement.step()) return null
            val conversation = ConversationId(statement.getText(0))
            val fingerprint = ar.com.faro.v5.kernel.contracts.PlanFingerprint(statement.getText(1))
            val text = statement.getText(2)
            val revision = statement.getLong(3)
            val state = ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState.valueOf(
                statement.getText(4)
            )
            val encoded = statement.getBlob(5).copyOf()
            val digest = statement.getText(6)
            check(encoded.isNotEmpty() && encoded.size <= MAX_PLAN_BYTES &&
                sha256(encoded) == digest &&
                planEnvelopeVerifier.verify(encoded.copyOf(), fingerprint)) {
                "Workspace plan envelope invalid or unavailable verifier"
            }
            val transactionId = if (statement.isNull(7)) null
                else ar.com.faro.v5.kernel.contracts.TransactionId(statement.getText(7))
            val receipt = if (statement.isNull(8)) null else
                ar.com.faro.v5.kernel.contracts.ExecutionReceipt(
                    ar.com.faro.v5.kernel.contracts.ReceiptId(statement.getText(8)),
                    requireNotNull(transactionId), fingerprint,
                )
            return ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot(
                conversationId = conversation, reviewId = reviewId,
                authorityToken = readAuthorityToken(), revision = revision,
                planFingerprint = fingerprint, reviewText = text,
                state = state, transactionId = transactionId, receipt = receipt,
            )
        }
    }

    private fun refreshPendingReviewFlag(chat: ConversationId) {
        val pendingCount = connection.prepare(
            """SELECT COUNT(*) FROM workspace_plan_review
               WHERE conversation_id = ?
                 AND state IN ('PENDING','APPROVED','APPLYING','RECOVERING_UNKNOWN')"""
        ).use {
            it.bindText(1, chat.value)
            check(it.step())
            it.getLong(0)
        }
        connection.prepare(
            "UPDATE workspace_conversation SET pending_review = ? WHERE id = ?"
        ).use {
            it.bindLong(1, if (pendingCount > 0) 1 else 0)
            it.bindText(2, chat.value)
            it.step()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** Staged port: not registered in Maintenance until complete Workspace backup is supported. */
    val backup: WorkspaceBackupPort = WorkspaceBackupPort(
        readSnapshot = ::readBackupSnapshot,
        restoreSnapshot = ::restoreOrVerifyBackup,
    )

    private fun readBackupSnapshot(): WorkspaceBackupSnapshot = synchronized(mutex) {
        checkOpen()
        val current = readCurrent()
        val receipts = mutableListOf<WorkspaceBackupReceipt>()
        connection.prepare(
            """SELECT key, fingerprint, committed_revision, changed
               FROM workspace_receipt ORDER BY key ASC"""
        ).use { statement ->
            while (statement.step()) {
                receipts += WorkspaceBackupReceipt(
                    key = statement.getText(0),
                    fingerprint = statement.getText(1),
                    committedRevision = statement.getLong(2),
                    changed = statement.getLong(3) == 1L,
                )
            }
        }
        val drafts = mutableListOf<WorkspaceBackupDraft>()
        connection.prepare(
            "SELECT conversation_id, revision, text FROM workspace_draft ORDER BY conversation_id"
        ).use { statement ->
            while (statement.step()) {
                drafts += WorkspaceBackupDraft(
                    ConversationId(statement.getText(0)),
                    statement.getLong(1),
                    statement.getText(2),
                )
            }
        }
        val reviews = mutableListOf<WorkspaceBackupPlanReview>()
        connection.prepare(
            """SELECT review_id, conversation_id, fingerprint, review_text, review_revision,
                      state, envelope, envelope_sha256, transaction_id, receipt_id
               FROM workspace_plan_review ORDER BY review_id ASC"""
        ).use { stmt ->
            while (stmt.step()) {
                val bytes = stmt.getBlob(6)
                val fingerprint = ar.com.faro.v5.kernel.contracts.PlanFingerprint(stmt.getText(2))
                check(bytes.isNotEmpty() && bytes.size <= MAX_PLAN_BYTES &&
                    sha256(bytes) == stmt.getText(7) &&
                    planEnvelopeVerifier.verify(bytes.copyOf(), fingerprint)) {
                    "Cannot snapshot unverified prepared review"
                }
                reviews += WorkspaceBackupPlanReview(
                    reviewId = stmt.getText(0),
                    conversationId = ConversationId(stmt.getText(1)),
                    planFingerprint = fingerprint,
                    reviewText = stmt.getText(3),
                    revision = stmt.getLong(4),
                    state = ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState.valueOf(stmt.getText(5)),
                    envelopeBase64 = java.util.Base64.getEncoder().encodeToString(bytes),
                    envelopeSha256 = stmt.getText(7),
                    transactionId = if (stmt.isNull(8)) null else stmt.getText(8),
                    receiptId = if (stmt.isNull(9)) null else stmt.getText(9),
                )
            }
        }
        val messages = mutableListOf<WorkspaceBackupMessage>()
        connection.prepare(
            """SELECT sequence, id, conversation_id, turn_id, role, state,
                      text, created_ms, reply_id
               FROM workspace_message ORDER BY sequence ASC"""
        ).use { statement ->
            while (statement.step()) {
                messages += WorkspaceBackupMessage(
                    sequence = statement.getLong(0),
                    message = ConversationMessage(
                        id = ConversationMessageId(statement.getText(1)),
                        conversationId = ConversationId(statement.getText(2)),
                        turnId = ConversationTurnId(statement.getText(3)),
                        role = ConversationMessageRole.valueOf(statement.getText(4)),
                        state = ConversationMessageState.valueOf(statement.getText(5)),
                        text = statement.getText(6),
                        createdAtEpochMillis = statement.getLong(7),
                        replyToMessageId = if (statement.isNull(8)) null
                            else ConversationMessageId(statement.getText(8)),
                    ),
                )
            }
        }
        WorkspaceBackupSnapshot(
            schemaVersion = WorkspaceBackupSnapshot.SCHEMA_VERSION,
            revision = current.revision,
            activeConversationId = current.workspace.activeConversationId,
            conversations = current.workspace.conversations,
            receipts = Collections.unmodifiableList(receipts.toList()),
            drafts = Collections.unmodifiableList(drafts.toList()),
            messages = Collections.unmodifiableList(messages.toList()),
            planReviews = Collections.unmodifiableList(reviews.toList()),
        ).also(WorkspaceBackupSnapshot::validateForRestore)
    }

    private fun restoreOrVerifyBackup(
        snapshot: WorkspaceBackupSnapshot,
    ): WorkspaceBackupSnapshot = transaction {
        snapshot.validateForRestore()
        val before = readBackupSnapshot()
        if (before == snapshot) return@transaction before
        check(
            before.revision == 0L &&
                before.conversations.isEmpty() &&
                before.receipts.isEmpty() &&
                before.drafts.isEmpty() &&
                before.messages.isEmpty() &&
                before.planReviews.isEmpty() &&
                before.activeConversationId == null
        ) { "Workspace semantic restore target is not fresh or identical" }

        snapshot.conversations.forEach { entry ->
            connection.prepare(
                """INSERT INTO workspace_conversation
                   (id, title, activity_ms, pinned, archived, pending_review)
                   VALUES (?, ?, ?, ?, ?, 0)"""
            ).use { statement ->
                statement.bindText(1, entry.id.value)
                statement.bindText(2, entry.title)
                statement.bindLong(3, entry.lastActivityEpochMillis)
                statement.bindLong(4, if (entry.pinned) 1 else 0)
                statement.bindLong(5, if (entry.archived) 1 else 0)
                statement.step()
            }
        }
        snapshot.drafts.forEach { draft ->
            connection.prepare(
                "INSERT INTO workspace_draft(conversation_id, revision, text) VALUES (?, ?, ?)"
            ).use { statement ->
                statement.bindText(1, draft.conversationId.value)
                statement.bindLong(2, draft.revision)
                statement.bindText(3, draft.text)
                statement.step()
            }
        }
        snapshot.messages.forEach { item ->
            val message = item.message
            connection.prepare(
                """INSERT INTO workspace_message
                   (sequence, id, conversation_id, turn_id, role, state,
                    text, created_ms, reply_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { statement ->
                statement.bindLong(1, item.sequence)
                statement.bindText(2, message.id.value)
                statement.bindText(3, message.conversationId.value)
                statement.bindText(4, message.turnId.value)
                statement.bindText(5, message.role.name)
                statement.bindText(6, message.state.name)
                statement.bindText(7, message.text)
                statement.bindLong(8, message.createdAtEpochMillis)
                val quoted = message.replyToMessageId
                if (quoted == null) statement.bindNull(9)
                else statement.bindText(9, quoted.value)
                statement.step()
            }
        }
        snapshot.planReviews.forEach { review ->
            val bytes = java.util.Base64.getDecoder().decode(review.envelopeBase64)
            check(planEnvelopeVerifier.verify(bytes.copyOf(), review.planFingerprint)) {
                "Cannot restore a plan without a verifying kernel runtime boundary"
            }
            connection.prepare(
                """INSERT INTO workspace_plan_review
                   (review_id, conversation_id, fingerprint, review_text, review_revision,
                    state, envelope, envelope_sha256, transaction_id, receipt_id)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).use { stmt ->
                stmt.bindText(1, review.reviewId)
                stmt.bindText(2, review.conversationId.value)
                stmt.bindText(3, review.planFingerprint.value)
                stmt.bindText(4, review.reviewText)
                stmt.bindLong(5, review.revision)
                stmt.bindText(6, review.state.name)
                stmt.bindBlob(7, bytes)
                stmt.bindText(8, review.envelopeSha256)
                if (review.transactionId == null) stmt.bindNull(9)
                else stmt.bindText(9, review.transactionId)
                if (review.receiptId == null) stmt.bindNull(10)
                else stmt.bindText(10, review.receiptId)
                stmt.step()
            }
        }
        snapshot.conversations.forEach { entry -> refreshPendingReviewFlag(entry.id) }
        updateMeta(snapshot.revision, snapshot.activeConversationId)
        faultInjector(WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE)
        snapshot.receipts.forEach { receipt ->
            saveReceipt(
                key = IdempotencyKey(receipt.key),
                fingerprint = receipt.fingerprint,
                revision = receipt.committedRevision,
                changed = receipt.changed,
            )
        }
        readBackupSnapshot().also { restored ->
            check(restored == snapshot) {
                "Workspace restored authority does not match the source backup"
            }
        }
    }

    override suspend fun createAndSelect(id: ConversationId, atMillis: Long, key: IdempotencyKey): ConversationSelectionResult {
        require(atMillis >= 0L)
        return selectionOutcome(create(WorkspaceCreateRequest(
            ConversationWorkspaceEntry(id, "Nuevo chat", atMillis), snapshot().revision, key, true,
        )))
    }
    override suspend fun select(id: ConversationId, key: IdempotencyKey): ConversationSelectionResult =
        selectionOutcome(transition(WorkspaceTransitionRequest(
            ConversationWorkspaceIntent.Activate(id), snapshot().revision, key,
        )))
    private fun selectionOutcome(value: WorkspaceWriteResult): ConversationSelectionResult = when (value) {
        is WorkspaceWriteResult.Committed -> ConversationSelectionResult.Saved(value.snapshot.workspace, value.replayed)
        is WorkspaceWriteResult.Stale -> ConversationSelectionResult.Stale(value.current.workspace)
        is WorkspaceWriteResult.Rejected -> ConversationSelectionResult.Rejected
        WorkspaceWriteResult.IdentityConflict -> ConversationSelectionResult.IdentityConflict
    }

    internal fun create(request: WorkspaceCreateRequest): WorkspaceWriteResult =
        transaction {
            require(request.expectedRevision >= 0) { "negative Workspace expected revision" }
            validateIdentity(request.entry.id)
            validateIdempotencyKey(request.idempotencyKey)
            require(!request.entry.archived && !request.entry.pinned && !request.entry.hasPendingReview) {
                "new conversation may not forge archive, pin or pending review state"
            }
            val title = request.entry.title.trim()
            require(validTitle(title)) { "new conversation title invalid" }
            val fingerprint = requestFingerprint(
                if (request.selectOnCreate) "create.select.v1" else "create",
                request.entry.id.value, request.expectedRevision,
                listOf(title, request.entry.lastActivityEpochMillis.toString()),
            )
            val replay = lookupReplay(request.idempotencyKey, fingerprint)
            if (replay != null) return@transaction replay
            val before = readCurrent()
            if (before.revision != request.expectedRevision) {
                return@transaction WorkspaceWriteResult.Stale(before)
            }
            // Preserve V4.4 empty-chat reuse without a second authority.
            if (request.selectOnCreate) {
                val active = before.workspace.activeConversationId
                val old = before.workspace.conversations.firstOrNull { it.id == active }
                if (active != null && old != null && !old.archived && !old.hasPendingReview &&
                    readDraftInternal(active)?.text?.isEmpty() == true &&
                    readTimelineInternal(active)?.messages?.isEmpty() == true
                ) {
                    saveReceipt(request.idempotencyKey, fingerprint, before.revision, false)
                    return@transaction WorkspaceWriteResult.Committed(readCurrent(), before.revision, false, false)
                }
            }
            if (before.workspace.conversations.any { it.id == request.entry.id }) {
                return@transaction WorkspaceWriteResult.Rejected(
                    WorkspaceWriteRejection.ID_ALREADY_EXISTS
                )
            }

            connection.prepare(
                """INSERT INTO workspace_conversation
                   (id, title, activity_ms, pinned, archived, pending_review)
                   VALUES (?, ?, ?, 0, 0, 0)"""
            ).use { statement ->
                statement.bindText(1, request.entry.id.value)
                statement.bindText(2, title)
                statement.bindLong(3, request.entry.lastActivityEpochMillis)
                statement.step()
            }
            updateMeta(before.revision + 1,
                if (request.selectOnCreate) request.entry.id else before.workspace.activeConversationId)
            faultInjector(WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE)
            saveReceipt(request.idempotencyKey, fingerprint, before.revision + 1, true)
            WorkspaceWriteResult.Committed(readCurrent(), before.revision + 1, true, false)
        }

    internal fun transition(request: WorkspaceTransitionRequest): WorkspaceWriteResult =
        transaction {
            require(request.expectedRevision >= 0) { "negative Workspace expected revision" }
            validateIdentity(request.intent.conversationId)
            validateIdempotencyKey(request.idempotencyKey)
            // Persisted fingerprint codes must survive Kotlin/R8 class renaming.
            val (operationCode, parameters) =
                when (val intent = request.intent) {
                    is ConversationWorkspaceIntent.Activate ->
                        "conversation.activate.v1" to emptyList()
                    is ConversationWorkspaceIntent.Rename ->
                        "conversation.rename.v1" to listOf(intent.title)
                    is ConversationWorkspaceIntent.SetPinned ->
                        "conversation.pin.set.v1" to listOf(intent.pinned.toString())
                    is ConversationWorkspaceIntent.SetArchived ->
                        "conversation.archive.set.v1" to listOf(intent.archived.toString())
                }
            val fingerprint = requestFingerprint(
                operationCode, request.intent.conversationId.value,
                request.expectedRevision, parameters,
            )
            val replay = lookupReplay(request.idempotencyKey, fingerprint)
            if (replay != null) return@transaction replay
            val before = readCurrent()
            if (before.revision != request.expectedRevision) {
                return@transaction WorkspaceWriteResult.Stale(before)
            }
            val evaluation = ConversationWorkspaceTransitions.evaluate(
                before.workspace, request.intent
            )
            when (evaluation) {
                is ConversationWorkspaceTransition.Rejected ->
                    WorkspaceWriteResult.Rejected(
                        when (evaluation.reason) {
                            ConversationWorkspaceRejection.NOT_FOUND ->
                                WorkspaceWriteRejection.UNKNOWN_CONVERSATION
                            ConversationWorkspaceRejection.ARCHIVED ->
                                WorkspaceWriteRejection.ARCHIVED_CONVERSATION
                            ConversationWorkspaceRejection.INVALID_TITLE ->
                                WorkspaceWriteRejection.INVALID_TITLE
                            ConversationWorkspaceRejection.PENDING_REVIEW ->
                                WorkspaceWriteRejection.PENDING_REVIEW
                        }
                    )
                is ConversationWorkspaceTransition.Accepted -> {
                    if (evaluation.changed) {
                        val changedEntry = evaluation.snapshot.conversations.first {
                            it.id == request.intent.conversationId
                        }
                        connection.prepare(
                            """UPDATE workspace_conversation
                               SET title = ?, activity_ms = ?, pinned = ?,
                                   archived = ?, pending_review = ?
                               WHERE id = ?"""
                        ).use { statement ->
                            statement.bindText(1, changedEntry.title)
                            statement.bindLong(2, changedEntry.lastActivityEpochMillis)
                            statement.bindLong(3, if (changedEntry.pinned) 1 else 0)
                            statement.bindLong(4, if (changedEntry.archived) 1 else 0)
                            statement.bindLong(5, if (changedEntry.hasPendingReview) 1 else 0)
                            statement.bindText(6, changedEntry.id.value)
                            statement.step()
                        }
                        updateMeta(before.revision + 1, evaluation.snapshot.activeConversationId)
                    }
                    faultInjector(WorkspaceFaultPoint.AFTER_AUTHORITY_UPDATE)
                    val committedRevision =
                        before.revision + if (evaluation.changed) 1 else 0
                    saveReceipt(
                        request.idempotencyKey, fingerprint,
                        committedRevision, evaluation.changed,
                    )
                    WorkspaceWriteResult.Committed(
                        readCurrent(), committedRevision, evaluation.changed, false
                    )
                }
            }
        }

    private fun lookupReplay(
        key: IdempotencyKey,
        fingerprint: String,
    ): WorkspaceWriteResult? {
        connection.prepare(
            "SELECT fingerprint, committed_revision, changed FROM workspace_receipt WHERE key = ?"
        ).use { statement ->
            statement.bindText(1, key.value)
            if (!statement.step()) return null
            if (statement.getText(0) != fingerprint) {
                return WorkspaceWriteResult.IdentityConflict
            }
            return WorkspaceWriteResult.Committed(
                readCurrent(), statement.getLong(1), statement.getLong(2) == 1L, true
            )
        }
    }

    private fun saveReceipt(
        key: IdempotencyKey,
        fingerprint: String,
        revision: Long,
        changed: Boolean,
    ) {
        connection.prepare(
            """INSERT INTO workspace_receipt
               (key, fingerprint, committed_revision, changed) VALUES (?, ?, ?, ?)"""
        ).use { statement ->
            statement.bindText(1, key.value)
            statement.bindText(2, fingerprint)
            statement.bindLong(3, revision)
            statement.bindLong(4, if (changed) 1 else 0)
            statement.step()
        }
    }

    private fun updateMeta(revision: Long, active: ConversationId?) {
        connection.prepare(
            "UPDATE workspace_meta SET revision = ?, active_id = ? WHERE singleton = 1"
        ).use { statement ->
            statement.bindLong(1, revision)
            if (active == null) statement.bindNull(2) else statement.bindText(2, active.value)
            statement.step()
        }
    }

    private fun readCurrent(): WorkspaceRevisionSnapshot {
        val revision: Long
        val active: ConversationId?
        connection.prepare(
            "SELECT revision, active_id FROM workspace_meta WHERE singleton = 1"
        ).use { statement ->
            check(statement.step()) { "Workspace metadata singleton missing" }
            revision = statement.getLong(0)
            active = if (statement.isNull(1)) null else ConversationId(statement.getText(1))
            check(!statement.step()) { "duplicate Workspace singleton" }
        }
        val entries = mutableListOf<ConversationWorkspaceEntry>()
        connection.prepare(
            """SELECT id, title, activity_ms, pinned, archived, pending_review
               FROM workspace_conversation ORDER BY id ASC"""
        ).use { statement ->
            while (statement.step()) {
                entries += ConversationWorkspaceEntry(
                    id = ConversationId(statement.getText(0)),
                    title = statement.getText(1),
                    lastActivityEpochMillis = statement.getLong(2),
                    pinned = statement.getLong(3) == 1L,
                    archived = statement.getLong(4) == 1L,
                    hasPendingReview = statement.getLong(5) == 1L,
                )
            }
        }
        check(active == null || entries.any { it.id == active && !it.archived }) {
            "active conversation must exist and not be archived"
        }
        return WorkspaceRevisionSnapshot(
            revision, ConversationWorkspaceSnapshot(
                Collections.unmodifiableList(entries.toList()),
                active,
            )
        )
    }

    /** Admission precedes Workspace mutex/SQLite and is reentrant under maintenance's exclusive lease. */
    private fun <T> transaction(action: () -> T): T = withMaterialAdmission {
        synchronized(mutex) {
            checkOpen()
            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                val result = action()
                connection.execSQL("COMMIT TRANSACTION")
                result
            } catch (error: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw error
            }
        }
    }

    private fun configure(fileName: String) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        connection.execSQL("PRAGMA synchronous = FULL")
        if (fileName != ":memory:") {
            connection.prepare("PRAGMA journal_mode = WAL").use { statement ->
                check(statement.step() && statement.getText(0).equals("wal", ignoreCase = true)) {
                    "Workspace requires WAL"
                }
            }
        }
    }

    private fun initialize(requireExistingSchema: Boolean) {
        val version = connection.prepare("PRAGMA user_version").use {
            check(it.step())
            it.getLong(0).toInt()
        }
        check(version in 0..4) { "unsupported Workspace schema version: $version" }
        check(!requireExistingSchema || version == 4) {
            "existing V2 Workspace schema is absent; initialization is forbidden"
        }
        if (version == 0) transaction {
            connection.execSQL(
                """CREATE TABLE workspace_conversation (
                   id TEXT NOT NULL PRIMARY KEY CHECK(length(id) BETWEEN 1 AND 128),
                   title TEXT NOT NULL CHECK(length(title) BETWEEN 1 AND 80),
                   activity_ms INTEGER NOT NULL CHECK(activity_ms >= 0),
                   pinned INTEGER NOT NULL CHECK(pinned IN (0,1)),
                   archived INTEGER NOT NULL CHECK(archived IN (0,1)),
                   pending_review INTEGER NOT NULL CHECK(pending_review IN (0,1)),
                   CHECK(archived = 0 OR pinned = 0)
                )"""
            )
            connection.execSQL(
                """CREATE TABLE workspace_meta (
                   singleton INTEGER NOT NULL PRIMARY KEY CHECK(singleton = 1),
                   revision INTEGER NOT NULL CHECK(revision >= 0),
                   active_id TEXT REFERENCES workspace_conversation(id)
                )"""
            )
            connection.execSQL(
                """CREATE TABLE workspace_receipt (
                   key TEXT NOT NULL PRIMARY KEY CHECK(length(key) BETWEEN 1 AND 160),
                   fingerprint TEXT NOT NULL,
                   committed_revision INTEGER NOT NULL CHECK(committed_revision >= 0),
                   changed INTEGER NOT NULL CHECK(changed IN (0,1))
                )"""
            )
            connection.execSQL(
                "INSERT INTO workspace_meta (singleton, revision, active_id) VALUES (1,0,NULL)"
            )
            connection.execSQL("PRAGMA user_version = 1")
        }
        if (version <= 1) transaction {
            connection.execSQL(
                """CREATE TABLE workspace_draft (
                   conversation_id TEXT NOT NULL PRIMARY KEY
                      REFERENCES workspace_conversation(id) ON DELETE CASCADE,
                   revision INTEGER NOT NULL CHECK(revision >= 0),
                   text TEXT NOT NULL CHECK(length(text) <= 32000)
                )"""
            )
            connection.execSQL(
                """CREATE TABLE workspace_authority (
                   singleton INTEGER NOT NULL PRIMARY KEY CHECK(singleton = 1),
                   token TEXT NOT NULL UNIQUE CHECK(length(token) BETWEEN 1 AND 160)
                )"""
            )
            connection.prepare(
                "INSERT INTO workspace_authority(singleton, token) VALUES (1, ?)"
            ).use { statement ->
                statement.bindText(1, UUID.randomUUID().toString())
                statement.step()
            }
            connection.execSQL("PRAGMA user_version = 2")
        }
        // Staged v2 -> v3 message authority migration. openExisting(version<3)
        // fails before schema writes, preserving sealed V2 generation admission.
        if (version <= 2) transaction {
            connection.execSQL(
                """CREATE TABLE workspace_message (
                   sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                   id TEXT NOT NULL UNIQUE CHECK(length(id) BETWEEN 1 AND 128),
                   conversation_id TEXT NOT NULL
                       REFERENCES workspace_conversation(id) ON DELETE CASCADE,
                   turn_id TEXT NOT NULL CHECK(length(turn_id) BETWEEN 1 AND 128),
                   role TEXT NOT NULL CHECK(role IN ('USER','FARO')),
                   state TEXT NOT NULL CHECK(state IN
                       ('ACCEPTED','PROCESSING','COMPLETED','PARTIAL','FAILED','OFFLINE')),
                   text TEXT NOT NULL CHECK(length(text) <= 32000),
                   created_ms INTEGER NOT NULL CHECK(created_ms >= 0),
                   reply_id TEXT REFERENCES workspace_message(id),
                   UNIQUE(conversation_id, turn_id, role)
                )"""
            )
            connection.execSQL(
                "CREATE INDEX workspace_message_chat_order ON workspace_message(conversation_id, sequence)"
            )
            connection.execSQL("PRAGMA user_version = 3")
        }
        // Schema3 -> Schema4 staged review authority. Sealed openExisting refuses
        // migration before any write. No V2 application activation.
        if (version <= 3) transaction {
            connection.execSQL(
                """CREATE TABLE workspace_plan_review (
                   review_id TEXT NOT NULL PRIMARY KEY CHECK(length(review_id) BETWEEN 1 AND 128),
                   conversation_id TEXT NOT NULL
                       REFERENCES workspace_conversation(id) ON DELETE RESTRICT,
                   fingerprint TEXT NOT NULL CHECK(length(fingerprint) = 64),
                   review_text TEXT NOT NULL CHECK(length(review_text) BETWEEN 1 AND 4000),
                   review_revision INTEGER NOT NULL CHECK(review_revision >= 0),
                   state TEXT NOT NULL CHECK(state IN
                     ('PENDING','APPROVED','APPLYING','RECOVERING_UNKNOWN',
                      'REJECTED','COMMITTED','DISCARDED')),
                   envelope BLOB NOT NULL CHECK(length(envelope) BETWEEN 1 AND 524288),
                   envelope_sha256 TEXT NOT NULL CHECK(length(envelope_sha256) = 64),
                   transaction_id TEXT,
                   receipt_id TEXT,
                   CHECK(
                     (state IN ('PENDING','APPROVED','DISCARDED')
                          AND transaction_id IS NULL AND receipt_id IS NULL)
                     OR (state IN ('APPLYING','RECOVERING_UNKNOWN','REJECTED')
                          AND transaction_id IS NOT NULL AND receipt_id IS NULL)
                     OR (state = 'COMMITTED' AND transaction_id IS NOT NULL
                          AND receipt_id IS NOT NULL)
                   )
                )"""
            )
            connection.execSQL(
                "CREATE INDEX workspace_plan_review_conversation ON workspace_plan_review(conversation_id)"
            )
            connection.execSQL("PRAGMA user_version = 4")
        }
    }

    private fun inspect() {
        check(connection.prepare("PRAGMA quick_check").use {
            it.step() && it.getText(0) == "ok" && !it.step()
        }) { "Workspace SQLite integrity failed" }
        check(connection.prepare("PRAGMA foreign_key_check").use { !it.step() }) {
            "Workspace foreign key failure"
        }
        val current = readCurrent()
        val authorityCount = connection.prepare(
            "SELECT COUNT(*) FROM workspace_authority WHERE singleton = 1 AND length(token) > 0"
        ).use { statement ->
            check(statement.step())
            statement.getLong(0)
        }
        check(authorityCount == 1L) { "Workspace authority token invalid" }
        val pending = mutableMapOf<ConversationId, Long>()
        connection.prepare(
            """SELECT conversation_id, COUNT(*) FROM workspace_plan_review
               WHERE state IN ('PENDING','APPROVED','APPLYING','RECOVERING_UNKNOWN')
               GROUP BY conversation_id"""
        ).use { stmt ->
            while (stmt.step()) pending[ConversationId(stmt.getText(0))] = stmt.getLong(1)
        }
        current.workspace.conversations.forEach { entry ->
            check(entry.hasPendingReview == ((pending[entry.id] ?: 0L) > 0L)) {
                "Workspace review metadata mismatches canonical plan records"
            }
        }
        connection.prepare("SELECT revision, text FROM workspace_draft").use { statement ->
            while (statement.step()) {
                check(statement.getLong(0) in 0..current.revision &&
                    statement.getText(1).length <= ConversationDraftPolicy.MAX_TEXT_CHARS) {
                    "Workspace draft revision or size violates authority"
                }
            }
        }
    }

    private fun checkOpen() {
        check(!closed) { "Workspace store is closed" }
    }

    override fun close() = synchronized(mutex) {
        if (!closed) {
            closed = true
            connection.close()
        }
    }

    companion object {
        private const val MAX_PLAN_BYTES = 524_288
        /**
         * S2.A1.3b: V2 runtime can open only a previously materialized, versioned
         * Workspace. Normal constructor remains the explicit new-generation
         * initializer. This entrypoint cannot silently create the fifth DB
         * or its schema when an interrupted stage is resumed.
         */
        fun openExisting(
            fileName: String,
            driver: SQLiteDriver = BundledSQLiteDriver(),
            materialWriteGate: MaterialWriteGate? = null,
        ): ConversationWorkspaceStore {
            val path = java.nio.file.Path.of(fileName).toAbsolutePath().normalize()
            check(!java.nio.file.Files.isSymbolicLink(path) &&
                java.nio.file.Files.isRegularFile(
                    path, java.nio.file.LinkOption.NOFOLLOW_LINKS
                ) && java.nio.file.Files.size(path) > 0L) {
                "existing Workspace V2 SQLite file must be materialized"
            }
            return ConversationWorkspaceStore(fileName, driver, {}, true,
                PreparedPlanEnvelopeVerificationPort { _, _ -> false }, materialWriteGate)
        }

        /**
         * Staged, explicitly verified owner constructor. Caller must inject the
         * canonical runtime codec verifier; without one the default store refuses
         * every new plan and cannot expose an existing one.
         */
        fun withPlanVerifier(
            fileName: String,
            verifier: PreparedPlanEnvelopeVerificationPort,
            driver: SQLiteDriver = BundledSQLiteDriver(),
            materialWriteGate: MaterialWriteGate? = null,
        ): ConversationWorkspaceStore =
            ConversationWorkspaceStore(fileName, driver, {}, false, verifier, materialWriteGate)

        fun openExistingWithPlanVerifier(
            fileName: String,
            verifier: PreparedPlanEnvelopeVerificationPort,
            driver: SQLiteDriver = BundledSQLiteDriver(),
            materialWriteGate: MaterialWriteGate? = null,
        ): ConversationWorkspaceStore {
            val path = java.nio.file.Path.of(fileName).toAbsolutePath().normalize()
            check(!java.nio.file.Files.isSymbolicLink(path) &&
                java.nio.file.Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                java.nio.file.Files.size(path) > 0L) {
                "existing Workspace SQLite plan owner must already be materialized"
            }
            return ConversationWorkspaceStore(fileName, driver, {}, true, verifier, materialWriteGate)
        }

        private fun validateIdentity(id: ConversationId) {
            require(id.value.length <= 128 && id.value.none { it.code < 32 || it.code == 127 }) {
                "invalid Workspace conversation identity"
            }
        }

        private fun validateIdempotencyKey(key: IdempotencyKey) {
            require(
                key.value.length <= 160 &&
                    key.value.none { it.code < 32 || it.code == 127 }
            ) { "invalid Workspace idempotency key" }
        }

        private fun validTitle(title: String) =
            title.isNotEmpty() && title.length <= ConversationWorkspaceTransitions.MAX_TITLE_CHARS &&
                title.none { it.code < 32 || it.code == 127 }

        private fun requestFingerprint(
            kind: String,
            id: String,
            expectedRevision: Long,
            args: List<String>,
        ): String {
            val bytes = ByteArrayOutputStream().use { out ->
                DataOutputStream(out).use { stream ->
                    val all = listOf("faro.workspace.v1", kind, id,
                        expectedRevision.toString()) + args
                    all.forEach { item ->
                        val encoded = item.toByteArray(Charsets.UTF_8)
                        stream.writeInt(encoded.size)
                        stream.write(encoded)
                    }
                }
                out.toByteArray()
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }
}
