package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelBindingReasoningPort
import ar.com.faro.v5.kernel.contracts.ModelBindingRegistry

class ImmutableModelBindingRegistry(
    bindings: List<ModelBindingReasoningPort>,
) : ModelBindingRegistry {
    private val byId: Map<ModelBindingId, ModelBindingReasoningPort>

    init {
        val ids = bindings.map { it.bindingId }
        require(ids.size == ids.distinct().size) {
            "model binding registry ids must be unique"
        }
        byId = bindings.associateBy { it.bindingId }.toMap()
    }

    override fun resolve(bindingId: ModelBindingId): ModelBindingReasoningPort? =
        byId[bindingId]

    override fun bindingIds(): List<ModelBindingId> =
        byId.keys.sortedBy { it.value }
}
