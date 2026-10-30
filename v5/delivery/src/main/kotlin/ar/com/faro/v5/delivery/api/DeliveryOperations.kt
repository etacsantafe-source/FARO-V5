package ar.com.faro.v5.delivery.api

import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.OperationBindingDescriptor
import ar.com.faro.v5.kernel.contracts.OperationId

object DeliveryOperations {
    val CAPABILITY_ID = CapabilityId("N-032")
    val EXECUTOR_ID = ExecutorId("delivery")

    val NOTICE_READ = OperationId("notice.read")
    val NOTICE_ROUTE = OperationId("notice.route")
    val NOTICE_RESOLVE = OperationId("notice.resolve")
    val NOTIFICATION_DISPATCH = OperationId("notification.delivery.dispatch")
    val NOTIFICATION_RECONCILE = OperationId("notification.delivery.reconcile")

    val READ_DESCRIPTOR = OperationBindingDescriptor(
        operationId = NOTICE_READ,
        canonicalExecutor = EXECUTOR_ID,
        executionProfile = ExecutionProfile.READ_ONLY,
    )

    val ROUTE_DESCRIPTOR = OperationBindingDescriptor(
        operationId = NOTICE_ROUTE,
        canonicalExecutor = EXECUTOR_ID,
        executionProfile = ExecutionProfile.AUXILIARY_MUTATION,
    )

    val RESOLVE_DESCRIPTOR = OperationBindingDescriptor(
        operationId = NOTICE_RESOLVE,
        canonicalExecutor = EXECUTOR_ID,
        executionProfile = ExecutionProfile.AUXILIARY_MUTATION,
    )

    val DISPATCH_DESCRIPTOR = OperationBindingDescriptor(
        operationId = NOTIFICATION_DISPATCH,
        canonicalExecutor = EXECUTOR_ID,
        executionProfile = ExecutionProfile.EXTERNAL_EFFECT,
    )

    val RECONCILE_DESCRIPTOR = OperationBindingDescriptor(
        operationId = NOTIFICATION_RECONCILE,
        canonicalExecutor = EXECUTOR_ID,
        executionProfile = ExecutionProfile.AUXILIARY_MUTATION,
    )
}
