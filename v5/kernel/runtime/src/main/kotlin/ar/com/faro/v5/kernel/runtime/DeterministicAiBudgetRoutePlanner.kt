package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiBudgetDecision
import ar.com.faro.v5.kernel.contracts.AiBudgetRoutePlanner
import ar.com.faro.v5.kernel.contracts.AiBudgetRouteRejection
import ar.com.faro.v5.kernel.contracts.AiBudgetedRoutePlan
import ar.com.faro.v5.kernel.contracts.AiMonetaryBudget
import ar.com.faro.v5.kernel.contracts.AiBudgetAuthorizer
import ar.com.faro.v5.kernel.contracts.ModelRoutePlan
import ar.com.faro.v5.kernel.contracts.ProjectedModelUsage
import ar.com.faro.v5.kernel.contracts.TrustedBindingPricing

object DeterministicAiBudgetRoutePlanner : AiBudgetRoutePlanner {
    override fun plan(
        routePlan: ModelRoutePlan,
        projectedUsage: ProjectedModelUsage,
        pricing: List<TrustedBindingPricing>,
        budget: AiMonetaryBudget,
        authorizer: AiBudgetAuthorizer,
    ): AiBudgetedRoutePlan {
        val pricingIds = pricing.map { it.bindingId }
        require(pricingIds.size == pricingIds.distinct().size) {
            "trusted pricing entries must have unique binding ids"
        }
        val pricingByBinding = pricing.associateBy { it.bindingId }

        val authorized = mutableListOf<ar.com.faro.v5.kernel.contracts.ModelProfileBinding>()
        val rejected = mutableListOf<AiBudgetRouteRejection>()

        routePlan.eligibleBindings.forEach { binding ->
            when (
                val decision = authorizer.authorize(
                    binding = binding,
                    projectedUsage = projectedUsage,
                    pricing = pricingByBinding[binding.bindingId],
                    budget = budget,
                )
            ) {
                is AiBudgetDecision.Allowed ->
                    authorized += binding

                is AiBudgetDecision.Blocked ->
                    rejected += AiBudgetRouteRejection(
                        bindingId = binding.bindingId,
                        reason = decision.reason,
                        projectedCost = decision.projectedCost,
                    )
            }
        }

        return AiBudgetedRoutePlan(
            authorizedBindings = authorized.toList(),
            budgetRejections = rejected.toList(),
        )
    }
}
