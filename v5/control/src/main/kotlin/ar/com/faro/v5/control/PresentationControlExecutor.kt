package ar.com.faro.v5.control

import ar.com.faro.v5.kernel.contracts.PreparedOperation
import ar.com.faro.v5.kernel.contracts.OperationExecutionPort
import ar.com.faro.v5.kernel.contracts.OperationBindingDescriptor

sealed interface PresentationThemeExecution {
    data class Applied(
        val outcome: ThemeSetOutcome,
    ) : PresentationThemeExecution

    data object RejectedContract : PresentationThemeExecution
    data object RejectedPayload : PresentationThemeExecution
    data object RejectedDependency : PresentationThemeExecution
}

class PresentationReadExecutor(
    private val store: PresentationPreferenceStore,
) : OperationExecutionPort {
    override val descriptor = OperationBindingDescriptor(
        operationId = PresentationOperations.PRESENTATION_READ,
        canonicalExecutor = PresentationOperations.EXECUTOR_ID,
        executionProfile = PresentationOperations.READ_CONTRACT.executionProfile,
    )

    suspend fun execute(): PresentationSnapshot = store.read()
}

class PresentationControlExecutor(
    private val store: PresentationPreferenceStore,
) : OperationExecutionPort {
    override val descriptor = OperationBindingDescriptor(
        operationId = PresentationOperations.THEME_SET,
        canonicalExecutor = PresentationOperations.EXECUTOR_ID,
        executionProfile = PresentationOperations.THEME_SET_CONTRACT.executionProfile,
    )

    suspend fun readPresentation(): PresentationSnapshot = store.read()

    suspend fun executeThemeSet(
        preparedOperation: PreparedOperation,
    ): PresentationThemeExecution {
        if (preparedOperation.contract != PresentationOperations.THEME_SET_CONTRACT) {
            return PresentationThemeExecution.RejectedContract
        }

        val theme = ThemeSetPayload.decode(preparedOperation.payload)
            ?: return PresentationThemeExecution.RejectedPayload

        val stamps = preparedOperation.dependencyStamps.asCanonicalList()
        if (stamps.size != 1) {
            return PresentationThemeExecution.RejectedDependency
        }
        val stamp = stamps.single()
        if (
            stamp.authorityBoundary != PresentationSnapshot.PRESENTATION_AUTHORITY ||
            stamp.dependencyKey != PresentationSnapshot.PRESENTATION_STATE_KEY
        ) {
            return PresentationThemeExecution.RejectedDependency
        }

        val expectedRevision = stamp.observedValue.value
            .removePrefix("revision:")
            .takeIf { "revision:$it" == stamp.observedValue.value }
            ?.toLongOrNull()
            ?.takeIf { it >= 0L }
            ?: return PresentationThemeExecution.RejectedDependency

        return PresentationThemeExecution.Applied(
            store.setTheme(
                ThemeSetRequest(
                    themeMode = theme,
                    expectedRevision = expectedRevision,
                    idempotencyKey = preparedOperation.idempotencyKey,
                )
            )
        )
    }
}
