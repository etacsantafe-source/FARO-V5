package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ContextPack
import ar.com.faro.v5.kernel.contracts.DataEgressDecision
import ar.com.faro.v5.kernel.contracts.DataEgressPolicy
import ar.com.faro.v5.kernel.contracts.DataEgressPolicyConfig
import ar.com.faro.v5.kernel.contracts.DataEgressTarget

class AllowlistDataEgressPolicy(
    private val config: DataEgressPolicyConfig,
) : DataEgressPolicy {
    override fun evaluate(
        pack: ContextPack,
        target: DataEgressTarget,
    ): DataEgressDecision {
        val allowed = config.allowedFor(target)
        val blockedIds = pack.fragments
            .asSequence()
            .filter { it.egressSensitivity !in allowed }
            .map { it.id }
            .distinct()
            .sortedBy { it.value }
            .toList()

        return if (blockedIds.isEmpty()) {
            DataEgressDecision.Allowed
        } else {
            DataEgressDecision.Blocked(blockedIds)
        }
    }
}
