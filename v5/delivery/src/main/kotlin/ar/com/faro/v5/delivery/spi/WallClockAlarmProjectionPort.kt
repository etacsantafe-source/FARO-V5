package ar.com.faro.v5.delivery.spi

private val OPAQUE_ALARM_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

data class WallClockAlarmIdentity(
    val workKind: String,
    val semanticId: String,
) {
    init {
        require(OPAQUE_ALARM_ID.matches(workKind)) {
            "workKind must be an opaque ASCII identifier (1..128 chars)"
        }
        require(OPAQUE_ALARM_ID.matches(semanticId)) {
            "semanticId must be an opaque ASCII identifier (1..128 chars)"
        }
    }
}

enum class WallClockAlarmPrecision {
    INEXACT,
    EXACT_REQUIRED,
}

data class WallClockAlarmProjectionRequest(
    val identity: WallClockAlarmIdentity,
    val triggerAtEpochMillis: Long,
    val precision: WallClockAlarmPrecision,
) {
    init {
        require(triggerAtEpochMillis > 0L) {
            "triggerAtEpochMillis must be > 0"
        }
    }
}

enum class WallClockAlarmProjectionResult {
    SCHEDULED,
    CANCELLED,
    EXACT_ACCESS_REQUIRED,
    EXECUTION_BRIDGE_UNAVAILABLE,
    INEXACT_NOT_HANDLED,
}

/**
 * Platform-neutral wall-clock alarm projection.
 *
 * Alarm state is rebuildable platform state, never FARO Truth. Implementations must keep stable
 * identity by workKind + semanticId and must never upgrade INEXACT intent to an exact alarm.
 */
interface WallClockAlarmProjectionPort {
    fun schedule(request: WallClockAlarmProjectionRequest): WallClockAlarmProjectionResult

    fun cancel(identity: WallClockAlarmIdentity): WallClockAlarmProjectionResult
}
