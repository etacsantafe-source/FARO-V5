package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.CapabilityDescriptor
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.kernel.contracts.OperationId

/** Immutable runtime snapshot supplied by explicit composition. */
class ContractCatalog private constructor(
    private val capabilitiesById: Map<CapabilityId, CapabilityDescriptor>,
    private val operationsById: Map<OperationId, OperationContractDescriptor>,
) : OperationContractLookupPort {
    val capabilities: Collection<CapabilityDescriptor> get() = capabilitiesById.values
    val operations: Collection<OperationContractDescriptor> get() = operationsById.values
    fun capability(id: CapabilityId): CapabilityDescriptor? = capabilitiesById[id]
    fun operation(id: OperationId): OperationContractDescriptor? = operationsById[id]
    override fun current(operationId: OperationId): OperationContractDescriptor? =
        operation(operationId)

    companion object {
        fun of(
            capabilities: Iterable<CapabilityDescriptor>,
            operations: Iterable<OperationContractDescriptor>,
        ): ContractCatalog {
            val capabilitySnapshot = LinkedHashMap<CapabilityId, CapabilityDescriptor>()
            capabilities.forEach { descriptor ->
                require(capabilitySnapshot.putIfAbsent(descriptor.capabilityId, descriptor) == null) {
                    "Duplicate capability id: ${descriptor.capabilityId.value}"
                }
            }
            val operationSnapshot = LinkedHashMap<OperationId, OperationContractDescriptor>()
            operations.forEach { descriptor ->
                require(capabilitySnapshot.containsKey(descriptor.capabilityId)) {
                    "Operation ${descriptor.operationId.value} references unknown capability ${descriptor.capabilityId.value}"
                }
                require(operationSnapshot.putIfAbsent(descriptor.operationId, descriptor) == null) {
                    "Duplicate operation id: ${descriptor.operationId.value}"
                }
            }
            return ContractCatalog(capabilitySnapshot.toMap(), operationSnapshot.toMap())
        }
    }
}
