package ar.com.faro.v5.delivery.api

import ar.com.faro.v5.delivery.spi.DeliveryDispatchPersistencePort
import ar.com.faro.v5.kernel.contracts.ScheduledWorkExecutionPort
import ar.com.faro.v5.kernel.contracts.ScheduledWorkExecutionResult

/**
 * Pure Delivery-side convergence for the durable notification scheduler projection.
 *
 * Scheduler input is only a trigger identity. Current Delivery state is always re-read before
 * deciding whether an external effect, reconciliation, retry, or terminal no-op is appropriate.
 */
class NotificationDeliveryScheduledWorkHandler(
    private val persistence: DeliveryDispatchPersistencePort,
    private val dispatcher: NotificationDeliveryDispatcher,
    private val reconciler: NotificationDeliveryReconciler,
) : ScheduledWorkExecutionPort {
    companion object {
        const val WORK_KIND: String = "delivery.notification.converge"
    }

    override fun execute(
        workKind: String,
        semanticId: String,
    ): ScheduledWorkExecutionResult {
        if (workKind != WORK_KIND) {
            return ScheduledWorkExecutionResult.FAILURE
        }
        val deliveryIntentId =
            try {
                DeliveryIntentId.of(semanticId)
            } catch (_: IllegalArgumentException) {
                return ScheduledWorkExecutionResult.FAILURE
            }
        return converge(deliveryIntentId)
    }

    fun converge(deliveryIntentId: DeliveryIntentId): ScheduledWorkExecutionResult {
        val current =
            persistence.readDeliveryIntent(deliveryIntentId)
                ?: return ScheduledWorkExecutionResult.SUCCESS

        return when (current.state) {
            DeliveryEffectState.PLANNED -> dispatchPlanned(deliveryIntentId)
            DeliveryEffectState.EFFECT_UNKNOWN -> reconcileUnknown(deliveryIntentId)
            DeliveryEffectState.SUPPRESSED_POLICY,
            DeliveryEffectState.BLOCKED_PERMISSION,
            DeliveryEffectState.POSTED_TO_OS,
            DeliveryEffectState.FAILED,
            DeliveryEffectState.CANCELLED,
            -> ScheduledWorkExecutionResult.SUCCESS
        }
    }

    private fun dispatchPlanned(
        deliveryIntentId: DeliveryIntentId,
    ): ScheduledWorkExecutionResult =
        when (val result = dispatcher.dispatch(deliveryIntentId)) {
            is NotificationDispatchExecution.Completed ->
                ScheduledWorkExecutionResult.SUCCESS
            is NotificationDispatchExecution.EffectUnknown ->
                ScheduledWorkExecutionResult.RETRY
            is NotificationDispatchExecution.NeedsReconcile ->
                ScheduledWorkExecutionResult.RETRY
            is NotificationDispatchExecution.NotEligible ->
                if (result.deliveryIntent.state == DeliveryEffectState.EFFECT_UNKNOWN) {
                    ScheduledWorkExecutionResult.RETRY
                } else {
                    ScheduledWorkExecutionResult.SUCCESS
                }
            NotificationDispatchExecution.NotFound ->
                ScheduledWorkExecutionResult.SUCCESS
        }

    private fun reconcileUnknown(
        deliveryIntentId: DeliveryIntentId,
    ): ScheduledWorkExecutionResult =
        when (val result = reconciler.reconcile(deliveryIntentId)) {
            is NotificationReconcileExecution.Completed ->
                ScheduledWorkExecutionResult.SUCCESS
            is NotificationReconcileExecution.StillUnknown ->
                ScheduledWorkExecutionResult.FAILURE
            is NotificationReconcileExecution.ReadyForDispatch ->
                dispatchAfterReconcile(deliveryIntentId)
            is NotificationReconcileExecution.NotEligible ->
                when (result.deliveryIntent.state) {
                    DeliveryEffectState.PLANNED -> ScheduledWorkExecutionResult.RETRY
                    DeliveryEffectState.EFFECT_UNKNOWN -> ScheduledWorkExecutionResult.FAILURE
                    else -> ScheduledWorkExecutionResult.SUCCESS
                }
            NotificationReconcileExecution.NotFound ->
                ScheduledWorkExecutionResult.SUCCESS
        }

    private fun dispatchAfterReconcile(
        deliveryIntentId: DeliveryIntentId,
    ): ScheduledWorkExecutionResult =
        when (val result = dispatcher.dispatch(deliveryIntentId)) {
            is NotificationDispatchExecution.Completed ->
                ScheduledWorkExecutionResult.SUCCESS
            is NotificationDispatchExecution.EffectUnknown ->
                ScheduledWorkExecutionResult.FAILURE
            is NotificationDispatchExecution.NeedsReconcile ->
                ScheduledWorkExecutionResult.FAILURE
            is NotificationDispatchExecution.NotEligible ->
                if (result.deliveryIntent.state == DeliveryEffectState.PLANNED ||
                    result.deliveryIntent.state == DeliveryEffectState.EFFECT_UNKNOWN
                ) {
                    ScheduledWorkExecutionResult.FAILURE
                } else {
                    ScheduledWorkExecutionResult.SUCCESS
                }
            NotificationDispatchExecution.NotFound ->
                ScheduledWorkExecutionResult.SUCCESS
        }
}
