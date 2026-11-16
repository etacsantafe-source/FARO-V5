package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.finance.api.*
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W01B2FinanceObjectGrantOwnerTest {
    @TempDir lateinit var root: Path

    private inner class Rig(val label: String, val withPolicy: Boolean = true) : AutoCloseable {
        val pointer = FileActiveGenerationPointer(root.resolve("pointer-" + label))
        val generation = StorageGenerationId("generation-" + label)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val scope = S4W01B2FinanceApprovedScope(
            FinanceOperations.ACCOUNT_CREATE, "account-1",
            FinanceAccountId.of("account-1"), FinanceCurrencyCode.of("ARS"),
            PlanFingerprint("a".repeat(64)),
        )
        val context = ConversationPlanReviewContext(
            ConversationId("chat-" + label), "review-" + label, "token-" + label,
            1L, scope.planFingerprint,
        )
        var policyEpoch: String? = "policy-v1"
        var hostEpoch: String? = "host-v1"
        var hostReady = true
        var readEnabled = true
        val reader = S4W01B2FinanceApprovedScopeReadPort { request ->
            if (readEnabled && request == context) scope else null
        }
        val policy = if (withPolicy) S4W01B2FinancePolicyEpochPort {
                principal, candidate, observed ->
            if (principal == "principal:dev" && candidate == scope && observed == pinned)
                policyEpoch else null
        } else null
        fun freshOwner() = S4W01B2FinanceObjectGrantOwner(
            "principal:dev", pinned, pointer, { hostEpoch },
            "host-v1", reader, policy, { hostReady },
        )
        val owner = freshOwner()
        fun grant() = owner.grantAfterTrustedPolicyCheck(context, scope)
        override fun close() { owner.close() }
    }

    @Test fun trustedDevPolicyMintsExactScopeNonceOnly() {
        Rig("valid").use { f ->
            val nonce = requireNotNull(f.grant())
            assertEquals(nonce, f.owner.epochOrNull(f.scope, f.pinned))
            assertTrue(f.owner.withCurrentGrantLease(f.scope, f.pinned, nonce) {
                "read-only sample"
            } is S4W01B2FinanceLeaseResult.Admitted)
        }
    }

    @Test fun defaultDenyWithoutPolicyOrApprovedScope() {
        Rig("no-policy", withPolicy = false).use { f ->
            assertNull(f.grant())
            assertNull(f.owner.epochOrNull(f.scope, f.pinned))
        }
        Rig("no-review").use { f ->
            f.readEnabled = false
            assertNull(f.grant())
            assertNull(f.owner.epochOrNull(f.scope, f.pinned))
        }
    }

    @Test fun otherObjectOrOperationCannotBorrowGrant() {
        Rig("scope").use { f ->
            val nonce = requireNotNull(f.grant())
            val differentObject = f.scope.copy(
                objectId = "different", accountId = FinanceAccountId.of("different"),
            )
            val differentOperation = f.scope.copy(
                operationId = FinanceOperations.ENTRY_RECORD,
            )
            assertNull(f.owner.epochOrNull(differentObject, f.pinned))
            assertNull(f.owner.epochOrNull(differentOperation, f.pinned))
            assertEquals(S4W01B2FinanceLeaseResult.Denied,
                f.owner.withCurrentGrantLease(differentObject, f.pinned, nonce) { 5 })
            assertNull(f.owner.grantAfterTrustedPolicyCheck(f.context, differentObject))
        }
    }

    @Test fun fullPointerAbaAndHostEpochRevokeGrant() {
        Rig("aba").use { f ->
            val nonce = requireNotNull(f.grant())
            val other = StorageGenerationId("away")
            assertNotNull(f.pointer.compareAndSet(f.generation, other))
            assertNotNull(f.pointer.compareAndSet(other, f.generation))
            assertNull(f.owner.epochOrNull(f.scope, f.pinned))
            assertNull(f.grant())
            assertEquals(S4W01B2FinanceLeaseResult.Denied,
                f.owner.withCurrentGrantLease(f.scope, f.pinned, nonce) { 1 })
        }
        Rig("host").use { f ->
            assertNotNull(f.grant())
            f.hostEpoch = "new-host"
            assertNull(f.owner.epochOrNull(f.scope, f.pinned))
            f.hostEpoch = "host-v1"
            f.hostReady = false
            assertNull(f.grant())
        }
    }

    @Test fun policyEpochChangeInvalidatesAndNewNonceIsUnique() {
        Rig("policy").use { f ->
            val first = requireNotNull(f.grant())
            f.policyEpoch = "policy-v2"
            assertNull(f.owner.epochOrNull(f.scope, f.pinned))
            val second = requireNotNull(f.grant())
            assertNotEquals(first, second)
            f.owner.revoke(f.scope)
            assertNull(f.owner.epochOrNull(f.scope, f.pinned))
            val third = requireNotNull(f.grant())
            assertNotEquals(second, third)
        }
    }

    @Test fun concurrentRevokeWaitsUntilInProcessLeaseEnds() {
        Rig("lease").use { f ->
            val nonce = requireNotNull(f.grant())
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val revokeDone = CountDownLatch(1)
            val active = Thread {
                f.owner.withCurrentGrantLease(f.scope, f.pinned, nonce) {
                    entered.countDown()
                    check(release.await(3, TimeUnit.SECONDS))
                    true
                }
            }
            active.start()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val revoke = Thread {
                f.owner.revoke(f.scope)
                revokeDone.countDown()
            }
            revoke.start()
            try {
                assertFalse(revokeDone.await(40, TimeUnit.MILLISECONDS))
            } finally {
                release.countDown()
            }
            active.join(2000L)
            revoke.join(2000L)
            assertFalse(active.isAlive)
            assertFalse(revoke.isAlive)
            assertNull(f.owner.epochOrNull(f.scope, f.pinned))
        }
    }

    @Test fun newOwnerNeverRestoresPreviousNonce() {
        Rig("restart").use { f ->
            val old = requireNotNull(f.grant())
            f.owner.close()
            f.freshOwner().use { newer ->
                assertNull(newer.epochOrNull(f.scope, f.pinned))
                assertEquals(S4W01B2FinanceLeaseResult.Denied,
                    newer.withCurrentGrantLease(f.scope, f.pinned, old) { true })
                val next = requireNotNull(newer.grantAfterTrustedPolicyCheck(f.context, f.scope))
                assertNotEquals(old, next)
            }
        }
    }

    @Test fun ownerCloseInvalidPrincipalAndMissingHostDeny() {
        Rig("closed").use { f ->
            assertNotNull(f.grant())
            f.owner.revokeAll()
            assertNull(f.owner.epochOrNull(f.scope, f.pinned))
            f.owner.close()
            assertNull(f.grant())
            assertThrows(IllegalArgumentException::class.java) {
                S4W01B2FinanceObjectGrantOwner(
                    "", f.pinned, f.pointer, { "host-v1" }, "host-v1",
                    f.reader, f.policy, { true },
                )
            }
        }
    }
}
