package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ModelContextTest {
    @Test
    fun `context budget must remain strictly positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContextBudget(
                maxEstimatedTokens = 0,
                maxBytes = 128,
                maxFragments = 1,
            )
        }
    }

    @Test
    fun `context fragment token estimate must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContextFragment(
                id = ContextFragmentId("request"),
                contextClass = ContextClass.CURRENT_REQUEST,
                payload = payload("request"),
                estimatedTokens = 0,
            )
        }
    }

    @Test
    fun `context pack rejects duplicate selected identities`() {
        val fragment = ContextFragment(
            id = ContextFragmentId("request"),
            contextClass = ContextClass.CURRENT_REQUEST,
            payload = payload("request"),
            estimatedTokens = 8,
        )

        assertThrows(IllegalArgumentException::class.java) {
            ContextPack(
                fragments = listOf(fragment, fragment),
                omittedFragmentIds = emptyList(),
            )
        }
    }

    private fun payload(value: String): CanonicalPayload =
        CanonicalPayload.fromEncoded(
            formatId = "text/plain",
            bytes = value.encodeToByteArray(),
        )
}
