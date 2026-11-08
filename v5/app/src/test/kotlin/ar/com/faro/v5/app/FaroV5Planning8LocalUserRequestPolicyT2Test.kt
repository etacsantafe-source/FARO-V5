package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.monthlyplan.api.MonthlyPlanOperations
import ar.com.faro.v5.owner.reminders.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Facts below are JVM fixtures, NOT an Android Activity or human identity.
 * The authority remains disconnected from both shipping and DEV runtimes.
 */
class FaroV5Planning8LocalUserRequestPolicyT2Test {
    @TempDir lateinit var root: Path
    private val scope = Planning8ObjectScope(ReminderOperations.CREATE, "request-rule")
    private val other = Planning8ObjectScope(ReminderOperations.CREATE, "other-rule")
    private val otherOperation =
        Planning8ObjectScope(MonthlyPlanOperations.ITEM_ADD, "request-rule")
    private val appId = "ar.com.faro.alpha.v2coredev"

    private inner class Fixture : AutoCloseable {
        val generation = StorageGenerationId("request-generation")
        val pointer = FileActiveGenerationPointer(root)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val workspace = ConversationWorkspaceStore.withPlanVerifier(
            root.resolve("workspace.db").toString(), CanonicalPreparedPlanEnvelopeVerifier,
        )
        val reality = RealityStore(root.resolve("reality.db").toString(), planningStage8 = true)
        val descriptor = OperationContractDescriptor(
            ReminderOperations.CREATE, ReminderOperations.CAPABILITY_ID,
            ReminderOperations.EXECUTOR_ID, ExecutionProfile.REALITY_MUTATION,
            ContractVersion("1"), ContractFingerprint("test-reminder-create-v1"),
            ContractExposure.BINDABLE,
        )
        val conversation = ConversationId("request-local-chat")
        var hostReady = true
        var hostEpoch: String? = "request-host-epoch"
        var processUid = 10510
        val facts = AndroidSandboxFactsReadPort {
            AndroidSandboxProcessFacts(
                processUid = processUid, applicationUid = 10510,
                processIsApplicationUid = true, processIsIsolated = false,
                processIsSdkSandbox = false, applicationId = appId,
            )
        }
        val sandbox = FaroV5AndroidSandboxPrincipal(
            appId, pinned, pointer, "request-host-epoch",
            { hostEpoch }, facts,
        )
        val review: ConversationPlanReviewSnapshot
        val plan: PreparedPlan
        val canonical: FaroV5Planning8ApprovedObjectGate
        val policy: FaroV5Planning8LocalUserRequestPolicy

        init {
            val rule = ReminderRule(
                ReminderRuleId.of("request-rule"), "Recordar reunión", "UTC",
                LocalDate.of(2026, 11, 20), LocalTime.of(10, 0),
                ReminderRecurrence.ONCE, true, null, 1000L,
            )
            val operation = PreparedOperation(
                descriptor, ReminderCreatePayload.payload(rule),
                DependencyStampSet.of(DependencyStamp(
                    AuthorityBoundaryId("reality.planning8"), DependencyKey("owner"),
                    DependencyStampValue("revision:0"),
                )),
                IdempotencyKey("local-request-operation"),
            )
            plan = PreparedPlan.of(
                ApplyPlanNode(PlanNodeId("request-rule-node"), operation, emptySet())
            )
            assertTrue(runBlocking {
                workspace.createAndSelect(
                    conversation, 100L, IdempotencyKey("local-request-chat"))
            } is ConversationSelectionResult.Saved)
            val token = runBlocking { workspace.readTimeline(conversation) }!!.authorityToken
            val proposed = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                conversation, "review-local-request", token, workspace.snapshot().revision,
                plan.planFingerprint, "Confirmar recordatorio",
                PreparedPlanEnvelopeCodec.encode(plan),
                IdempotencyKey("create-local-request-review"),
            )) as WorkspacePlanReviewWriteResult.Saved
            review = (runBlocking {
                workspace.decide(
                    ConversationPlanReviewIntent.Approve(proposed.review.context()),
                    IdempotencyKey("approve-local-request"),
                )
            } as ConversationReviewDecision.Saved).review
            canonical = FaroV5Planning8ApprovedObjectGate(
                generation.value, pointer, { hostReady }, workspace,
                OperationContractLookupPort { id ->
                    descriptor.takeIf { it.operationId == id }
                }, null,
            )
            policy = FaroV5Planning8LocalUserRequestPolicy(
                canonical, sandbox, pointer, { hostReady },
            )
        }

        fun issue() = policy.issueAfterTrustedHumanApplyRequest(review.context())

        fun beginApply(): Pair<ConversationPlanReviewSnapshot, ApplyAttemptIdentity> {
            val identity = reality.applyAttemptIdentities.resolve(plan)
            val result = workspace.advance(
                ConversationPlanReviewIntent.BeginApply(review.context(), identity),
                IdempotencyKey("local-request-begin-apply"),
                reality.applyAttemptLookup, reality.executionReceiptLookup,
            )
            val saved = result as ConversationReviewDecision.Saved
            assertEquals(ConversationPlanReviewState.APPLYING, saved.review.state)
            return saved.review to identity
        }

        fun owner(ticket: Planning8LocalUserRequestTicket) =
            FaroV5Planning8ObjectGrantOwner(
                ticket.principalId, pinned, pointer,
                { hostEpoch }, "request-host-epoch", policy,
            )

        override fun close() {
            policy.close()
            sandbox.close()
            workspace.close()
            reality.close()
        }
    }

    @Test fun canonicalApprovedScopeAloneCannotGrantWriteOrIssuePolicyEpoch() {
        Fixture().use { f ->
            assertEquals(scope, f.canonical.currentApprovedScopeForTrustedRequestOrNull(
                f.review.context()))
            assertFalse(f.canonical.hasCurrentApprovedObjectReview(f.review.context()))
            val principal = requireNotNull(f.policy.principalIdOrNull())
            assertNull(f.policy.policyEpochOrNull(principal, scope, f.pinned))
            val unissuedOwner = FaroV5Planning8ObjectGrantOwner(
                principal, f.pinned, f.pointer,
                { f.hostEpoch }, "request-host-epoch", f.policy,
            )
            unissuedOwner.use { assertNull(it.grantAfterTrustedPolicyCheck(scope)) }
        }
    }

    @Test fun explicitOneShotRequestEnablesOnlyMatchingScopedOwnerGrant() {
        Fixture().use { f ->
            val ticket = requireNotNull(f.issue())
            assertEquals(scope, ticket.scope)
            assertEquals(ticket.policyEpoch,
                f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
            f.owner(ticket).use { owner ->
                val nonce = requireNotNull(owner.grantAfterTrustedPolicyCheck(scope))
                assertEquals(nonce, owner.epochOrNull(scope, f.pinned))
                assertNull(owner.grantAfterTrustedPolicyCheck(other))
                val guard = FaroV5Planning8ApprovedObjectGate(
                    f.generation.value, f.pointer, { f.hostReady }, f.workspace,
                    OperationContractLookupPort { id ->
                        f.descriptor.takeIf { it.operationId == id }
                    }, owner,
                )
                assertEquals(scope, guard.currentApprovedObjectScopeOrNull(f.review.context()))
            }
        }
    }

    @Test fun wrongPrincipalObjectOperationAndPointerCannotUseIssuedPolicy() {
        Fixture().use { f ->
            val ticket = requireNotNull(f.issue())
            assertNull(f.policy.policyEpochOrNull("forged-principal", scope, f.pinned))
            assertNull(f.policy.policyEpochOrNull(ticket.principalId, other, f.pinned))
            assertNull(f.policy.policyEpochOrNull(ticket.principalId, otherOperation, f.pinned))
            assertNull(f.policy.policyEpochOrNull(
                ticket.principalId, scope,
                f.pinned.copy(sequence = f.pinned.sequence + 1)))
        }
    }

    @Test fun onlyOneExplicitRequestPerHostSessionAndNoAutomaticReissue() {
        Fixture().use { f ->
            val first = requireNotNull(f.issue())
            assertNull(f.issue())
            assertEquals(first.policyEpoch,
                f.policy.policyEpochOrNull(first.principalId, scope, f.pinned))
        }
    }

    @Test fun canonicalHumanDiscardInvalidatesExistingPreapplyPermission() {
        Fixture().use { f ->
            val ticket = requireNotNull(f.issue())
            assertTrue(runBlocking {
                f.workspace.decide(
                    ConversationPlanReviewIntent.Discard(f.review.context()),
                    IdempotencyKey("discard-local-request"),
                )
            } is ConversationReviewDecision.Saved)
            assertNull(f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
            f.owner(ticket).use { owner ->
                assertNull(owner.grantAfterTrustedPolicyCheck(scope))
            }
        }
    }

    @Test fun hostUnavailableAndPointerAbaNeverRevalidateOldRequest() {
        Fixture().use { f ->
            val ticket = requireNotNull(f.issue())
            f.hostReady = false
            assertNull(f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
            f.hostReady = true
            val alternate = StorageGenerationId("request-alternate")
            assertNotNull(f.pointer.compareAndSet(f.generation, alternate))
            val returned = requireNotNull(f.pointer.compareAndSet(alternate, f.generation))
            assertTrue(returned.sequence > f.pinned.sequence)
            assertNull(f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
            assertNull(f.issue())
        }
    }

    @Test fun mutatedAndroidUidAndClosedOrNewPolicyCannotReplayRequest() {
        Fixture().use { f ->
            val issued = requireNotNull(f.issue())
            f.processUid = 11000
            assertNull(f.policy.policyEpochOrNull(issued.principalId, scope, f.pinned))
            f.processUid = 10510
            assertNull(f.policy.policyEpochOrNull(issued.principalId, scope, f.pinned))
            f.policy.close()
            assertNull(f.policy.principalIdOrNull())
            assertNull(f.issue())
            val nextSandbox = FaroV5AndroidSandboxPrincipal(
                appId, f.pinned, f.pointer, "request-host-epoch",
                { f.hostEpoch }, f.facts,
            )
            nextSandbox.use {
                FaroV5Planning8LocalUserRequestPolicy(
                    f.canonical, nextSandbox, f.pointer, { f.hostReady },
                ).use { newPolicy ->
                    val newPrincipal = requireNotNull(newPolicy.principalIdOrNull())
                    assertNotEquals(issued.principalId, newPrincipal)
                    assertNull(newPolicy.policyEpochOrNull(newPrincipal, scope, f.pinned))
                }
            }
        }
    }

    @Test fun inventedApplyingContextCannotPromoteWithoutCanonicalBeginApply() {
        Fixture().use { f ->
            val ticket = requireNotNull(f.issue())
            val identity = f.reality.applyAttemptIdentities.resolve(f.plan)
            val forged = f.review.context().copy(expectedRevision = f.review.revision + 1)
            assertNull(f.canonical.currentApplyingScopeForTrustedTransitionOrNull(forged, identity))
            assertFalse(f.policy.promoteAfterCanonicalBeginApply(forged, identity))
            assertEquals(ticket.policyEpoch,
                f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
        }
    }

    @Test fun realWorkspaceBeginApplyPromotesExactScopedRequestAndPreservesGrant() {
        Fixture().use { f ->
            val ticket = requireNotNull(f.issue())
            f.owner(ticket).use { owner ->
                val nonce = requireNotNull(owner.grantAfterTrustedPolicyCheck(scope))
                // Production must keep this same owner monitor from BeginApply
                // across the future Reality8 COMMIT, not release it here.
                val result = owner.withCurrentGrantLease(scope, f.pinned, nonce) {
                    val (applying, identity) = f.beginApply()
                    assertNull(f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
                    assertTrue(f.policy.promoteAfterCanonicalBeginApply(applying.context(), identity))
                    assertEquals(scope, f.canonical.currentApplyingScopeForTrustedTransitionOrNull(
                        applying.context(), identity))
                    assertNull(f.canonical.currentApprovedScopeForTrustedRequestOrNull(
                        f.review.context()))
                    assertEquals(ticket.policyEpoch,
                        f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
                    assertEquals(nonce, owner.epochOrNull(scope, f.pinned))
                    "transaction-identity-validated"
                }
                assertEquals(Planning8GrantLeaseResult.Admitted("transaction-identity-validated"), result)
                assertNull(f.issue()) // no second request while APPLYING
                assertTrue(f.reality.planningBackup.snapshot().reminders.rules.isEmpty())
            }
        }
    }

    @Test fun wrongRealityIdentityCannotPromoteEvenAfterCanonicalBeginApply() {
        Fixture().use { f ->
            val ticket = requireNotNull(f.issue())
            val (applying, identity) = f.beginApply()
            val forgedTx = ApplyAttemptIdentity(
                TransactionId("forged-other-transaction"), identity.planFingerprint)
            val forgedFingerprint = ApplyAttemptIdentity(
                identity.transactionId, PlanFingerprint("forged-another-plan"))
            assertFalse(f.policy.promoteAfterCanonicalBeginApply(applying.context(), forgedTx))
            assertFalse(f.policy.promoteAfterCanonicalBeginApply(applying.context(), forgedFingerprint))
            assertNull(f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
            assertTrue(f.policy.promoteAfterCanonicalBeginApply(applying.context(), identity))
            assertEquals(ticket.policyEpoch,
                f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
        }
    }

    @Test fun wrongReviewContextAndRevisionCannotPromoteOrReplay() {
        Fixture().use { f ->
            val ticket = requireNotNull(f.issue())
            val (applying, identity) = f.beginApply()
            assertFalse(f.policy.promoteAfterCanonicalBeginApply(
                applying.context().copy(reviewId = "unrelated-review"), identity))
            assertFalse(f.policy.promoteAfterCanonicalBeginApply(
                applying.context().copy(authorityToken = "untrusted-token"), identity))
            assertFalse(f.policy.promoteAfterCanonicalBeginApply(
                applying.context().copy(expectedRevision = f.review.revision + 3), identity))
            assertFalse(f.policy.promoteAfterCanonicalBeginApply(
                applying.context().copy(conversationId = ConversationId("other-chat")), identity))
            assertTrue(f.policy.promoteAfterCanonicalBeginApply(applying.context(), identity))
            assertFalse(f.policy.promoteAfterCanonicalBeginApply(applying.context(), identity))
            assertNull(f.policy.policyEpochOrNull(ticket.principalId, other, f.pinned))
            assertNull(f.policy.policyEpochOrNull(ticket.principalId, otherOperation, f.pinned))
        }
    }

    @Test fun lossOfSandboxAfterBeginApplyStillDeniesPromotedRequest() {
        Fixture().use { f ->
            val ticket = requireNotNull(f.issue())
            val (applying, identity) = f.beginApply()
            assertTrue(f.policy.promoteAfterCanonicalBeginApply(applying.context(), identity))
            f.processUid = 10201
            assertNull(f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
            f.processUid = 10510
            assertNull(f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
            f.policy.close()
            assertNull(f.policy.policyEpochOrNull(ticket.principalId, scope, f.pinned))
        }
    }

}
