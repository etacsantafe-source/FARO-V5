package ar.com.faro.v5.delivery.spi

import ar.com.faro.v5.delivery.api.DeliveryIntentId

/**
 * Bounded read-side capability for rebuilding scheduler projections from durable Delivery truth.
 *
 * Implementations must return only notification intents whose durable state still requires a
 * convergence opportunity. Scheduler state is never consulted as domain authority.
 */
fun interface DeliveryProjectionPersistencePort {
    fun readNotificationProjectionCandidateIds(limit: Int): List<DeliveryIntentId>
}
