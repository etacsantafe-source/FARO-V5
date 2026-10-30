package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiBudgetBlockReason
import ar.com.faro.v5.kernel.contracts.AiMonetaryBudget
import ar.com.faro.v5.kernel.contracts.DataEgressTarget
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelProfileBinding
import ar.com.faro.v5.kernel.contracts.ModelRoutePlan
import ar.com.faro.v5.kernel.contracts.ProjectedModelUsage
import ar.com.faro.v5.kernel.contracts.TrustedBindingPricing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DeterministicAiBudgetRoutePlannerTest {
    @Test
    fun `budget filtering preserves original route order`() {
        val expensive = binding("remote-expensive")
        val cheap = binding("remote-cheap")
        val plan = routePlan(expensive, cheap)

        val result = DeterministicAiBudgetRoutePlanner.plan(
            routePlan = plan,
            projectedUsage = ProjectedModelUsage(
                inputTokens = 1_000_000,
                maxOutputTokens = 0,
            ),
            pricing = listOf(
                pricing("remote-expensive", inputPerMillionMinor = 200),
                pricing("remote-cheap", inputPerMillionMinor = 50),
            ),
            budget = AiMonetaryBudget.Capped(
                currencyCode = "USD",
                remainingMinor = 100,
            ),
            authorizer = ConservativeAiBudgetAuthorizer,
        )

        assertEquals(listOf(cheap), result.authorizedBindings)
        assertEquals(1, result.budgetRejections.size)
        assertEquals(
            ModelBindingId("remote-expensive"),
            result.budgetRejections.single().bindingId,
        )
        assertEquals(
            AiBudgetBlockReason.BUDGET_EXCEEDED,
            result.budgetRejections.single().reason,
        )
    }

    @Test
    fun `missing trusted pricing blocks capped remote route`() {
        val remote = binding("remote")

        val result = DeterministicAiBudgetRoutePlanner.plan(
            routePlan = routePlan(remote),
            projectedUsage = ProjectedModelUsage(100, 100),
            pricing = emptyList(),
            budget = AiMonetaryBudget.Capped("USD", 100),
            authorizer = ConservativeAiBudgetAuthorizer,
        )

        assertEquals(emptyList<ModelProfileBinding>(), result.authorizedBindings)
        assertEquals(
            AiBudgetBlockReason.TRUSTED_PRICING_REQUIRED,
            result.budgetRejections.single().reason,
        )
    }

    @Test
    fun `on-device route stays authorized without pricing under capped budget`() {
        val local = ModelProfileBinding(
            bindingId = ModelBindingId("local"),
            target = DataEgressTarget.ON_DEVICE_MODEL,
        )

        val result = DeterministicAiBudgetRoutePlanner.plan(
            routePlan = routePlan(local),
            projectedUsage = ProjectedModelUsage(10_000, 10_000),
            pricing = emptyList(),
            budget = AiMonetaryBudget.Capped("USD", 0),
            authorizer = ConservativeAiBudgetAuthorizer,
        )

        assertEquals(listOf(local), result.authorizedBindings)
        assertEquals(0, result.budgetRejections.size)
    }

    @Test
    fun `duplicate trusted pricing fails closed`() {
        val remote = binding("remote")
        val duplicate = pricing("remote", 10)

        assertThrows(IllegalArgumentException::class.java) {
            DeterministicAiBudgetRoutePlanner.plan(
                routePlan = routePlan(remote),
                projectedUsage = ProjectedModelUsage(1, 1),
                pricing = listOf(duplicate, duplicate),
                budget = AiMonetaryBudget.Disabled,
                authorizer = ConservativeAiBudgetAuthorizer,
            )
        }
    }

    private fun routePlan(vararg bindings: ModelProfileBinding): ModelRoutePlan =
        ModelRoutePlan(
            eligibleBindings = bindings.toList(),
            rejections = emptyList(),
        )

    private fun binding(id: String): ModelProfileBinding =
        ModelProfileBinding(
            bindingId = ModelBindingId(id),
            target = DataEgressTarget.REMOTE_MODEL,
        )

    private fun pricing(
        id: String,
        inputPerMillionMinor: Long,
    ): TrustedBindingPricing =
        TrustedBindingPricing(
            bindingId = ModelBindingId(id),
            currencyCode = "USD",
            inputPerMillionMinor = inputPerMillionMinor,
            outputPerMillionMinor = 0,
            source = "test",
            effectiveAtEpochMillis = 1,
        )
}
