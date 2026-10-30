package ar.com.faro.v5.control

import ar.com.faro.v5.kernel.contracts.AuthorityBoundaryId
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ContractFingerprint
import ar.com.faro.v5.kernel.contracts.ContractVersion
import ar.com.faro.v5.kernel.contracts.DependencyKey
import ar.com.faro.v5.kernel.contracts.DependencyStamp
import ar.com.faro.v5.kernel.contracts.DependencyStampValue
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationId

enum class OnboardingLifecycle {
    PENDING,
    DEFERRED,
    COMPLETED,
}

data class OnboardingSnapshot(
    val currentVersion: Int,
    val storedVersion: Int,
    val lifecycle: OnboardingLifecycle,
    val revision: Long,
) {
    init {
        require(currentVersion > 0) { "current onboarding version must be positive" }
        require(storedVersion >= 0) { "stored onboarding version must be non-negative" }
        require(revision >= 0L) { "onboarding revision must be non-negative" }
    }

    fun shouldPresentAutomatically(): Boolean =
        storedVersion < currentVersion ||
            (storedVersion == currentVersion && lifecycle == OnboardingLifecycle.PENDING)

    fun isHandledForCurrentVersion(): Boolean =
        storedVersion == currentVersion && lifecycle != OnboardingLifecycle.PENDING

    fun dependencyStamp(): DependencyStamp =
        dependencyStampForRevision(revision)

    companion object {
        val ONBOARDING_AUTHORITY = AuthorityBoundaryId("control.onboarding")
        val ONBOARDING_STATE_KEY = DependencyKey("onboarding:state")

        fun dependencyStampForRevision(revision: Long): DependencyStamp {
            require(revision >= 0L) { "onboarding revision must be non-negative" }
            return DependencyStamp(
                authorityBoundary = ONBOARDING_AUTHORITY,
                dependencyKey = ONBOARDING_STATE_KEY,
                observedValue = DependencyStampValue("revision:$revision"),
            )
        }
    }
}

object OnboardingOperations {
    const val CURRENT_VERSION: Int = 1

    val CAPABILITY_ID = CapabilityId("N-047")
    val EXECUTOR_ID = ExecutorId("onboarding-control")

    val STATE_READ = OperationId("onboarding.state.read")
    val DEFER = OperationId("onboarding.defer")
    val COMPLETE = OperationId("onboarding.complete")

    private val METADATA_VERSION = ContractVersion("metadata-v1")

    val STATE_READ_CONTRACT = OperationContractDescriptor(
        operationId = STATE_READ,
        capabilityId = CAPABILITY_ID,
        canonicalExecutor = EXECUTOR_ID,
        executionProfile = ExecutionProfile.READ_ONLY,
        contractVersion = METADATA_VERSION,
        contractFingerprint = ContractFingerprint(
            "95df7fbdc894b0996a7a786a018e883069a890d6a5283df235076a029a6c9a76"
        ),
        exposure = ContractExposure.BINDABLE,
    )

    val DEFER_CONTRACT = OperationContractDescriptor(
        operationId = DEFER,
        capabilityId = CAPABILITY_ID,
        canonicalExecutor = EXECUTOR_ID,
        executionProfile = ExecutionProfile.AUXILIARY_MUTATION,
        contractVersion = METADATA_VERSION,
        contractFingerprint = ContractFingerprint(
            "baa5475795f72064d0e5424ce5408d431df8f74a4075546535311397557889fa"
        ),
        exposure = ContractExposure.BINDABLE,
    )

    val COMPLETE_CONTRACT = OperationContractDescriptor(
        operationId = COMPLETE,
        capabilityId = CAPABILITY_ID,
        canonicalExecutor = EXECUTOR_ID,
        executionProfile = ExecutionProfile.AUXILIARY_MUTATION,
        contractVersion = METADATA_VERSION,
        contractFingerprint = ContractFingerprint(
            "c86b1789f1b9fe54aa8d61629b0d3252642ce2f643158620592ea33b71046722"
        ),
        exposure = ContractExposure.BINDABLE,
    )
}

data class OnboardingMutationRequest(
    val target: OnboardingLifecycle,
    val onboardingVersion: Int,
    val expectedRevision: Long,
    val idempotencyKey: IdempotencyKey,
) {
    init {
        require(target == OnboardingLifecycle.DEFERRED || target == OnboardingLifecycle.COMPLETED) {
            "onboarding mutation target must be DEFERRED or COMPLETED"
        }
        require(onboardingVersion > 0) { "onboarding version must be positive" }
        require(expectedRevision >= 0L) { "expected onboarding revision must be non-negative" }
    }
}

sealed interface OnboardingMutationOutcome {
    val snapshot: OnboardingSnapshot

    data class Committed(
        override val snapshot: OnboardingSnapshot,
        val changed: Boolean,
        val replayed: Boolean,
        val idempotencyKey: IdempotencyKey,
    ) : OnboardingMutationOutcome

    data class RejectedStale(
        override val snapshot: OnboardingSnapshot,
        val expectedRevision: Long,
    ) : OnboardingMutationOutcome

    data class RejectedIdentityConflict(
        override val snapshot: OnboardingSnapshot,
        val idempotencyKey: IdempotencyKey,
    ) : OnboardingMutationOutcome

    data class RejectedFutureVersion(
        override val snapshot: OnboardingSnapshot,
    ) : OnboardingMutationOutcome
}
