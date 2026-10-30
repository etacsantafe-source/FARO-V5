package ar.com.faro.v5.kernel.contracts

@JvmInline
value class PlatformRequirementId(val value: String) {
    init {
        require(value.isNotBlank()) { "platform requirement id must not be blank" }
    }
}

@JvmInline
value class PlatformFeatureId(val value: String) {
    init {
        require(value.isNotBlank()) { "platform feature id must not be blank" }
    }
}

enum class PlatformRequirementState {
    SATISFIED,
    DENIED,
    UNAVAILABLE,
    UNKNOWN,
    NOT_APPLICABLE,
}

data class PlatformRequirementStatus(
    val requirementId: PlatformRequirementId,
    val state: PlatformRequirementState,
)

enum class PlatformAvailabilityState {
    AVAILABLE,
    DEGRADED,
    UNAVAILABLE,
    UNKNOWN,
}

data class PlatformAvailability(
    val featureId: PlatformFeatureId,
    val state: PlatformAvailabilityState,
    val blockers: Set<PlatformRequirementId> = emptySet(),
) {
    init {
        if (state == PlatformAvailabilityState.AVAILABLE) {
            require(blockers.isEmpty()) {
                "available platform feature must not report blockers"
            }
        }
    }
}

fun interface PlatformRequirementStatusPort {
    fun status(requirementId: PlatformRequirementId): PlatformRequirementStatus
}

fun interface PlatformAvailabilityPort {
    fun availability(featureId: PlatformFeatureId): PlatformAvailability
}
