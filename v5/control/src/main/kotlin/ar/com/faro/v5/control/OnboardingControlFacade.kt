package ar.com.faro.v5.control

import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import java.io.File
import kotlinx.coroutines.runBlocking

class OnboardingControlFacade(
    file: File,
) : AutoCloseable {
    private val currentVersion = OnboardingOperations.CURRENT_VERSION
    private val store = OnboardingPreferenceStore(file, currentVersion)
    private val readExecutor = OnboardingReadExecutor(store)
    private val deferExecutor =
        OnboardingMutationExecutor(
            store = store,
            canonicalContract = OnboardingOperations.DEFER_CONTRACT,
            target = OnboardingLifecycle.DEFERRED,
        )
    private val completeExecutor =
        OnboardingMutationExecutor(
            store = store,
            canonicalContract = OnboardingOperations.COMPLETE_CONTRACT,
            target = OnboardingLifecycle.COMPLETED,
        )

    fun readBlocking(): OnboardingSnapshot =
        runBlocking { readExecutor.execute() }

    fun deferBlocking(
        expectedRevision: Long,
        idempotencyKey: String,
    ): OnboardingMutationOutcome =
        executeMutation(
            executor = deferExecutor,
            contract = OnboardingOperations.DEFER_CONTRACT,
            expectedRevision = expectedRevision,
            idempotencyKey = idempotencyKey,
        )

    fun completeBlocking(
        expectedRevision: Long,
        idempotencyKey: String,
    ): OnboardingMutationOutcome =
        executeMutation(
            executor = completeExecutor,
            contract = OnboardingOperations.COMPLETE_CONTRACT,
            expectedRevision = expectedRevision,
            idempotencyKey = idempotencyKey,
        )

    private fun executeMutation(
        executor: OnboardingMutationExecutor,
        contract: ar.com.faro.v5.kernel.contracts.OperationContractDescriptor,
        expectedRevision: Long,
        idempotencyKey: String,
    ): OnboardingMutationOutcome =
        runBlocking {
            val prepared =
                OnboardingMutationExecutor.prepared(
                    contract = contract,
                    onboardingVersion = currentVersion,
                    expectedRevision = expectedRevision,
                    idempotencyKey = IdempotencyKey(idempotencyKey),
                )
            when (val execution = executor.execute(prepared)) {
                is OnboardingMutationExecution.Applied -> execution.outcome
                OnboardingMutationExecution.RejectedContract ->
                    error("canonical onboarding contract rejected")
                OnboardingMutationExecution.RejectedPayload ->
                    error("canonical onboarding payload rejected")
                OnboardingMutationExecution.RejectedDependency ->
                    error("canonical onboarding dependency rejected")
            }
        }

    override fun close() {
        store.close()
    }
}
