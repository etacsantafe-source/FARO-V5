package ar.com.faro.v5.control

import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.kernel.contracts.AiMonetaryBudget
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelProfileRef

data class AiBindingCredentialRef(
    val bindingId: ModelBindingId,
    val secretId: SecretId,
)

data class AiControlSnapshot(
    val remoteReasoningEnabled: Boolean,
    val activeProfile: ModelProfileRef,
    val budget: AiMonetaryBudget,
    val credentialRefs: List<AiBindingCredentialRef>,
    val revision: Long,
) {
    init {
        require(revision >= 0L) {
            "AI control revision must be non-negative"
        }
        val bindingIds = credentialRefs.map { it.bindingId }
        require(bindingIds.size == bindingIds.distinct().size) {
            "AI credential refs must have unique binding ids"
        }
    }

    fun credentialRefFor(bindingId: ModelBindingId): SecretId? =
        credentialRefs.firstOrNull { it.bindingId == bindingId }?.secretId
}
