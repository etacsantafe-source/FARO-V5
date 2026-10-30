package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CapabilitySessionTest {
    @Test
    fun `session is ephemeral and snapshots catalog metadata`() {
        val registry = BindingRegistry.of(
            catalog = catalog(),
            operationBindings = listOf(operationBinding()),
        )

        val first = CapabilitySession.open(registry)
        val second = CapabilitySession.open(registry)

        assertNotSame(first, second)
        assertEquals(1, first.capabilities.size)
        assertEquals(1, first.contracts.size)
        assertEquals(OP, first.contracts.single().operationId.value)
    }

    @Test
    fun `available contract resolves exact canonical binding`() {
        val binding = operationBinding()
        val session = CapabilitySession.open(
            BindingRegistry.of(catalog(), operationBindings = listOf(binding))
        )

        val resolved = session.resolve(OperationId(OP))

        assertNotNull(resolved)
        assertEquals("fp-v1", resolved!!.contract.contractFingerprint.value)
        assertSame(binding, resolved.executor)
        assertEquals(ContractAvailability.AVAILABLE, session.availability(OperationId(OP)))
    }


    @Test
    fun `non-Reality canonical executor is available without Reality binding`() {
        val executor = object : OperationExecutionPort {
            override val descriptor = OperationBindingDescriptor(
                operationId = OperationId(OP),
                canonicalExecutor = ExecutorId(EXEC),
                executionProfile = ExecutionProfile.READ_ONLY,
            )
        }
        val session = CapabilitySession.open(
            BindingRegistry.of(
                catalog(),
                additionalExecutors = listOf(executor),
            )
        )

        val resolved = session.resolve(OperationId(OP))
        assertNotNull(resolved)
        assertSame(executor, resolved!!.executor)
        assertEquals(
            ContractAvailability.AVAILABLE,
            session.availability(OperationId(OP)),
        )
    }

    @Test
    fun `provider binding alone never becomes executable or provider visible`() {
        val provider = providerBinding()
        val session = CapabilitySession.open(
            BindingRegistry.of(catalog(), providerBindings = listOf(provider))
        )

        assertEquals(
            ContractAvailability.KNOWN_BUT_UNAVAILABLE,
            session.availability(OperationId(OP)),
        )
        assertNull(session.resolve(OperationId(OP)))
        assertTrue(session.providerBindings(OperationId(OP)).isEmpty())
    }

    @Test
    fun `provider binding is visible only with canonical operation binding`() {
        val provider = providerBinding()
        val session = CapabilitySession.open(
            BindingRegistry.of(
                catalog(),
                operationBindings = listOf(operationBinding()),
                providerBindings = listOf(provider),
            )
        )

        assertEquals(listOf(provider), session.providerBindings(OperationId(OP)))
    }

    @Test
    fun `unknown operation fails closed`() {
        val session = CapabilitySession.open(BindingRegistry.of(catalog()))

        assertNull(session.contract(OperationId("unknown.operation")))
        assertNull(session.availability(OperationId("unknown.operation")))
        assertNull(session.resolve(OperationId("unknown.operation")))
        assertTrue(session.providerBindings(OperationId("unknown.operation")).isEmpty())
    }

    @Test
    fun `internal only operation can be internally executable but cannot become provider binding`() {
        val internalCatalog = catalog(ContractExposure.INTERNAL_ONLY)
        val session = CapabilitySession.open(
            BindingRegistry.of(
                internalCatalog,
                operationBindings = listOf(operationBinding()),
            )
        )

        assertNotNull(session.resolve(OperationId(OP)))
        assertTrue(session.providerBindings(OperationId(OP)).isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            BindingRegistry.of(
                internalCatalog,
                operationBindings = listOf(operationBinding()),
                providerBindings = listOf(providerBinding()),
            )
        }
    }

    private fun catalog(
        exposure: ContractExposure = ContractExposure.BINDABLE,
    ) = ContractCatalog.of(
        capabilities = listOf(CapabilityDescriptor(CapabilityId(CAP))),
        operations = listOf(
            OperationContractDescriptor(
                operationId = OperationId(OP),
                capabilityId = CapabilityId(CAP),
                canonicalExecutor = ExecutorId(EXEC),
                executionProfile = ExecutionProfile.READ_ONLY,
                contractVersion = ContractVersion("metadata-v1"),
                contractFingerprint = ContractFingerprint("fp-v1"),
                exposure = exposure,
            )
        ),
    )

    private fun operationBinding() = object : OperationBindingPort {
        override val descriptor = OperationBindingDescriptor(
            operationId = OperationId(OP),
            canonicalExecutor = ExecutorId(EXEC),
            executionProfile = ExecutionProfile.READ_ONLY,
        )
        override fun execute(preparedOperation: PreparedOperation, transactionScope: RealityTransactionScope): OperationBindingExecutionResult = OperationBindingExecutionResult.Completed
    }

    private fun providerBinding() = object : ProviderBindingPort {
        override val descriptor = ProviderBindingDescriptor(
            operationId = OperationId(OP),
            executionProfile = ExecutionProfile.READ_ONLY,
        )
    }

    private companion object {
        const val CAP = "N-025"
        const val OP = "finance.snapshot.read"
        const val EXEC = "finance"
    }
}
