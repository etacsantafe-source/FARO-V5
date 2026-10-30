package ar.com.faro.v5.kernel.contracts

import java.util.PriorityQueue

class ApplyPlan private constructor(
    private val nodesById: Map<PlanNodeId, ApplyPlanNode>,
    val topologicalOrder: List<PlanNodeId>,
) {
    fun node(nodeId: PlanNodeId): ApplyPlanNode? = nodesById[nodeId]

    fun nodesInTopologicalOrder(): List<ApplyPlanNode> =
        topologicalOrder.map(nodesById::getValue)

    companion object {
        fun of(vararg nodes: ApplyPlanNode): ApplyPlan {
            require(nodes.isNotEmpty()) { "apply plan must contain at least one node" }

            val groupedById = nodes.groupBy { it.nodeId }
            require(groupedById.values.none { it.size > 1 }) {
                "apply plan cannot contain duplicate node ids"
            }

            val nodesById = groupedById.mapValues { (_, entries) -> entries.single() }
            val nodeIds = nodesById.keys

            nodesById.values.forEach { node ->
                require(node.nodeId !in node.dependsOn) {
                    "apply plan node cannot depend on itself"
                }
                require(node.dependsOn.all(nodeIds::contains)) {
                    "apply plan dependency must reference an existing node"
                }
            }

            val incoming = nodesById.mapValues { (_, node) -> node.dependsOn.size }.toMutableMap()
            val dependents = mutableMapOf<PlanNodeId, MutableSet<PlanNodeId>>()

            nodesById.values.forEach { node ->
                node.dependsOn.forEach { dependency ->
                    dependents.getOrPut(dependency) { mutableSetOf() }.add(node.nodeId)
                }
            }

            val ready = PriorityQueue<PlanNodeId>(compareBy { it.value })
            incoming
                .filterValues { it == 0 }
                .keys
                .forEach(ready::add)

            val order = mutableListOf<PlanNodeId>()
            while (ready.isNotEmpty()) {
                val current = ready.remove()
                order += current

                dependents[current]
                    .orEmpty()
                    .sortedBy { it.value }
                    .forEach { dependent ->
                        val remaining = incoming.getValue(dependent) - 1
                        incoming[dependent] = remaining
                        if (remaining == 0) {
                            ready.add(dependent)
                        }
                    }
            }

            require(order.size == nodesById.size) {
                "apply plan must be acyclic"
            }

            return ApplyPlan(
                nodesById = nodesById.toMap(),
                topologicalOrder = order.toList(),
            )
        }
    }
}
