package ar.com.faro.v5.kernel.contracts

data class PreparedOperation(
    val contract: OperationContractDescriptor,
    val payload: CanonicalPayload,
    val dependencyStamps: DependencyStampSet,
    val idempotencyKey: IdempotencyKey,
) {
    val payloadFingerprint: PayloadFingerprint
        get() = payload.fingerprint
}
