package ar.com.faro.v5.owner.profile.api

import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.OperationBindingDescriptor
import ar.com.faro.v5.kernel.contracts.OperationBindingExecutionResult
import ar.com.faro.v5.kernel.contracts.OperationBindingPort
import ar.com.faro.v5.kernel.contracts.OperationRevalidationFailure
import ar.com.faro.v5.kernel.contracts.OperationRevalidationPort
import ar.com.faro.v5.kernel.contracts.OperationRevalidationResult
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.owner.profile.spi.ProfilePersistencePort

class ProfileUpdateDeclaredBinding(
    private val persistence: ProfilePersistencePort,
) : OperationBindingPort {
    override val descriptor = OperationBindingDescriptor(
        operationId = ProfileOperations.UPDATE_DECLARED,
        canonicalExecutor = ProfileOperations.EXECUTOR_ID,
        executionProfile = ExecutionProfile.REALITY_MUTATION,
    )

    override fun execute(
        preparedOperation: PreparedOperation,
        transactionScope: RealityTransactionScope,
    ): OperationBindingExecutionResult {
        val contract = preparedOperation.contract
        if (
            contract.operationId != descriptor.operationId ||
            contract.canonicalExecutor != descriptor.canonicalExecutor ||
            contract.executionProfile != descriptor.executionProfile
        ) {
            return OperationBindingExecutionResult.Rejected(
                "profile_contract_mismatch"
            )
        }

        val update = ProfileDeclaredUpdatePayload.decode(preparedOperation.payload)
            ?: return OperationBindingExecutionResult.Rejected(
                "profile_payload_invalid"
            )

        persistence.updateDeclared(
            displayName = update.displayName,
            transactionScope = transactionScope,
        )
        return OperationBindingExecutionResult.Completed
    }
}

class ProfileUpdateDeclaredRevalidation : OperationRevalidationPort {
    override val operationId = ProfileOperations.UPDATE_DECLARED

    override fun revalidate(
        preparedOperation: PreparedOperation,
    ): OperationRevalidationResult =
        if (ProfileDeclaredUpdatePayload.decode(preparedOperation.payload) != null) {
            OperationRevalidationResult.Valid
        } else {
            OperationRevalidationResult.Rejected(
                failure = OperationRevalidationFailure.PRECONDITION_FAILED,
                code = "profile_payload_invalid",
            )
        }
}
