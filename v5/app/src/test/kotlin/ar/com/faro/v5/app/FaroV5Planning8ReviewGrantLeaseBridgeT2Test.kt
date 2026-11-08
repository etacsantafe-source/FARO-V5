package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanOperations
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * N033C2B4 verifies REVIEW→exact scope→grant monitor only.
 * Policy below is an explicit test fixture, NOT an Android/host authority.
 * No Reality mutation or user-facing Apply capability is exercised here.
 */
class FaroV5Planning8ReviewGrantLeaseBridgeT2Test {
    @TempDir lateinit var root: Path

    private val target = Planning8ObjectScope(ReminderOperations.CREATE, "rule-a")
    private val another = Planning8ObjectScope(ReminderOperations.CREATE, "rule-b")
    private val differentOperation = Planning8ObjectScope(MonthlyPlanOperations.ITEM_ADD, "rule-a")

    private inner class Fixture : AutoCloseable {
        val generation = StorageGenerationId("review-lease-gen")
        val pointer = FileActiveGenerationPointer(root)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val workspace = ConversationWorkspaceStore.withPlanVerifier(
            root.resolve("workspace.db").toString(), CanonicalPreparedPlanEnvelopeVerifier,
        )
        var ready = true
        var hostEpoch: String? = "host-lease-epoch"
        val policies = mutableMapOf(target to "trusted-policy-fixture-A")
        val policy = Planning8TrustedObjectPolicyPort { principal, scope, observed ->
            if (principal == "synthetic-local-sandbox" && observed == pinned)
                policies[scope] else null
        }
        val owner = FaroV5Planning8ObjectGrantOwner(
            "synthetic-local-sandbox", pinned, pointer,
            { hostEpoch }, "host-lease-epoch", policy,
        )
        val descriptor = OperationContractDescriptor(
            ReminderOperations.CREATE, ReminderOperations.CAPABILITY_ID,
            ReminderOperations.EXECUTOR_ID, ExecutionProfile.REALITY_MUTATION,
            ContractVersion("1"), ContractFingerprint("test-reminder-create-v1"),
            ContractExposure.BINDABLE,
        )
        val conversation = ConversationId("review-lease-chat")
        val plan: PreparedPlan
        val review: ConversationPlanReviewSnapshot
        val scopeGuard: FaroV5Planning8ApprovedObjectGate
        val bridge: FaroV5Planning8ReviewGrantLeaseBridge

        init {
            val record = ReminderRule(
                ReminderRuleId.of("rule-a"), "Recordar cita", "UTC",
                LocalDate.of(2026, 11, 20), LocalTime.of(10, 0),
                ReminderRecurrence.ONCE, true, null, 1000L,
            )
            val operation = PreparedOperation(
                descriptor, ReminderCreatePayload.payload(record),
                DependencyStampSet.of(DependencyStamp(
                    AuthorityBoundaryId("reality.planning8"), DependencyKey("owner"),
                    DependencyStampValue("revision:0"),
                )),
                IdempotencyKey("review-lease-op"),
            )
            plan = PreparedPlan.of(
                ApplyPlanNode(PlanNodeId("create-rule"), operation, emptySet())
            )
            assertTrue(runBlocking {
                workspace.createAndSelect(conversation, 100L, IdempotencyKey("chat-create"))
            } is ConversationSelectionResult.Saved)
            val token = runBlocking {
                workspace.readTimeline(conversation)
            }!!.authorityToken
            val proposal = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                conversation, "review-lease-id", token, workspace.snapshot().revision,
                plan.planFingerprint, "Confirmar recordatorio",
                PreparedPlanEnvelopeCodec.encode(plan), IdempotencyKey("create-review"),
            )) as WorkspacePlanReviewWriteResult.Saved
            review = (runBlocking {
                workspace.decide(
                    ConversationPlanReviewIntent.Approve(proposal.review.context()),
                    IdempotencyKey("human-approve"),
                )
            } as ConversationReviewDecision.Saved).review
            scopeGuard = FaroV5Planning8ApprovedObjectGate(
                generation.value, pointer, { ready }, workspace,
                OperationContractLookupPort { id ->
                    descriptor.takeIf { it.operationId == id }
                }, owner,
            )
            bridge = FaroV5Planning8ReviewGrantLeaseBridge(
                scopeGuard, owner, pointer, { ready },
            )
        }

        fun grant(scope: Planning8ObjectScope = target): String =
            requireNotNull(owner.grantAfterTrustedPolicyCheck(scope))

        fun <T> execute(
            scope: Planning8ObjectScope = target, nonce: String,
            action: () -> T,
        ): Planning8GrantLeaseResult<T> =
            bridge.withReviewedGrantLease(review.context(), scope, nonce, action)

        override fun close() {
            owner.close()
            workspace.close()
        }
    }

    @Test fun missingGrantNeverExecutesEvenWithApprovedCanonicalReview() {
        Fixture().use { f ->
            assertNull(f.owner.epochOrNull(target, f.pinned))
            val calls = AtomicInteger()
            assertEquals(Planning8GrantLeaseResult.Denied,
                f.execute(nonce = "invented-nonce") { calls.incrementAndGet() })
            assertEquals(0, calls.get())
        }
    }

    @Test fun exactReviewObjectAndGrantCanEnterOneSynchronousCallback() {
        Fixture().use { f ->
            val nonce = f.grant()
            assertEquals(target, f.scopeGuard.currentApprovedObjectScopeOrNull(
                f.review.context()))
            val calls = AtomicInteger()
            assertEquals(Planning8GrantLeaseResult.Admitted("sync-value"),
                f.execute(nonce = nonce) {
                    assertEquals(nonce, f.owner.epochOrNull(target, f.pinned))
                    calls.incrementAndGet()
                    "sync-value"
                })
            assertEquals(1, calls.get())
        }
    }

    @Test fun reviewOfObjectACannotBorrowSimultaneousGrantForObjectBOrAnotherOperation() {
        Fixture().use { f ->
            f.grant()
            f.policies[another] = "trusted-policy-fixture-B"
            f.policies[differentOperation] = "trusted-policy-fixture-other-operation"
            val b = f.grant(another)
            val otherOp = f.grant(differentOperation)
            val calls = AtomicInteger()
            assertEquals(Planning8GrantLeaseResult.Denied,
                f.execute(scope = another, nonce = b) { calls.incrementAndGet() })
            assertEquals(Planning8GrantLeaseResult.Denied,
                f.execute(scope = differentOperation, nonce = otherOp) {
                    calls.incrementAndGet()
                })
            assertEquals(0, calls.get())
        }
    }

    @Test fun discardedReviewCannotStartLeaseOrExecute() {
        Fixture().use { f ->
            val nonce = f.grant()
            assertTrue(runBlocking {
                f.workspace.decide(
                    ConversationPlanReviewIntent.Discard(f.review.context()),
                    IdempotencyKey("human-discard"),
                )
            } is ConversationReviewDecision.Saved)
            val calls = AtomicInteger()
            assertEquals(Planning8GrantLeaseResult.Denied,
                f.execute(nonce = nonce) { calls.incrementAndGet() })
            assertEquals(0, calls.get())
        }
    }

    @Test fun generationPointerAbaAndLostHostReadinessDenyOldLease() {
        Fixture().use { f ->
            val nonce = f.grant()
            f.ready = false
            assertEquals(Planning8GrantLeaseResult.Denied,
                f.execute(nonce = nonce) { "not-reached" })
            f.ready = true
            val otherGeneration = StorageGenerationId("review-lease-other")
            assertNotNull(f.pointer.compareAndSet(f.generation, otherGeneration))
            val back = requireNotNull(
                f.pointer.compareAndSet(otherGeneration, f.generation))
            assertTrue(back.sequence > f.pinned.sequence)
            assertEquals(Planning8GrantLeaseResult.Denied,
                f.execute(nonce = nonce) { "not-reached" })
        }
    }

    @Test fun concurrentOwnerRevokeCannotFinishBeforeLeasedCallbackReturns() {
        Fixture().use { f ->
            val nonce = f.grant()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val revokeStarted = CountDownLatch(1)
            val revokeFinished = AtomicBoolean(false)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val applying = pool.submit<Planning8GrantLeaseResult<String>> {
                    f.execute(nonce = nonce) {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        "callback-ended"
                    }
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val revocation = pool.submit {
                    revokeStarted.countDown()
                    f.owner.revoke(target)
                    revokeFinished.set(true)
                }
                assertTrue(revokeStarted.await(5, TimeUnit.SECONDS))
                assertFalse(revokeFinished.get()) // still inside grant monitor
                release.countDown()
                assertEquals(Planning8GrantLeaseResult.Admitted("callback-ended"),
                    applying.get(5, TimeUnit.SECONDS))
                revocation.get(5, TimeUnit.SECONDS)
                assertTrue(revokeFinished.get())
                assertNull(f.owner.epochOrNull(target, f.pinned))
            } finally {
                release.countDown()
                pool.shutdownNow()
            }
        }
    }

    @Test fun callbackFailureReleasesOwnerMonitorAndNeverReportsSuccess() {
        Fixture().use { f ->
            val nonce = f.grant()
            assertThrows(IllegalStateException::class.java) {
                f.execute(nonce = nonce) {
                    error("synthetic callback failure")
                }
            }
            f.owner.revoke(target)
            assertNull(f.owner.epochOrNull(target, f.pinned))
            assertEquals(Planning8GrantLeaseResult.Denied,
                f.execute(nonce = nonce) { "not-reached" })
        }
    }
}
