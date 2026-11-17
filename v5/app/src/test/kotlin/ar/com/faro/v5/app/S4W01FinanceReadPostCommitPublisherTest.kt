package ar.com.faro.v5.app

import ar.com.faro.v5.store.reality.Finance9CommittedReadPolicyChange
import ar.com.faro.v5.store.reality.Finance9TrustedReadPolicyIssueRequest
import ar.com.faro.v5.store.reality.Finance9TrustedReadPolicyRevocationRequest
import ar.com.faro.v5.store.reality.RealityFinance9CommittedReadPolicyChangePort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Eight NEW JVM handoff checks; not Android two-PID IPC certification. */
class S4W01FinanceReadPostCommitPublisherTest {
    private fun issue() = Finance9TrustedReadPolicyIssueRequest(
        "a".repeat(64), "committed-policy", 3L, 1000L, 5000L,
    )

    private fun revoke() = Finance9TrustedReadPolicyRevocationRequest(
        "a".repeat(64), "committed-policy", 3L,
    )

    private class Stub : RealityFinance9CommittedReadPolicyChangePort {
        var issued: Finance9CommittedReadPolicyChange? = null
        var revoked: Finance9CommittedReadPolicyChange? = null
        var fail = false
        var entered = false
        var afterReturn = false
        override fun issueAfterExternallyVerifiedReadAdmission(
            request: Finance9TrustedReadPolicyIssueRequest,
        ): Finance9CommittedReadPolicyChange? {
            entered = true
            if (fail) throw IllegalStateException("synthetic SQL error")
            afterReturn = true
            return issued
        }
        override fun revokeAfterExternallyVerifiedReadAdmission(
            request: Finance9TrustedReadPolicyRevocationRequest,
        ): Finance9CommittedReadPolicyChange? {
            entered = true
            if (fail) throw IllegalStateException("synthetic SQL error")
            afterReturn = true
            return revoked
        }
    }

    @Test fun a01AbsentSqlResultNeverNotifies() {
        val fake = Stub()
        var count = 0
        val adapter = S4W01FinanceReadPostCommitPublisher(fake) { count++; true }
        assertNull(adapter.issueAfterExternallyVerifiedReadAdmission(issue()))
        assertEquals(0, count)
        assertTrue(fake.entered)
    }

    @Test fun a02IssuedCommittedResultNotifiesOnlyAfterPortReturns() {
        val fake = Stub().apply { issued = Finance9CommittedReadPolicyChange(1L, true) }
        var count = 0
        val adapter = S4W01FinanceReadPostCommitPublisher(fake) {
            assertTrue(fake.afterReturn)
            count++
            true
        }
        assertEquals(S4W01FinanceReadPolicyNoticeReceipt(1L, true, true),
            adapter.issueAfterExternallyVerifiedReadAdmission(issue()))
        assertEquals(1, count)
    }

    @Test fun a03RevokedCommittedTransitionNotifiesOnce() {
        val fake = Stub().apply { revoked = Finance9CommittedReadPolicyChange(2L, true) }
        var count = 0
        val adapter = S4W01FinanceReadPostCommitPublisher(fake) { count++; true }
        assertEquals(S4W01FinanceReadPolicyNoticeReceipt(2L, true, true),
            adapter.revokeAfterExternallyVerifiedReadAdmission(revoke()))
        assertEquals(1, count)
    }

    @Test fun a04IdempotentRevokeNeverNotifies() {
        val fake = Stub().apply { revoked = Finance9CommittedReadPolicyChange(2L, false) }
        var count = 0
        val adapter = S4W01FinanceReadPostCommitPublisher(fake) { count++; true }
        assertEquals(S4W01FinanceReadPolicyNoticeReceipt(2L, false, false),
            adapter.revokeAfterExternallyVerifiedReadAdmission(revoke()))
        assertEquals(0, count)
    }

    @Test fun a05ReissueIsNewTransitionAndCanNotifyAgain() {
        val fake = Stub().apply { issued = Finance9CommittedReadPolicyChange(1L, true) }
        var count = 0
        val adapter = S4W01FinanceReadPostCommitPublisher(fake) { count++; true }
        assertNotNull(adapter.issueAfterExternallyVerifiedReadAdmission(issue()))
        fake.issued = Finance9CommittedReadPolicyChange(3L, true)
        assertEquals(S4W01FinanceReadPolicyNoticeReceipt(3L, true, true),
            adapter.issueAfterExternallyVerifiedReadAdmission(issue()))
        assertEquals(2, count)
    }

    @Test fun a06UnavailableAndroidDeliveryKeepsCommittedSqlOutcome() {
        val fake = Stub().apply { revoked = Finance9CommittedReadPolicyChange(2L, true) }
        val adapter = S4W01FinanceReadPostCommitPublisher(fake) { false }
        assertEquals(S4W01FinanceReadPolicyNoticeReceipt(2L, true, false),
            adapter.revokeAfterExternallyVerifiedReadAdmission(revoke()))
    }

    @Test fun a07ThrownAndroidDeliveryIsReportedAsNotDelivered() {
        val fake = Stub().apply { issued = Finance9CommittedReadPolicyChange(4L, true) }
        val adapter = S4W01FinanceReadPostCommitPublisher(fake) {
            throw IllegalStateException("synthetic ContentResolver error")
        }
        assertEquals(S4W01FinanceReadPolicyNoticeReceipt(4L, true, false),
            adapter.issueAfterExternallyVerifiedReadAdmission(issue()))
    }

    @Test fun a08FailedSqlAndAbsentRevocationNeverNotify() {
        val fake = Stub().apply { fail = true }
        var count = 0
        val adapter = S4W01FinanceReadPostCommitPublisher(fake) { count++; true }
        assertNull(adapter.issueAfterExternallyVerifiedReadAdmission(issue()))
        fake.fail = false
        assertNull(adapter.revokeAfterExternallyVerifiedReadAdmission(revoke()))
        assertEquals(0, count)
    }
}
