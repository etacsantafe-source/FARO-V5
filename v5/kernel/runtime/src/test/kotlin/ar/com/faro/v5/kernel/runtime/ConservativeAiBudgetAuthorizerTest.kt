package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiBudgetBlockReason
import ar.com.faro.v5.kernel.contracts.AiBudgetDecision
import ar.com.faro.v5.kernel.contracts.AiMonetaryBudget
import ar.com.faro.v5.kernel.contracts.DataEgressTarget
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelProfileBinding
import ar.com.faro.v5.kernel.contracts.ProjectedModelUsage
import ar.com.faro.v5.kernel.contracts.TrustedBindingPricing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConservativeAiBudgetAuthorizerTest {
    @Test
    fun `capped remote budget requires trusted pricing`() {
        val decision = ConservativeAiBudgetAuthorizer.authorize(
            binding = remote("remote"),
            projectedUsage = usage(),
            pricing = null,
            budget = AiMonetaryBudget.Capped(
                currencyCode = "USD",
                remainingMinor = 100,
            ),
        )

        val blocked = assertInstanceOf(
            AiBudgetDecision.Blocked::class.java,
            decision,
        )
        assertEquals(
            AiBudgetBlockReason.TRUSTED_PRICING_REQUIRED,
            blocked.reason,
        )
    }

    @Test
    fun `capped remote budget blocks currency mismatch and overspend`() {
        val mismatch = ConservativeAiBudgetAuthorizer.authorize(
            binding = remote("remote"),
            projectedUsage = usage(),
            pricing = pricing("remote", "EUR", 100, 300),
            budget = AiMonetaryBudget.Capped(
                currencyCode = "USD",
                remainingMinor = 1_000,
            ),
        )
        assertEquals(
            AiBudgetBlockReason.CURRENCY_MISMATCH,
            (mismatch as AiBudgetDecision.Blocked).reason,
        )

        val overspend = ConservativeAiBudgetAuthorizer.authorize(
            binding = remote("remote"),
            projectedUsage = usage(),
            pricing = pricing("remote", "USD", 100, 300),
            budget = AiMonetaryBudget.Capped(
                currencyCode = "USD",
                remainingMinor = 249,
            ),
        )
        assertEquals(
            AiBudgetBlockReason.BUDGET_EXCEEDED,
            (overspend as AiBudgetDecision.Blocked).reason,
        )
        assertEquals(250L, overspend.projectedCost?.minorUnits)
    }

    @Test
    fun `remote route is allowed when projected ceiling fits remaining budget`() {
        val decision = ConservativeAiBudgetAuthorizer.authorize(
            binding = remote("remote"),
            projectedUsage = usage(),
            pricing = pricing("remote", "USD", 100, 300),
            budget = AiMonetaryBudget.Capped(
                currencyCode = "USD",
                remainingMinor = 250,
            ),
        )

        val allowed = assertInstanceOf(
            AiBudgetDecision.Allowed::class.java,
            decision,
        )
        assertEquals(250L, allowed.projectedCost?.minorUnits)
    }

    @Test
    fun `on-device route does not consume monetary remote budget`() {
        val decision = ConservativeAiBudgetAuthorizer.authorize(
            binding = ModelProfileBinding(
                bindingId = ModelBindingId("local"),
                target = DataEgressTarget.ON_DEVICE_MODEL,
            ),
            projectedUsage = usage(),
            pricing = null,
            budget = AiMonetaryBudget.Capped(
                currencyCode = "USD",
                remainingMinor = 0,
            ),
        )

        val allowed = assertInstanceOf(
            AiBudgetDecision.Allowed::class.java,
            decision,
        )
        assertEquals(null, allowed.projectedCost)
    }

    @Test
    fun `pricing for another binding fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            ConservativeAiBudgetAuthorizer.authorize(
                binding = remote("remote"),
                projectedUsage = usage(),
                pricing = pricing("other", "USD", 100, 300),
                budget = AiMonetaryBudget.Disabled,
            )
        }
    }

    private fun remote(id: String): ModelProfileBinding =
        ModelProfileBinding(
            bindingId = ModelBindingId(id),
            target = DataEgressTarget.REMOTE_MODEL,
        )

    private fun usage(): ProjectedModelUsage =
        ProjectedModelUsage(
            inputTokens = 1_000_000,
            maxOutputTokens = 500_000,
        )

    private fun pricing(
        id: String,
        currency: String,
        input: Long,
        output: Long,
    ): TrustedBindingPricing =
        TrustedBindingPricing(
            bindingId = ModelBindingId(id),
            currencyCode = currency,
            inputPerMillionMinor = input,
            outputPerMillionMinor = output,
            source = "trusted-fixture",
            effectiveAtEpochMillis = 1,
        )
}
