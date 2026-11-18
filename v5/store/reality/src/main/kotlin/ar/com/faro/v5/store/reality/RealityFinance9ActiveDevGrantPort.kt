package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.ApplyAttemptIdentity
import ar.com.faro.v5.kernel.contracts.ExecutionReceipt
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope

/** SQL admission owned by the isolated ACTIVE9 DEV session. */
fun interface RealityFinance9ActiveDevGrantIssuer {
    /** Call only after a foreground confirmation of this canonical review. */
    fun issue(
        operationId: OperationId,
        objectScope: String,
        fingerprint: PlanFingerprint,
        intentKey: IdempotencyKey,
    ): RealityFinance9ActiveDevGrant?
}

/**
 * Bound to the issuing Reality store and scope. Invoke after the real receipt
 * is inserted, before SQLite COMMIT. A denial requires rolling back Apply.
 */
class RealityFinance9ActiveDevGrant internal constructor(
    private val consume: (RealityTransactionScope, ApplyAttemptIdentity, ExecutionReceipt) -> Boolean,
) {
    fun consumeInsideFinalCommit(
        scope: RealityTransactionScope,
        identity: ApplyAttemptIdentity,
        receipt: ExecutionReceipt,
    ): Boolean = consume(scope, identity, receipt)
}
