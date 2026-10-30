package ar.com.faro.v5.kernel.contracts

@JvmInline
value class ModelProfileVersion(val value: Int) {
    init {
        require(value > 0) {
            "model profile version must be positive"
        }
    }
}

data class ModelProfileRef(
    val profileId: ModelProfileId,
    val version: ModelProfileVersion,
)

data class ModelProfileBinding(
    val bindingId: ModelBindingId,
    val target: DataEgressTarget,
)

data class ModelProfile(
    val ref: ModelProfileRef,
    val orderedBindings: List<ModelProfileBinding>,
) {
    init {
        require(orderedBindings.isNotEmpty()) {
            "model profile must contain at least one binding"
        }
        val ids = orderedBindings.map { it.bindingId }
        require(ids.size == ids.distinct().size) {
            "model profile binding ids must be unique"
        }
    }
}

interface ModelProfileRegistry {
    fun resolve(ref: ModelProfileRef): ModelProfile?
    fun versions(profileId: ModelProfileId): List<ModelProfileVersion>
}
