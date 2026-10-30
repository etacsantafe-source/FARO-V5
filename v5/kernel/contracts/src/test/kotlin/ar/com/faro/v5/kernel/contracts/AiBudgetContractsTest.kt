package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AiBudgetContractsTest {
    @Test
    fun `capped budget requires valid currency and non-negative remainder`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiMonetaryBudget.Capped(
                currencyCode = "usd",
                remainingMinor = 1,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiMonetaryBudget.Capped(
                currencyCode = "USD",
                remainingMinor = -1,
            )
        }
    }

    @Test
    fun `projected usage cannot contain negative tokens`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectedModelUsage(
                inputTokens = -1,
                maxOutputTokens = 10,
            )
        }
    }
}
