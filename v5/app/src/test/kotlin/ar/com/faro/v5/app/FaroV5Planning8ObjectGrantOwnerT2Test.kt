package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanOperations
import ar.com.faro.v5.owner.reminders.api.ReminderOperations
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Native durable pointer and real in-memory grant owner; POLICY is a fixture, not shipping. */
class FaroV5Planning8ObjectGrantOwnerT2Test {
    @TempDir lateinit var root: Path

    private val reminder = Planning8ObjectScope(ReminderOperations.CREATE, "rule-one")
    private val otherReminder = Planning8ObjectScope(ReminderOperations.CREATE, "rule-two")
    private val monthly = Planning8ObjectScope(MonthlyPlanOperations.ITEM_ADD, "rule-one")

    private inner class Fixture {
        val current = StorageGenerationId("planning-eight")
        val pointer = FileActiveGenerationPointer(root)
        val pinned: ActiveGenerationPointer = requireNotNull(pointer.compareAndSet(null, current))
        var hostEpoch: String? = "host-epoch-a"
        var principalAccepted = "trusted-local-session"
        var throwPolicy = false
        val epochs = mutableMapOf(reminder to "policy-a")
        val policy = Planning8TrustedObjectPolicyPort { principal, scope, observed ->
            if (throwPolicy) throw IllegalStateException("owner policy unavailable")
            if (principal != principalAccepted || observed != pinned) null
            else epochs[scope]
        }

        fun owner(
            principal: String = "trusted-local-session",
            policyPort: Planning8TrustedObjectPolicyPort? = policy,
        ) = FaroV5Planning8ObjectGrantOwner(
            principal, pinned, pointer, { hostEpoch }, "host-epoch-a", policyPort,
        )
    }

    @Test fun noTrustedPolicyMeansDenyEvenForHealthyHost() {
        val f = Fixture()
        f.owner(policyPort = null).use { owner ->
            assertNull(owner.epochOrNull(reminder, f.pinned))
            assertNull(owner.grantAfterTrustedPolicyCheck(reminder))
        }
    }

    @Test fun exactPrincipalScopeAndPointerCanIssueOneFreshGrant() {
        val f = Fixture()
        f.owner().use { owner ->
            assertNull(owner.epochOrNull(reminder, f.pinned))
            val issued = requireNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            assertEquals(issued, owner.epochOrNull(reminder, f.pinned))
        }
    }

    @Test fun otherObjectOrOperationCannotUseOneScopedGrant() {
        val f = Fixture()
        f.owner().use { owner ->
            assertNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            assertNull(owner.epochOrNull(otherReminder, f.pinned))
            assertNull(owner.epochOrNull(monthly, f.pinned))
            assertNull(owner.grantAfterTrustedPolicyCheck(otherReminder))
            assertNull(owner.grantAfterTrustedPolicyCheck(monthly))
            assertNotNull(owner.epochOrNull(reminder, f.pinned))
        }
    }

    @Test fun revokeOneScopeDoesNotEraseDifferentAuthorizedScope() {
        val f = Fixture()
        f.epochs[otherReminder] = "policy-other"
        f.owner().use { owner ->
            val a = requireNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            val b = requireNotNull(owner.grantAfterTrustedPolicyCheck(otherReminder))
            assertNotEquals(a, b)
            owner.revoke(reminder)
            assertNull(owner.epochOrNull(reminder, f.pinned))
            assertEquals(b, owner.epochOrNull(otherReminder, f.pinned))
        }
    }

    @Test fun regrantSameObjectHasNewNonceAndRevokedGrantNeverReturns() {
        val f = Fixture()
        f.owner().use { owner ->
            val original = requireNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            owner.revoke(reminder)
            assertNull(owner.epochOrNull(reminder, f.pinned))
            val fresh = requireNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            assertNotEquals(original, fresh)
            owner.revokeAll()
            assertNull(owner.epochOrNull(reminder, f.pinned))
        }
    }

