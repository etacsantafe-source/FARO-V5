package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.AiFailure
import ar.com.faro.v5.kernel.contracts.AiFailureKind
import ar.com.faro.v5.kernel.contracts.AiRouteFailureAction
import ar.com.faro.v5.kernel.contracts.AiRouteFailureDecision
import ar.com.faro.v5.kernel.contracts.RetryDisposition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConservativeAiRouteFailurePolicyTest {
    @Test
    fun `rate limit uses next binding instead of immediate retry`() {
        val decision = decide(AiFailureKind.RATE_LIMIT, true, true, 60_000)
        assertEquals(AiRouteFailureAction.TRY_NEXT_BINDING, decision.action)
        assertEquals(null, decision.retryAfterMillis)
    }

    @Test
    fun `rate limit without next binding defers with retry after`() {
        val decision = decide(AiFailureKind.RATE_LIMIT, false, true, 60_000)
        assertEquals(AiRouteFailureAction.DEFER, decision.action)
        assertEquals(60_000L, decision.retryAfterMillis)
    }

    @Test
    fun `timeout retries same binding only when bounded retry is allowed`() {
        assertEquals(
            AiRouteFailureAction.RETRY_SAME_BINDING,
            decide(AiFailureKind.TIMEOUT, true, true).action,
        )
        assertEquals(
            AiRouteFailureAction.TRY_NEXT_BINDING,
            decide(AiFailureKind.TIMEOUT, true, false).action,
        )
    }

    @Test
    fun `offline prefers next binding and otherwise defers when retryable`() {
        assertEquals(
            AiRouteFailureAction.TRY_NEXT_BINDING,
            decide(AiFailureKind.OFFLINE, true, true).action,
        )
        assertEquals(
            AiRouteFailureAction.DEFER,
            decide(AiFailureKind.OFFLINE, false, true).action,
        )
    }

    @Test
    fun `terminal cancelled and invalid response stop`() {
        listOf(
            AiFailureKind.TERMINAL_REQUEST_ERROR,
            AiFailureKind.CANCELLED,
            AiFailureKind.INVALID_STRUCTURED_RESPONSE,
        ).forEach { kind ->
            assertEquals(AiRouteFailureAction.STOP, decide(kind, true, true).action)
        }
    }

    @Test
    fun `context overflow may move to another binding but never blind retry`() {
        assertEquals(
            AiRouteFailureAction.TRY_NEXT_BINDING,
            decide(AiFailureKind.CONTEXT_TOO_LARGE, true, true).action,
        )
        assertEquals(
            AiRouteFailureAction.STOP,
            decide(AiFailureKind.CONTEXT_TOO_LARGE, false, true).action,
        )
    }

    @Test
    fun `retry after is only valid for deferred decision`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiRouteFailureDecision(
                action = AiRouteFailureAction.TRY_NEXT_BINDING,
                retryAfterMillis = 1,
            )
        }
    }

    private fun decide(
        kind: AiFailureKind,
        hasNext: Boolean,
        retryAllowed: Boolean,
        retryAfterMillis: Long? = null,
    ) = ConservativeAiRouteFailurePolicy.decide(
        failure = AiFailure(
            kind = kind,
            retryDisposition = RetryDisposition.RETRYABLE,
            retryAfterMillis = retryAfterMillis,
        ),
        hasNextBinding = hasNext,
        sameBindingRetryAllowed = retryAllowed,
    )
}
