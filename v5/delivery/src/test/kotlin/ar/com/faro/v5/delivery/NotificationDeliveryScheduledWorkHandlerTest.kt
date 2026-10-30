package ar.com.faro.v5.delivery

import ar.com.faro.v5.delivery.api.DeliveryDesiredAction
import ar.com.faro.v5.delivery.api.DeliveryEffectState
import ar.com.faro.v5.delivery.api.DeliveryIntent
import ar.com.faro.v5.delivery.api.DeliveryIntentId
import ar.com.faro.v5.delivery.api.DeliverySink
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NotificationDeliveryDispatcher
import ar.com.faro.v5.delivery.api.NotificationDeliveryReconciler
import ar.com.faro.v5.delivery.api.NotificationDeliveryScheduledWorkHandler
import ar.com.faro.v5.delivery.spi.DeliveryDispatchPersistencePort
import ar.com.faro.v5.delivery.spi.NotificationDeliveryObserver
import ar.com.faro.v5.delivery.spi.NotificationDeliverySink
import ar.com.faro.v5.delivery.spi.NotificationDispatchClaim
import ar.com.faro.v5.delivery.spi.NotificationProjectionObservation
import ar.com.faro.v5.delivery.spi.NotificationProjectionRequest
import ar.com.faro.v5.delivery.spi.NotificationReconcileClaim
import ar.com.faro.v5.delivery.spi.NotificationSinkOutcome
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.kernel.contracts.ScheduledWorkExecutionResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationDeliveryScheduledWorkHandlerTest {
    @Test
    fun rejectsUnknownWorkKindAndMalformedSemanticId() {
        val persistence = FakeDispatchPersistence(intent(DeliveryEffectState.PLANNED))
        val harness = harness(persistence)

        assertEquals(
            ScheduledWorkExecutionResult.FAILURE,
            harness.handler.execute("other.work", DELIVERY_ID.value),
        )
        assertEquals(
            ScheduledWorkExecutionResult.FAILURE,
            harness.handler.execute(NotificationDeliveryScheduledWorkHandler.WORK_KIND, "bad id"),
        )
        assertEquals(0, persistence.readCount)
    }

    @Test
    fun missingAndTerminalIntentsAreSuccessfulNoOps() {
        val missing = FakeDispatchPersistence(null)
        val missingHarness = harness(missing)
        assertEquals(ScheduledWorkExecutionResult.SUCCESS, missingHarness.execute())
        assertEquals(0, missingHarness.sinkCalls)

        for (
            state in listOf(
                DeliveryEffectState.SUPPRESSED_POLICY,
                DeliveryEffectState.BLOCKED_PERMISSION,
                DeliveryEffectState.POSTED_TO_OS,
                DeliveryEffectState.FAILED,
                DeliveryEffectState.CANCELLED,
            )
        ) {
            val terminal = FakeDispatchPersistence(intent(state))
            val terminalHarness = harness(terminal)
            assertEquals(ScheduledWorkExecutionResult.SUCCESS, terminalHarness.execute())
            assertEquals(0, terminalHarness.sinkCalls)
            assertEquals(0, terminalHarness.observeCalls)
        }
    }

    @Test
    fun plannedDispatchCompletesInOneBoundedExecution() {
        val persistence = FakeDispatchPersistence(intent(DeliveryEffectState.PLANNED))
        val harness = harness(persistence, sinkOutcome = NotificationSinkOutcome.POSTED_TO_OS)

        assertEquals(ScheduledWorkExecutionResult.SUCCESS, harness.execute())
        assertEquals(1, harness.sinkCalls)
        assertEquals(DeliveryEffectState.POSTED_TO_OS, persistence.current?.state)
    }

    @Test
    fun uncertainPlannedDispatchRetriesOnceThenReconcilesFromDurableState() {
        val persistence = FakeDispatchPersistence(intent(DeliveryEffectState.PLANNED))
        var first = true
        val harness = harness(
            persistence = persistence,
            sink = {
                if (first) {
                    first = false
                    throw IllegalStateException("sink uncertainty")
                }
                NotificationSinkOutcome.POSTED_TO_OS
            },
            observation = NotificationProjectionObservation.ACTIVE,
        )

        assertEquals(ScheduledWorkExecutionResult.RETRY, harness.execute())
        assertEquals(DeliveryEffectState.EFFECT_UNKNOWN, persistence.current?.state)
        assertEquals(ScheduledWorkExecutionResult.SUCCESS, harness.execute())
        assertEquals(DeliveryEffectState.POSTED_TO_OS, persistence.current?.state)
        assertEquals(1, harness.sinkCalls)
        assertEquals(1, harness.observeCalls)
    }

    @Test
    fun unresolvedUnknownIsTerminalForThisWorkRequest() {
        val persistence = FakeDispatchPersistence(intent(DeliveryEffectState.EFFECT_UNKNOWN))
        val harness = harness(
            persistence = persistence,
            observation = NotificationProjectionObservation.UNKNOWN,
        )

        assertEquals(ScheduledWorkExecutionResult.FAILURE, harness.execute())
        assertEquals(DeliveryEffectState.EFFECT_UNKNOWN, persistence.current?.state)
        assertEquals(0, harness.sinkCalls)
        assertEquals(1, harness.observeCalls)
    }

    @Test
    fun unknownCancelObservedActiveRearmsAndDispatchesCancelInSameExecution() {
        val persistence = FakeDispatchPersistence(
            intent(
                state = DeliveryEffectState.EFFECT_UNKNOWN,
                desiredAction = DeliveryDesiredAction.CANCEL,
            )
        )
        val harness = harness(
            persistence = persistence,
            sinkOutcome = NotificationSinkOutcome.CANCELLED,
            observation = NotificationProjectionObservation.ACTIVE,
        )

        assertEquals(ScheduledWorkExecutionResult.SUCCESS, harness.execute())
        assertEquals(1, harness.observeCalls)
        assertEquals(1, harness.sinkCalls)
        assertEquals(DeliveryDesiredAction.CANCEL, harness.lastRequest?.desiredAction)
        assertEquals(DeliveryEffectState.CANCELLED, persistence.current?.state)
    }

    @Test
    fun postReconcileEffectUnknownDoesNotCreateAnUnboundedRetryLoop() {
        val persistence = FakeDispatchPersistence(
            intent(
                state = DeliveryEffectState.EFFECT_UNKNOWN,
                desiredAction = DeliveryDesiredAction.CANCEL,
            )
        )
        val harness = harness(
            persistence = persistence,
            sink = { throw IllegalStateException("cancel effect uncertain") },
            observation = NotificationProjectionObservation.ACTIVE,
        )

        assertEquals(ScheduledWorkExecutionResult.FAILURE, harness.execute())
        assertEquals(1, harness.observeCalls)
        assertEquals(1, harness.sinkCalls)
        assertEquals(DeliveryEffectState.EFFECT_UNKNOWN, persistence.current?.state)
    }

    private fun harness(
        persistence: FakeDispatchPersistence,
        sinkOutcome: NotificationSinkOutcome = NotificationSinkOutcome.POSTED_TO_OS,
        sink: (() -> NotificationSinkOutcome)? = null,
        observation: NotificationProjectionObservation = NotificationProjectionObservation.UNKNOWN,
    ): Harness {
        var sinkCalls = 0
        var observeCalls = 0
        var lastRequest: NotificationProjectionRequest? = null
        val deliverySink = NotificationDeliverySink { request ->
            sinkCalls += 1
            lastRequest = request
            sink?.invoke() ?: sinkOutcome
        }
        val observer = NotificationDeliveryObserver {
            observeCalls += 1
            observation
        }
        val handler = NotificationDeliveryScheduledWorkHandler(
            persistence = persistence,
            dispatcher = NotificationDeliveryDispatcher(persistence, deliverySink),
            reconciler = NotificationDeliveryReconciler(persistence, observer),
        )
        return Harness(
            handler = handler,
            sinkCallsProvider = { sinkCalls },
            observeCallsProvider = { observeCalls },
            lastRequestProvider = { lastRequest },
        )
    }

    private fun intent(
        state: DeliveryEffectState,
        desiredAction: DeliveryDesiredAction = DeliveryDesiredAction.UPSERT,
    ) = DeliveryIntent(
        deliveryIntentId = DELIVERY_ID,
        effectId = EffectId("effect-work"),
        noticeId = NoticeId.of("notice-work"),
        sink = DeliverySink.ANDROID_NOTIFICATION,
        desiredAction = desiredAction,
        state = state,
    )

    private data class Harness(
        val handler: NotificationDeliveryScheduledWorkHandler,
        val sinkCallsProvider: () -> Int,
        val observeCallsProvider: () -> Int,
        val lastRequestProvider: () -> NotificationProjectionRequest?,
    ) {
        val sinkCalls: Int get() = sinkCallsProvider()
        val observeCalls: Int get() = observeCallsProvider()
        val lastRequest: NotificationProjectionRequest? get() = lastRequestProvider()

        fun execute(): ScheduledWorkExecutionResult =
            handler.execute(NotificationDeliveryScheduledWorkHandler.WORK_KIND, DELIVERY_ID.value)
    }

    private companion object {
        val DELIVERY_ID = DeliveryIntentId.of("delivery-work")
    }
}

