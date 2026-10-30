package ar.com.faro.v5.kernel.contracts

data class ContextCompactionRequest(
    val fragments: List<ContextFragment>,
    val previousPack: ContextPack,
    val previousBudget: ContextBudget,
    val retryBudget: ContextBudget,
    val compactionAlreadyAttempted: Boolean,
) {
    init {
        require(retryBudget.maxEstimatedTokens <= previousBudget.maxEstimatedTokens) {
            "retry context token budget cannot expand"
        }
        require(retryBudget.maxBytes <= previousBudget.maxBytes) {
            "retry context byte budget cannot expand"
        }
        require(retryBudget.maxFragments <= previousBudget.maxFragments) {
            "retry context fragment budget cannot expand"
        }
        require(
            retryBudget.maxEstimatedTokens < previousBudget.maxEstimatedTokens ||
                retryBudget.maxBytes < previousBudget.maxBytes ||
                retryBudget.maxFragments < previousBudget.maxFragments
        ) {
            "retry context budget must be strictly smaller"
        }
    }
}

sealed interface ContextCompactionResult {
    data class Compacted(
        val pack: ContextPack,
    ) : ContextCompactionResult

    data class RequiredContentTooLarge(
        val requiredFragmentIds: List<ContextFragmentId>,
    ) : ContextCompactionResult

    data object RetryLimitReached : ContextCompactionResult

    data object NoReduction : ContextCompactionResult
}

fun interface ContextCompactor {
    fun compact(
        request: ContextCompactionRequest,
        assembler: ContextAssembler,
    ): ContextCompactionResult
}
