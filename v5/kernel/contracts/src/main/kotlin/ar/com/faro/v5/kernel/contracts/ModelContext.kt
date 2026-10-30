package ar.com.faro.v5.kernel.contracts

@JvmInline
value class ContextFragmentId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "context fragment id must not be blank"
        }
    }
}

enum class ContextClass {
    SYSTEM_PROMPT,
    CURRENT_REQUEST,
    REALITY,
    WORKSPACE_CONTINUITY,
    EVIDENCE,
    TOOL_RESULT,
}

data class ContextFragment(
    val id: ContextFragmentId,
    val contextClass: ContextClass,
    val payload: CanonicalPayload,
    val estimatedTokens: Int,
    val priority: Int = 0,
    val required: Boolean = false,
    val egressSensitivity: EgressSensitivity = EgressSensitivity.UNCLASSIFIED,
) {
    init {
        require(estimatedTokens > 0) {
            "context fragment estimated tokens must be positive"
        }
    }
}

data class ContextBudget(
    val maxEstimatedTokens: Int,
    val maxBytes: Int,
    val maxFragments: Int,
) {
    init {
        require(maxEstimatedTokens > 0) {
            "context token budget must be positive"
        }
        require(maxBytes > 0) {
            "context byte budget must be positive"
        }
        require(maxFragments > 0) {
            "context fragment budget must be positive"
        }
    }
}

data class ContextPack(
    val fragments: List<ContextFragment>,
    val omittedFragmentIds: List<ContextFragmentId>,
) {
    init {
        val fragmentIds = fragments.map { it.id }
        require(fragmentIds.size == fragmentIds.distinct().size) {
            "context pack must not contain duplicate fragment ids"
        }
        require(fragmentIds.none { it in omittedFragmentIds }) {
            "context fragment cannot be both selected and omitted"
        }
        require(omittedFragmentIds.size == omittedFragmentIds.distinct().size) {
            "omitted context fragment ids must be unique"
        }
    }

    val estimatedTokens: Int = fragments.sumOf { it.estimatedTokens }
    val bytes: Int = fragments.sumOf { it.payload.size }
}

sealed interface ContextAssemblyResult {
    data class Ready(
        val pack: ContextPack,
    ) : ContextAssemblyResult

    data class RequiredContentTooLarge(
        val requiredFragmentIds: List<ContextFragmentId>,
    ) : ContextAssemblyResult
}

fun interface ContextAssembler {
    fun assemble(
        fragments: List<ContextFragment>,
        budget: ContextBudget,
    ): ContextAssemblyResult
}
