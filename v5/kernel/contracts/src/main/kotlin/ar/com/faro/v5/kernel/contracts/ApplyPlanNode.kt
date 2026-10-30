package ar.com.faro.v5.kernel.contracts

class ApplyPlanNode(
    val nodeId: PlanNodeId,
    val preparedOperation: PreparedOperation,
    dependsOn: Set<PlanNodeId>,
) {
    val dependsOn: Set<PlanNodeId> = dependsOn.toSet()

    override fun equals(other: Any?): Boolean =
        other is ApplyPlanNode &&
            nodeId == other.nodeId &&
            preparedOperation == other.preparedOperation &&
            dependsOn == other.dependsOn

    override fun hashCode(): Int {
        var result = nodeId.hashCode()
        result = 31 * result + preparedOperation.hashCode()
        result = 31 * result + dependsOn.hashCode()
        return result
    }

    override fun toString(): String =
        "ApplyPlanNode(nodeId=" + nodeId + ", dependsOn=" + dependsOn + ")"
}
