package ar.com.faro.v5.delivery.api

import ar.com.faro.v5.delivery.spi.DeliveryProjectionPersistencePort
import ar.com.faro.v5.delivery.spi.ScheduledDeliveryWorkProjectionPort

/**
 * Rebuilds missing notification scheduler projections from current durable Delivery state.
 *
 * This operation is intentionally repeatable. It never mutates Delivery truth; the platform
 * adapter must provide idempotent unique-work semantics.
 */
class NotificationDeliveryReprojector(
    private val persistence: DeliveryProjectionPersistencePort,
    private val workProjection: ScheduledDeliveryWorkProjectionPort,
) {
    companion object {
        const val DEFAULT_SCAN_LIMIT: Int = 64
        const val MAX_SCAN_LIMIT: Int = 256
    }

    fun reproject(limit: Int = DEFAULT_SCAN_LIMIT): Int {
        require(limit in 1..MAX_SCAN_LIMIT) {
            "notification reprojection limit must be in 1..$MAX_SCAN_LIMIT"
        }

        val candidates = persistence.readNotificationProjectionCandidateIds(limit)
        check(candidates.size <= limit) {
            "delivery projection persistence exceeded requested scan limit"
        }

        candidates.forEach { deliveryIntentId ->
            workProjection.ensureOneTime(
                NotificationDeliveryScheduledWorkHandler.WORK_KIND,
                deliveryIntentId.value,
            )
        }
        return candidates.size
    }
}
