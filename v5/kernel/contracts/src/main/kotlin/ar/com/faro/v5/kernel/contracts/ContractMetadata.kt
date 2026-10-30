package ar.com.faro.v5.kernel.contracts

enum class ExecutionProfile {
    READ_ONLY,
    REALITY_MUTATION,
    AUXILIARY_MUTATION,
    OBSERVATIONAL_WRITE,
    EXTERNAL_EFFECT,
    MAINTENANCE_EFFECT,
}

/**
 * Exposure is eligibility, not runtime availability.
 *
 * BINDABLE means a surface/provider binding may exist.
 * INTERNAL_ONLY forbids ordinary model/surface exposure.
 */
enum class ContractExposure {
    BINDABLE,
    INTERNAL_ONLY,
}

data class CapabilityDescriptor(
    val capabilityId: CapabilityId,
)

data class OperationContractDescriptor(
    val operationId: OperationId,
    val capabilityId: CapabilityId,
    val canonicalExecutor: ExecutorId,
    val executionProfile: ExecutionProfile,
    val contractVersion: ContractVersion,
    val contractFingerprint: ContractFingerprint,
    val exposure: ContractExposure,
)

/**
 * Minimal provider-neutral binding metadata.
 *
 * Registry/runtime code must verify that the referenced operation exists,
 * is bindable, and declares the same execution profile before registration.
 */
data class ProviderBindingDescriptor(
    val operationId: OperationId,
    val executionProfile: ExecutionProfile,
)


data class OperationBindingDescriptor(
    val operationId: OperationId,
    val canonicalExecutor: ExecutorId,
    val executionProfile: ExecutionProfile,
)
