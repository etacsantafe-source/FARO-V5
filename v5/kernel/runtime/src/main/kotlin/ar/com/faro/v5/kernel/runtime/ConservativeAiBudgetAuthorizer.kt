package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiBudgetAuthorizer
import ar.com.faro.v5.kernel.contracts.AiBudgetBlockReason
import ar.com.faro.v5.kernel.contracts.AiBudgetDecision
import ar.com.faro.v5.kernel.contracts.AiMonetaryBudget
import ar.com.faro.v5.kernel.contracts.DataEgressTarget
import ar.com.faro.v5.kernel.contracts.ModelProfileBinding
import ar.com.faro.v5.kernel.contracts.ProjectedModelUsage
import ar.com.faro.v5.kernel.contracts.TrustedBindingPricing

object ConservativeAiBudgetAuthorizer : AiBudgetAuthorizer {
    override fun authorize(
        binding: ModelProfileBinding,
        projectedUsage: ProjectedModelUsage,
        pricing: TrustedBindingPricing?,
        budget: AiMonetaryBudget,
    ): AiBudgetDecision {
        if (binding.target == DataEgressTarget.ON_DEVICE_MODEL) {
            return AiBudgetDecision.Allowed(projectedCost = null)
        }

        if (pricing != null) {
            require(pricing.bindingId == binding.bindingId) {
                "pricing binding must match route binding"
            }
        }

        val projectedCost =
            pricing?.let {
                ModelCostEstimator.estimate(
                    usage = projectedUsage.asUsage(),
                    pricing = it,
                )
            }

        return when (budget) {
            AiMonetaryBudget.Disabled ->
                AiBudgetDecision.Allowed(projectedCost = projectedCost)

            is AiMonetaryBudget.Capped -> {
                if (pricing == null || projectedCost == null) {
                    AiBudgetDecision.Blocked(
                        reason = AiBudgetBlockReason.TRUSTED_PRICING_REQUIRED,
                    )
                } else if (pricing.currencyCode != budget.currencyCode) {
                    AiBudgetDecision.Blocked(
                        reason = AiBudgetBlockReason.CURRENCY_MISMATCH,
                        projectedCost = projectedCost,
                    )
                } else if (projectedCost.minorUnits > budget.remainingMinor) {
                    AiBudgetDecision.Blocked(
                        reason = AiBudgetBlockReason.BUDGET_EXCEEDED,
                        projectedCost = projectedCost,
                    )
                } else {
                    AiBudgetDecision.Allowed(
                        projectedCost = projectedCost,
                    )
                }
            }
        }
    }
}
