package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiUsage
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.TrustedBindingPricing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ModelCostEstimatorTest {
    @Test
    fun `cost estimation is binding-scoped and half-up in minor units`() {
        val pricing = TrustedBindingPricing(
            bindingId = ModelBindingId("binding-main"),
            currencyCode = "USD",
            inputPerMillionMinor = 100,
            outputPerMillionMinor = 300,
            source = "trusted-fixture",
            effectiveAtEpochMillis = 1,
        )

        val estimated = ModelCostEstimator.estimate(
            usage = AiUsage(
                inputTokens = 1_000_000,
                outputTokens = 500_000,
            ),
            pricing = pricing,
        )

        requireNotNull(estimated)
        assertEquals(250L, estimated.minorUnits)
        assertEquals("USD", estimated.currencyCode)
        assertEquals("trusted-fixture", estimated.pricingSource)
    }

    @Test
    fun `missing usage or pricing never invents cost`() {
        val pricing = TrustedBindingPricing(
            bindingId = ModelBindingId("binding-main"),
            currencyCode = "USD",
            inputPerMillionMinor = 100,
            outputPerMillionMinor = 300,
            source = "trusted-fixture",
            effectiveAtEpochMillis = 1,
        )

        assertNull(ModelCostEstimator.estimate(AiUsage(inputTokens = 10), pricing))
        assertNull(
            ModelCostEstimator.estimate(
                AiUsage(inputTokens = 10, outputTokens = 20),
                null,
            )
        )
    }
}
