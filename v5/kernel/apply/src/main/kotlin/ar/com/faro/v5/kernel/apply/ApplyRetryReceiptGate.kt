package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.ApplyAttemptIdentity
import ar.com.faro.v5.kernel.contracts.ApplyAttemptIdentityPort
import ar.com.faro.v5.kernel.contracts.ExecutionReceipt
import ar.com.faro.v5.kernel.contracts.ExecutionReceiptLookupPort
import ar.com.faro.v5.kernel.contracts.PreparedPlanView

enum class ApplyRetryRejection {
    IDENTITY_PLAN_MISMATCH,
    RECEIPT_TRANSACTION_MISMATCH,
    RECEIPT_PLAN_CONFLICT,
}

sealed interface ApplyRetryResolution {
    data class Proceed(
        val identity: ApplyAttemptIdentity,
    ) : ApplyRetryResolution

    data class AlreadyCommitted(
        val receipt: ExecutionReceipt,
    ) : ApplyRetryResolution

    data class Rejected(
        val reason: ApplyRetryRejection,
    ) : ApplyRetryResolution
}

/**
 * Resolves retry identity and performs receipt-first short-circuiting before
 * owner execution or transaction entry.
 */
class ApplyRetryReceiptGate(
    private val identities: ApplyAttemptIdentityPort,
    private val receipts: ExecutionReceiptLookupPort,
) {
    fun resolve(preparedPlan: PreparedPlanView): ApplyRetryResolution {
        val identity = identities.resolve(preparedPlan)

        if (identity.planFingerprint != preparedPlan.planFingerprint) {
            return ApplyRetryResolution.Rejected(
                ApplyRetryRejection.IDENTITY_PLAN_MISMATCH,
            )
        }

        val existingReceipt = receipts.find(identity.transactionId)
            ?: return ApplyRetryResolution.Proceed(identity)

        if (existingReceipt.transactionId != identity.transactionId) {
            return ApplyRetryResolution.Rejected(
                ApplyRetryRejection.RECEIPT_TRANSACTION_MISMATCH,
            )
        }

        if (existingReceipt.planFingerprint != preparedPlan.planFingerprint) {
            return ApplyRetryResolution.Rejected(
                ApplyRetryRejection.RECEIPT_PLAN_CONFLICT,
            )
        }

        return ApplyRetryResolution.AlreadyCommitted(existingReceipt)
    }
}
