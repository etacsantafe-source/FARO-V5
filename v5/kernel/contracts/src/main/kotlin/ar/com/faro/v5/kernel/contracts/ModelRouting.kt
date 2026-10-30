package ar.com.faro.v5.kernel.contracts

enum class ModelRouteRejectionReason {
    SNAPSHOT_MISSING,
    LIFECYCLE_NOT_ACTIVE,
    CAPABILITY_MISMATCH,
    CONTEXT_LIMIT_UNKNOWN,
    CONTEXT_LIMIT_TOO_SMALL,
    EGRESS_BLOCKED,
}

data class ModelRouteRejection(
    val bindingId: ModelBindingId,
    val reason: ModelRouteRejectionReason,
    val blockedFragmentIds: List<ContextFragmentId> = emptyList(),
) {
    init {
        if (reason == ModelRouteRejectionReason.EGRESS_BLOCKED) {
            require(blockedFragmentIds.isNotEmpty()) {
                "egress-blocked route rejection must identify blocked fragments"
            }
        } else {
            require(blockedFragmentIds.isEmpty()) {
                "only egress-blocked route rejection may carry blocked fragments"
            }
        }
        require(blockedFragmentIds.size == blockedFragmentIds.distinct().size) {
            "blocked route fragment ids must be unique"
        }
    }
}

data class ModelRoutePlan(
    val eligibleBindings: List<ModelProfileBinding>,
    val rejections: List<ModelRouteRejection>,
) {
    init {
        val eligibleIds = eligibleBindings.map { it.bindingId }
        val rejectedIds = rejections.map { it.bindingId }
        require(eligibleIds.size == eligibleIds.distinct().size) {
            "eligible route binding ids must be unique"
        }
        require(rejectedIds.size == rejectedIds.distinct().size) {
            "route rejection binding ids must be unique"
        }
        require(eligibleIds.none { it in rejectedIds }) {
            "binding cannot be both eligible and rejected"
        }
    }
}

fun interface ModelRoutePlanner {
    fun plan(
        profile: ModelProfile,
        snapshots: List<ProviderCapabilitySnapshot>,
        requirements: ModelRequirements,
        contextPack: ContextPack,
        egressPolicy: DataEgressPolicy,
    ): ModelRoutePlan
}
