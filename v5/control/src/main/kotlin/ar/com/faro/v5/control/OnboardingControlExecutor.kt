package ar.com.faro.v5.control

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.DependencyStampSet
import ar.com.faro.v5.kernel.contracts.OperationBindingDescriptor
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationExecutionPort
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object OnboardingMutationPayload {
    const val FORMAT_ID = "onboarding.mutation.v1"

    fun payload(onboardingVersion: Int): CanonicalPayload {
        require(onboardingVersion > 0) { "onboarding version must be positive" }
        val bytes =
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeInt(1)
                    output.writeInt(onboardingVersion)
                }
                buffer.toByteArray()
            }
        return CanonicalPayload.fromEncoded(FORMAT_ID, bytes)
    }

    fun decode(payload: CanonicalPayload): Int? {
        if (payload.formatId != FORMAT_ID || payload.size != 8) {
            return null
        }
        return runCatching {
            DataInputStream(ByteArrayInputStream(payload.copyBytes())).use { input ->
                require(input.readInt() == 1)
                val version = input.readInt()
                require(version > 0)
                require(input.available() == 0)
                version
            }
        }.getOrNull()
    }
}

sealed interface OnboardingMutationExecution {
    data class Applied(
        val outcome: OnboardingMutationOutcome,
    ) : OnboardingMutationExecution

    data object RejectedContract : OnboardingMutationExecution
    data object RejectedPayload : OnboardingMutationExecution
    data object RejectedDependency : OnboardingMutationExecution
}

internal class OnboardingReadExecutor(
    private val store: OnboardingPreferenceStore,
) : OperationExecutionPort {
    override val descriptor =
        OperationBindingDescriptor(
            operationId = OnboardingOperations.STATE_READ,
            canonicalExecutor = OnboardingOperations.EXECUTOR_ID,
            executionProfile = OnboardingOperations.STATE_READ_CONTRACT.executionProfile,
        )

    suspend fun execute(): OnboardingSnapshot = store.read()
}

internal class OnboardingMutationExecutor(
    private val store: OnboardingPreferenceStore,
    private val canonicalContract: OperationContractDescriptor,
    private val target: OnboardingLifecycle,
) : OperationExecutionPort {
    init {
        require(
            (canonicalContract == OnboardingOperations.DEFER_CONTRACT &&
                target == OnboardingLifecycle.DEFERRED) ||
                (canonicalContract == OnboardingOperations.COMPLETE_CONTRACT &&
                    target == OnboardingLifecycle.COMPLETED)
        ) {
            "onboarding executor contract/target mismatch"
        }
    }

    override val descriptor =
        OperationBindingDescriptor(
            operationId = canonicalContract.operationId,
            canonicalExecutor = canonicalContract.canonicalExecutor,
            executionProfile = canonicalContract.executionProfile,
        )

    suspend fun execute(
        preparedOperation: PreparedOperation,
    ): OnboardingMutationExecution {
        if (preparedOperation.contract != canonicalContract) {
            return OnboardingMutationExecution.RejectedContract
        }

        val onboardingVersion =
            OnboardingMutationPayload.decode(preparedOperation.payload)
                ?: return OnboardingMutationExecution.RejectedPayload

        val stamps = preparedOperation.dependencyStamps.asCanonicalList()
        if (stamps.size != 1) {
            return OnboardingMutationExecution.RejectedDependency
        }
        val stamp = stamps.single()
        if (
            stamp.authorityBoundary != OnboardingSnapshot.ONBOARDING_AUTHORITY ||
                stamp.dependencyKey != OnboardingSnapshot.ONBOARDING_STATE_KEY
        ) {
            return OnboardingMutationExecution.RejectedDependency
        }

        val expectedRevision =
            stamp.observedValue.value
                .removePrefix("revision:")
                .takeIf { "revision:$it" == stamp.observedValue.value }
                ?.toLongOrNull()
                ?.takeIf { it >= 0L }
                ?: return OnboardingMutationExecution.RejectedDependency

        return OnboardingMutationExecution.Applied(
            store.mutate(
                OnboardingMutationRequest(
                    target = target,
                    onboardingVersion = onboardingVersion,
                    expectedRevision = expectedRevision,
                    idempotencyKey = preparedOperation.idempotencyKey,
                )
            )
        )
    }

    companion object {
        fun prepared(
            contract: OperationContractDescriptor,
            onboardingVersion: Int,
            expectedRevision: Long,
            idempotencyKey: ar.com.faro.v5.kernel.contracts.IdempotencyKey,
        ): PreparedOperation =
            PreparedOperation(
                contract = contract,
                payload = OnboardingMutationPayload.payload(onboardingVersion),
                dependencyStamps =
                    DependencyStampSet.of(
                        OnboardingSnapshot.dependencyStampForRevision(expectedRevision)
                    ),
                idempotencyKey = idempotencyKey,
            )
    }
}
