package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.ContextAssemblyResult
import ar.com.faro.v5.kernel.contracts.ContextBudget
import ar.com.faro.v5.kernel.contracts.ContextClass
import ar.com.faro.v5.kernel.contracts.ContextFragment
import ar.com.faro.v5.kernel.contracts.ContextFragmentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class DeterministicContextAssemblerTest {
    private val assembler = DeterministicContextAssembler()

    @Test
    fun `required context is preserved and optional context is bounded by explicit priority`() {
        val required = fragment(
            id = "request",
            text = "REQ",
            tokens = 4,
            priority = 0,
            required = true,
        )
        val high = fragment(
            id = "reality",
            text = "REALITY",
            tokens = 5,
            priority = 20,
        )
        val low = fragment(
            id = "history",
            text = "HISTORY",
            tokens = 5,
            priority = 10,
        )

        val result = assembler.assemble(
            fragments = listOf(low, required, high),
            budget = ContextBudget(
                maxEstimatedTokens = 9,
                maxBytes = 32,
                maxFragments = 2,
            ),
        )

        val ready = assertInstanceOf(ContextAssemblyResult.Ready::class.java, result)
        assertEquals(
            listOf(ContextFragmentId("request"), ContextFragmentId("reality")),
            ready.pack.fragments.map { it.id },
        )
        assertEquals(
            listOf(ContextFragmentId("history")),
            ready.pack.omittedFragmentIds,
        )
    }

    @Test
    fun `assembly is deterministic when input order changes`() {
        val a = fragment("a", "A", tokens = 3, priority = 10)
        val b = fragment("b", "B", tokens = 3, priority = 10)
        val budget = ContextBudget(
            maxEstimatedTokens = 3,
            maxBytes = 8,
            maxFragments = 1,
        )

        val first = assembler.assemble(listOf(b, a), budget)
        val second = assembler.assemble(listOf(a, b), budget)

        val firstReady = assertInstanceOf(ContextAssemblyResult.Ready::class.java, first)
        val secondReady = assertInstanceOf(ContextAssemblyResult.Ready::class.java, second)

        assertEquals(
            firstReady.pack.fragments.map { it.id },
            secondReady.pack.fragments.map { it.id },
        )
        assertEquals(
            listOf(ContextFragmentId("a")),
            firstReady.pack.fragments.map { it.id },
        )
    }

    @Test
    fun `required content overflow is explicit and never silently truncated`() {
        val required = fragment(
            id = "request",
            text = "required",
            tokens = 12,
            priority = 0,
            required = true,
        )

        val result = assembler.assemble(
            fragments = listOf(required),
            budget = ContextBudget(
                maxEstimatedTokens = 8,
                maxBytes = 64,
                maxFragments = 1,
            ),
        )

        val tooLarge = assertInstanceOf(
            ContextAssemblyResult.RequiredContentTooLarge::class.java,
            result,
        )
        assertEquals(
            listOf(ContextFragmentId("request")),
            tooLarge.requiredFragmentIds,
        )
    }

    private fun fragment(
        id: String,
        text: String,
        tokens: Int,
        priority: Int,
        required: Boolean = false,
    ): ContextFragment =
        ContextFragment(
            id = ContextFragmentId(id),
            contextClass = if (required) {
                ContextClass.CURRENT_REQUEST
            } else {
                ContextClass.WORKSPACE_CONTINUITY
            },
            payload = CanonicalPayload.fromEncoded(
                formatId = "text/plain",
                bytes = text.encodeToByteArray(),
            ),
            estimatedTokens = tokens,
            priority = priority,
            required = required,
        )
}
