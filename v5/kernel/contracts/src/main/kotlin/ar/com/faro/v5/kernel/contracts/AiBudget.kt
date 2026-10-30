package ar.com.faro.v5.kernel.contracts

data class ProjectedModelUsage(
    val inputTokens: Long,
    val maxOutputTokens: Long,
) {
    init {
        require(inputTokens >= 0L) {
            "projected input tokens must be non-negative"
        }
        require(maxOutputTokens >= 0L) {
            "projected max output tokens must be non-negative"
        }
    }

    fun asUsage(): AiUsage =
        AiUsage(
            inputTokens = inputTokens,
            outputTokens = maxOutputTokens,
        )
}

sealed interface AiMonetaryBudget {
    data object Disabled : AiMonetaryBudget

    data class Capped(
        val currencyCode: String,
        val remainingMinor: Long,
    ) : AiMonetaryBudget {
        init {
            require(currencyCode.matches(Regex("[A-Z]{3}"))) {
                "budget currency must be an uppercase ISO-like 3-letter code"
            }
            require(remainingMinor >= 0L) {
                "remaining budget must be non-negative"
            }
        }
    }
}

enum class AiBudgetBlockReason {
    TRUSTED_PRICING_REQUIRED,
    CURRENCY_MISMATCH,
    BUDGET_EXCEEDED,
}

sealed interface AiBudgetDecision {
    data class Allowed(
        val projectedCost: EstimatedModelCost?,
    ) : AiBudgetDecision

    data class Blocked(
        val reason: AiBudgetBlockReason,
        val projectedCost: EstimatedModelCost? = null,
    ) : AiBudgetDecision
}

fun interface AiBudgetAuthorizer {
    fun authorize(
        binding: ModelProfileBinding,
        projectedUsage: ProjectedModelUsage,
        pricing: TrustedBindingPricing?,
        budget: AiMonetaryBudget,
    ): AiBudgetDecision
}
