package ar.com.faro.v5.store.auxiliary

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import ar.com.faro.v5.delivery.api.DeliveryDesiredAction
import ar.com.faro.v5.delivery.api.DeliveryEffectState
import ar.com.faro.v5.delivery.api.DeliveryIntent
import ar.com.faro.v5.delivery.api.DeliveryIntentId
import ar.com.faro.v5.delivery.api.DeliverySink
import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeMaterializationIds
import ar.com.faro.v5.delivery.api.NoticeRelevance
import ar.com.faro.v5.delivery.api.NoticeResolutionReason
import ar.com.faro.v5.delivery.api.NoticeResolveResult
import ar.com.faro.v5.delivery.api.NoticeRouteResult
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSnapshot
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.delivery.spi.DeliveryDispatchPersistencePort
import ar.com.faro.v5.delivery.spi.DeliveryPersistencePort
import ar.com.faro.v5.delivery.spi.DeliveryProjectionPersistencePort
import ar.com.faro.v5.delivery.spi.NotificationDispatchClaim
import ar.com.faro.v5.delivery.spi.NotificationProjectionObservation
import ar.com.faro.v5.delivery.spi.NotificationProjectionRequest
import ar.com.faro.v5.delivery.spi.NotificationReconcileClaim
import ar.com.faro.v5.delivery.spi.NotificationSinkOutcome
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import java.util.concurrent.CopyOnWriteArraySet

internal enum class AuxiliaryDeliveryFaultPoint {
    AFTER_NOTICE_INSERT,
}

/**
 * W03-D2B1: the committed NoticeSnapshot itself is the durable CAS outcome.
 * There is deliberately NO request-key idempotency receipt in schema3.
 * An independent append-only receipt ledger needs a staged schema4 cutover.
 */
sealed interface S4W03NoticeAttentionCasResult {
    data class Saved(val notice: NoticeSnapshot) : S4W03NoticeAttentionCasResult
    data class Stale(val revision: Long) : S4W03NoticeAttentionCasResult
    data object Terminal : S4W03NoticeAttentionCasResult
    data object NotFound : S4W03NoticeAttentionCasResult
    data object Denied : S4W03NoticeAttentionCasResult
}

/** Historical committed revision, not a claim about current Notice state. */
data class S4W03NoticeAttentionReceiptV4(
    val requestKey: String,
    val requestHash: String,
    val noticeId: NoticeId,
    val expectedRevision: Long,
    val target: NoticeAttention,
    val resultRevision: Long,
) {
    init {
        require(requestKey.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:/-]{0,191}")))
        require(requestHash.matches(Regex("[0-9a-f]{64}")))
        require(target == NoticeAttention.SEEN || target == NoticeAttention.DISMISSED)
        require(expectedRevision > 0L && resultRevision == expectedRevision + 1L)
    }
}

sealed interface S4W03NoticeAttentionReceiptV4Result {
    data class Committed(val resultRevision: Long, val replayed: Boolean) :
        S4W03NoticeAttentionReceiptV4Result
    data class Stale(val currentRevision: Long) : S4W03NoticeAttentionReceiptV4Result
    data object IdentityConflict : S4W03NoticeAttentionReceiptV4Result
    data object Terminal : S4W03NoticeAttentionReceiptV4Result
    data object NotFound : S4W03NoticeAttentionReceiptV4Result
    data object Denied : S4W03NoticeAttentionReceiptV4Result
}

