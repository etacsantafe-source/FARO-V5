package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ContextPack
import ar.com.faro.v5.kernel.contracts.DataEgressDecision
import ar.com.faro.v5.kernel.contracts.DataEgressPolicy
import ar.com.faro.v5.kernel.contracts.ModelLifecycle
import ar.com.faro.v5.kernel.contracts.ModelProfile
import ar.com.faro.v5.kernel.contracts.ModelProfileBinding
import ar.com.faro.v5.kernel.contracts.ModelRequirements
import ar.com.faro.v5.kernel.contracts.ModelRoutePlan
import ar.com.faro.v5.kernel.contracts.ModelRoutePlanner
import ar.com.faro.v5.kernel.contracts.ModelRouteRejection
import ar.com.faro.v5.kernel.contracts.ModelRouteRejectionReason
import ar.com.faro.v5.kernel.contracts.ProviderCapabilitySnapshot

object DeterministicModelRoutePlanner : ModelRoutePlanner {
    override fun plan(
        profile: ModelProfile,
        snapshots: List<ProviderCapabilitySnapshot>,
        requirements: ModelRequirements,
        contextPack: ContextPack,
        egressPolicy: DataEgressPolicy,
    ): ModelRoutePlan {
        val snapshotIds = snapshots.map { it.bindingId }
        require(snapshotIds.size == snapshotIds.distinct().size) {
            "provider capability snapshots must have unique binding ids"
        }
        val byBinding = snapshots.associateBy { it.bindingId }

        val eligible = mutableListOf<ModelProfileBinding>()
        val rejected = mutableListOf<ModelRouteRejection>()

        profile.orderedBindings.forEach { binding ->
            val snapshot = byBinding[binding.bindingId]
            if (snapshot == null) {
                rejected += rejection(
                    binding,
                    ModelRouteRejectionReason.SNAPSHOT_MISSING,
                )
                return@forEach
            }
            if (snapshot.lifecycle != ModelLifecycle.ACTIVE) {
                rejected += rejection(
                    binding,
                    ModelRouteRejectionReason.LIFECYCLE_NOT_ACTIVE,
                )
                return@forEach
            }
            if (!snapshot.capabilities.containsAll(requirements.requiredCapabilities)) {
                rejected += rejection(
                    binding,
                    ModelRouteRejectionReason.CAPABILITY_MISMATCH,
                )
                return@forEach
            }

            val minimumContext = requirements.minimumContextTokens
            if (minimumContext != null) {
                val contextLimit = snapshot.contextLimitTokens
                if (contextLimit == null) {
                    rejected += rejection(
                        binding,
                        ModelRouteRejectionReason.CONTEXT_LIMIT_UNKNOWN,
                    )
                    return@forEach
                }
                if (contextLimit < minimumContext) {
                    rejected += rejection(
                        binding,
                        ModelRouteRejectionReason.CONTEXT_LIMIT_TOO_SMALL,
                    )
                    return@forEach
                }
            }

            when (val egress = egressPolicy.evaluate(contextPack, binding.target)) {
                DataEgressDecision.Allowed -> eligible += binding
                is DataEgressDecision.Blocked ->
                    rejected += ModelRouteRejection(
                        bindingId = binding.bindingId,
                        reason = ModelRouteRejectionReason.EGRESS_BLOCKED,
                        blockedFragmentIds = egress.fragmentIds.sortedBy { it.value },
                    )
            }
        }

        return ModelRoutePlan(
            eligibleBindings = eligible.toList(),
            rejections = rejected.toList(),
        )
    }

    private fun rejection(
        binding: ModelProfileBinding,
        reason: ModelRouteRejectionReason,
    ): ModelRouteRejection =
        ModelRouteRejection(
            bindingId = binding.bindingId,
            reason = reason,
        )
}