    @Test fun lostHostEpochRevokesAndCannotReissueFromOriginalHost() {
        val f = Fixture()
        f.owner().use { owner ->
            assertNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            f.hostEpoch = "host-epoch-b"
            assertNull(owner.epochOrNull(reminder, f.pinned))
            assertNull(owner.grantAfterTrustedPolicyCheck(reminder))
        }
    }

    @Test fun policyRevokeAndNewPolicyEpochInvalidateOldGrant() {
        val f = Fixture()
        f.owner().use { owner ->
            val old = requireNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            f.epochs.remove(reminder)
            assertNull(owner.epochOrNull(reminder, f.pinned))
            f.epochs[reminder] = "policy-b"
            assertNull(owner.epochOrNull(reminder, f.pinned))
            val fresh = requireNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            assertNotEquals(old, fresh)
        }
    }

    @Test fun pointerSwitchAwayAndBackCannotReviveOrReissueOldGrant() {
        val f = Fixture()
        f.owner().use { owner ->
            assertNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            val other = StorageGenerationId("another-generation")
            assertNotNull(f.pointer.compareAndSet(f.current, other))
            val back = requireNotNull(f.pointer.compareAndSet(other, f.current))
            assertTrue(back.sequence > f.pinned.sequence)
            assertNull(owner.epochOrNull(reminder, f.pinned))
            assertNull(owner.grantAfterTrustedPolicyCheck(reminder))
        }
    }

    @Test fun forgedCallerPointerIsNotTrustedEvenForSameGeneration() {
        val f = Fixture()
        f.owner().use { owner ->
            assertNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            val forged = ActiveGenerationPointer(f.pinned.sequence + 1, f.current)
            assertNull(owner.epochOrNull(reminder, forged))
            assertNull(owner.epochOrNull(reminder, f.pinned))
        }
    }

    @Test fun distinctPrincipalIsDeniedByIndependentPolicy() {
        val f = Fixture()
        f.owner(principal = "other-session").use { owner ->
            assertNull(owner.grantAfterTrustedPolicyCheck(reminder))
            assertNull(owner.epochOrNull(reminder, f.pinned))
        }
    }

    @Test fun closedOwnerNeverRestoresOrReissuesGrant() {
        val f = Fixture()
        val owner = f.owner()
        assertNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
        owner.close()
        assertNull(owner.epochOrNull(reminder, f.pinned))
        assertNull(owner.grantAfterTrustedPolicyCheck(reminder))
        owner.revokeAll()
        assertNull(owner.epochOrNull(reminder, f.pinned))
    }

    @Test fun policyExceptionsFailClosedAndRevokeExistingGrant() {
        val f = Fixture()
        f.owner().use { owner ->
            assertNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            f.throwPolicy = true
            assertNull(owner.epochOrNull(reminder, f.pinned))
            assertNull(owner.grantAfterTrustedPolicyCheck(reminder))
        }
    }

    @Test fun missingOrBlankHostAndPolicyEpochsDenyIssuance() {
        val f = Fixture()
        f.owner().use { owner ->
            f.hostEpoch = null
            assertNull(owner.grantAfterTrustedPolicyCheck(reminder))
            f.hostEpoch = "host-epoch-a"
            f.epochs[reminder] = "  "
            assertNull(owner.grantAfterTrustedPolicyCheck(reminder))
            assertNull(owner.epochOrNull(reminder, f.pinned))
        }
    }

    @Test fun freshOwnerNeverInheritsPriorOwnersGrantsOrNonces() {
        val f = Fixture()
        val old = f.owner()
        val first = requireNotNull(old.grantAfterTrustedPolicyCheck(reminder))
        f.owner().use { fresh ->
            assertNull(fresh.epochOrNull(reminder, f.pinned))
            val second = requireNotNull(fresh.grantAfterTrustedPolicyCheck(reminder))
            assertNotEquals(first, second)
        }
        old.close()
    }