class AuxiliaryDeliveryStore private constructor(
    fileName: String,
    driver: SQLiteDriver,
    private val faultInjector: (AuxiliaryDeliveryFaultPoint) -> Unit,
    private val materialWriteGate: MaterialWriteGate?,
    private val bookmarkAuthorityGate: BookmarkAuthorityGate?,
    private val receiptAuthorityFence: (() -> Boolean)?,
    private val stagedReceiptOwner: Boolean = false,
) : DeliveryPersistencePort,
    DeliveryDispatchPersistencePort,
    DeliveryProjectionPersistencePort,
    AutoCloseable {
    constructor(
        fileName: String,
        driver: SQLiteDriver = BundledSQLiteDriver(),
        materialWriteGate: MaterialWriteGate? = null,
        bookmarkAuthorityGate: BookmarkAuthorityGate? = null,
    ) : this(
        fileName = fileName,
        driver = driver,
        faultInjector = {},
        materialWriteGate = materialWriteGate,
        bookmarkAuthorityGate = bookmarkAuthorityGate,
        receiptAuthorityFence = null,
    )

    internal constructor(
        fileName: String,
        faultInjector: (AuxiliaryDeliveryFaultPoint) -> Unit,
    ) : this(
        fileName = fileName,
        driver = BundledSQLiteDriver(),
        faultInjector = faultInjector,
        materialWriteGate = null,
        bookmarkAuthorityGate = null,
        receiptAuthorityFence = null,
    )
    private val lock = Any()
    // Owner-originated, process-local notice commits. Not an ACL/generation watcher.
    private val noticeCommitListeners = CopyOnWriteArraySet<Runnable>()

    fun addNoticeCommitListener(listener: Runnable) {
        synchronized(lock) {
            checkOpen()
            noticeCommitListeners.add(listener)
        }
    }

    fun removeNoticeCommitListener(listener: Runnable) {
        noticeCommitListeners.remove(listener)
    }

    private fun publishNoticeCommit() {
        // Notify after a committed route/resolve OR the canonical owner closes.
        for (listener in noticeCommitListeners) {
            try {
                listener.run()
            } catch (_: RuntimeException) {
                noticeCommitListeners.remove(listener)
            }
        }
    }
    /** Optional admission for staged V2; callers must share the host's SINGLE gate. */
    private fun <T> withMaterialAdmission(block: () -> T): T =
        materialWriteGate?.withMaterialWrite(block) ?: block()
    private val connection: SQLiteConnection = driver.open(fileName)
    private var closed = false

    /** Not mounted by shipping UI; null authority denies all Guardados access. */
    val bookmarks: AuxiliaryBookmarkStore = AuxiliaryBookmarkStore(
        connection, lock, materialWriteGate, bookmarkAuthorityGate, { !closed }
    )

    /**
     * Narrow read view on this owner's existing connection and mutex.
     * Injecting read authority never exposes or grants mutation authority.
     */
    fun bookmarkReaderForTrustedHost(
        authority: BookmarkAuthorityGate,
    ): ar.com.faro.v5.kernel.contracts.BookmarkReadPort {
        val readOnlyAuthority = object : BookmarkAuthorityGate {
            override fun isScopeActive(generationId: String, authorityToken: String) =
                authority.isScopeActive(generationId, authorityToken)
            override fun isTargetAvailable(target: ar.com.faro.v5.kernel.contracts.BookmarkTarget) =
                authority.isTargetAvailable(target)
        }
        val reader = AuxiliaryBookmarkStore(
            connection, lock, materialWriteGate, readOnlyAuthority, { !closed },
        )
        return ar.com.faro.v5.kernel.contracts.BookmarkReadPort { request -> reader.read(request) }
    }

    /** Same owner connection/lock; this seam has no write operation. */
    fun bookmarkReviewSnapshotForTrustedHost(
        authority: BookmarkAuthorityGate,
        target: ar.com.faro.v5.kernel.contracts.BookmarkTarget,
    ): BookmarkReviewSnapshot? = AuxiliaryBookmarkStore(
        connection, lock, materialWriteGate, authority, { !closed },
    ).reviewSnapshot(target)

    /** Same SQLite owner and mutex, technical read projections only. */
    fun bookmarkRemovedPageForTrustedHost(authority: BookmarkAuthorityGate,
        request: ar.com.faro.v5.kernel.contracts.BookmarkReadRequest): BookmarkRemovedPage? =
        AuxiliaryBookmarkStore(connection, lock, materialWriteGate, authority, { !closed }).removedSnapshot(request)

    fun bookmarkRemovedByIdForTrustedHost(authority: BookmarkAuthorityGate,
        request: ar.com.faro.v5.kernel.contracts.BookmarkReadRequest,
        id: ar.com.faro.v5.kernel.contracts.BookmarkId): BookmarkRemovedPage? =
        AuxiliaryBookmarkStore(connection, lock, materialWriteGate, authority, { !closed }).removedSnapshot(request, id)

    fun bookmarkFoldersForTrustedHost(authority: BookmarkAuthorityGate, generation: String,
        token: String): BookmarkFolderSnapshot? =
        AuxiliaryBookmarkStore(connection, lock, materialWriteGate, authority, { !closed }).folderSnapshot(generation, token)

    /** Mutation-only trusted port. Neither the read port nor a generic write boolean can grant it. */
    fun bookmarkMutatorForTrustedHost(
        authority: RequestScopedBookmarkAuthorityGate,
    ): ar.com.faro.v5.kernel.contracts.BookmarkMutationPort {
        val owner = AuxiliaryBookmarkStore(connection, lock, materialWriteGate, authority, { !closed })
        return ar.com.faro.v5.kernel.contracts.BookmarkMutationPort { request -> owner.mutate(request) }
    }

    val maintenance: AuxiliaryMaintenancePort =
        AuxiliaryMaintenancePort(::inspectForMaintenance)

    val backup: AuxiliaryBackupPort =
        object : AuxiliaryBackupPort {
            override fun snapshot(): AuxiliaryBackupSnapshot = readBackupSnapshot()

            override fun restoreOrVerify(
                snapshot: AuxiliaryBackupSnapshot,
            ): AuxiliaryBackupSnapshot = restoreOrVerifyBackup(snapshot)
        }

    val portability: AuxiliaryPortabilityPort =
        AuxiliaryPortabilityPort(::readPortabilitySnapshot)

    /** Opt-in metadata export; constructing a port cannot grant authority or bind UI. */
    fun bookmarkPortability(generationId: String, authorityToken: String): BookmarkPortabilityPort =
        BookmarkPortabilityPort {
            synchronized(lock) {
                checkOpen()
                val authorized = checkNotNull(bookmarks.authorizedPortabilitySnapshot(generationId, authorityToken)) {
                    "Guardados export authority unavailable"
                }
                BookmarkPortabilityProjection.from(readSchemaVersion(), authorized)
            }
        }


    private fun readPortabilitySnapshot(): AuxiliaryPortabilitySnapshot =
        synchronized(lock) {
            checkOpen()
            check(receiptAuthorityFence == null) { "Stage4 portability requires receipt codec" }
            AuxiliaryPortabilityProjection.fromStored(
                schemaVersion = readSchemaVersion(),
                notices = readNotices(),
            )
        }

    init {
        try {
            withMaterialAdmission {
                synchronized(lock) {
                    receiptAuthorityFence?.let { fence ->
                        check(fence()) { "Auxiliary4 owner lost generation authority" }
                        if (!stagedReceiptOwner) validateSchema4ReceiptTable()
                    }
                    configure(fileName)
                    if (receiptAuthorityFence == null) initializeSchema()
                    else initializeReceiptSchema4()
                }
            }
        } catch (failure: Throwable) {
            runCatching { connection.close() }
            throw failure
        }
    }

    /**
     * W03-D2B1 trusted, explicit-click CAS mutation on an existing LOCAL_ONLY
     * Reminder Notice. This never updates Reminder/Matter nor OS delivery.
     * The admission callback must only inspect in-memory scoped policy/pointer;
     * it must NOT acquire Reality or Workspace locks.
     *
     * Distinct request-key receipts are a separate schema4 migration task.
     */
    fun mutateReminderNoticeAttention(
        noticeId: NoticeId,
        expectedRevision: Long,
        target: NoticeAttention,
        admitted: () -> Boolean,
    ): S4W03NoticeAttentionCasResult {
        require(expectedRevision > 0L && expectedRevision < Long.MAX_VALUE)
        require(target == NoticeAttention.SEEN || target == NoticeAttention.DISMISSED)
        val result = withMaterialAdmission { synchronized(lock) {
            checkOpen()
            if (!admitted()) return@synchronized S4W03NoticeAttentionCasResult.Denied
            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                fun abort(outcome: S4W03NoticeAttentionCasResult): S4W03NoticeAttentionCasResult {
                    connection.execSQL("ROLLBACK TRANSACTION")
                    return outcome
                }
                val prior = readRouteByNoticeId(noticeId)
                    ?: return@synchronized abort(S4W03NoticeAttentionCasResult.NotFound)
                val notice = prior.notice
                if (!notice.sourceRef.value.startsWith("reminder:") ||
                    !notice.noticeKey.value.startsWith("reminder:") ||
                    notice.destinationRef.value != "faro:avisos.local" ||
                    prior.intent.state != DeliveryEffectState.SUPPRESSED_POLICY
                ) return@synchronized abort(S4W03NoticeAttentionCasResult.Denied)
                if (notice.revision != expectedRevision)
                    return@synchronized abort(S4W03NoticeAttentionCasResult.Stale(notice.revision))
                if (notice.relevance != NoticeRelevance.OPEN ||
                    notice.attention == NoticeAttention.DISMISSED ||
                    notice.attention == target
                ) return@synchronized abort(S4W03NoticeAttentionCasResult.Terminal)
                if (!admitted())
                    return@synchronized abort(S4W03NoticeAttentionCasResult.Denied)
                connection.prepare(
                    """
                    UPDATE delivery_notice SET attention = ?, revision = revision + 1
                    WHERE notice_id = ? AND revision = ? AND relevance = ?
                      AND attention = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.bindText(1, target.name)
                    statement.bindText(2, noticeId.value)
                    statement.bindLong(3, expectedRevision)
                    statement.bindText(4, NoticeRelevance.OPEN.name)
                    statement.bindText(5, notice.attention.name)
                    statement.step()
                }
                connection.prepare("SELECT changes()").use { statement ->
                    check(statement.step() && statement.getLong(0) == 1L) {
                        "Notice attention CAS expected exactly one row"
                    }
                }
                val committed = checkNotNull(readRouteByNoticeId(noticeId)).notice
                check(committed.revision == expectedRevision + 1L &&
                    committed.attention == target)
                if (!admitted())
                    return@synchronized abort(S4W03NoticeAttentionCasResult.Denied)
                connection.execSQL("COMMIT TRANSACTION")
                S4W03NoticeAttentionCasResult.Saved(committed)
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }
        } }
        if (result is S4W03NoticeAttentionCasResult.Saved) publishNoticeCommit()
        return result
    }

    /**
     * Receipt CAS: one SQLite owner, lock, BEGIN IMMEDIATE,
     * canonical request fingerprint, durable replay and before-COMMIT fences.
     * Requires explicit schema4 generation authority.
     */
    fun mutateReminderNoticeAttentionWithReceipt(
        requestKey: String,
        noticeId: NoticeId,
        expectedRevision: Long,
        target: NoticeAttention,
        admitted: () -> Boolean,
    ): S4W03NoticeAttentionReceiptV4Result {
        require(requestKey.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:/-]{0,191}")))
        require(expectedRevision > 0L && expectedRevision < Long.MAX_VALUE)
        require(target == NoticeAttention.SEEN || target == NoticeAttention.DISMISSED)
        val fingerprint = noticeAttentionReceiptHash(noticeId, expectedRevision, target)
        val result = withMaterialAdmission { synchronized(lock) {
            checkOpen()
            val fence = checkNotNull(receiptAuthorityFence) {
                "Receipt ledger requires authorized Auxiliary4"
            }
            check(readSchemaVersion() == 4) { "Notice receipt requires schema4" }
            if (!fence() || !admitted())
                return@synchronized S4W03NoticeAttentionReceiptV4Result.Denied
            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                fun abort(v: S4W03NoticeAttentionReceiptV4Result): S4W03NoticeAttentionReceiptV4Result {
                    connection.execSQL("ROLLBACK TRANSACTION")
                    return v
                }
                val prior = readReceiptV4(requestKey)
                if (prior != null) {
                    if (!fence() || !admitted())
                        return@synchronized abort(S4W03NoticeAttentionReceiptV4Result.Denied)
                    return@synchronized abort(
                        if (prior.requestHash == fingerprint &&
                            prior.noticeId == noticeId &&
                            prior.expectedRevision == expectedRevision && prior.target == target
                        ) S4W03NoticeAttentionReceiptV4Result.Committed(
                            prior.resultRevision, replayed = true,
                        ) else S4W03NoticeAttentionReceiptV4Result.IdentityConflict
                    )
                }
                val priorRoute = readRouteByNoticeId(noticeId)
                    ?: return@synchronized abort(S4W03NoticeAttentionReceiptV4Result.NotFound)
                val notice = priorRoute.notice
                if (!notice.sourceRef.value.startsWith("reminder:") ||
                    !notice.noticeKey.value.startsWith("reminder:") ||
                    notice.destinationRef.value != "faro:avisos.local" ||
                    priorRoute.intent.state != DeliveryEffectState.SUPPRESSED_POLICY
                ) return@synchronized abort(S4W03NoticeAttentionReceiptV4Result.Denied)
                if (notice.revision != expectedRevision)
                    return@synchronized abort(S4W03NoticeAttentionReceiptV4Result.Stale(notice.revision))
                if (notice.relevance != NoticeRelevance.OPEN ||
                    notice.attention == NoticeAttention.DISMISSED || notice.attention == target
                ) return@synchronized abort(S4W03NoticeAttentionReceiptV4Result.Terminal)
                if (!fence() || !admitted())
                    return@synchronized abort(S4W03NoticeAttentionReceiptV4Result.Denied)
                connection.prepare("""
                    UPDATE delivery_notice SET attention=?, revision=revision+1
                    WHERE notice_id=? AND revision=? AND relevance=? AND attention=?
                """.trimIndent()).use { st ->
                    st.bindText(1, target.name)
                    st.bindText(2, noticeId.value)
                    st.bindLong(3, expectedRevision)
                    st.bindText(4, NoticeRelevance.OPEN.name)
                    st.bindText(5, notice.attention.name)
                    st.step()
                }
                connection.prepare("SELECT changes()").use { st ->
                    check(st.step() && st.getLong(0) == 1L) {
                        "Notice receipt mutation requires one row CAS"
                    }
                }
                insertReceiptV4(
                    S4W03NoticeAttentionReceiptV4(
                        requestKey, fingerprint, noticeId, expectedRevision, target, expectedRevision + 1L,
                    )
                )
                if (!fence() || !admitted())
                    return@synchronized abort(S4W03NoticeAttentionReceiptV4Result.Denied)
                connection.execSQL("COMMIT TRANSACTION")
                S4W03NoticeAttentionReceiptV4Result.Committed(
                    expectedRevision + 1L, replayed = false,
                )
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }
        } }
        if (result is S4W03NoticeAttentionReceiptV4Result.Committed && !result.replayed)
            publishNoticeCommit()
        return result
    }

    /** Inspect isolated receipts. This is NOT a receipt-aware FBS2 backup. */
    fun readIsolatedStage4NoticeReceipts(): List<S4W03NoticeAttentionReceiptV4> =
        synchronized(lock) {
            checkOpen()
            check(stagedReceiptOwner && receiptAuthorityFence?.invoke() == true && readSchemaVersion() == 4)
            readAllReceiptsV4()
        }

    private fun readAllReceiptsV4(): List<S4W03NoticeAttentionReceiptV4> =
        connection.prepare("""
            SELECT request_key, request_hash, notice_id,
                   expected_revision, target_attention, result_revision
            FROM notice_attention_receipt ORDER BY request_key
        """.trimIndent()).use { statement ->
            buildList {
                while (statement.step()) add(S4W03NoticeAttentionReceiptV4(
                    statement.getText(0), statement.getText(1), NoticeId.of(statement.getText(2)),
                    statement.getLong(3), NoticeAttention.valueOf(statement.getText(4)), statement.getLong(5),
                ))
            }
        }

    private fun insertReceiptV4(receipt: S4W03NoticeAttentionReceiptV4) {
        connection.prepare("""
            INSERT INTO notice_attention_receipt(
                request_key, request_hash, notice_id,
                expected_revision, target_attention, result_revision
            ) VALUES(?,?,?,?,?,?)
        """.trimIndent()).use { statement ->
            statement.bindText(1, receipt.requestKey)
            statement.bindText(2, receipt.requestHash)
            statement.bindText(3, receipt.noticeId.value)
            statement.bindLong(4, receipt.expectedRevision)
            statement.bindText(5, receipt.target.name)
            statement.bindLong(6, receipt.resultRevision)
            statement.step()
        }
    }

    private fun readReceiptV4(key: String): S4W03NoticeAttentionReceiptV4? =
        connection.prepare("""
            SELECT request_key, request_hash, notice_id,
                   expected_revision, target_attention, result_revision
            FROM notice_attention_receipt WHERE request_key=?
        """.trimIndent()).use { st ->
            st.bindText(1, key)
            if (!st.step()) null else S4W03NoticeAttentionReceiptV4(
                st.getText(0), st.getText(1), NoticeId.of(st.getText(2)),
                st.getLong(3), NoticeAttention.valueOf(st.getText(4)), st.getLong(5),
            )
        }

    override fun readNotices(): List<NoticeSnapshot> = synchronized(lock) {
        checkOpen()
        val notices = mutableListOf<NoticeSnapshot>()
        connection.prepare(
            """
            SELECT notice_id,
                   notice_key,
                   source_ref,
                   occurrence_ref,
                   materiality,
                   sensitivity,
                   summary,
                   destination_ref,
                   relevance,
                   attention,
                   revision,
                   resolution_reason
            FROM delivery_notice
            ORDER BY notice_key, occurrence_ref
            """.trimIndent()
        ).use { statement ->
            while (statement.step()) {
                notices += statement.toNoticeSnapshot()
            }
        }
        notices
    }

    override fun route(
        candidate: NoticeCandidate,
        newIds: NoticeMaterializationIds,
    ): NoticeRouteResult {
        val result = withMaterialAdmission { synchronized(lock) {
        checkOpen()
        require(candidate.materiality != NoticeMateriality.SILENT) {
            "AuxiliaryDeliveryStore cannot materialize SILENT candidates"
        }

        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            val existing = readRoute(candidate.noticeKey, candidate.occurrenceRef)
            val result =
                if (existing == null) {
                    insertNew(candidate, newIds)
                } else {
                    coalesce(existing, candidate)
                }
            connection.execSQL("COMMIT TRANSACTION")
            result
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
        } }
        // A committed route can be idempotent; a redundant refresh is safe.
        publishNoticeCommit()
        return result
    }


    /**
     * W03-D2A: dedicated, LOCAL_ONLY Reminder Notice materialization.
     * Never dispatches Android nor changes the Reminder/Matter source owner.
     * Trusted DEV composition must supply a fresh scoped policy fence.
     * The admission callback MUST NOT acquire Reality/Workspace mutexes.
     */
    fun routeReminderLocalOnly(
        candidate: NoticeCandidate,
        admitted: () -> Boolean,
    ): NoticeRouteResult.Materialized {
        require(
            candidate.noticeKey.value.startsWith("reminder:") &&
                candidate.sourceRef.value.startsWith("reminder:") &&
                candidate.destinationRef.value == "faro:avisos.local" &&
                candidate.materiality == NoticeMateriality.NOTICE
        ) { "W03-D2A only routes real local Reminder Notice candidates" }
        val ids = reminderLocalIds(candidate)
        val result = withMaterialAdmission { synchronized(lock) {
            checkOpen()
            check(admitted()) { "Reminder local Notice admission missing" }
            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                val prior = readRoute(candidate.noticeKey, candidate.occurrenceRef)
                val routed = if (prior == null) {
                    insertNew(candidate, ids, DeliveryEffectState.SUPPRESSED_POLICY)
                } else {
                    check(
                        prior.notice.sourceRef == candidate.sourceRef &&
                            prior.notice.destinationRef == candidate.destinationRef &&
                            prior.intent.state == DeliveryEffectState.SUPPRESSED_POLICY &&
                            prior.intent.desiredAction == DeliveryDesiredAction.UPSERT &&
                            prior.intent.sink == DeliverySink.ANDROID_NOTIFICATION
                    ) { "Local Notice refuses to take over an OS or resolved route" }
                    coalesce(prior, candidate)
                }
                check(routed.deliveryIntent.state == DeliveryEffectState.SUPPRESSED_POLICY) {
                    "Local Reminder Notice unexpectedly became dispatchable"
                }
                check(admitted()) { "Reminder local Notice permission revoked before commit" }
                connection.execSQL("COMMIT TRANSACTION")
                routed
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }
        } }
        publishNoticeCommit()
        return result
    }

    /**
     * Source resolution ONLY; preserves Notice identity and attention history.
     * Never re-arms the OS (state remains SUPPRESSED_POLICY).
     */
    fun resolveReminderLocalOnly(
        noticeId: NoticeId,
        admitted: () -> Boolean,
    ): NoticeResolveResult {
        val result = withMaterialAdmission { synchronized(lock) {
            checkOpen()
            check(admitted()) { "Reminder local resolution admission missing" }
            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                val prior = readRouteByNoticeId(noticeId)
                val resolved = when {
                    prior == null -> NoticeResolveResult.NotFound
                    else -> {
                        check(
                            prior.notice.sourceRef.value.startsWith("reminder:") &&
                                prior.notice.destinationRef.value == "faro:avisos.local" &&
                                prior.intent.state == DeliveryEffectState.SUPPRESSED_POLICY
                        ) { "Cannot resolve an OS or non-Reminder route using local-only API" }
                        if (prior.notice.relevance == NoticeRelevance.RESOLVED) {
                            NoticeResolveResult.AlreadyResolved(prior.notice, prior.intent)
                        } else {
                            connection.prepare("""
                                UPDATE delivery_notice SET relevance=?, resolution_reason=?,
                                    revision=revision+1
                                WHERE notice_id=? AND relevance=?
                            """.trimIndent()).use { statement ->
                                statement.bindText(1, NoticeRelevance.RESOLVED.name)
                                statement.bindText(2, NoticeResolutionReason.SOURCE_RESOLVED.name)
                                statement.bindText(3, noticeId.value)
                                statement.bindText(4, NoticeRelevance.OPEN.name)
                                statement.step()
                            }
                            connection.prepare("SELECT changes()").use { statement ->
                                check(statement.step() && statement.getLong(0) == 1L) {
                                    "Local Notice resolution CAS failed"
                                }
                            }
                            connection.prepare("""
                                UPDATE delivery_intent SET desired_action=?
                                WHERE notice_id=? AND state=? AND desired_action=?
                            """.trimIndent()).use { statement ->
                                statement.bindText(1, DeliveryDesiredAction.CANCEL.name)
                                statement.bindText(2, noticeId.value)
                                statement.bindText(3, DeliveryEffectState.SUPPRESSED_POLICY.name)
                                statement.bindText(4, DeliveryDesiredAction.UPSERT.name)
                                statement.step()
                            }
                            connection.prepare("SELECT changes()").use { statement ->
                                check(statement.step() && statement.getLong(0) == 1L) {
                                    "Local Notice intent cancellation CAS failed"
                                }
                            }
                            val after = checkNotNull(readRouteByNoticeId(noticeId))
                            check(after.intent.state == DeliveryEffectState.SUPPRESSED_POLICY)
                            NoticeResolveResult.Resolved(after.notice, after.intent)
                        }
                    }
                }
                check(admitted()) { "Reminder local Notice resolution permission revoked" }
                connection.execSQL("COMMIT TRANSACTION")
                resolved
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }
        } }
        if (result is NoticeResolveResult.Resolved) publishNoticeCommit()
        return result
    }

    private fun reminderLocalIds(candidate: NoticeCandidate): NoticeMaterializationIds {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (part in listOf(candidate.noticeKey.value, candidate.occurrenceRef.value)) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        val key = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        return NoticeMaterializationIds(
            noticeId = NoticeId.of("rnotice-" + key),
            deliveryIntentId = DeliveryIntentId.of("rdelivery-" + key),
            effectId = EffectId("reffect-" + key),
        )
    }

    override fun resolve(
        noticeId: NoticeId,
        reason: NoticeResolutionReason,
    ): NoticeResolveResult {
        val result = withMaterialAdmission { synchronized(lock) {
        checkOpen()
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            val current = readRouteByNoticeId(noticeId)
            val result =
                if (current == null) {
                    NoticeResolveResult.NotFound
                } else if (current.notice.relevance == NoticeRelevance.RESOLVED) {
                    NoticeResolveResult.AlreadyResolved(current.notice, current.intent)
                } else {
                    connection.prepare(
                        """
                        UPDATE delivery_notice
                        SET relevance = ?,
                            resolution_reason = ?,
                            revision = revision + 1
                        WHERE notice_id = ? AND relevance = ?
                        """.trimIndent()
                    ).use { statement ->
                        statement.bindText(1, NoticeRelevance.RESOLVED.name)
                        statement.bindText(2, reason.name)
                        statement.bindText(3, noticeId.value)
                        statement.bindText(4, NoticeRelevance.OPEN.name)
                        statement.step()
                    }

                    val nextState =
                        if (current.intent.state == DeliveryEffectState.POSTED_TO_OS) {
                            DeliveryEffectState.PLANNED
                        } else {
                            current.intent.state
                        }
                    connection.prepare(
                        """
                        UPDATE delivery_intent
                        SET desired_action = ?,
                            state = ?
                        WHERE delivery_intent_id = ?
                        """.trimIndent()
                    ).use { statement ->
                        statement.bindText(1, DeliveryDesiredAction.CANCEL.name)
                        statement.bindText(2, nextState.name)
                        statement.bindText(3, current.intent.deliveryIntentId.value)
                        statement.step()
                    }

                    val committed =
                        checkNotNull(readRouteByNoticeId(noticeId)) {
                            "resolved Notice disappeared before commit"
                        }
                    NoticeResolveResult.Resolved(committed.notice, committed.intent)
                }
            connection.execSQL("COMMIT TRANSACTION")
            result
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
        } }
        if (result is NoticeResolveResult.Resolved) {
            publishNoticeCommit()
        }
        return result
    }


    override fun beginNotificationDispatch(
        deliveryIntentId: DeliveryIntentId,
    ): NotificationDispatchClaim = withMaterialAdmission { synchronized(lock) {
        checkOpen()
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            val stored = readRouteByDeliveryIntentId(deliveryIntentId)
            val current = stored?.let(::alignDesiredActionWithNotice)
            val claim =
                if (current == null) {
                    NotificationDispatchClaim.NotFound
                } else {
                    when (current.intent.state) {
                        DeliveryEffectState.PLANNED -> {
                            connection.prepare(
                                """
                                UPDATE delivery_intent
                                SET state = ?
                                WHERE delivery_intent_id = ? AND state = ?
                                """.trimIndent()
                            ).use { statement ->
                                statement.bindText(1, DeliveryEffectState.EFFECT_UNKNOWN.name)
                                statement.bindText(2, deliveryIntentId.value)
                                statement.bindText(3, DeliveryEffectState.PLANNED.name)
                                statement.step()
                            }

                            NotificationDispatchClaim.Ready(
                                NotificationProjectionRequest(
                                    deliveryIdentity = deliveryIntentId.value,
                                    desiredAction = current.intent.desiredAction,
                                    summary = current.notice.summary.value,
                                    sensitive =
                                        current.notice.sensitivity == NoticeSensitivity.SENSITIVE,
                                )
                            )
                        }

                        DeliveryEffectState.EFFECT_UNKNOWN ->
                            NotificationDispatchClaim.NeedsReconcile(current.intent)

                        else ->
                            NotificationDispatchClaim.NotEligible(current.intent)
                    }
                }
            connection.execSQL("COMMIT TRANSACTION")
            claim
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
    } }

    override fun completeNotificationDispatch(
        deliveryIntentId: DeliveryIntentId,
        outcome: NotificationSinkOutcome,
    ): DeliveryIntent = withMaterialAdmission { synchronized(lock) {
        checkOpen()
        val terminalState =
            when (outcome) {
                NotificationSinkOutcome.POSTED_TO_OS -> DeliveryEffectState.POSTED_TO_OS
                NotificationSinkOutcome.CANCELLED -> DeliveryEffectState.CANCELLED
                NotificationSinkOutcome.BLOCKED_PERMISSION -> DeliveryEffectState.BLOCKED_PERMISSION
                NotificationSinkOutcome.FAILED -> DeliveryEffectState.FAILED
            }

        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            val current =
                checkNotNull(readDeliveryIntentInternal(deliveryIntentId)) {
                    "delivery intent missing during completion"
                }
            check(current.state == DeliveryEffectState.EFFECT_UNKNOWN) {
                "delivery completion requires EFFECT_UNKNOWN, got ${current.state}"
            }

            connection.prepare(
                """
                UPDATE delivery_intent
                SET state = ?
                WHERE delivery_intent_id = ? AND state = ?
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, terminalState.name)
                statement.bindText(2, deliveryIntentId.value)
                statement.bindText(3, DeliveryEffectState.EFFECT_UNKNOWN.name)
                statement.step()
            }

            val committed =
                checkNotNull(readDeliveryIntentInternal(deliveryIntentId)) {
                    "delivery intent disappeared during completion"
                }
            check(committed.state == terminalState) {
                "delivery completion state mismatch"
            }
            connection.execSQL("COMMIT TRANSACTION")
            committed
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
    } }

    override fun reconcileNotificationDispatch(
        deliveryIntentId: DeliveryIntentId,
        observation: NotificationProjectionObservation,
    ): NotificationReconcileClaim = withMaterialAdmission { synchronized(lock) {
        checkOpen()
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            val stored = readRouteByDeliveryIntentId(deliveryIntentId)
            val current = stored?.let(::alignDesiredActionWithNotice)?.intent
            val result =
                if (current == null) {
                    NotificationReconcileClaim.NotFound
                } else if (current.state != DeliveryEffectState.EFFECT_UNKNOWN) {
                    NotificationReconcileClaim.NotEligible(current)
                } else {
                    val reconciledState =
                        when {
                            current.desiredAction == DeliveryDesiredAction.UPSERT &&
                                observation == NotificationProjectionObservation.ACTIVE ->
                                DeliveryEffectState.POSTED_TO_OS

                            current.desiredAction == DeliveryDesiredAction.CANCEL &&
                                observation == NotificationProjectionObservation.NOT_ACTIVE ->
                                DeliveryEffectState.CANCELLED

                            current.desiredAction == DeliveryDesiredAction.CANCEL &&
                                observation == NotificationProjectionObservation.ACTIVE ->
                                DeliveryEffectState.PLANNED

                            else -> null
                        }

                    if (reconciledState == null) {
                        NotificationReconcileClaim.StillUnknown(current)
                    } else {
                        connection.prepare(
                            """
                            UPDATE delivery_intent
                            SET state = ?
                            WHERE delivery_intent_id = ? AND state = ?
                            """.trimIndent()
                        ).use { statement ->
                            statement.bindText(1, reconciledState.name)
                            statement.bindText(2, deliveryIntentId.value)
                            statement.bindText(3, DeliveryEffectState.EFFECT_UNKNOWN.name)
                            statement.step()
                        }

                        val committed =
                            checkNotNull(readDeliveryIntentInternal(deliveryIntentId)) {
                                "delivery intent disappeared during reconciliation"
                            }
                        check(committed.state == reconciledState) {
                            "delivery reconciliation state mismatch"
                        }
                        if (reconciledState == DeliveryEffectState.PLANNED) {
                            NotificationReconcileClaim.ReadyForDispatch(committed)
                        } else {
                            NotificationReconcileClaim.Reconciled(committed)
                        }
                    }
                }
            connection.execSQL("COMMIT TRANSACTION")
            result
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
    } }

    override fun readDeliveryIntent(
        deliveryIntentId: DeliveryIntentId,
    ): DeliveryIntent? = synchronized(lock) {
        checkOpen()
        readDeliveryIntentInternal(deliveryIntentId)
    }

    override fun readNotificationProjectionCandidateIds(
        limit: Int,
    ): List<DeliveryIntentId> = synchronized(lock) {
        checkOpen()
        require(limit in 1..MAX_PROJECTION_SCAN_LIMIT) {
            "notification projection scan limit must be in 1..$MAX_PROJECTION_SCAN_LIMIT"
        }
        val ids = mutableListOf<DeliveryIntentId>()
        connection.prepare(
            """
            SELECT delivery_intent_id
            FROM delivery_intent
            WHERE sink = ?
              AND state IN (?, ?)
            ORDER BY delivery_intent_id
            LIMIT $limit
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, DeliverySink.ANDROID_NOTIFICATION.name)
            statement.bindText(2, DeliveryEffectState.PLANNED.name)
            statement.bindText(3, DeliveryEffectState.EFFECT_UNKNOWN.name)
            while (statement.step()) {
                ids += DeliveryIntentId.of(statement.getText(0))
            }
        }
        ids
    }

    internal fun deliveryIntentCount(): Int = synchronized(lock) {
        checkOpen()
        connection.prepare("SELECT COUNT(*) FROM delivery_intent").use { statement ->
            check(statement.step()) { "delivery intent count returned no row" }
            statement.getLong(0).toInt()
        }
    }

    internal fun integrityCheck(): String = synchronized(lock) {
        checkOpen()
        readIntegrityCheck()
    }

    private fun inspectForMaintenance(): AuxiliaryMaintenanceInspection =
        synchronized(lock) {
            checkOpen()
            AuxiliaryMaintenanceInspection(
                schemaVersion = readSchemaVersion(),
                integrityHealthy = readIntegrityCheck().equals("ok", ignoreCase = true),
                foreignKeysHealthy = readForeignKeysHealthy(),
                noticeCount = readAuxiliaryRowCount("delivery_notice"),
                deliveryIntentCount = readAuxiliaryRowCount("delivery_intent"),
            )
        }

    private fun readIntegrityCheck(): String =
        connection.prepare("PRAGMA integrity_check").use { statement ->
            check(statement.step()) { "integrity_check returned no row" }
            statement.getText(0)
        }

    private fun readForeignKeysHealthy(): Boolean =
        connection.prepare("PRAGMA foreign_key_check").use { statement ->
            !statement.step()
        }

    private fun readAuxiliaryRowCount(table: String): Long {
        require(table == "delivery_notice" || table == "delivery_intent") {
            "unsupported Auxiliary maintenance table"
        }
        return connection.prepare("SELECT COUNT(*) FROM $table").use { statement ->
            check(statement.step()) { "Auxiliary row count returned no row for $table" }
            statement.getLong(0)
        }
    }

    internal fun schemaVersion(): Int = synchronized(lock) {
        checkOpen()
        readSchemaVersion()
    }

    override fun close() {
        val didClose = synchronized(lock) {
            if (closed) {
                false
            } else {
                connection.close()
                closed = true
                true
            }
        }
        // Graph rotation revokes this read owner even if the old Home port stays
        // mounted. Do not leave its last count visible after close.
        if (didClose) {
            publishNoticeCommit()
            noticeCommitListeners.clear()
        }
    }


    private fun readBackupSnapshot(): AuxiliaryBackupSnapshot =
        synchronized(lock) {
            checkOpen()
            check(receiptAuthorityFence == null) { "Stage4 backup requires receipt codec" }
            readBackupProjection(readSchemaVersion())
        }

    private fun readBackupProjection(schemaVersion: Int) = AuxiliaryBackupSnapshot(
        schemaVersion, readNotices(), AuxiliaryBookmarkBackupStorage.read(connection),
    )

    private fun requireStagedReceiptBackupAuthority() {
        check(stagedReceiptOwner && receiptAuthorityFence?.invoke() == true && readSchemaVersion() == 4) {
            "Receipt backup requires the pinned Auxiliary4 stage"
        }
    }

    /** Capture the owner and its ledger from the same SQLite read transaction. */
    fun snapshotStagedReceiptBackup(): AuxiliaryReceiptBackupSnapshot = synchronized(lock) {
        checkOpen()
        requireStagedReceiptBackupAuthority()
        connection.execSQL("BEGIN DEFERRED TRANSACTION")
        try {
            val health = inspectForMaintenance()
            check(health.integrityHealthy && health.foreignKeysHealthy)
            val snapshot = AuxiliaryReceiptBackupSnapshot(
                readBackupProjection(SCHEMA_VERSION), readAllReceiptsV4(),
            )
            requireStagedReceiptBackupAuthority()
            snapshot
        } finally {
            connection.execSQL("ROLLBACK TRANSACTION")
        }
    }

    /** Restore every durable Auxiliary4 row together, excluding DeliveryIntent. */
    fun restoreOrVerifyStagedReceiptBackup(snapshot: AuxiliaryReceiptBackupSnapshot) {
        snapshot.validate()
        restoreOrVerifyBackup(snapshot.base, snapshot.receipts)
    }

    private fun restoreOrVerifyBackup(
        snapshot: AuxiliaryBackupSnapshot,
        receipts: List<S4W03NoticeAttentionReceiptV4>? = null,
    ): AuxiliaryBackupSnapshot = withMaterialAdmission {
        synchronized(lock) {
            checkOpen()
            if (receipts == null) {
                check(receiptAuthorityFence == null) { "Stage4 restore requires receipt codec" }
                check(readSchemaVersion() == SCHEMA_VERSION)
            } else {
                requireStagedReceiptBackupAuthority()
            }
            check(snapshot.schemaVersion == SCHEMA_VERSION) {
                "Auxiliary backup schema requires explicit migration"
            }
            val bookmarkSnapshot = checkNotNull(snapshot.bookmarks) {
                "Legacy backup has no verified Guardados section; explicit migration required"
            }
            bookmarkSnapshot.validate()
            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
                val current = readBackupProjection(SCHEMA_VERSION)
                val currentReceipts = receipts?.let { readAllReceiptsV4() }
                if (current == snapshot && currentReceipts == receipts && deliveryIntentCount() == 0) {
                    if (receipts != null) {
                        val health = inspectForMaintenance()
                        check(health.integrityHealthy && health.foreignKeysHealthy)
                        requireStagedReceiptBackupAuthority()
                    }
                    connection.execSQL("ROLLBACK TRANSACTION")
                    return@synchronized current
                }
                val before = inspectForMaintenance()
                check(before.integrityHealthy && before.foreignKeysHealthy) {
                    "Auxiliary restore target must be healthy"
                }
                check(before.deliveryIntentCount == 0L && currentReceipts.isNullOrEmpty()) {
                    "Auxiliary restore cannot overwrite delivery intents or receipts"
                }

                val exactBaseForReceipts = receipts != null && current == snapshot
                if (!exactBaseForReceipts) {
                    check(before.noticeCount == 0L && current.bookmarks == BookmarkBackupSnapshot.EMPTY) {
                        "Auxiliary restore target must be fresh or exactly match backup"
                    }
                    snapshot.notices.forEach { notice ->
                        connection.prepare(
                            """
                            INSERT INTO delivery_notice(
                                notice_id, notice_key, source_ref, occurrence_ref,
                                materiality, sensitivity, summary, destination_ref,
                                relevance, attention, revision, resolution_reason
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """.trimIndent()
                        ).use { statement ->
                            statement.bindText(1, notice.noticeId.value)
                            statement.bindText(2, notice.noticeKey.value)
                            statement.bindText(3, notice.sourceRef.value)
                            statement.bindText(4, notice.occurrenceRef.value)
                            statement.bindText(5, notice.materiality.name)
                            statement.bindText(6, notice.sensitivity.name)
                            statement.bindText(7, notice.summary.value)
                            statement.bindText(8, notice.destinationRef.value)
                            statement.bindText(9, notice.relevance.name)
                            statement.bindText(10, notice.attention.name)
                            statement.bindLong(11, notice.revision)
                            notice.resolutionReason?.let { statement.bindText(12, it.name) }
                                ?: statement.bindNull(12)
                            statement.step()
                        }
                    }
                    AuxiliaryBookmarkBackupStorage.restore(connection, bookmarkSnapshot)
                }
                receipts?.forEach(::insertReceiptV4)

                val after = inspectForMaintenance()
                check(after.integrityHealthy && after.foreignKeysHealthy) {
                    "Auxiliary restore target failed integrity checks"
                }
                check(after.deliveryIntentCount == 0L) {
                    "Auxiliary restore must strip Class D DeliveryIntent rows"
                }
                val restored = readBackupProjection(SCHEMA_VERSION)
                check(restored == snapshot && (receipts == null || readAllReceiptsV4() == receipts)) {
                    "Auxiliary restore verification failed"
                }
                if (receipts != null) requireStagedReceiptBackupAuthority()
                connection.execSQL("COMMIT TRANSACTION")
                restored
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }
        }
    }

    private fun insertNew(
        candidate: NoticeCandidate,
        ids: NoticeMaterializationIds,
        initialState: DeliveryEffectState = DeliveryEffectState.PLANNED,
    ): NoticeRouteResult.Materialized {
        connection.prepare(
            """
            INSERT INTO delivery_notice(
                notice_id,
                notice_key,
                source_ref,
                occurrence_ref,
                materiality,
                sensitivity,
                summary,
                destination_ref,
                relevance,
                attention,
                revision
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, ids.noticeId.value)
            statement.bindText(2, candidate.noticeKey.value)
            statement.bindText(3, candidate.sourceRef.value)
            statement.bindText(4, candidate.occurrenceRef.value)
            statement.bindText(5, candidate.materiality.name)
            statement.bindText(6, candidate.sensitivity.name)
            statement.bindText(7, candidate.summary.value)
            statement.bindText(8, candidate.destinationRef.value)
            statement.bindText(9, NoticeRelevance.OPEN.name)
            statement.bindText(10, NoticeAttention.UNSEEN.name)
            statement.step()
        }

        faultInjector(AuxiliaryDeliveryFaultPoint.AFTER_NOTICE_INSERT)

        connection.prepare(
            """
            INSERT INTO delivery_intent(
                delivery_intent_id,
                effect_id,
                notice_id,
                sink,
                desired_action,
                state
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, ids.deliveryIntentId.value)
            statement.bindText(2, ids.effectId.value)
            statement.bindText(3, ids.noticeId.value)
            statement.bindText(4, DeliverySink.ANDROID_NOTIFICATION.name)
            statement.bindText(5, DeliveryDesiredAction.UPSERT.name)
            statement.bindText(6, initialState.name)
            statement.step()
        }

        val stored = checkNotNull(readRoute(candidate.noticeKey, candidate.occurrenceRef)) {
            "materialized Delivery route disappeared before commit"
        }
        return NoticeRouteResult.Materialized(
            notice = stored.notice,
            deliveryIntent = stored.intent,
            created = true,
        )
    }

    private fun coalesce(
        existing: StoredRoute,
        candidate: NoticeCandidate,
    ): NoticeRouteResult.Materialized {
        val notice = existing.notice
        val canUpdate =
            notice.relevance == NoticeRelevance.OPEN &&
                notice.attention != NoticeAttention.DISMISSED

        val changed =
            canUpdate &&
                (
                    notice.sourceRef != candidate.sourceRef ||
                        notice.materiality != candidate.materiality ||
                        notice.sensitivity != candidate.sensitivity ||
                        notice.summary != candidate.summary ||
                        notice.destinationRef != candidate.destinationRef
                )

        if (changed) {
            connection.prepare(
                """
                UPDATE delivery_notice
                SET source_ref = ?,
                    materiality = ?,
                    sensitivity = ?,
                    summary = ?,
                    destination_ref = ?,
                    revision = revision + 1
                WHERE notice_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.bindText(1, candidate.sourceRef.value)
                statement.bindText(2, candidate.materiality.name)
                statement.bindText(3, candidate.sensitivity.name)
                statement.bindText(4, candidate.summary.value)
                statement.bindText(5, candidate.destinationRef.value)
                statement.bindText(6, notice.noticeId.value)
                statement.step()
            }

            if (existing.intent.state == DeliveryEffectState.POSTED_TO_OS) {
                connection.prepare(
                    """
                    UPDATE delivery_intent
                    SET desired_action = ?,
                        state = ?
                    WHERE delivery_intent_id = ? AND state = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.bindText(1, DeliveryDesiredAction.UPSERT.name)
                    statement.bindText(2, DeliveryEffectState.PLANNED.name)
                    statement.bindText(3, existing.intent.deliveryIntentId.value)
                    statement.bindText(4, DeliveryEffectState.POSTED_TO_OS.name)
                    statement.step()
                }
            }
        }

        val stored = checkNotNull(readRoute(candidate.noticeKey, candidate.occurrenceRef)) {
            "coalesced Delivery route disappeared"
        }
        return NoticeRouteResult.Materialized(
            notice = stored.notice,
            deliveryIntent = stored.intent,
            created = false,
        )
    }

    private fun alignDesiredActionWithNotice(stored: StoredRoute): StoredRoute {
        if (
            stored.notice.relevance == NoticeRelevance.OPEN ||
                stored.intent.desiredAction != DeliveryDesiredAction.UPSERT
        ) {
            return stored
        }

        connection.prepare(
            """
            UPDATE delivery_intent
            SET desired_action = ?
            WHERE delivery_intent_id = ? AND desired_action = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, DeliveryDesiredAction.CANCEL.name)
            statement.bindText(2, stored.intent.deliveryIntentId.value)
            statement.bindText(3, DeliveryDesiredAction.UPSERT.name)
            statement.step()
        }

        return stored.copy(
            intent = stored.intent.copy(desiredAction = DeliveryDesiredAction.CANCEL),
        )
    }

    private fun readRoute(
        noticeKey: NoticeKey,
        occurrenceRef: OccurrenceRef,
    ): StoredRoute? =
        connection.prepare(
            """
            SELECT n.notice_id,
                   n.notice_key,
                   n.source_ref,
                   n.occurrence_ref,
                   n.materiality,
                   n.sensitivity,
                   n.summary,
                   n.destination_ref,
                   n.relevance,
                   n.attention,
                   n.revision,
                   n.resolution_reason,
                   d.delivery_intent_id,
                   d.effect_id,
                   d.sink,
                   d.desired_action,
                   d.state
            FROM delivery_notice n
            JOIN delivery_intent d ON d.notice_id = n.notice_id
            WHERE n.notice_key = ? AND n.occurrence_ref = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, noticeKey.value)
            statement.bindText(2, occurrenceRef.value)
            if (!statement.step()) {
                return@use null
            }
            StoredRoute(
                notice = statement.toNoticeSnapshot(),
                intent =
                    DeliveryIntent(
                        deliveryIntentId = DeliveryIntentId.of(statement.getText(12)),
                        effectId = EffectId(statement.getText(13)),
                        noticeId = NoticeId.of(statement.getText(0)),
                        sink = DeliverySink.valueOf(statement.getText(14)),
                        desiredAction = DeliveryDesiredAction.valueOf(statement.getText(15)),
                        state = DeliveryEffectState.valueOf(statement.getText(16)),
                    ),
            )
        }

    private fun readRouteByDeliveryIntentId(
        deliveryIntentId: DeliveryIntentId,
    ): StoredRoute? =
        connection.prepare(
            """
            SELECT n.notice_id,
                   n.notice_key,
                   n.source_ref,
                   n.occurrence_ref,
                   n.materiality,
                   n.sensitivity,
                   n.summary,
                   n.destination_ref,
                   n.relevance,
                   n.attention,
                   n.revision,
                   n.resolution_reason,
                   d.delivery_intent_id,
                   d.effect_id,
                   d.sink,
                   d.desired_action,
                   d.state
            FROM delivery_notice n
            JOIN delivery_intent d ON d.notice_id = n.notice_id
            WHERE d.delivery_intent_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, deliveryIntentId.value)
            if (!statement.step()) {
                return@use null
            }
            StoredRoute(
                notice = statement.toNoticeSnapshot(),
                intent =
                    DeliveryIntent(
                        deliveryIntentId = DeliveryIntentId.of(statement.getText(12)),
                        effectId = EffectId(statement.getText(13)),
                        noticeId = NoticeId.of(statement.getText(0)),
                        sink = DeliverySink.valueOf(statement.getText(14)),
                        desiredAction = DeliveryDesiredAction.valueOf(statement.getText(15)),
                        state = DeliveryEffectState.valueOf(statement.getText(16)),
                    ),
            )
        }

    private fun readRouteByNoticeId(
        noticeId: NoticeId,
    ): StoredRoute? =
        connection.prepare(
            """
            SELECT n.notice_id,
                   n.notice_key,
                   n.source_ref,
                   n.occurrence_ref,
                   n.materiality,
                   n.sensitivity,
                   n.summary,
                   n.destination_ref,
                   n.relevance,
                   n.attention,
                   n.revision,
                   n.resolution_reason,
                   d.delivery_intent_id,
                   d.effect_id,
                   d.sink,
                   d.desired_action,
                   d.state
            FROM delivery_notice n
            JOIN delivery_intent d ON d.notice_id = n.notice_id
            WHERE n.notice_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, noticeId.value)
            if (!statement.step()) {
                return@use null
            }
            StoredRoute(
                notice = statement.toNoticeSnapshot(),
                intent =
                    DeliveryIntent(
                        deliveryIntentId = DeliveryIntentId.of(statement.getText(12)),
                        effectId = EffectId(statement.getText(13)),
                        noticeId = NoticeId.of(statement.getText(0)),
                        sink = DeliverySink.valueOf(statement.getText(14)),
                        desiredAction = DeliveryDesiredAction.valueOf(statement.getText(15)),
                        state = DeliveryEffectState.valueOf(statement.getText(16)),
                    ),
            )
        }

    private fun readDeliveryIntentInternal(
        deliveryIntentId: DeliveryIntentId,
    ): DeliveryIntent? =
        connection.prepare(
            """
            SELECT effect_id, notice_id, sink, desired_action, state
            FROM delivery_intent
            WHERE delivery_intent_id = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, deliveryIntentId.value)
            if (!statement.step()) {
                return@use null
            }
            DeliveryIntent(
                deliveryIntentId = deliveryIntentId,
                effectId = EffectId(statement.getText(0)),
                noticeId = NoticeId.of(statement.getText(1)),
                sink = DeliverySink.valueOf(statement.getText(2)),
                desiredAction = DeliveryDesiredAction.valueOf(statement.getText(3)),
                state = DeliveryEffectState.valueOf(statement.getText(4)),
            )
        }

    private fun androidx.sqlite.SQLiteStatement.toNoticeSnapshot(): NoticeSnapshot =
        NoticeSnapshot(
            noticeId = NoticeId.of(getText(0)),
            noticeKey = NoticeKey.of(getText(1)),
            sourceRef = SourceRef.of(getText(2)),
            occurrenceRef = OccurrenceRef.of(getText(3)),
            materiality = NoticeMateriality.valueOf(getText(4)),
            sensitivity = NoticeSensitivity.valueOf(getText(5)),
            summary = NoticeSummary.of(getText(6)),
            destinationRef = DestinationRef.of(getText(7)),
            relevance = NoticeRelevance.valueOf(getText(8)),
            attention = NoticeAttention.valueOf(getText(9)),
            revision = getLong(10),
            resolutionReason =
                if (isNull(11)) null else NoticeResolutionReason.valueOf(getText(11)),
        )

    private fun configure(fileName: String) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        connection.execSQL("PRAGMA synchronous = FULL")
        if (fileName != ":memory:") {
            connection.prepare("PRAGMA journal_mode = WAL").use { statement ->
                check(statement.step()) { "journal_mode returned no row" }
                check(statement.getText(0).equals("wal", ignoreCase = true)) {
                    "AuxiliaryDeliveryStore requires WAL journal mode"
                }
            }
        }
    }


    /**
     * Guardados is metadata only. No message text, raw authority token,
     * Workspace copy, or separate Truth database is permitted in these tables.
     * A future owner must validate Workspace generation/ACL before using them.
     */
    private fun createBookmarkSchemaV3() {
        connection.execSQL(
            """
            CREATE TABLE bookmark_scope (
                scope_id INTEGER NOT NULL PRIMARY KEY CHECK(scope_id = 1),
                generation_id TEXT NOT NULL CHECK(length(generation_id) BETWEEN 1 AND 128),
                authority_digest TEXT NOT NULL CHECK(length(authority_digest) = 64),
                revision INTEGER NOT NULL CHECK(revision >= 0)
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TABLE bookmark_folder (
                folder_id TEXT NOT NULL PRIMARY KEY,
                scope_id INTEGER NOT NULL DEFAULT 1,
                name TEXT NOT NULL CHECK(length(trim(name)) BETWEEN 1 AND 80),
                revision INTEGER NOT NULL CHECK(revision > 0),
                FOREIGN KEY(scope_id) REFERENCES bookmark_scope(scope_id)
                    ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TABLE bookmark_item (
                bookmark_id TEXT NOT NULL PRIMARY KEY,
                scope_id INTEGER NOT NULL DEFAULT 1,
                generation_id TEXT NOT NULL,
                conversation_id TEXT NOT NULL,
                message_id TEXT NOT NULL,
                folder_id TEXT,
                favorite INTEGER NOT NULL DEFAULT 0 CHECK(favorite IN (0, 1)),
                lifecycle TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK(lifecycle IN ('ACTIVE', 'REMOVED')),
                revision INTEGER NOT NULL CHECK(revision > 0),
                FOREIGN KEY(scope_id) REFERENCES bookmark_scope(scope_id)
                    ON DELETE RESTRICT,
                FOREIGN KEY(folder_id) REFERENCES bookmark_folder(folder_id)
                    ON DELETE RESTRICT,
                UNIQUE(scope_id, generation_id, conversation_id, message_id)
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TABLE bookmark_receipt (
                idempotency_key TEXT NOT NULL PRIMARY KEY,
                scope_id INTEGER NOT NULL DEFAULT 1,
                request_hash TEXT NOT NULL CHECK(length(request_hash) = 64),
                result_revision INTEGER NOT NULL CHECK(result_revision > 0),
                FOREIGN KEY(scope_id) REFERENCES bookmark_scope(scope_id)
                    ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX idx_bookmark_item_scope_key
            ON bookmark_item(scope_id, bookmark_id)
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE INDEX idx_bookmark_item_folder
            ON bookmark_item(folder_id)
            """.trimIndent()
        )
    }

    /** Staged-only schema4 receipt ledger; default v3 opener NEVER calls this. */
    private fun initializeReceiptSchema4() {
        val fence = checkNotNull(receiptAuthorityFence)
        check(fence()) { "STAGED authority absent before schema4" }
        val version = readSchemaVersion()
        check(version == 4 || (version == 3 && stagedReceiptOwner)) {
            "Auxiliary4 migration requires a quarantined Auxiliary3 stage"
        }
        validateRequiredSchema()
        validateNoticeResolutionRows()
        check(readIntegrityCheck().equals("ok", ignoreCase = true) && readForeignKeysHealthy()) {
            "Receipt stage requires healthy existing owners"
        }
        if (version == 3) schemaTransaction {
            check(fence()) { "STAGED authority changed before receipt migration" }
            connection.execSQL("""
                CREATE TABLE notice_attention_receipt(
                    request_key TEXT NOT NULL PRIMARY KEY
                        CHECK(length(request_key) BETWEEN 1 AND 192),
                    request_hash TEXT NOT NULL CHECK(length(request_hash)=64),
                    notice_id TEXT NOT NULL,
                    expected_revision INTEGER NOT NULL CHECK(expected_revision>0),
                    target_attention TEXT NOT NULL
                        CHECK(target_attention IN ('SEEN','DISMISSED')),
                    result_revision INTEGER NOT NULL
                        CHECK(result_revision=expected_revision+1),
                    FOREIGN KEY(notice_id) REFERENCES delivery_notice(notice_id)
                )
            """.trimIndent())
            connection.execSQL("""
                CREATE INDEX idx_notice_attention_receipt_notice
                ON notice_attention_receipt(notice_id)
            """.trimIndent())
            check(fence()) { "STAGED authority revoked before schema4 version" }
            setSchemaVersion(4)
            check(fence()) { "STAGED authority revoked before schema4 commit" }
        }
        validateSchema4ReceiptTable()
        check(readForeignKeysHealthy() && readIntegrityCheck().equals("ok", ignoreCase = true))
        check(fence()) { "Auxiliary4 authority changed after schema verification" }
    }

    private fun validateSchema4ReceiptTable() {
        check(readSchemaVersion() == 4 && tableExists("notice_attention_receipt")) {
            "Auxiliary4 receipt schema is missing"
        }
        val actual = connection.prepare("PRAGMA table_info(notice_attention_receipt)").use { st ->
            buildList {
                while (st.step()) {
                    check(st.getLong(3) == 1L) { "Auxiliary4 receipt columns must be required" }
                    add(Triple(st.getText(1), st.getText(2), st.getLong(5)))
                }
            }
        }
        check(actual == listOf(
            Triple("request_key", "TEXT", 1L), Triple("request_hash", "TEXT", 0L),
            Triple("notice_id", "TEXT", 0L), Triple("expected_revision", "INTEGER", 0L),
            Triple("target_attention", "TEXT", 0L), Triple("result_revision", "INTEGER", 0L),
        )) { "Auxiliary4 receipt column or primary key mismatch" }
        check(connection.prepare("PRAGMA foreign_key_list(notice_attention_receipt)").use { st ->
            st.step() && st.getText(2) == "delivery_notice" &&
                st.getText(3) == "notice_id" && st.getText(4) == "notice_id" && !st.step()
        }) { "Auxiliary4 receipt Notice foreign key is missing" }
    }

    private fun initializeSchema() {
        val version = readSchemaVersion()
        check(version in 0..SCHEMA_VERSION) {
            "unsupported AuxiliaryDeliveryStore schema version: $version"
        }
        when (version) {
            0 -> schemaTransaction {
                connection.execSQL(
                    """
                    CREATE TABLE delivery_notice (
                        notice_id TEXT NOT NULL PRIMARY KEY,
                        notice_key TEXT NOT NULL,
                        source_ref TEXT NOT NULL,
                        occurrence_ref TEXT NOT NULL,
                        materiality TEXT NOT NULL,
                        sensitivity TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        destination_ref TEXT NOT NULL,
                        relevance TEXT NOT NULL,
                        attention TEXT NOT NULL,
                        revision INTEGER NOT NULL CHECK(revision > 0),
                        resolution_reason TEXT CHECK(
                            resolution_reason IS NULL OR
                            resolution_reason IN ('SOURCE_RESOLVED','EXPIRED','REPLACED')
                        ),
                        UNIQUE(notice_key, occurrence_ref)
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE delivery_intent (
                        delivery_intent_id TEXT NOT NULL PRIMARY KEY,
                        effect_id TEXT NOT NULL UNIQUE,
                        notice_id TEXT NOT NULL UNIQUE,
                        sink TEXT NOT NULL,
                        desired_action TEXT NOT NULL,
                        state TEXT NOT NULL,
                        FOREIGN KEY(notice_id)
                            REFERENCES delivery_notice(notice_id)
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                createBookmarkSchemaV3()
                setSchemaVersion(SCHEMA_VERSION)
            }

            1 -> schemaTransaction {
                connection.execSQL(
                    """
                    ALTER TABLE delivery_notice
                    ADD COLUMN resolution_reason TEXT CHECK(
                        resolution_reason IS NULL OR
                        resolution_reason IN ('SOURCE_RESOLVED','EXPIRED','REPLACED')
                    )
                    """.trimIndent()
                )
                createBookmarkSchemaV3()
                setSchemaVersion(SCHEMA_VERSION)
            }

            2 -> schemaTransaction {
                createBookmarkSchemaV3()
                setSchemaVersion(SCHEMA_VERSION)
            }

            SCHEMA_VERSION -> Unit
        }
        validateRequiredSchema()
        validateNoticeResolutionRows()
    }

    private fun validateRequiredSchema() {
        listOf(
            "delivery_notice", "delivery_intent",
            "bookmark_scope", "bookmark_folder", "bookmark_item", "bookmark_receipt",
        ).forEach { table ->
            check(tableExists(table)) {
                "AuxiliaryDeliveryStore required table missing: $table"
            }
        }
        check(columnExists("bookmark_item", "message_id") &&
            columnExists("bookmark_item", "generation_id") &&
            columnExists("bookmark_item", "folder_id") &&
            columnExists("bookmark_receipt", "idempotency_key") &&
            columnExists("bookmark_scope", "authority_digest")) {
            "AuxiliaryDeliveryStore required bookmark v3 columns missing"
        }
        check(columnExists("delivery_notice", "resolution_reason")) {
            "AuxiliaryDeliveryStore required column missing: delivery_notice.resolution_reason"
        }
    }

    private fun validateNoticeResolutionRows() {
        val invalid =
            connection.prepare(
                """
                SELECT COUNT(*)
                FROM delivery_notice
                WHERE
                    (relevance = 'OPEN' AND resolution_reason IS NOT NULL)
                    OR
                    (relevance = 'RESOLVED' AND resolution_reason IS NULL)
                    OR
                    (resolution_reason IS NOT NULL AND
                        resolution_reason NOT IN ('SOURCE_RESOLVED','EXPIRED','REPLACED'))
                """.trimIndent()
            ).use { statement ->
                check(statement.step()) { "resolution invariant query returned no row" }
                statement.getLong(0)
            }
        check(invalid == 0L) {
            "AuxiliaryDeliveryStore has Notice rows without valid resolution provenance"
        }
    }

    private fun columnExists(
        table: String,
        column: String,
    ): Boolean =
        connection.prepare("PRAGMA table_info($table)").use { statement ->
            var found = false
            while (statement.step()) {
                if (statement.getText(1) == column) {
                    found = true
                }
            }
            found
        }

    private fun tableExists(table: String): Boolean =
        connection.prepare(
            """
            SELECT 1 FROM sqlite_master
            WHERE type = 'table' AND name = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindText(1, table)
            statement.step()
        }

    private fun readSchemaVersion(): Int =
        connection.prepare("PRAGMA user_version").use { statement ->
            check(statement.step()) { "user_version returned no row" }
            statement.getLong(0).toInt()
        }

    private fun setSchemaVersion(version: Int) {
        connection.execSQL("PRAGMA user_version = $version")
    }

    private inline fun schemaTransaction(block: () -> Unit) {
        connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
        try {
            block()
            connection.execSQL("COMMIT TRANSACTION")
        } catch (failure: Throwable) {
            runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
            throw failure
        }
    }

    private fun checkOpen() {
        check(!closed) { "AuxiliaryDeliveryStore is closed" }
    }

    private data class StoredRoute(
        val notice: NoticeSnapshot,
        val intent: DeliveryIntent,
    )

    companion object {
        /**
         * Only from generation owner AFTER sealed STAGED preflight.
         * Not registered for startup, active generation or shipping UI.
         */
        @JvmStatic
        fun openQuarantinedStagedReceiptV4ForTrustedGeneration(
            fileName: String,
            stageFence: () -> Boolean,
        ): AuxiliaryDeliveryStore = AuxiliaryDeliveryStore(
            fileName, BundledSQLiteDriver(), {}, null, null, stageFence, stagedReceiptOwner = true,
        )

        /** Open exactly schema4 after the generation owner has checked the active pointer and graph. */
        @JvmStatic
        fun openActiveReceiptV4ForTrustedGeneration(
            fileName: String,
            materialWriteGate: MaterialWriteGate,
            activeFence: () -> Boolean,
        ): AuxiliaryDeliveryStore {
            check(activeFence()) { "Auxiliary4 active generation authority absent" }
            return AuxiliaryDeliveryStore(
                fileName, existingReceiptDriver, {}, materialWriteGate, null, activeFence,
            )
        }

        private val existingReceiptDriver = object : SQLiteDriver {
            override fun open(fileName: String): SQLiteConnection = BundledSQLiteDriver().open(
                fileName, SQLITE_OPEN_READWRITE or SQLITE_OPEN_NOFOLLOW,
            )
        }

        @JvmStatic
        fun open(fileName: String): AuxiliaryDeliveryStore = AuxiliaryDeliveryStore(fileName)

        private const val SCHEMA_VERSION = 3
        private const val MAX_PROJECTION_SCAN_LIMIT = 1024
    }
}
