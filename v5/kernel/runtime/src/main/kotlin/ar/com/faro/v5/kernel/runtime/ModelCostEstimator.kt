package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiUsage
import ar.com.faro.v5.kernel.contracts.EstimatedModelCost
import ar.com.faro.v5.kernel.contracts.TrustedBindingPricing
import java.math.BigInteger

object ModelCostEstimator {
    private val MILLION = BigInteger.valueOf(1_000_000L)
    private val HALF_MILLION = BigInteger.valueOf(500_000L)

    fun estimate(
        usage: AiUsage,
        pricing: TrustedBindingPricing?,
    ): EstimatedModelCost? {
        if (pricing == null || !usage.complete) return null

        val inputTokens = BigInteger.valueOf(requireNotNull(usage.inputTokens))
        val outputTokens = BigInteger.valueOf(requireNotNull(usage.outputTokens))
        val inputRate = BigInteger.valueOf(pricing.inputPerMillionMinor)
        val outputRate = BigInteger.valueOf(pricing.outputPerMillionMinor)

        val numerator = inputTokens.multiply(inputRate)
            .add(outputTokens.multiply(outputRate))

        val minorUnits = numerator
            .add(HALF_MILLION)
            .divide(MILLION)
            .longValueExact()

        return EstimatedModelCost(
            minorUnits = minorUnits,
            currencyCode = pricing.currencyCode,
            pricingSource = pricing.source,
            pricingEffectiveAtEpochMillis = pricing.effectiveAtEpochMillis,
        )
    }
}
