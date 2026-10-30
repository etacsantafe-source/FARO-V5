package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExecutionOutcomesTest {
    @Test
    fun `execution outcome states are the frozen logical set`() {
        assertEquals(
            setOf(
                ExecutionOutcomeState.NOT_STARTED,
                ExecutionOutcomeState.APPLYING,
                ExecutionOutcomeState.RECOVERING_UNKNOWN,
                ExecutionOutcomeState.REJECTED,
                ExecutionOutcomeState.COMMITTED,
                ExecutionOutcomeState.PARTIAL_COMPENSATION_REQUIRED,
            ),
            ExecutionOutcomeState.entries.toSet(),
        )
    }

    @Test
    fun `retry dispositions are the frozen set`() {
        assertEquals(
            setOf(
                RetryDisposition.RETRYABLE,
                RetryDisposition.NOT_RETRYABLE,
                RetryDisposition.REQUIRES_REPREPARE,
                RetryDisposition.REQUIRES_HUMAN_ACTION,
            ),
            RetryDisposition.entries.toSet(),
        )
    }

    @Test
    fun `effect certainty is independent from retryability`() {
        assertEquals(
            setOf(
                EffectCertainty.NO_EFFECT_KNOWN,
                EffectCertainty.EFFECT_COMMITTED,
                EffectCertainty.EFFECT_REJECTED,
                EffectCertainty.EFFECT_UNKNOWN_RECOVERING,
                EffectCertainty.PARTIAL_EFFECT,
            ),
            EffectCertainty.entries.toSet(),
        )
    }

    @Test
    fun `execution outcome keeps state retryability and effect certainty independent`() {
        val outcome = ExecutionOutcome(
            state = ExecutionOutcomeState.RECOVERING_UNKNOWN,
            retryDisposition = RetryDisposition.RETRYABLE,
            effectCertainty = EffectCertainty.EFFECT_UNKNOWN_RECOVERING,
        )

        assertEquals(ExecutionOutcomeState.RECOVERING_UNKNOWN, outcome.state)
        assertEquals(RetryDisposition.RETRYABLE, outcome.retryDisposition)
        assertEquals(EffectCertainty.EFFECT_UNKNOWN_RECOVERING, outcome.effectCertainty)
    }

    @Test
    fun `same logical state can carry different retry disposition without hidden inference`() {
        val retryable = ExecutionOutcome(
            state = ExecutionOutcomeState.RECOVERING_UNKNOWN,
            retryDisposition = RetryDisposition.RETRYABLE,
            effectCertainty = EffectCertainty.EFFECT_UNKNOWN_RECOVERING,
        )
        val humanAction = retryable.copy(
            retryDisposition = RetryDisposition.REQUIRES_HUMAN_ACTION,
        )

        assertEquals(retryable.state, humanAction.state)
        assertEquals(retryable.effectCertainty, humanAction.effectCertainty)
        assertEquals(RetryDisposition.REQUIRES_HUMAN_ACTION, humanAction.retryDisposition)
    }

}
