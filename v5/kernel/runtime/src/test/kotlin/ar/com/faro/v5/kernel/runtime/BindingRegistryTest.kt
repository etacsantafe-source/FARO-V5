package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BindingRegistryTest {
    @Test fun `provider binding alone does not make contract executable and inputs are snapshotted`() {
        val providers = mutableListOf(providerBinding())
        val registry = BindingRegistry.of(catalog(), providerBindings=providers)
        providers.clear()
        assertEquals(ContractAvailability.KNOWN_BUT_UNAVAILABLE, registry.availability(OperationId(OP)))
        assertEquals(1, registry.providerBindings(OperationId(OP)).size)
        assertNull(registry.availability(OperationId("unknown.operation")))
    }
    @Test fun `canonical operation binding makes known contract available`() {
        val registry = BindingRegistry.of(catalog(), operationBindings=listOf(operationBinding()))
        assertEquals(ContractAvailability.AVAILABLE, registry.availability(OperationId(OP)))
    }

    @Test fun `additional canonical executor makes contract available without Reality binding`() {
        val executor = operationExecutor()
        val registry = BindingRegistry.of(catalog(), additionalExecutors=listOf(executor))
        assertEquals(ContractAvailability.AVAILABLE, registry.availability(OperationId(OP)))
        assertSame(executor, registry.operationExecutor(OperationId(OP)))
        assertNull(registry.operationBinding(OperationId(OP)))
    }
    @Test fun `additional executor metadata mismatch fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            BindingRegistry.of(
                catalog(),
                additionalExecutors=listOf(operationExecutor(executor="other-owner")),
            )
        }
    }
    @Test fun `same operation cannot have Reality binding and additional executor`() {
        assertThrows(IllegalArgumentException::class.java) {
            BindingRegistry.of(
                catalog(),
                operationBindings=listOf(operationBinding()),
                additionalExecutors=listOf(operationExecutor()),
            )
        }
    }
    @Test fun `unknown operation binding fails closed`() {
        assertThrows(IllegalArgumentException::class.java) { BindingRegistry.of(catalog(), operationBindings=listOf(operationBinding("unknown.operation"))) }
    }
    @Test fun `operation binding executor mismatch fails closed`() {
        assertThrows(IllegalArgumentException::class.java) { BindingRegistry.of(catalog(), operationBindings=listOf(operationBinding(executor="other-owner"))) }
    }
    @Test fun `operation binding profile mismatch fails closed`() {
        assertThrows(IllegalArgumentException::class.java) { BindingRegistry.of(catalog(), operationBindings=listOf(operationBinding(profile=ExecutionProfile.REALITY_MUTATION))) }
    }
    @Test fun `duplicate operation binding fails closed`() {
        val b=operationBinding(); assertThrows(IllegalArgumentException::class.java) { BindingRegistry.of(catalog(), operationBindings=listOf(b,b)) }
    }
    @Test fun `provider binding cannot expose internal only contract`() {
        assertThrows(IllegalArgumentException::class.java) { BindingRegistry.of(catalog(ContractExposure.INTERNAL_ONLY), providerBindings=listOf(providerBinding())) }
    }
    @Test fun `provider binding profile mismatch fails closed`() {
        assertThrows(IllegalArgumentException::class.java) { BindingRegistry.of(catalog(), providerBindings=listOf(providerBinding(ExecutionProfile.REALITY_MUTATION))) }
    }
    private fun catalog(exposure:ContractExposure=ContractExposure.BINDABLE)=ContractCatalog.of(
        listOf(CapabilityDescriptor(CapabilityId(CAP))),
        listOf(OperationContractDescriptor(OperationId(OP),CapabilityId(CAP),ExecutorId(EXEC),ExecutionProfile.READ_ONLY,ContractVersion("1"),ContractFingerprint("fixture"),exposure))
    )
    private fun operationBinding(operationId:String=OP, executor:String=EXEC, profile:ExecutionProfile=ExecutionProfile.READ_ONLY)=object:OperationBindingPort {
        override val descriptor=OperationBindingDescriptor(OperationId(operationId),ExecutorId(executor),profile)
        override fun execute(
            preparedOperation: PreparedOperation,
            transactionScope: RealityTransactionScope,
        ): OperationBindingExecutionResult = OperationBindingExecutionResult.Completed
    }

    private fun operationExecutor(
        operationId:String=OP,
        executor:String=EXEC,
        profile:ExecutionProfile=ExecutionProfile.READ_ONLY,
    )=object:OperationExecutionPort {
        override val descriptor=OperationBindingDescriptor(
            OperationId(operationId),
            ExecutorId(executor),
            profile,
        )
    }
    private fun providerBinding(profile:ExecutionProfile=ExecutionProfile.READ_ONLY)=object:ProviderBindingPort {
        override val descriptor=ProviderBindingDescriptor(OperationId(OP),profile)
    }
    private companion object { const val CAP="N-025"; const val OP="finance.snapshot.read"; const val EXEC="finance" }
}
