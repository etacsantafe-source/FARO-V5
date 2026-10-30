package ar.com.faro.v5.delivery.spi

/**
 * Platform-neutral request to ensure one durable scheduler projection exists.
 *
 * Implementations must be idempotent for the same workKind + semanticId pair. Submission does not
 * transfer domain authority to the platform scheduler.
 */
fun interface ScheduledDeliveryWorkProjectionPort {
    fun ensureOneTime(
        workKind: String,
        semanticId: String,
    )
}
