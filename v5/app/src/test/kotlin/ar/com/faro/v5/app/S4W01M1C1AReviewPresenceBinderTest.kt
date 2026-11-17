package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * DEV JVM binding policy only. Synthesized M1B evidence here is NOT a
 * CryptoObject biometric attestation, a trusted principal or a SQL grant.
 */
class S4W01M1C1AReviewPresenceBinderTest {
    @TempDir lateinit var root: Path
    private val catalog = BuiltInContractCatalog.create()
    private val appId = "ar.com.faro.alpha.v2coredev"
    private val uid = 10123

    private inner class Rig(
        label: String,
        approveReview: Boolean = true,
        val text: String = "Confirmar creación de cuenta ARS",
    ) : AutoCloseable {
        private val folder = java.nio.file.Files.createDirectories(
            root.resolve("m1c1a-" + label),
        )
        val pointer = FileActiveGenerationPointer(folder.resolve("pointer"))
        val generation = StorageGenerationId("m1c1a-" + label)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val workspace = ConversationWorkspaceStore.withPlanVerifier(
            folder.resolve("workspace.db").toString(),
            CanonicalPreparedPlanEnvelopeVerifier,
        )
        val conversation = ConversationId("chat-" + label)
        val objectId = "account-" + label
        var epoch: String? = "epoch-current"
        var ready = true
        var now = 1000L
        val plan: PreparedPlan
        var review: ConversationPlanReviewSnapshot

        init {
            val op = PreparedOperation(
                requireNotNull(catalog.current(FinanceOperations.ACCOUNT_CREATE)),
                FinanceAccountCreatePayload.payload(FinanceAccountCreate(
                    FinanceAccountId.of(objectId),
                    FinanceAccountDisplayName.of("Cuenta ARS"),
                    FinanceCurrencyCode.of("ARS"),
                    FinanceAccountNature.CASH,
                    0L,
                )),
                DependencyStampSet.empty(),
                IdempotencyKey("m1c1a-intent-" + label),
            )
            plan = PreparedPlan.of(ApplyPlanNode(
                PlanNodeId("finance"), op, emptySet(),
            ))
            assertTrue(runBlocking {
                workspace.createAndSelect(
                    conversation, 100L, IdempotencyKey("create-" + label),
                )
            } is ConversationSelectionResult.Saved)
            val token = requireNotNull(runBlocking {
                workspace.readTimeline(conversation)
            }).authorityToken
            val saved = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                conversation, "review-" + label, token, workspace.snapshot().revision,
                plan.planFingerprint, text, PreparedPlanEnvelopeCodec.encode(plan),
                IdempotencyKey("m1c1a-review-" + label),
            )) as WorkspacePlanReviewWriteResult.Saved
            review = saved.review
            if (approveReview) {
                review = (runBlocking {
                    workspace.decide(
                        ConversationPlanReviewIntent.Approve(review.context()),
                        IdempotencyKey("m1c1a-approve-" + label),
                    )
                } as ConversationReviewDecision.Saved).review
            }
        }

        fun proof() = S4W01FinanceLocalPresenceEvidence(
            challengeDigestHex = "a".repeat(64),
            signatureDigestHex = "b".repeat(64),
            scope = S4W01FinanceLocalConsentScope(
                FinanceOperations.ACCOUNT_CREATE.value,
                objectId,
                plan.planFingerprint.value,
                if (text.length <= 120) text else text.take(120),
            ),
            pointer = pinned,
            applicationUid = uid,
            applicationId = appId,
            expiresAtMs = 120_000L,
        )

        fun binder(
            trusted: TrustedConversationApplyReviewPort = workspace,
            approvedReader: S4W01B2FinanceApprovedScopeReadPort =
                S4W01B2FinanceApprovedScopeReadPort {
                    S4W01B2FinanceApprovedScopeReader(
                        generation.value, pinned, pointer, workspace, catalog,
                        "epoch-current", { epoch }, { ready },
                    ).read(it)
                },
        ) = S4W01M1C1AReviewPresenceBinder(
            pinned = pinned,
            pointer = pointer,
            workspace = trusted,
            approved = approvedReader,
            expectedHostEpoch = "epoch-current",
            hostEpochOrNull = { epoch },
            hostReady = { ready },
            expectedApplicationId = appId,
            expectedApplicationUid = uid,
            nowMs = { now },
        )

        override fun close() { workspace.close() }
    }

    @Test fun t01CanonicalApprovedReviewAndMatchingPresenceProduceOnlyInformationalBinding() {
        Rig("valid").use { f ->
            f.binder().use { bridge ->
                val matched = requireNotNull(bridge.bindAfterTrustedPresence(
                    f.review.context(), f.proof(),
                ))
                assertEquals(f.review.reviewId, matched.reviewId)
                assertEquals(f.review.revision, matched.reviewRevision)
                assertEquals(f.plan.planFingerprint, matched.planFingerprint)
                assertEquals(FinanceOperations.ACCOUNT_CREATE.value, matched.operationId)
                assertEquals(f.objectId, matched.objectScope)
                assertEquals(f.pinned, matched.pointer)
                assertEquals(uid, matched.applicationUid)
                assertNull(f.review.receipt)
            }
        }
    }

    @Test fun t02OneShotMatchCannotBeReplayedEvenWithIdenticalPresence() {
        Rig("replay").use { f ->
            f.binder().use { bridge ->
                val proof = f.proof()
                assertNotNull(bridge.bindAfterTrustedPresence(f.review.context(), proof))
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), proof))
            }
            f.binder().use { bridge ->
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), null))
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), f.proof()))
            }
        }
    }

    @Test fun t03ForeignOperationOrObjectNeverBindsApprovedScope() {
        Rig("foreign").use { f ->
            f.binder().use { bridge ->
                val fake = f.proof().let {
                    it.copy(scope = it.scope.copy(objectScope = "other-account"))
                }
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), fake))
            }
            f.binder().use { bridge ->
                val fake = f.proof().let {
                    it.copy(scope = it.scope.copy(operationId = FinanceOperations.ENTRY_RECORD.value))
                }
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), fake))
            }
        }
    }

    @Test fun t04ModifiedFingerprintOrForeignReviewContextCannotPass() {
        Rig("fingerprint").use { f ->
            f.binder().use { bridge ->
                val altered = f.proof().let {
                    it.copy(scope = it.scope.copy(planFingerprintHex = "f".repeat(64)))
                }
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), altered))
            }
            f.binder().use { bridge ->
                assertNull(bridge.bindAfterTrustedPresence(
                    f.review.context().copy(reviewId = "other-review"), f.proof(),
                ))
            }
        }
    }

    @Test fun t05UnreviewedOrTruncatedPromptTextAndOversizedReviewAreDenied() {
        Rig("different-text").use { f ->
            f.binder().use { bridge ->
                val changed = f.proof().let {
                    it.copy(scope = it.scope.copy(reviewedSummary = "Otro saldo y moneda"))
                }
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), changed))
            }
        }
        Rig("long-text", text = "Texto aprobado completo " + "X".repeat(140)).use { f ->
            f.binder().use { bridge ->
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), f.proof()))
            }
        }
    }

    @Test fun t06DifferentAndroidUidOrPackageDeniesRegardlessOfMatchingReview() {
        Rig("foreign-app").use { f ->
            f.binder().use { bridge ->
                assertNull(bridge.bindAfterTrustedPresence(
                    f.review.context(), f.proof().copy(applicationUid = 10002),
                ))
            }
            f.binder().use { bridge ->
                assertNull(bridge.bindAfterTrustedPresence(
                    f.review.context(),
                    f.proof().copy(applicationId = "ar.com.faro.untrusted"),
                ))
            }
        }
    }

    @Test fun t07PointerAbaAndHostEpochOrReadinessRevocationDeny() {
        Rig("pointer-aba").use { f ->
            f.binder().use { bridge ->
                val other = StorageGenerationId("other-stage")
                assertNotNull(f.pointer.compareAndSet(f.generation, other))
                assertNotNull(f.pointer.compareAndSet(other, f.generation))
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), f.proof()))
            }
        }
        Rig("host-epoch").use { f ->
            f.epoch = "revoked"
            f.binder().use { bridge ->
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), f.proof()))
            }
            f.epoch = "epoch-current"
            f.ready = false
            f.binder().use { bridge ->
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), f.proof()))
            }
        }
    }

    @Test fun t08ExpiredInvalidDigestsAndExplicitCloseFailClosed() {
        Rig("expiry").use { f ->
            f.now = 120_000L
            f.binder().use { bridge ->
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), f.proof()))
            }
            f.now = 1000L
            f.binder().use { bridge ->
                assertNull(bridge.bindAfterTrustedPresence(
                    f.review.context(), f.proof().copy(signatureDigestHex = "bad"),
                ))
            }
            val closed = f.binder()
            closed.close()
            assertNull(closed.bindAfterTrustedPresence(f.review.context(), f.proof()))
        }
    }

    @Test fun t09WorkspaceMutationBetweenReadsAndPendingReviewDeny() {
        Rig("workspace-drift").use { f ->
            var reads = 0
            val changing = object : TrustedConversationApplyReviewPort {
                override fun load(
                    context: ConversationPlanReviewContext,
                ): TrustedApprovedConversationPlan? {
                    reads += 1
                    val value = f.workspace.load(context) ?: return null
                    return if (reads == 1) value else TrustedApprovedConversationPlan(
                        value.review, value.envelope() + byteArrayOf(99),
                    )
                }

                override fun advance(
                    intent: ConversationPlanReviewIntent,
                    key: IdempotencyKey,
                    attempts: ApplyAttemptLookupPort,
                    receipts: ExecutionReceiptLookupPort,
                ): ConversationReviewDecision = ConversationReviewDecision.Rejected
            }
            f.binder(trusted = changing).use { bridge ->
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), f.proof()))
                assertEquals(2, reads)
            }
        }
        Rig("pending", approveReview = false).use { f ->
            f.binder().use { bridge ->
                assertNull(bridge.bindAfterTrustedPresence(f.review.context(), f.proof()))
            }
        }
    }
}
