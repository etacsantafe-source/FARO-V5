package ar.com.faro.v5.delivery.spi

import ar.com.faro.v5.delivery.api.DeliveryIntent
import ar.com.faro.v5.delivery.api.DeliveryIntentId

sealed interface NotificationReconcileClaim {
    data class Reconciled(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationReconcileClaim

    data class StillUnknown(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationReconcileClaim

    data class ReadyForDispatch(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationReconcileClaim

    data class NotEligible(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationReconcileClaim

    data object NotFound : NotificationReconcileClaim
}

sealed interface NotificationDispatchClaim {
    data class Ready(
        val request: NotificationProjectionRequest,
    ) : NotificationDispatchClaim

    data class NeedsReconcile(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationDispatchClaim

    data class NotEligible(
        val deliveryIntent: DeliveryIntent,
    ) : NotificationDispatchClaim

    data object NotFound : NotificationDispatchClaim
}

interface DeliveryDispatchPersistencePort {
    fun beginNotificationDispatch(
        deliveryIntentId: DeliveryIntentId,
    ): NotificationDispatchClaim

    fun completeNotificationDispatch(
        deliveryIntentId: DeliveryIntentId,
        outcome: NotificationSinkOutcome,
    ): DeliveryIntent

    fun reconcileNotificationDispatch(
        deliveryIntentId: DeliveryIntentId,
        observation: NotificationProjectionObservation,
    ): NotificationReconcileClaim

    fun readDeliveryIntent(
        deliveryIntentId: DeliveryIntentId,
    ): DeliveryIntent?
}