    @Test fun leaseAdmitsOnlyExactLiveNonceAndReturnsCallbackValue() {
        val f = Fixture()
        f.owner().use { owner ->
            val nonce = requireNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            var calls = 0
            val result = owner.withCurrentGrantLease(reminder, f.pinned, nonce) {
                calls++
                assertEquals(nonce, owner.epochOrNull(reminder, f.pinned))
                "sql-commit-complete"
            }
            assertEquals(Planning8GrantLeaseResult.Admitted("sql-commit-complete"), result)
            assertEquals(1, calls)
            assertEquals(nonce, owner.epochOrNull(reminder, f.pinned))
        }
    }

    @Test fun deniedLeaseNeverCallsActionForMissingWrongOrRevokedGrant() {
        val f = Fixture()
        f.owner().use { owner ->
            var calls = 0
            fun attempt(scope: Planning8ObjectScope, nonce: String) =
                owner.withCurrentGrantLease(scope, f.pinned, nonce) {
                    calls++
                    "unexpected"
                }
            assertEquals(Planning8GrantLeaseResult.Denied, attempt(reminder, "fake-nonce"))
            val nonce = requireNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            assertEquals(Planning8GrantLeaseResult.Denied, attempt(reminder, " "))
            assertEquals(Planning8GrantLeaseResult.Denied, attempt(otherReminder, nonce))
            assertEquals(Planning8GrantLeaseResult.Denied, attempt(reminder, "fake-nonce"))
            owner.revoke(reminder)
            assertEquals(Planning8GrantLeaseResult.Denied, attempt(reminder, nonce))
            assertEquals(0, calls)
        }
    }

    @Test fun ownerRevocationCannotCompleteUntilLeasedTransactionCallbackExits() {
        val f = Fixture()
        f.owner().use { owner ->
            val nonce = requireNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val revokerStarting = CountDownLatch(1)
            val revocationComplete = CountDownLatch(1)
            val workerError = AtomicReference<Throwable?>(null)
            val leasedResult = AtomicReference<Planning8GrantLeaseResult<String>?>(null)
            val transaction = Thread {
                try {
                    leasedResult.set(owner.withCurrentGrantLease(reminder, f.pinned, nonce) {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS)) { "simulated commit never released" }
                        "committed"
                    })
                } catch (failure: Throwable) {
                    workerError.set(failure)
                }
            }
            val revoker = Thread {
                revokerStarting.countDown()
                owner.revoke(reminder)
                revocationComplete.countDown()
            }
            transaction.start()
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                revoker.start()
                assertTrue(revokerStarting.await(5, TimeUnit.SECONDS))
                assertFalse(revocationComplete.await(200, TimeUnit.MILLISECONDS))
            } finally {
                release.countDown()
                transaction.join(5000)
                if (revoker.state != Thread.State.NEW) revoker.join(5000)
            }
            assertFalse(transaction.isAlive)
            assertFalse(revoker.isAlive)
            assertNull(workerError.get())
            assertEquals(Planning8GrantLeaseResult.Admitted("committed"), leasedResult.get())
            assertEquals(0L, revocationComplete.count)
            assertNull(owner.epochOrNull(reminder, f.pinned))
        }
    }

    @Test fun callbackFailureReleasesMonitorAndAllowsSafeRevocation() {
        val f = Fixture()
        f.owner().use { owner ->
            val nonce = requireNotNull(owner.grantAfterTrustedPolicyCheck(reminder))
            assertThrows(IllegalStateException::class.java) {
                owner.withCurrentGrantLease(reminder, f.pinned, nonce) {
                    error("transaction rollback")
                }
            }
            owner.revokeAll()
            assertEquals(Planning8GrantLeaseResult.Denied,
                owner.withCurrentGrantLease(reminder, f.pinned, nonce) { "never" })
        }
    }

}
