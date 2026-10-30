package ar.com.faro.v5.owner.profile.api

import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationId

object ProfileOperations {
    val CAPABILITY_ID = CapabilityId("N-038")
    val EXECUTOR_ID = ExecutorId("profile")
    val READ = OperationId("profile.read")
    val UPDATE_DECLARED = OperationId("profile.update_declared")
}
