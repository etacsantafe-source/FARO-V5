package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContractMetadataTest {
    @Test
    fun `execution profiles are the six canonical profiles`() {
        assertEquals(
            setOf(
                ExecutionProfile.READ_ONLY,
                ExecutionProfile.REALITY_MUTATION,
                ExecutionProfile.AUXILIARY_MUTATION,
                ExecutionProfile.OBSERVATIONAL_WRITE,
                ExecutionProfile.EXTERNAL_EFFECT,
                ExecutionProfile.MAINTENANCE_EFFECT,
            ),
            ExecutionProfile.entries.toSet(),
        )
    }

    @Test
    fun `operation descriptor keeps executor profile fingerprint and exposure explicit`() {
        val descriptor = OperationContractDescriptor(
            operationId = OperationId("finance.snapshot.read"),
            capabilityId = CapabilityId("N-025"),
            canonicalExecutor = ExecutorId("finance"),
            executionProfile = ExecutionProfile.READ_ONLY,
            contractVersion = ContractVersion("1"),
            contractFingerprint = ContractFingerprint("fixture-fingerprint"),
            exposure = ContractExposure.BINDABLE,
        )

        assertEquals(ExecutorId("finance"), descriptor.canonicalExecutor)
        assertEquals(ExecutionProfile.READ_ONLY, descriptor.executionProfile)
        assertEquals(ContractFingerprint("fixture-fingerprint"), descriptor.contractFingerprint)
        assertEquals(ContractExposure.BINDABLE, descriptor.exposure)
    }

    @Test
    fun `internal only remains distinct from binding availability`() {
        val operation = OperationContractDescriptor(
            operationId = OperationId("memory.learn_validated"),
            capabilityId = CapabilityId("N-011"),
            canonicalExecutor = ExecutorId("semantic-memory"),
            executionProfile = ExecutionProfile.AUXILIARY_MUTATION,
            contractVersion = ContractVersion("1"),
            contractFingerprint = ContractFingerprint("internal-fixture"),
            exposure = ContractExposure.INTERNAL_ONLY,
        )

        val candidateBinding = ProviderBindingDescriptor(
            operationId = operation.operationId,
            executionProfile = operation.executionProfile,
        )

        assertEquals(ContractExposure.INTERNAL_ONLY, operation.exposure)
        assertEquals(operation.operationId, candidateBinding.operationId)
        assertEquals(operation.executionProfile, candidateBinding.executionProfile)
    }
}
