package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ContextBudget
import ar.com.faro.v5.kernel.contracts.ContextClass
import ar.com.faro.v5.kernel.contracts.ContextCompactionRequest
import ar.com.faro.v5.kernel.contracts.ContextCompactionResult
import ar.com.faro.v5.kernel.contracts.ContextFragment
import ar.com.faro.v5.kernel.contracts.ContextFragmentId
import ar.com.faro.v5.kernel.contracts.ContextPack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SingleRetryContextCompactorTest {
    private val assembler = DeterministicContextAssembler()

    @Test
    fun `smaller retry budget deterministically removes lower-priority optional context`() {
        val required = fragment("required", tokens = 40, priority = 100, required = true)
        val high = fragment("high", tokens = 30, priority = 10)
        val low = fragment("low", tokens = 30, priority = 1)
        val previous = ContextPack(
            fragments = listOf(required, high, low),
            omittedFragmentIds = emptyList(),
        )

        val result = SingleRetryContextCompactor.compact(
            request = ContextCompactionRequest(
                fragments = listOf(low, required, high),
                previousPack = previous,
                previousBudget = ContextBudget(
                    maxEstimatedTokens = 100,
                    maxBytes = 10_000,
                    maxFragments = 3,
                ),
                retryBudget = ContextBudget(
                    maxEstimatedTokens = 70,
                    maxBytes = 10_000,
                    maxFragments = 3,
                ),
                compactionAlreadyAttempted = false,
            ),
            assembler = assembler,
        )

        val compacted = assertInstanceOf(
            ContextCompactionResult.Compacted::class.java,
            result,
        )
        assertEquals(
            listOf(ContextFragmentId("required"), ContextFragmentId("high")),
            compacted.pack.fragments.map { it.id },
        )
        assertEquals(
            listOf(ContextFragmentId("low")),
            compacted.pack.omittedFragmentIds,
        )
    }

    @Test
    fun `required content that cannot fit is explicit instead of silently dropped`() {
        val required = fragment("required", tokens = 80, priority = 1, required = true)
        val previous = ContextPack(
            fragments = listOf(required),
            omittedFragmentIds = emptyList(),
        )

        val result = SingleRetryContextCompactor.compact(
            request = ContextCompactionRequest(
                fragments = listOf(required),
                previousPack = previous,
                previousBudget = ContextBudget(100, 10_000, 2),
                retryBudget = ContextBudget(70, 10_000, 2),
                compactionAlreadyAttempted = false,
            ),
            assembler = assembler,
        )

        val tooLarge = assertInstanceOf(
            ContextCompactionResult.RequiredContentTooLarge::class.java,
            result,
        )
        assertEquals(listOf(ContextFragmentId("required")), tooLarge.requiredFragmentIds)
    }

    @Test
    fun `second compaction in same route attempt is blocked`() {
        val required = fragment("required", 20, required = true)
        val previous = ContextPack(listOf(required), emptyList())

        val result = SingleRetryContextCompactor.compact(
            request = ContextCompactionRequest(
                fragments = listOf(required),
                previousPack = previous,
                previousBudget = ContextBudget(100, 10_000, 2),
                retryBudget = ContextBudget(90, 10_000, 2),
                compactionAlreadyAttempted = true,
            ),
            assembler = assembler,
        )

        assertEquals(ContextCompactionResult.RetryLimitReached, result)
    }

    @Test
    fun `retry budget that does not actually reduce selected context does not authorize retry`() {
        val required = fragment("required", 20, required = true)
        val previous = ContextPack(listOf(required), emptyList())

        val result = SingleRetryContextCompactor.compact(
            request = ContextCompactionRequest(
                fragments = listOf(required),
                previousPack = previous,
                previousBudget = ContextBudget(100, 10_000, 2),
                retryBudget = ContextBudget(90, 10_000, 2),
                compactionAlreadyAttempted = false,
            ),
            assembler = assembler,
        )

        assertEquals(ContextCompactionResult.NoReduction, result)
    }

    @Test
    fun `retry budget cannot expand any dimension`() {
        val required = fragment("required", 20, required = true)
        val previous = ContextPack(listOf(required), emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            ContextCompactionRequest(
                fragments = listOf(required),
                previousPack = previous,
                previousBudget = ContextBudget(100, 100, 2),
                retryBudget = ContextBudget(90, 101, 2),
                compactionAlreadyAttempted = false,
            )
        }
    }

    private fun fragment(
        id: String,
        tokens: Int,
        priority: Int = 0,
        required: Boolean = false,
    ): ContextFragment =
        ContextFragment(
            id = ContextFragmentId(id),
            contextClass = ContextClass.REALITY,
            payload = CanonicalPayload.fromEncoded(
                formatId = "application/test",
                bytes = id.toByteArray(),
            ),
            estimatedTokens = tokens,
            priority = priority,
            required = required,
        )
}
