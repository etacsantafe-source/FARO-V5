package ar.com.faro.v5.app

import android.content.Context
import ar.com.faro.v5.store.reality.Finance9CommittedReadPolicyChange
import ar.com.faro.v5.store.reality.Finance9TrustedReadPolicyIssueRequest
import ar.com.faro.v5.store.reality.Finance9TrustedReadPolicyRevocationRequest
import ar.com.faro.v5.store.reality.RealityFinance9CommittedReadPolicyChangePort
import ar.com.faro.v5.store.reality.RealityStore

/**
 * Read-only observation of a completed DEV SQL policy operation + IPC attempt.
 * notified=false does NOT undo SQL. Neither field authorizes Finance rendering.
 */
internal data class S4W01FinanceReadPolicyNoticeReceipt(
    val committedPolicyEpoch: Long,
    val changed: Boolean,
    val notified: Boolean,
)

/**
 * DEV-only cooperative change path. The port has already returned from SQL
 * COMMIT before we attempt ContentResolver.notifyChange. No payload is sent.
 *
 * This adapter is DORMANT until explicitly called by a future DEV read owner.
 * Raw sqlite3 writers and process-death gaps must be caught on ON_RESUME SQL.
 */
internal class S4W01FinanceReadPostCommitPublisher(
    private val committedChanges: RealityFinance9CommittedReadPolicyChangePort,
    private val sendEmptyNotice: () -> Boolean,
) {
    fun issueAfterExternallyVerifiedReadAdmission(
        request: Finance9TrustedReadPolicyIssueRequest,
    ): S4W01FinanceReadPolicyNoticeReceipt? = deliver(
        runCatching {
            committedChanges.issueAfterExternallyVerifiedReadAdmission(request)
        }.getOrNull(),
    )

    fun revokeAfterExternallyVerifiedReadAdmission(
        request: Finance9TrustedReadPolicyRevocationRequest,
    ): S4W01FinanceReadPolicyNoticeReceipt? = deliver(
        runCatching {
            committedChanges.revokeAfterExternallyVerifiedReadAdmission(request)
        }.getOrNull(),
    )

    private fun deliver(
        outcome: Finance9CommittedReadPolicyChange?,
    ): S4W01FinanceReadPolicyNoticeReceipt? {
        if (outcome == null) return null
        val notified = outcome.changed && runCatching {
            sendEmptyNotice()
        }.getOrDefault(false)
        return S4W01FinanceReadPolicyNoticeReceipt(
            committedPolicyEpoch = outcome.observedPolicyEpoch,
            changed = outcome.changed,
            notified = notified,
        )
    }

    companion object {
        /**
         * No shipping/V2 CORE access: W01 has a separate package with the
         * already certified private provider. Does NOT authenticate a human.
         */
        fun forIsolatedW01DevOnly(
            context: Context,
            stagedReality: RealityStore,
        ): S4W01FinanceReadPostCommitPublisher {
            require(context.packageName == "ar.com.faro.alpha.w01financedev") {
                "Finance read notifications require isolated W01 DEV package"
            }
            return S4W01FinanceReadPostCommitPublisher(
                stagedReality.finance9CommittedReadPolicyChangesForDevOnly(),
                { S4W01FinanceReadPrivateInvalidation
                    .publishAfterVerifiedCommitForDevOnly(context) },
            )
        }
    }
}
