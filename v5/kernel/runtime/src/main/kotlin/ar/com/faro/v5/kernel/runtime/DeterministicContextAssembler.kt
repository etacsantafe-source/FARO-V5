package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ContextAssembler
import ar.com.faro.v5.kernel.contracts.ContextAssemblyResult
import ar.com.faro.v5.kernel.contracts.ContextBudget
import ar.com.faro.v5.kernel.contracts.ContextFragment
import ar.com.faro.v5.kernel.contracts.ContextPack

class DeterministicContextAssembler : ContextAssembler {
    override fun assemble(
        fragments: List<ContextFragment>,
        budget: ContextBudget,
    ): ContextAssemblyResult {
        val ids = fragments.map { it.id }
        require(ids.size == ids.distinct().size) {
            "context assembler requires unique fragment ids"
        }

        val ordered = fragments.sortedWith(
            compareByDescending<ContextFragment> { it.required }
                .thenByDescending { it.priority }
                .thenBy { it.id.value }
        )

        val required = ordered.filter { it.required }
        if (!fits(required, budget)) {
            return ContextAssemblyResult.RequiredContentTooLarge(
                requiredFragmentIds = required.map { it.id },
            )
        }

        val selected = required.toMutableList()
        val omitted = mutableListOf<ar.com.faro.v5.kernel.contracts.ContextFragmentId>()

        ordered.asSequence()
            .filterNot { it.required }
            .forEach { candidate ->
                val next = selected + candidate
                if (fits(next, budget)) {
                    selected += candidate
                } else {
                    omitted += candidate.id
                }
            }

        return ContextAssemblyResult.Ready(
            ContextPack(
                fragments = selected.toList(),
                omittedFragmentIds = omitted.toList(),
            )
        )
    }

    private fun fits(
        fragments: List<ContextFragment>,
        budget: ContextBudget,
    ): Boolean =
        fragments.size <= budget.maxFragments &&
            fragments.sumOf { it.estimatedTokens } <= budget.maxEstimatedTokens &&
            fragments.sumOf { it.payload.size } <= budget.maxBytes
}
