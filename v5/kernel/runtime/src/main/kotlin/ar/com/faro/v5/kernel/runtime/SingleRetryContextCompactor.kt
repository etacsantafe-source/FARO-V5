package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ContextAssembler
import ar.com.faro.v5.kernel.contracts.ContextAssemblyResult
import ar.com.faro.v5.kernel.contracts.ContextCompactionRequest
import ar.com.faro.v5.kernel.contracts.ContextCompactionResult
import ar.com.faro.v5.kernel.contracts.ContextCompactor
import ar.com.faro.v5.kernel.contracts.ContextPack

object SingleRetryContextCompactor : ContextCompactor {
    override fun compact(
        request: ContextCompactionRequest,
        assembler: ContextAssembler,
    ): ContextCompactionResult {
        if (request.compactionAlreadyAttempted) {
            return ContextCompactionResult.RetryLimitReached
        }

        return when (
            val assembled = assembler.assemble(
                fragments = request.fragments,
                budget = request.retryBudget,
            )
        ) {
            is ContextAssemblyResult.RequiredContentTooLarge ->
                ContextCompactionResult.RequiredContentTooLarge(
                    requiredFragmentIds = assembled.requiredFragmentIds,
                )

            is ContextAssemblyResult.Ready ->
                if (isStrictlySmaller(assembled.pack, request.previousPack)) {
                    ContextCompactionResult.Compacted(assembled.pack)
                } else {
                    ContextCompactionResult.NoReduction
                }
        }
    }

    private fun isStrictlySmaller(
        next: ContextPack,
        previous: ContextPack,
    ): Boolean =
        next.fragments.size < previous.fragments.size ||
            next.estimatedTokens < previous.estimatedTokens ||
            next.bytes < previous.bytes
}
