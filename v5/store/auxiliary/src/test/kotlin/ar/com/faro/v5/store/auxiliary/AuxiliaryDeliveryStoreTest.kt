package ar.com.faro.v5.store.auxiliary

import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ar.com.faro.v5.delivery.api.DeliveryDesiredAction
import ar.com.faro.v5.delivery.api.DeliveryEffectState
import ar.com.faro.v5.delivery.api.DeliveryIntentId
import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NotificationDeliveryDispatcher
import ar.com.faro.v5.delivery.api.NotificationDeliveryReconciler
import ar.com.faro.v5.delivery.api.NotificationDispatchExecution
import ar.com.faro.v5.delivery.api.NotificationReconcileExecution
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
import ar.com.faro.v5.delivery.api.NoticeRouter
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.delivery.spi.NotificationDeliveryObserver
import ar.com.faro.v5.delivery.spi.NotificationDeliverySink
import ar.com.faro.v5.delivery.spi.NotificationProjectionObservation
import ar.com.faro.v5.delivery.spi.NotificationSinkOutcome
import ar.com.faro.v5.kernel.contracts.EffectId
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuxiliaryDeliveryStoreTest {
    @Test
    fun materializedNoticeAndIntentSurviveRealFileReopen() {
        withDatabase { file ->
            AuxiliaryDeliveryStore(file).use { store ->
                val result = materialize(store, "first", candidate())
                assertTrue(result.created)
                assertEquals(1, store.readNotices().size)
                assertEquals(1, store.deliveryIntentCount())
                assertEquals("ok", store.integrityCheck())
                assertEquals(2, store.schemaVersion())
            }

            AuxiliaryDeliveryStore(file).use { reopened ->
                val notices = reopened.readNotices()
                assertEquals(1, notices.size)
                assertEquals("notice-first", notices.single().noticeId.value)
                assertEquals(1, reopened.deliveryIntentCount())
                assertEquals("ok", reopened.integrityCheck())
            }
        }
    }

    @Test
    fun notificationProjectionCandidateScanTracksOnlyRecoverableStates() {
        withDatabase { file ->
            AuxiliaryDeliveryStore(file).use { store ->
                val materialized = materialize(store, "projection-scan", candidate())

                assertEquals(
                    listOf(materialized.deliveryIntent.deliveryIntentId),
                    store.readNotificationProjectionCandidateIds(10),
                )

                val postedDispatcher =
                    NotificationDeliveryDispatcher(
                        persistence = store,
                        sink = NotificationDeliverySink {
                            NotificationSinkOutcome.POSTED_TO_OS
                        },
                    )
                assertInstanceOf(
                    NotificationDispatchExecution.Completed::class.java,
                    postedDispatcher.dispatch(materialized.deliveryIntent.deliveryIntentId),
                )
                assertTrue(store.readNotificationProjectionCandidateIds(10).isEmpty())

                val rearmed =
                    materialize(
                        store,
                        "ignored-new-ids",
                        candidate(summary = "Cambio que requiere reproyección."),
                    )
                assertEquals(
                    DeliveryEffectState.PLANNED,
                    rearmed.deliveryIntent.state,
                )

                val uncertainDispatcher =
                    NotificationDeliveryDispatcher(
                        persistence = store,
                        sink = NotificationDeliverySink {
                            error("simulated uncertain projection")
                        },
                    )
                assertInstanceOf(
                    NotificationDispatchExecution.EffectUnknown::class.java,
                    uncertainDispatcher.dispatch(rearmed.deliveryIntent.deliveryIntentId),
                )
                assertEquals(
                    listOf(rearmed.deliveryIntent.deliveryIntentId),
                    store.readNotificationProjectionCandidateIds(1),
                )
                assertThrows(IllegalArgumentException::class.java) {
                    store.readNotificationProjectionCandidateIds(0)
                }
            }
        }
    }

    @Test
    fun legacyV1OpenNoticeMigratesToV2WithoutInventingResolutionReason() {
        withDatabase { file ->
            createLegacyV1Database(file)
            AuxiliaryDeliveryStore(file).use { store ->
                assertEquals(2, store.schemaVersion())
                val notice = store.readNotices().single()
                assertEquals(NoticeRelevance.OPEN, notice.relevance)
                assertEquals(null, notice.resolutionReason)
                assertEquals("ok", store.integrityCheck())
            }
        }
    }

    @Test
    fun resolvePersistsTypedReasonAndRearmsPostedProjectionForCancel() {
        withDatabase { file ->
            val noticeId =
                AuxiliaryDeliveryStore(file).use { store ->
                    val materialized = materialize(store, "resolve-posted", candidate())
                    val dispatcher =
                        NotificationDeliveryDispatcher(
                            persistence = store,
                            sink = NotificationDeliverySink {
                                NotificationSinkOutcome.POSTED_TO_OS
                            },
                        )
                    assertInstanceOf(
                        NotificationDispatchExecution.Completed::class.java,
                        dispatcher.dispatch(materialized.deliveryIntent.deliveryIntentId),
                    )

                    val resolved =
                        assertInstanceOf(
                            NoticeResolveResult.Resolved::class.java,
                            store.resolve(
                                materialized.notice.noticeId,
                                NoticeResolutionReason.SOURCE_RESOLVED,
                            ),
                        )
                    assertEquals(NoticeRelevance.RESOLVED, resolved.notice.relevance)
                    assertEquals(
                        NoticeResolutionReason.SOURCE_RESOLVED,
                        resolved.notice.resolutionReason,
                    )
                    assertEquals(NoticeAttention.UNSEEN, resolved.notice.attention)
                    assertEquals(2L, resolved.notice.revision)
                    assertEquals(DeliveryDesiredAction.CANCEL, resolved.deliveryIntent.desiredAction)
                    assertEquals(DeliveryEffectState.PLANNED, resolved.deliveryIntent.state)
                    materialized.notice.noticeId
                }

            AuxiliaryDeliveryStore(file).use { reopened ->
                val notice = reopened.readNotices().single { it.noticeId == noticeId }
                assertEquals(NoticeResolutionReason.SOURCE_RESOLVED, notice.resolutionReason)
                val replay =
                    assertInstanceOf(
                        NoticeResolveResult.AlreadyResolved::class.java,
                        reopened.resolve(noticeId, NoticeResolutionReason.REPLACED),
                    )
                assertEquals(NoticeResolutionReason.SOURCE_RESOLVED, replay.notice.resolutionReason)
                assertEquals(2L, replay.notice.revision)

                var action: DeliveryDesiredAction? = null
                val dispatcher =
                    NotificationDeliveryDispatcher(
                        persistence = reopened,
                        sink = NotificationDeliverySink { request ->
                            action = request.desiredAction
                            NotificationSinkOutcome.CANCELLED
                        },
                    )
                val completed =
                    assertInstanceOf(
                        NotificationDispatchExecution.Completed::class.java,
                        dispatcher.dispatch(replay.deliveryIntent.deliveryIntentId),
                    )
                assertEquals(DeliveryDesiredAction.CANCEL, action)
                assertEquals(DeliveryEffectState.CANCELLED, completed.deliveryIntent.state)
            }
        }
    }

    @Test
    fun duplicateOccurrenceKeepsStableIdentitiesAndDoesNotDuplicateRows() {
        withDatabase { file ->
            AuxiliaryDeliveryStore(file).use { store ->
                val first = materialize(store, "first", candidate())
                val second = materialize(store, "second", candidate())

                assertTrue(first.created)
                assertEquals(false, second.created)
                assertEquals(first.notice.noticeId, second.notice.noticeId)
                assertEquals(
                    first.deliveryIntent.deliveryIntentId,
                    second.deliveryIntent.deliveryIntentId,
                )
                assertEquals(first.deliveryIntent.effectId, second.deliveryIntent.effectId)
                assertEquals(1, store.readNotices().size)
                assertEquals(1, store.deliveryIntentCount())
            }
        }
    }

    @Test
    fun sameOpenOccurrenceCoalescesContentAndIncrementsRevisionWithoutNewIdentity() {
        withDatabase { file ->
            AuxiliaryDeliveryStore(file).use { store ->
                val first = materialize(store, "first", candidate())
                val updatedCandidate =
                    candidate(
                        summary = "El precio observado bajó otra vez.",
                        materiality = NoticeMateriality.NEEDS_ATTENTION,
                    )
                val second = materialize(store, "second", updatedCandidate)

                assertEquals(first.notice.noticeId, second.notice.noticeId)
                assertEquals(first.deliveryIntent.effectId, second.deliveryIntent.effectId)
                assertEquals(2L, second.notice.revision)
                assertEquals(NoticeMateriality.NEEDS_ATTENTION, second.notice.materiality)
                assertEquals("El precio observado bajó otra vez.", second.notice.summary.value)
                assertEquals(1, store.deliveryIntentCount())
            }
        }
    }

    @Test
    fun changedOpenNoticeRearmsPostedProjectionWithoutChangingIdentity() {
        withDatabase { file ->
            AuxiliaryDeliveryStore(file).use { store ->
                val first = materialize(store, "reproject", candidate())
                val dispatcher =
                    NotificationDeliveryDispatcher(
                        persistence = store,
                        sink = NotificationDeliverySink {
                            NotificationSinkOutcome.POSTED_TO_OS
                        },
                    )
                assertInstanceOf(
                    NotificationDispatchExecution.Completed::class.java,
                    dispatcher.dispatch(first.deliveryIntent.deliveryIntentId),
                )

                val changed =
                    materialize(
                        store,
                        "ignored-new-ids",
                        candidate(
                            summary = "El precio observado cambió de nuevo.",
                            materiality = NoticeMateriality.NEEDS_ATTENTION,
                        ),
                    )

                assertEquals(first.notice.noticeId, changed.notice.noticeId)
                assertEquals(first.deliveryIntent.deliveryIntentId, changed.deliveryIntent.deliveryIntentId)
                assertEquals(first.deliveryIntent.effectId, changed.deliveryIntent.effectId)
                assertEquals(2L, changed.notice.revision)
                assertEquals(DeliveryEffectState.PLANNED, changed.deliveryIntent.state)

                var projectedSummary: String? = null
                val redispatch =
                    NotificationDeliveryDispatcher(
                        persistence = store,
                        sink = NotificationDeliverySink { request ->
                            projectedSummary = request.summary
                            NotificationSinkOutcome.POSTED_TO_OS
                        },
                    )
                val result =
                    assertInstanceOf(
                        NotificationDispatchExecution.Completed::class.java,
                        redispatch.dispatch(changed.deliveryIntent.deliveryIntentId),
                    )
                assertEquals("El precio observado cambió de nuevo.", projectedSummary)
                assertEquals(DeliveryEffectState.POSTED_TO_OS, result.deliveryIntent.state)
            }
        }
    }

    @Test
    fun changedOpenNoticeNeverRearmsUnknownProjection() {
        withDatabase { file ->
            AuxiliaryDeliveryStore(file).use { store ->
                val first = materialize(store, "unknown-reproject", candidate())
                val dispatcher =
                    NotificationDeliveryDispatcher(
                        persistence = store,
                        sink = NotificationDeliverySink {
                            error("simulated uncertain external effect")
                        },
                    )
                assertInstanceOf(
                    NotificationDispatchExecution.EffectUnknown::class.java,
                    dispatcher.dispatch(first.deliveryIntent.deliveryIntentId),
                )

                val changed =
                    materialize(
                        store,
                        "ignored-unknown-ids",
                        candidate(summary = "Contenido nuevo mientras el efecto es incierto."),
                    )

                assertEquals(first.deliveryIntent.deliveryIntentId, changed.deliveryIntent.deliveryIntentId)
                assertEquals(first.deliveryIntent.effectId, changed.deliveryIntent.effectId)
                assertEquals(2L, changed.notice.revision)
                assertEquals(DeliveryEffectState.EFFECT_UNKNOWN, changed.deliveryIntent.state)
            }
        }
    }

    @Test
    fun faultBetweenNoticeAndIntentRollsBackBothArtifacts() {
        withDatabase { file ->
            assertThrows(IllegalStateException::class.java) {
                AuxiliaryDeliveryStore(
                    fileName = file,
                    faultInjector = { point ->
                        if (point == AuxiliaryDeliveryFaultPoint.AFTER_NOTICE_INSERT) {
                            error("injected delivery materialization fault")
                        }
                    },
                ).use { store ->
                    materialize(store, "fault", candidate())
                }
            }

            AuxiliaryDeliveryStore(file).use { reopened ->
                assertTrue(reopened.readNotices().isEmpty())
                assertEquals(0, reopened.deliveryIntentCount())
                assertEquals("ok", reopened.integrityCheck())
            }
        }
    }

    @Test
    fun successfulDispatchPersistsTerminalOutcomeAcrossReopen() {
        withDatabase { file ->
            val deliveryId =
                AuxiliaryDeliveryStore(file).use { store ->
                    val materialized = materialize(store, "dispatch", candidate())
                    val dispatcher =
                        NotificationDeliveryDispatcher(
                            persistence = store,
                            sink = NotificationDeliverySink {
                                NotificationSinkOutcome.POSTED_TO_OS
                            },
                        )

                    val result =
                        assertInstanceOf(
                            NotificationDispatchExecution.Completed::class.java,
                            dispatcher.dispatch(materialized.deliveryIntent.deliveryIntentId),
                        )
                    assertEquals(
                        DeliveryEffectState.POSTED_TO_OS,
                        result.deliveryIntent.state,
                    )
                    assertEquals(
                        DeliveryEffectState.POSTED_TO_OS,
                        store.readDeliveryIntent(materialized.deliveryIntent.deliveryIntentId)?.state,
                    )
                    materialized.deliveryIntent.deliveryIntentId
                }

            AuxiliaryDeliveryStore(file).use { reopened ->
                assertEquals(
                    DeliveryEffectState.POSTED_TO_OS,
                    reopened.readDeliveryIntent(deliveryId)?.state,
                )
            }
        }
    }

    @Test
    fun crashOrSinkFailureLeavesDurableUnknownAndRetryDoesNotCallSinkAgain() {
        withDatabase { file ->
            var sinkCalls = 0
            val deliveryId =
                AuxiliaryDeliveryStore(file).use { store ->
                    val materialized = materialize(store, "unknown", candidate())
                    val dispatcher =
                        NotificationDeliveryDispatcher(
                            persistence = store,
                            sink = NotificationDeliverySink {
                                sinkCalls += 1
                                error("simulated uncertain external effect")
                            },
                        )

                    assertInstanceOf(
                        NotificationDispatchExecution.EffectUnknown::class.java,
                        dispatcher.dispatch(materialized.deliveryIntent.deliveryIntentId),
                    )
                    assertEquals(1, sinkCalls)
                    assertEquals(
                        DeliveryEffectState.EFFECT_UNKNOWN,
                        store.readDeliveryIntent(materialized.deliveryIntent.deliveryIntentId)?.state,
                    )
                    materialized.deliveryIntent.deliveryIntentId
                }

            AuxiliaryDeliveryStore(file).use { reopened ->
                val retryDispatcher =
                    NotificationDeliveryDispatcher(
                        persistence = reopened,
                        sink = NotificationDeliverySink {
                            sinkCalls += 1
                            NotificationSinkOutcome.POSTED_TO_OS
                        },
                    )

                val retry =
                    assertInstanceOf(
                        NotificationDispatchExecution.NeedsReconcile::class.java,
                        retryDispatcher.dispatch(deliveryId),
                    )
                assertEquals(1, sinkCalls)
                assertEquals(DeliveryEffectState.EFFECT_UNKNOWN, retry.deliveryIntent.state)
                assertEquals(
                    DeliveryEffectState.EFFECT_UNKNOWN,
                    reopened.readDeliveryIntent(deliveryId)?.state,
                )
            }
        }
    }

    @Test
    fun activeProjectionReconcilesUnknownUpsertAndPersistsAcrossReopen() {
        withDatabase { file ->
            val deliveryId =
                AuxiliaryDeliveryStore(file).use { store ->
                    val materialized = materialize(store, "reconcile-active", candidate())
                    val dispatcher =
                        NotificationDeliveryDispatcher(
                            persistence = store,
                            sink = NotificationDeliverySink {
                                error("simulated crash after possible OS effect")
                            },
                        )
                    assertInstanceOf(
                        NotificationDispatchExecution.EffectUnknown::class.java,
                        dispatcher.dispatch(materialized.deliveryIntent.deliveryIntentId),
                    )

                    val reconciler =
                        NotificationDeliveryReconciler(
                            persistence = store,
                            observer = NotificationDeliveryObserver {
                                NotificationProjectionObservation.ACTIVE
                            },
                        )
                    val result =
                        assertInstanceOf(
                            NotificationReconcileExecution.Completed::class.java,
                            reconciler.reconcile(materialized.deliveryIntent.deliveryIntentId),
                        )
                    assertEquals(DeliveryEffectState.POSTED_TO_OS, result.deliveryIntent.state)
                    materialized.deliveryIntent.deliveryIntentId
                }

            AuxiliaryDeliveryStore(file).use { reopened ->
                assertEquals(
                    DeliveryEffectState.POSTED_TO_OS,
                    reopened.readDeliveryIntent(deliveryId)?.state,
                )
            }
        }
    }

    @Test
    fun missingProjectionDoesNotInventOutcomeForUnknownUpsert() {
        withDatabase { file ->
            AuxiliaryDeliveryStore(file).use { store ->
                val materialized = materialize(store, "reconcile-missing", candidate())
                val dispatcher =
                    NotificationDeliveryDispatcher(
                        persistence = store,
                        sink = NotificationDeliverySink {
                            error("simulated uncertain external effect")
                        },
                    )
                assertInstanceOf(
                    NotificationDispatchExecution.EffectUnknown::class.java,
                    dispatcher.dispatch(materialized.deliveryIntent.deliveryIntentId),
                )

                val reconciler =
                    NotificationDeliveryReconciler(
                        persistence = store,
                        observer = NotificationDeliveryObserver {
                            NotificationProjectionObservation.NOT_ACTIVE
                        },
                    )
                val result =
                    assertInstanceOf(
                        NotificationReconcileExecution.StillUnknown::class.java,
                        reconciler.reconcile(materialized.deliveryIntent.deliveryIntentId),
                    )
                assertEquals(DeliveryEffectState.EFFECT_UNKNOWN, result.deliveryIntent.state)
                assertEquals(
                    DeliveryEffectState.EFFECT_UNKNOWN,
                    store.readDeliveryIntent(materialized.deliveryIntent.deliveryIntentId)?.state,
                )
            }
        }
    }

    @Test
    fun resolvedNoticeCannotEmitStaleUpsertAndConvergesToCancel() {
        withDatabase { file ->
            val materialized =
                AuxiliaryDeliveryStore(file).use { store ->
                    materialize(store, "resolved-before-dispatch", candidate())
                }
            AuxiliaryDeliveryStore(file).use { resolverStore ->
                assertInstanceOf(
                    NoticeResolveResult.Resolved::class.java,
                    resolverStore.resolve(
                        materialized.notice.noticeId,
                        NoticeResolutionReason.SOURCE_RESOLVED,
                    ),
                )
            }

            var observedAction: DeliveryDesiredAction? = null
            AuxiliaryDeliveryStore(file).use { reopened ->
                val dispatcher =
                    NotificationDeliveryDispatcher(
                        persistence = reopened,
                        sink = NotificationDeliverySink { request ->
                            observedAction = request.desiredAction
                            NotificationSinkOutcome.CANCELLED
                        },
                    )

                val result =
                    assertInstanceOf(
                        NotificationDispatchExecution.Completed::class.java,
                        dispatcher.dispatch(materialized.deliveryIntent.deliveryIntentId),
                    )
                assertEquals(DeliveryDesiredAction.CANCEL, observedAction)
                assertEquals(DeliveryDesiredAction.CANCEL, result.deliveryIntent.desiredAction)
                assertEquals(DeliveryEffectState.CANCELLED, result.deliveryIntent.state)
            }
        }
    }

    @Test
    fun resolvedUnknownUpsertObservedActiveRearmsAndDispatchesCancel() {
        withDatabase { file ->
            val materialized =
                AuxiliaryDeliveryStore(file).use { store ->
                    val routed = materialize(store, "resolved-unknown", candidate())
                    val dispatcher =
                        NotificationDeliveryDispatcher(
                            persistence = store,
                            sink = NotificationDeliverySink {
                                error("simulated uncertain upsert")
                            },
                        )
                    assertInstanceOf(
                        NotificationDispatchExecution.EffectUnknown::class.java,
                        dispatcher.dispatch(routed.deliveryIntent.deliveryIntentId),
                    )
                    routed
                }

            AuxiliaryDeliveryStore(file).use { resolverStore ->
                val resolved =
                    assertInstanceOf(
                        NoticeResolveResult.Resolved::class.java,
                        resolverStore.resolve(
                            materialized.notice.noticeId,
                            NoticeResolutionReason.SOURCE_RESOLVED,
                        ),
                    )
                assertEquals(DeliveryDesiredAction.CANCEL, resolved.deliveryIntent.desiredAction)
                assertEquals(DeliveryEffectState.EFFECT_UNKNOWN, resolved.deliveryIntent.state)
            }

            AuxiliaryDeliveryStore(file).use { reopened ->
                val reconciler =
                    NotificationDeliveryReconciler(
                        persistence = reopened,
                        observer = NotificationDeliveryObserver {
                            NotificationProjectionObservation.ACTIVE
                        },
                    )
                val rearmed =
                    assertInstanceOf(
                        NotificationReconcileExecution.ReadyForDispatch::class.java,
                        reconciler.reconcile(materialized.deliveryIntent.deliveryIntentId),
                    )
                assertEquals(DeliveryDesiredAction.CANCEL, rearmed.deliveryIntent.desiredAction)
                assertEquals(DeliveryEffectState.PLANNED, rearmed.deliveryIntent.state)

                var dispatchedAction: DeliveryDesiredAction? = null
                val cancelDispatcher =
                    NotificationDeliveryDispatcher(
                        persistence = reopened,
                        sink = NotificationDeliverySink { request ->
                            dispatchedAction = request.desiredAction
                            NotificationSinkOutcome.CANCELLED
                        },
                    )
                val completed =
                    assertInstanceOf(
                        NotificationDispatchExecution.Completed::class.java,
                        cancelDispatcher.dispatch(materialized.deliveryIntent.deliveryIntentId),
                    )
                assertEquals(DeliveryDesiredAction.CANCEL, dispatchedAction)
                assertEquals(DeliveryEffectState.CANCELLED, completed.deliveryIntent.state)
            }
        }
    }

    @Test
    fun blockedPermissionIsDurableAndNeverReportedAsPosted() {
        withDatabase { file ->
            AuxiliaryDeliveryStore(file).use { store ->
                val materialized = materialize(store, "blocked", candidate())
                val dispatcher =
                    NotificationDeliveryDispatcher(
                        persistence = store,
                        sink = NotificationDeliverySink {
                            NotificationSinkOutcome.BLOCKED_PERMISSION
                        },
                    )

                val result =
                    assertInstanceOf(
                        NotificationDispatchExecution.Completed::class.java,
                        dispatcher.dispatch(materialized.deliveryIntent.deliveryIntentId),
                    )
                assertEquals(
                    DeliveryEffectState.BLOCKED_PERMISSION,
                    result.deliveryIntent.state,
                )
                assertEquals(
                    DeliveryEffectState.BLOCKED_PERMISSION,
                    store.readDeliveryIntent(materialized.deliveryIntent.deliveryIntentId)?.state,
                )
            }
        }
    }

    private fun materialize(
        store: AuxiliaryDeliveryStore,
        suffix: String,
        candidate: NoticeCandidate,
    ): NoticeRouteResult.Materialized =
        assertInstanceOf(
            NoticeRouteResult.Materialized::class.java,
            NoticeRouter(store) { ids(suffix) }.route(candidate),
        )

    private fun candidate(
        summary: String = "Bajó un precio observado.",
        materiality: NoticeMateriality = NoticeMateriality.NOTICE,
    ) = NoticeCandidate(
        noticeKey = NoticeKey.of("radar.price-change"),
        sourceRef = SourceRef.of("radar:result:42"),
        occurrenceRef = OccurrenceRef.of("occurrence:2026-10-23T12:00"),
        materiality = materiality,
        sensitivity = NoticeSensitivity.NORMAL,
        summary = NoticeSummary.of(summary),
        destinationRef = DestinationRef.of("commercial:evidence:42"),
    )

    private fun ids(suffix: String) = NoticeMaterializationIds(
        noticeId = NoticeId.of("notice-$suffix"),
        deliveryIntentId = DeliveryIntentId.of("delivery-$suffix"),
        effectId = EffectId("effect-$suffix"),
    )

    private fun createLegacyV1Database(file: String) {
        val connection = BundledSQLiteDriver().open(file)
        try {
            connection.execSQL("PRAGMA foreign_keys = ON")
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
                    FOREIGN KEY(notice_id) REFERENCES delivery_notice(notice_id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO delivery_notice(
                    notice_id, notice_key, source_ref, occurrence_ref, materiality, sensitivity,
                    summary, destination_ref, relevance, attention, revision
                ) VALUES(
                    'notice-legacy', 'radar.price-change', 'radar:result:legacy',
                    'occurrence:legacy', 'NOTICE', 'NORMAL', 'Legacy open notice.',
                    'commercial:evidence:legacy', 'OPEN', 'UNSEEN', 1
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                INSERT INTO delivery_intent(
                    delivery_intent_id, effect_id, notice_id, sink, desired_action, state
                ) VALUES(
                    'delivery-legacy', 'effect-legacy', 'notice-legacy',
                    'ANDROID_NOTIFICATION', 'UPSERT', 'PLANNED'
                )
                """.trimIndent()
            )
            connection.execSQL("PRAGMA user_version = 1")
        } finally {
            connection.close()
        }
    }

    private fun withDatabase(block: (String) -> Unit) {
        val dir = Files.createTempDirectory("faro-v5-auxiliary-delivery")
        try {
            block(dir.resolve("auxiliary.db").toString())
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