private class FakeDispatchPersistence(
    initial: DeliveryIntent?,
) : DeliveryDispatchPersistencePort {
    var current: DeliveryIntent? = initial
    var readCount: Int = 0

    override fun readDeliveryIntent(deliveryIntentId: DeliveryIntentId): DeliveryIntent? {
        readCount += 1
        return current
    }

    override fun beginNotificationDispatch(
        deliveryIntentId: DeliveryIntentId,
    ): NotificationDispatchClaim {
        val value = current ?: return NotificationDispatchClaim.NotFound
        return when (value.state) {
            DeliveryEffectState.PLANNED -> {
                current = value.copy(state = DeliveryEffectState.EFFECT_UNKNOWN)
                NotificationDispatchClaim.Ready(
                    NotificationProjectionRequest(
                        deliveryIdentity = value.deliveryIntentId.value,
                        desiredAction = value.desiredAction,
                        summary = "scheduled delivery",
                        sensitive = false,
                    )
                )
            }
            DeliveryEffectState.EFFECT_UNKNOWN ->
                NotificationDispatchClaim.NeedsReconcile(value)
            else ->
                NotificationDispatchClaim.NotEligible(value)
        }
    }

    override fun completeNotificationDispatch(
        deliveryIntentId: DeliveryIntentId,
        outcome: NotificationSinkOutcome,
    ): DeliveryIntent {
        val value = checkNotNull(current)
        check(value.state == DeliveryEffectState.EFFECT_UNKNOWN)
        val terminal =
            when (outcome) {
                NotificationSinkOutcome.POSTED_TO_OS -> DeliveryEffectState.POSTED_TO_OS
                NotificationSinkOutcome.CANCELLED -> DeliveryEffectState.CANCELLED
                NotificationSinkOutcome.BLOCKED_PERMISSION -> DeliveryEffectState.BLOCKED_PERMISSION
                NotificationSinkOutcome.FAILED -> DeliveryEffectState.FAILED
            }
        return value.copy(state = terminal).also { current = it }
    }

    override fun reconcileNotificationDispatch(
        deliveryIntentId: DeliveryIntentId,
        observation: NotificationProjectionObservation,
    ): NotificationReconcileClaim {
        val value = current ?: return NotificationReconcileClaim.NotFound
        if (value.state != DeliveryEffectState.EFFECT_UNKNOWN) {
            return NotificationReconcileClaim.NotEligible(value)
        }
        val reconciled =
            when {
                value.desiredAction == DeliveryDesiredAction.UPSERT &&
                    observation == NotificationProjectionObservation.ACTIVE ->
                    DeliveryEffectState.POSTED_TO_OS
                value.desiredAction == DeliveryDesiredAction.CANCEL &&
                    observation == NotificationProjectionObservation.NOT_ACTIVE ->
                    DeliveryEffectState.CANCELLED
                value.desiredAction == DeliveryDesiredAction.CANCEL &&
                    observation == NotificationProjectionObservation.ACTIVE ->
                    DeliveryEffectState.PLANNED
                else -> null
            }
        if (reconciled == null) {
            return NotificationReconcileClaim.StillUnknown(value)
        }
        val committed = value.copy(state = reconciled).also { current = it }
        return if (reconciled == DeliveryEffectState.PLANNED) {
            NotificationReconcileClaim.ReadyForDispatch(committed)
        } else {
            NotificationReconcileClaim.Reconciled(committed)
        }
    }
}
