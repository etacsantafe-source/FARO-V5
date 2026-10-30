package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiTelemetryContractsTest {
    @Test
    fun `usage may be unknown but reported tokens cannot be negative`() {
        assertFalse(AiUsage().complete)
        assertTrue(AiUsage(inputTokens = 1, outputTokens = 2).complete)

        assertThrows(IllegalArgumentException::class.java) {
            AiUsage(inputTokens = -1)
        }
    }

    @Test
    fun `incomplete usage cannot claim an estimated cost`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiUsageRecord(
                requestId = ModelRequestId("request-1"),
                bindingId = ModelBindingId("binding-1"),
                usage = AiUsage(inputTokens = 10),
                estimatedCost = EstimatedModelCost(
                    minorUnits = 1,
                    currencyCode = "USD",
                    pricingSource = "fixture",
                    pricingEffectiveAtEpochMillis = 1,
                ),
            )
        }
    }
}
