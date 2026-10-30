package ar.com.faro.v5.kernel.contracts

data class AiBudgetRouteRejection(
    val bindingId: ModelBindingId,
    val reason: AiBudgetBlockReason,
    val projectedCost: EstimatedModelCost? = null,
)

data class AiBudgetedRoutePlan(
    val authorizedBindings: List<ModelProfileBinding>,
    val budgetRejections: List<AiBudgetRouteRejection>,
) {
    init {
        val authorizedIds = authorizedBindings.map { it.bindingId }
        val rejectedIds = budgetRejections.map { it.bindingId }
        require(authorizedIds.size == authorizedIds.distinct().size) {
            "budget-authorized binding ids must be unique"
        }
        require(rejectedIds.size == rejectedIds.distinct().size) {
            "budget-rejected binding ids must be unique"
        }
        require(authorizedIds.none { it in rejectedIds }) {
            "binding cannot be both budget-authorized and budget-rejected"
        }
    }
}

fun interface AiBudgetRoutePlanner {
    fun plan(
        routePlan: ModelRoutePlan,
        projectedUsage: ProjectedModelUsage,
        pricing: List<TrustedBindingPricing>,
        budget: AiMonetaryBudget,
        authorizer: AiBudgetAuthorizer,
    ): AiBudgetedRoutePlan
}
