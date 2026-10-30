package ar.com.faro.v5.delivery.api

import ar.com.faro.v5.delivery.spi.DeliveryDispatchPersistencePort
import ar.com.faro.v5.delivery.spi.NotificationDeliveryObserver
import ar.com.faro.v5.delivery.spi.NotificationDeliverySink
import ar.com.faro.v5.delivery.spi.NotificationDispatchClaim
import ar.com.faro.v5.delivery.spi.NotificationProjectionObservation
import ar.com.faro.v5.delivery.spi.NotificationReconcileClaim
import ar.com.faro.v5.kernel.contracts.OperationExecutionPort

sealed interface NotificationDispatchExecution {
    data class Completed(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationDispatchExecution

    data class NeedsReconcile(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationDispatchExecution

    data class NotEligible(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationDispatchExecution

    data class EffectUnknown(
        val deliveryIntentId: DeliveryIntentId,
    ) : NotificationDispatchExecution

    data object NotFound : NotificationDispatchExecution
}

class NotificationDeliveryDispatcher(
    private val persistence: DeliveryDispatchPersistencePort,
    private val sink: NotificationDeliverySink,
) : OperationExecutionPort {
    override val descriptor = DeliveryOperations.DISPATCH_DESCRIPTOR

    fun dispatch(
        deliveryIntentId: DeliveryIntentId,
    ): NotificationDispatchExecution {
        return when (val claim = persistence.beginNotificationDispatch(deliveryIntentId)) {
            is NotificationDispatchClaim.Ready -> {
                val outcome =
                    try {
                        sink.dispatch(claim.request)
                    } catch (_: RuntimeException) {
                        return NotificationDispatchExecution.EffectUnknown(deliveryIntentId)
                    }

                val committed =
                    try {
                        persistence.completeNotificationDispatch(
                            deliveryIntentId = deliveryIntentId,
                            outcome = outcome,
                        )
                    } catch (_: RuntimeException) {
                        return NotificationDispatchExecution.EffectUnknown(deliveryIntentId)
                    }
                NotificationDispatchExecution.Completed(committed)
            }

            is NotificationDispatchClaim.NeedsReconcile ->
                NotificationDispatchExecution.NeedsReconcile(claim.deliveryIntent)

            is NotificationDispatchClaim.NotEligible ->
                NotificationDispatchExecution.NotEligible(claim.deliveryIntent)

            NotificationDispatchClaim.NotFound ->
                NotificationDispatchExecution.NotFound
        }
    }
}


sealed interface NotificationReconcileExecution {
    data class Completed(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationReconcileExecution

    data class StillUnknown(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationReconcileExecution

    data class ReadyForDispatch(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationReconcileExecution

    data class NotEligible(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationReconcileExecution

    data object NotFound : NotificationReconcileExecution
}

class NotificationDeliveryReconciler(
    private val persistence: DeliveryDispatchPersistencePort,
    private val observer: NotificationDeliveryObserver,
) : OperationExecutionPort {
    override val descriptor = DeliveryOperations.RECONCILE_DESCRIPTOR

    fun reconcile(
        deliveryIntentId: DeliveryIntentId,
    ): NotificationReconcileExecution {
        val current = persistence.readDeliveryIntent(deliveryIntentId)
            ?: return NotificationReconcileExecution.NotFound
        if (current.state != DeliveryEffectState.EFFECT_UNKNOWN) {
            return NotificationReconcileExecution.NotEligible(current)
        }

        val observation =
            try {
                observer.observe(deliveryIntentId.value)
            } catch (_: RuntimeException) {
                NotificationProjectionObservation.UNKNOWN
            }

        return when (
            val result =
                persistence.reconcileNotificationDispatch(
                    deliveryIntentId = deliveryIntentId,
                    observation = observation,
                )
        ) {
            is NotificationReconcileClaim.Reconciled ->
                NotificationReconcileExecution.Completed(result.deliveryIntent)

            is NotificationReconcileClaim.StillUnknown ->
                NotificationReconcileExecution.StillUnknown(result.deliveryIntent)

            is NotificationReconcileClaim.ReadyForDispatch ->
                NotificationReconcileExecution.ReadyForDispatch(result.deliveryIntent)

            is NotificationReconcileClaim.NotEligible ->
                NotificationReconcileExecution.NotEligible(result.deliveryIntent)

            NotificationReconcileClaim.NotFound ->
                NotificationReconcileExecution.NotFound
        }
    }
}
