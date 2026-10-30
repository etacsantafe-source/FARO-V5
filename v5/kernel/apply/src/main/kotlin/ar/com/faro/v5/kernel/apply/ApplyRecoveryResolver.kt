package ar.com.faro.v5.kernel.apply

import ar.com.faro.v5.kernel.contracts.ApplyAttemptJournalState
import ar.com.faro.v5.kernel.contracts.ApplyAttemptLookupPort
import ar.com.faro.v5.kernel.contracts.ApplyAttemptSnapshot
import ar.com.faro.v5.kernel.contracts.ExecutionOutcome
import ar.com.faro.v5.kernel.contracts.ExecutionOutcomeState
import ar.com.faro.v5.kernel.contracts.EffectCertainty
import ar.com.faro.v5.kernel.contracts.RetryDisposition

enum class ApplyRecoveryRejection {
    RETRY_GATE_REJECTED,
    ATTEMPT_TRANSACTION_MISMATCH,
    ATTEMPT_PLAN_MISMATCH,
    COMMITTED_ATTEMPT_WITHOUT_RECEIPT,
}

sealed interface ApplyRecoveryResolution {
    val outcome: ExecutionOutcome

    data class Committed(
        val receipt: ar.com.faro.v5.kernel.contracts.ExecutionReceipt,
        override val outcome: ExecutionOutcome = ExecutionOutcome(
            state = ExecutionOutcomeState.COMMITTED,
            retryDisposition = RetryDisposition.NOT_RETRYABLE,
            effectCertainty = EffectCertainty.EFFECT_COMMITTED,
        ),
    ) : ApplyRecoveryResolution

    data class NotStarted(
        val identity: ar.com.faro.v5.kernel.contracts.ApplyAttemptIdentity,
        override val outcome: ExecutionOutcome = ExecutionOutcome(
            state = ExecutionOutcomeState.NOT_STARTED,
            retryDisposition = RetryDisposition.RETRYABLE,
            effectCertainty = EffectCertainty.NO_EFFECT_KNOWN,
        ),
    ) : ApplyRecoveryResolution

    data class PreparedForRevalidation(
        val attempt: ApplyAttemptSnapshot,
        override val outcome: ExecutionOutcome = ExecutionOutcome(
            state = ExecutionOutcomeState.RECOVERING_UNKNOWN,
            retryDisposition = RetryDisposition.RETRYABLE,
            effectCertainty = EffectCertainty.EFFECT_UNKNOWN_RECOVERING,
        ),
    ) : ApplyRecoveryResolution

    data class Rejected(
        val reason: ApplyRecoveryRejection,
        override val outcome: ExecutionOutcome,
    ) : ApplyRecoveryResolution
}

class ApplyRecoveryResolver(
    private val retryGate: ApplyRetryReceiptGate,
    private val attempts: ApplyAttemptLookupPort,
) {
    fun resolve(
        preparedPlan: ar.com.faro.v5.kernel.contracts.PreparedPlanView,
    ): ApplyRecoveryResolution {
        return when (val retry = retryGate.resolve(preparedPlan)) {
            is ApplyRetryResolution.AlreadyCommitted ->
                ApplyRecoveryResolution.Committed(retry.receipt)

            is ApplyRetryResolution.Rejected ->
                ApplyRecoveryResolution.Rejected(
                    reason = ApplyRecoveryRejection.RETRY_GATE_REJECTED,
                    outcome = ExecutionOutcome(
                        state = ExecutionOutcomeState.REJECTED,
                        retryDisposition = RetryDisposition.REQUIRES_HUMAN_ACTION,
                        effectCertainty = EffectCertainty.EFFECT_UNKNOWN_RECOVERING,
                    ),
                )

            is ApplyRetryResolution.Proceed -> {
                val attempt = attempts.find(retry.identity.transactionId)
                    ?: return ApplyRecoveryResolution.NotStarted(retry.identity)

                if (attempt.transactionId != retry.identity.transactionId) {
                    return ApplyRecoveryResolution.Rejected(
                        reason = ApplyRecoveryRejection.ATTEMPT_TRANSACTION_MISMATCH,
                        outcome = inconsistentOutcome(),
                    )
                }

                if (attempt.planFingerprint != preparedPlan.planFingerprint) {
                    return ApplyRecoveryResolution.Rejected(
                        reason = ApplyRecoveryRejection.ATTEMPT_PLAN_MISMATCH,
                        outcome = inconsistentOutcome(),
                    )
                }

                when (attempt.state) {
                    ApplyAttemptJournalState.PREPARED ->
                        ApplyRecoveryResolution.PreparedForRevalidation(attempt)

                    ApplyAttemptJournalState.COMMITTED ->
                        ApplyRecoveryResolution.Rejected(
                            reason = ApplyRecoveryRejection.COMMITTED_ATTEMPT_WITHOUT_RECEIPT,
                            outcome = inconsistentOutcome(),
                        )
                }
            }
        }
    }

    private fun inconsistentOutcome() =
        ExecutionOutcome(
            state = ExecutionOutcomeState.RECOVERING_UNKNOWN,
            retryDisposition = RetryDisposition.REQUIRES_HUMAN_ACTION,
            effectCertainty = EffectCertainty.EFFECT_UNKNOWN_RECOVERING,
        )
}
