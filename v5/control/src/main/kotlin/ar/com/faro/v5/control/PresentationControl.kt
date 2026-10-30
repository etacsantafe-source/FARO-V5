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

enum class PresentationThemeMode {
    SYSTEM,
    DAY,
    NIGHT,
}

data class PresentationSnapshot(
    val themeMode: PresentationThemeMode,
    val revealAmounts: Boolean,
    val revision: Long,
) {
    init {
        require(revision >= 0L) { "presentation revision must be non-negative" }
    }

    fun dependencyStamp(): DependencyStamp =
        DependencyStamp(
            authorityBoundary = PRESENTATION_AUTHORITY,
            dependencyKey = PRESENTATION_STATE_KEY,
            observedValue = DependencyStampValue("revision:$revision"),
        )

    companion object {
        val PRESENTATION_AUTHORITY = AuthorityBoundaryId("control.presentation")
        val PRESENTATION_STATE_KEY = DependencyKey("presentation:state")

        val DEFAULT = PresentationSnapshot(
            themeMode = PresentationThemeMode.SYSTEM,
            revealAmounts = false,
            revision = 0L,
        )
    }
}

object PresentationOperations {
    val CAPABILITY_ID = CapabilityId("N-039")
    val EXECUTOR_ID = ExecutorId("presentation-control")

    val PRESENTATION_READ = OperationId("settings.presentation.read")
    val THEME_SET = OperationId("settings.theme.set")
    val AMOUNT_VISIBILITY_SET = OperationId("settings.amount_visibility.set")

    val READ_CONTRACT = OperationContractDescriptor(
        operationId = PRESENTATION_READ,
        capabilityId = CAPABILITY_ID,
        canonicalExecutor = EXECUTOR_ID,
        executionProfile = ExecutionProfile.READ_ONLY,
        contractVersion = ContractVersion("1"),
        contractFingerprint = ContractFingerprint("settings-presentation-read-v1"),
        exposure = ContractExposure.BINDABLE,
    )

    val THEME_SET_CONTRACT = OperationContractDescriptor(
        operationId = THEME_SET,
        capabilityId = CAPABILITY_ID,
        canonicalExecutor = EXECUTOR_ID,
        executionProfile = ExecutionProfile.AUXILIARY_MUTATION,
        contractVersion = ContractVersion("1"),
        contractFingerprint = ContractFingerprint("settings-theme-set-v1"),
        exposure = ContractExposure.BINDABLE,
    )
}

data class ThemeSetRequest(
    val themeMode: PresentationThemeMode,
    val expectedRevision: Long,
    val idempotencyKey: IdempotencyKey,
) {
    init {
        require(expectedRevision >= 0L) {
            "presentation expected revision must be non-negative"
        }
    }
}

sealed interface ThemeSetOutcome {
    data class Committed(
        val snapshot: PresentationSnapshot,
        val replayed: Boolean,
        val changed: Boolean,
        val idempotencyKey: IdempotencyKey,
    ) : ThemeSetOutcome

    data class RejectedStale(
        val current: PresentationSnapshot,
        val expectedRevision: Long,
    ) : ThemeSetOutcome

    data class RejectedIdentityConflict(
        val current: PresentationSnapshot,
        val idempotencyKey: IdempotencyKey,
    ) : ThemeSetOutcome
}
