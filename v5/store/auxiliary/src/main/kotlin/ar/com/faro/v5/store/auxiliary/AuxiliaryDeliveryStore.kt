package ar.com.faro.v5.store.auxiliary

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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

internal enum class AuxiliaryDeliveryFaultPoint {
    AFTER_NOTICE_INSERT,
}

class AuxiliaryDeliveryStore private constructor(
    fileName: String,
    driver: SQLiteDriver,
    private val faultInjector: (AuxiliaryDeliveryFaultPoint) -> Unit,
    private val materialWriteGate: MaterialWriteGate?,
) : DeliveryPersistencePort,
    DeliveryDispatchPersistencePort,
    DeliveryProjectionPersistencePort,
    AutoCloseable {
    constructor(
        fileName: String,
        driver: SQLiteDriver = BundledSQLiteDriver(),
        materialWriteGate: MaterialWriteGate? = null,
    ) : this(
        fileName = fileName,
        driver = driver,
        faultInjector = {},
        materialWriteGate = materialWriteGate,
    )

    internal constructor(
        fileName: String,
        faultInjector: (AuxiliaryDeliveryFaultPoint) -> Unit,
    ) : this(
        fileName = fileName,
        driver = BundledSQLiteDriver(),
        faultInjector = faultInjector,
        materialWriteGate = null,
    )
    private val lock = Any()
    /** Optional admission for staged V2; callers must share the host's SINGLE gate. */
    private fun <T> withMaterialAdmission(block: () -> T): T =
        materialWriteGate?.withMaterialWrite(block) ?: block()
    private val connection: SQLiteConnection = driver.open(fileName)
    private var closed = false

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


    private fun readPortabilitySnapshot(): AuxiliaryPortabilitySnapshot =
        synchronized(lock) {
            checkOpen()
            AuxiliaryPortabilityProjection.fromStored(
                schemaVersion = readSchemaVersion(),
                notices = readNotices(),
            )
        }

    init {
        withMaterialAdmission {
            synchronized(lock) {
                configure(fileName)
                initializeSchema()
            }
        }
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
    ): NoticeRouteResult = withMaterialAdmission { synchronized(lock) {
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


    override fun resolve(
        noticeId: NoticeId,
        reason: NoticeResolutionReason,
    ): NoticeResolveResult = withMaterialAdmission { synchronized(lock) {
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

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        connection.close()
        closed = true
    }


    private fun readBackupSnapshot(): AuxiliaryBackupSnapshot =
        synchronized(lock) {
            checkOpen()
            AuxiliaryBackupSnapshot(
                schemaVersion = readSchemaVersion(),
                notices = readNotices(),
            )
        }

    private fun restoreOrVerifyBackup(
        snapshot: AuxiliaryBackupSnapshot,
    ): AuxiliaryBackupSnapshot =
        withMaterialAdmission { synchronized(lock) {
            checkOpen()
            check(snapshot.schemaVersion == SCHEMA_VERSION) {
                "Auxiliary backup schema requires explicit migration"
            }
            val current = readBackupSnapshot()
            if (current == snapshot && deliveryIntentCount() == 0) {
                return@synchronized current
            }
            val before = inspectForMaintenance()
            check(before.integrityHealthy && before.foreignKeysHealthy) {
                "Auxiliary restore target must be healthy"
            }
            check(before.noticeCount == 0L && before.deliveryIntentCount == 0L) {
                "Auxiliary restore target must be fresh or exactly match backup"
            }

            connection.execSQL("BEGIN IMMEDIATE TRANSACTION")
            try {
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
                connection.execSQL("COMMIT TRANSACTION")
            } catch (failure: Throwable) {
                runCatching { connection.execSQL("ROLLBACK TRANSACTION") }
                throw failure
            }

            val after = inspectForMaintenance()
            check(after.integrityHealthy && after.foreignKeysHealthy) {
                "Auxiliary restore target failed integrity checks"
            }
            check(after.deliveryIntentCount == 0L) {
                "Auxiliary restore must strip Class D DeliveryIntent rows"
            }
            val restored = readBackupSnapshot()
            check(restored == snapshot) {
                "Auxiliary restore verification failed"
            }
            restored
        } }

    private fun insertNew(
        candidate: NoticeCandidate,
        ids: NoticeMaterializationIds,
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
            statement.bindText(6, DeliveryEffectState.PLANNED.name)
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
                setSchemaVersion(SCHEMA_VERSION)
            }

            SCHEMA_VERSION -> Unit
        }
        validateRequiredSchema()
        validateNoticeResolutionRows()
    }

    private fun validateRequiredSchema() {
        listOf("delivery_notice", "delivery_intent").forEach { table ->
            check(tableExists(table)) {
                "AuxiliaryDeliveryStore required table missing: $table"
            }
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
        @JvmStatic
        fun open(fileName: String): AuxiliaryDeliveryStore = AuxiliaryDeliveryStore(fileName)

        private const val SCHEMA_VERSION = 2
        private const val MAX_PROJECTION_SCAN_LIMIT = 1024
    }
}
