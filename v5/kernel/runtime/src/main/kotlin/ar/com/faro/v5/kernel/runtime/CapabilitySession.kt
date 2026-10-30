package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.CapabilityDescriptor
import ar.com.faro.v5.kernel.contracts.OperationExecutionPort
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.ProviderBindingPort

data class ResolvedCapabilityOperation(
    val contract: OperationContractDescriptor,
    val executor: OperationExecutionPort,
)

/**
 * Ephemeral interaction-scoped view over immutable catalog/binding snapshots.
 *
 * It owns no durable state and does not discover or register new contracts/bindings.
 */
class CapabilitySession private constructor(
    private val registry: BindingRegistry,
) {
    private val capabilitiesSnapshot = registry.catalog.capabilities.toList()
    private val contractsSnapshot = registry.catalog.operations.toList()

    val capabilities: List<CapabilityDescriptor> get() = capabilitiesSnapshot
    val contracts: List<OperationContractDescriptor> get() = contractsSnapshot

    fun contract(operationId: OperationId): OperationContractDescriptor? =
        registry.catalog.operation(operationId)

    fun availability(operationId: OperationId): ContractAvailability? =
        registry.availability(operationId)

    fun resolve(operationId: OperationId): ResolvedCapabilityOperation? {
        val contract = registry.catalog.operation(operationId) ?: return null
        val executor = registry.operationExecutor(operationId) ?: return null
        return ResolvedCapabilityOperation(contract, executor)
    }

    /**
     * Provider adapters are visible to an interaction only when the operation
     * is also executable through its canonical operation executor.
     */
    fun providerBindings(operationId: OperationId): List<ProviderBindingPort> {
        if (resolve(operationId) == null) return emptyList()
        return registry.providerBindings(operationId).toList()
    }

    companion object {
        fun open(registry: BindingRegistry): CapabilitySession =
            CapabilitySession(registry)
    }
}
