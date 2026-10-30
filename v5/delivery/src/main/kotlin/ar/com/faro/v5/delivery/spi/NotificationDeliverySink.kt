package ar.com.faro.v5.delivery.spi

import ar.com.faro.v5.delivery.api.DeliveryDesiredAction
import ar.com.faro.v5.delivery.api.NoticeSummary

data class NotificationProjectionRequest(
    val deliveryIdentity: String,
    val desiredAction: DeliveryDesiredAction,
    val summary: String,
    val sensitive: Boolean,
) {
    init {
        require(deliveryIdentity.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) {
            "deliveryIdentity must be an opaque bounded identifier"
        }
        require(summary.isNotBlank() && summary.length <= NoticeSummary.MAX_LENGTH) {
            "notification summary must be bounded and non-blank"
        }
    }
}

enum class NotificationSinkOutcome {
    POSTED_TO_OS,
    CANCELLED,
    BLOCKED_PERMISSION,
    FAILED,
}

fun interface NotificationDeliverySink {
    fun dispatch(request: NotificationProjectionRequest): NotificationSinkOutcome
}


enum class NotificationProjectionObservation {
    ACTIVE,
    NOT_ACTIVE,
    UNKNOWN,
}

fun interface NotificationDeliveryObserver {
    fun observe(deliveryIdentity: String): NotificationProjectionObservation
}
