package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ApplyPlan
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.kernel.contracts.PlanFingerprint

object PlanFingerprinter {
    fun fingerprint(plan: ApplyPlan): PlanFingerprint {
        val hash = CanonicalHashBuilder.domain("apply-plan")
            .string("nodeCount", plan.topologicalOrder.size.toString())

        plan.nodesInTopologicalOrder().forEachIndexed { index, node ->
            val prepared = node.preparedOperation
            val contract = prepared.contract
            val prefix = "node[" + index + "]"

            hash
                .string(prefix + ".nodeId", node.nodeId.value)
                .string(prefix + ".operationId", contract.operationId.value)
                .string(prefix + ".capabilityId", contract.capabilityId.value)
                .string(prefix + ".executor", contract.canonicalExecutor.value)
                .string(prefix + ".profile", contract.executionProfile.name)
                .string(prefix + ".contractVersion", contract.contractVersion.value)
                .string(prefix + ".contractFingerprint", contract.contractFingerprint.value)
                .string(prefix + ".payloadFingerprint", prepared.payloadFingerprint.value)
                .string(prefix + ".idempotencyKey", prepared.idempotencyKey.value)

            val stamps = prepared.dependencyStamps.asCanonicalList()
            hash.string(prefix + ".stampCount", stamps.size.toString())
            stamps.forEachIndexed { stampIndex, stamp ->
                val stampPrefix = prefix + ".stamp[" + stampIndex + "]"
                hash
                    .string(stampPrefix + ".authority", stamp.authorityBoundary.value)
                    .string(stampPrefix + ".key", stamp.dependencyKey.value)
                    .string(stampPrefix + ".value", stamp.observedValue.value)
            }

            val dependencies = node.dependsOn.sortedBy { it.value }
            hash.string(prefix + ".dependencyCount", dependencies.size.toString())
            dependencies.forEachIndexed { dependencyIndex, dependency ->
                hash.string(
                    prefix + ".dependency[" + dependencyIndex + "]",
                    dependency.value,
                )
            }
        }

        return PlanFingerprint(hash.sha256Hex())
    }
}
