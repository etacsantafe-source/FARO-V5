package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.OperationBindingPort
import ar.com.faro.v5.kernel.contracts.OperationExecutionPort
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.ProviderBindingPort

enum class ContractAvailability { KNOWN_BUT_UNAVAILABLE, AVAILABLE }

/** Immutable binding snapshot. Provider bindings never create executability. */
class BindingRegistry private constructor(
    internal val catalog: ContractCatalog,
    private val operationExecutorsById: Map<OperationId, OperationExecutionPort>,
    private val operationBindingsById: Map<OperationId, OperationBindingPort>,
    private val providerBindingsByOperationId: Map<OperationId, List<ProviderBindingPort>>,
) {
    fun availability(operationId: OperationId): ContractAvailability? {
        if (catalog.operation(operationId) == null) return null
        return if (operationExecutorsById.containsKey(operationId)) ContractAvailability.AVAILABLE
        else ContractAvailability.KNOWN_BUT_UNAVAILABLE
    }
    fun operationExecutor(operationId: OperationId): OperationExecutionPort? =
        operationExecutorsById[operationId]
    fun operationBinding(operationId: OperationId): OperationBindingPort? =
        operationBindingsById[operationId]
    fun providerBindings(operationId: OperationId): List<ProviderBindingPort> = providerBindingsByOperationId[operationId].orEmpty()

    companion object {
        fun of(
            catalog: ContractCatalog,
            operationBindings: Iterable<OperationBindingPort> = emptyList(),
            additionalExecutors: Iterable<OperationExecutionPort> = emptyList(),
            providerBindings: Iterable<ProviderBindingPort> = emptyList(),
        ): BindingRegistry {
            val operationSnapshot = LinkedHashMap<OperationId, OperationBindingPort>()
            val executorSnapshot = LinkedHashMap<OperationId, OperationExecutionPort>()
            operationBindings.forEach { binding ->
                val descriptor = binding.descriptor
                val contract = requireNotNull(catalog.operation(descriptor.operationId)) {
                    "Operation binding references unknown operation: ${descriptor.operationId.value}"
                }
                require(contract.canonicalExecutor == descriptor.canonicalExecutor) {
                    "Operation binding executor mismatch for ${descriptor.operationId.value}"
                }
                require(contract.executionProfile == descriptor.executionProfile) {
                    "Operation binding profile mismatch for ${descriptor.operationId.value}"
                }
                require(operationSnapshot.putIfAbsent(descriptor.operationId, binding) == null) {
                    "Duplicate operation binding: ${descriptor.operationId.value}"
                }
                require(executorSnapshot.putIfAbsent(descriptor.operationId, binding) == null) {
                    "Duplicate operation executor: ${descriptor.operationId.value}"
                }
            }
            additionalExecutors.forEach { executor ->
                val descriptor = executor.descriptor
                val contract = requireNotNull(catalog.operation(descriptor.operationId)) {
                    "Operation executor references unknown operation: ${descriptor.operationId.value}"
                }
                require(contract.canonicalExecutor == descriptor.canonicalExecutor) {
                    "Operation executor mismatch for ${descriptor.operationId.value}"
                }
                require(contract.executionProfile == descriptor.executionProfile) {
                    "Operation executor profile mismatch for ${descriptor.operationId.value}"
                }
                require(executorSnapshot.putIfAbsent(descriptor.operationId, executor) == null) {
                    "Duplicate operation executor: ${descriptor.operationId.value}"
                }
            }
            val providerSnapshot = LinkedHashMap<OperationId, MutableList<ProviderBindingPort>>()
            providerBindings.forEach { binding ->
                val descriptor = binding.descriptor
                val contract = requireNotNull(catalog.operation(descriptor.operationId)) {
                    "Provider binding references unknown operation: ${descriptor.operationId.value}"
                }
                require(contract.exposure == ContractExposure.BINDABLE) {
                    "Provider binding forbidden for INTERNAL_ONLY operation: ${descriptor.operationId.value}"
                }
                require(contract.executionProfile == descriptor.executionProfile) {
                    "Provider binding profile mismatch for ${descriptor.operationId.value}"
                }
                providerSnapshot.getOrPut(descriptor.operationId) { mutableListOf() }.add(binding)
            }
            return BindingRegistry(
                catalog,
                executorSnapshot.toMap(),
                operationSnapshot.toMap(),
                providerSnapshot.mapValues { (_, bindings) -> bindings.toList() }.toMap(),
            )
        }
    }
}
