package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.ApplyAttemptIdentity
import ar.com.faro.v5.kernel.contracts.ExecutionReceipt
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope

/**
 * M1C1B1B: typed STAGED9-only grant reference for an already admitted DEV host.
 * The reference is NOT a grant issuer, a principal authenticator or a capability.
 * The Reality9 SQL owner revalidates every field inside the active Apply transaction.
 */
data class Finance9TrustedStageGrant(
    val principalHash: String,
    val generationId: String,
    val pointerSequence: Long,
    val operationId: String,
    val objectScope: String,
    val grantId: String,
    val expectedPolicyEpoch: Long,
) {
    init {
        require(Regex("[0-9a-f]{64}").matches(principalHash))
        require(pointerSequence > 0L && expectedPolicyEpoch > 0L)
        for (value in listOf(generationId, operationId, objectScope, grantId)) {
            require(value.length in 1..128 && value == value.trim() &&
                value.none { it.code < 32 || it.code == 127 })
        }
    }
}

/**
 * Narrow cross-module boundary: no SQLite handle, no grant issuance, no generic
 * transaction control. Always called AFTER the real receipt but BEFORE SQL COMMIT.
 * Only RealityStore STAGED9 can supply a live implementation; false -> rollback.
 */
fun interface RealityFinance9TrustedStageGrantPort {
    fun consumeInsideFinalCommit(
        transactionScope: RealityTransactionScope,
        grant: Finance9TrustedStageGrant,
        identity: ApplyAttemptIdentity,
        receipt: ExecutionReceipt,
        consumedAtEpochMillis: Long,
    ): Boolean
}
