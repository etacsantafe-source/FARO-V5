package ar.com.faro.v5.kernel.contracts

data class AiUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
) {
    init {
        require(inputTokens == null || inputTokens >= 0L) {
            "input tokens must be non-negative when reported"
        }
        require(outputTokens == null || outputTokens >= 0L) {
            "output tokens must be non-negative when reported"
        }
    }

    val complete: Boolean
        get() = inputTokens != null && outputTokens != null
}

data class TrustedBindingPricing(
    val bindingId: ModelBindingId,
    val currencyCode: String,
    val inputPerMillionMinor: Long,
    val outputPerMillionMinor: Long,
    val source: String,
    val effectiveAtEpochMillis: Long,
) {
    init {
        require(currencyCode.matches(Regex("[A-Z]{3}"))) {
            "pricing currency must be an uppercase ISO-like 3-letter code"
        }
        require(inputPerMillionMinor >= 0L) {
            "input pricing must be non-negative"
        }
        require(outputPerMillionMinor >= 0L) {
            "output pricing must be non-negative"
        }
        require(source.isNotBlank()) {
            "pricing source must not be blank"
        }
        require(effectiveAtEpochMillis > 0L) {
            "pricing effective time must be positive"
        }
    }
}

data class EstimatedModelCost(
    val minorUnits: Long,
    val currencyCode: String,
    val pricingSource: String,
    val pricingEffectiveAtEpochMillis: Long,
) {
    init {
        require(minorUnits >= 0L) {
            "estimated cost must be non-negative"
        }
        require(currencyCode.matches(Regex("[A-Z]{3}"))) {
            "estimated cost currency must be an uppercase ISO-like 3-letter code"
        }
        require(pricingSource.isNotBlank()) {
            "estimated cost pricing source must not be blank"
        }
        require(pricingEffectiveAtEpochMillis > 0L) {
            "estimated cost pricing time must be positive"
        }
    }
}

data class AiUsageRecord(
    val requestId: ModelRequestId,
    val bindingId: ModelBindingId,
    val usage: AiUsage,
    val estimatedCost: EstimatedModelCost?,
) {
    init {
        if (!usage.complete) {
            require(estimatedCost == null) {
                "incomplete token usage cannot carry an estimated cost"
            }
        }
    }
}

fun interface AiTelemetrySink {
    fun record(record: AiUsageRecord)
}
