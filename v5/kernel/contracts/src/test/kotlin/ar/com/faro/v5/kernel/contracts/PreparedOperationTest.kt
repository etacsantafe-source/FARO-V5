package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PreparedOperationTest {
    @Test
    fun preparedOperationPinsExactContractCanonicalPayloadStampsAndIdempotency() {
        val contract = OperationContractDescriptor(
            operationId = OperationId("finance.transfer.prepare"),
            capabilityId = CapabilityId("N-025"),
            canonicalExecutor = ExecutorId("finance"),
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("1"),
            contractFingerprint = ContractFingerprint("contract-v1"),
            exposure = ContractExposure.BINDABLE,
        )
        val payload = CanonicalPayload.fromEncoded(
            "finance-transfer-v1",
            "checking|5000".toByteArray(),
        )
        val stamp = DependencyStamp(
            authorityBoundary = AuthorityBoundaryId("reality.finance"),
            dependencyKey = DependencyKey("account:checking"),
            observedValue = DependencyStampValue("revision:42"),
        )
        val prepared = PreparedOperation(
            contract = contract,
            payload = payload,
            dependencyStamps = DependencyStampSet.of(stamp),
            idempotencyKey = IdempotencyKey("transfer-1"),
        )

        assertEquals(contract, prepared.contract)
        assertEquals(payload, prepared.payload)
        assertEquals(payload.fingerprint, prepared.payloadFingerprint)
        assertEquals(DependencyStampSet.of(stamp), prepared.dependencyStamps)
        assertEquals(IdempotencyKey("transfer-1"), prepared.idempotencyKey)
    }

    @Test
    fun changingPinnedContractFingerprintChangesPreparedOperationIdentityByValue() {
        val base = OperationContractDescriptor(
            operationId = OperationId("finance.transfer.prepare"),
            capabilityId = CapabilityId("N-025"),
            canonicalExecutor = ExecutorId("finance"),
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("1"),
            contractFingerprint = ContractFingerprint("contract-v1"),
            exposure = ContractExposure.BINDABLE,
        )
        val changed = base.copy(
            contractFingerprint = ContractFingerprint("contract-v2")
        )

        val first = PreparedOperation(
            contract = base,
            payload = CanonicalPayload.fromEncoded(
                "finance-transfer-v1",
                "checking|5000".toByteArray(),
            ),
            dependencyStamps = DependencyStampSet.empty(),
            idempotencyKey = IdempotencyKey("transfer-1"),
        )
        val second = first.copy(contract = changed)

        assertNotEquals(first, second)
    }
}
