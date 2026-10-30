package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.OperationBindingPort
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.PlanNodeId
import ar.com.faro.v5.kernel.contracts.PreparedPlanView

enum class ApplyBindingRejection {
    BINDING_UNAVAILABLE,
    BINDING_EXECUTOR_MISMATCH,
    BINDING_PROFILE_MISMATCH,
}

sealed interface ApplyBindingResolution {
    data class Resolved(
        val bindingsInPlanOrder: List<ResolvedApplyBinding>,
    ) : ApplyBindingResolution

    data class Rejected(
        val reason: ApplyBindingRejection,
        val nodeId: PlanNodeId,
    ) : ApplyBindingResolution
}

data class ResolvedApplyBinding(
    val nodeId: PlanNodeId,
    val binding: OperationBindingPort,
)

class ApplyBindingResolutionGate(
    operationBindings: Iterable<OperationBindingPort>,
) {
    private val bindingsByOperation: Map<OperationId, OperationBindingPort> =
        LinkedHashMap<OperationId, OperationBindingPort>().apply {
            operationBindings.forEach { binding ->
                require(putIfAbsent(binding.descriptor.operationId, binding) == null) {
                    "Duplicate Apply operation binding: ${binding.descriptor.operationId.value}"
                }
            }
        }.toMap()

    fun resolve(preparedPlan: PreparedPlanView): ApplyBindingResolution {
        val resolved = ArrayList<ResolvedApplyBinding>()

        preparedPlan.applyPlan.nodesInTopologicalOrder().forEach { node ->
            val contract = node.preparedOperation.contract
            val binding = bindingsByOperation[contract.operationId]
                ?: return ApplyBindingResolution.Rejected(
                    reason = ApplyBindingRejection.BINDING_UNAVAILABLE,
                    nodeId = node.nodeId,
                )

            if (binding.descriptor.canonicalExecutor != contract.canonicalExecutor) {
                return ApplyBindingResolution.Rejected(
                    reason = ApplyBindingRejection.BINDING_EXECUTOR_MISMATCH,
                    nodeId = node.nodeId,
                )
            }

            if (binding.descriptor.executionProfile != contract.executionProfile) {
                return ApplyBindingResolution.Rejected(
                    reason = ApplyBindingRejection.BINDING_PROFILE_MISMATCH,
                    nodeId = node.nodeId,
                )
            }

            resolved += ResolvedApplyBinding(
                nodeId = node.nodeId,
                binding = binding,
            )
        }

        return ApplyBindingResolution.Resolved(resolved.toList())
    }
}
