package ar.com.faro.v5.workspace

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.kernel.runtime.PreparedPlan
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import java.nio.file.Path
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Native SQLite T2 only (Linux glibc): cannot be executed on Android Termux's
 * Bionic libc. Verifies staged Workspace schema4, not a live V2 user experience.
 */
class WorkspacePlanReviewT2Test {
    @TempDir lateinit var directory: Path

    private fun file(name: String) = directory.resolve(name).toString()
    private fun open(name: String = "work.db") = ConversationWorkspaceStore.withPlanVerifier(
        file(name), CanonicalPreparedPlanEnvelopeVerifier,
    )
    private fun plan(payload: String = "amount=100"): PreparedPlan =
        PreparedPlan.of(ApplyPlanNode(
            nodeId = PlanNodeId("expense-1"),
            preparedOperation = PreparedOperation(
                contract = OperationContractDescriptor(
                    OperationId("finance.expense"), CapabilityId("N-025"), ExecutorId("finance"),
                    ExecutionProfile.REALITY_MUTATION, ContractVersion("1"),
                    ContractFingerprint("finance-expense-v1"), ContractExposure.BINDABLE,
                ),
                payload = CanonicalPayload.fromEncoded("fixture-v1", payload.toByteArray()),
                dependencyStamps = DependencyStampSet.of(DependencyStamp(
                    AuthorityBoundaryId("reality.finance"),
                    DependencyKey("monthly-budget"), DependencyStampValue("version:1"),
                )),
                idempotencyKey = IdempotencyKey("expense-1"),
            ),
            dependsOn = emptySet(),
        ))
    private fun createChat(store: ConversationWorkspaceStore, id: String = "chat-a", revision: Long = 0) {
        assertTrue(store.create(WorkspaceCreateRequest(
            ConversationWorkspaceEntry(ConversationId(id), "Chat " + id, 100),
            revision, IdempotencyKey("create-" + id),
        )) is WorkspaceWriteResult.Committed)
    }
    private fun proposal(
        store: ConversationWorkspaceStore,
        reviewId: String = "review-a",
        chat: String = "chat-a",
        expected: Long = 1L,
        payload: String = "amount=100",
        key: String = "review-create",
    ): WorkspacePlanReviewCreateRequest {
        val ready = plan(payload)
        return WorkspacePlanReviewCreateRequest(
            conversationId = ConversationId(chat), reviewId = reviewId,
            authorityToken = store.draftSnapshot(ConversationId(chat))!!.authorityToken,
            expectedWorkspaceRevision = expected,
            planFingerprint = ready.planFingerprint,
            reviewText = "Registrar gasto de luz por 100 pesos.",
            planEnvelope = PreparedPlanEnvelopeCodec.encode(ready),
            idempotencyKey = IdempotencyKey(key),
        )
    }
    private fun saved(result: WorkspacePlanReviewWriteResult): ConversationPlanReviewSnapshot =
        (result as WorkspacePlanReviewWriteResult.Saved).review

    @Test fun defaultWorkspaceStoreCannotAdmitUnverifiedPlan() {
        ConversationWorkspaceStore(file("no-verifier.db")).use { store ->
            createChat(store)
            val request = proposal(store)
            assertEquals(
                WorkspacePlanReviewWriteResult.Rejected(WorkspacePlanReviewRejection.INVALID_PLAN),
                store.createPlanReview(request),
            )
            assertFalse(store.snapshot().workspace.conversations.single().hasPendingReview)
            assertTrue(store.backup.snapshot().planReviews.isEmpty())
        }
    }

    @Test fun pendingReviewAndEnvelopeSurviveCloseAndVerifiedReopen() {
        val original = open().use { store ->
            createChat(store)
            val p = saved(store.createPlanReview(proposal(store)))
            assertEquals(ConversationPlanReviewState.PENDING, p.state)
            assertTrue(store.snapshot().workspace.conversations.single().hasPendingReview)
            store.backup.snapshot()
        }
        assertEquals(1, original.planReviews.size)
        assertEquals(WorkspaceBackupSnapshot.SCHEMA_VERSION, original.schemaVersion)
        ConversationWorkspaceStore.openExistingWithPlanVerifier(
            file("work.db"), CanonicalPreparedPlanEnvelopeVerifier,
        ).use { reopened ->
            assertEquals(original, reopened.backup.snapshot())
            val p = reopened.planReviewSnapshot("review-a")!!
            assertEquals(ConversationPlanReviewState.PENDING, p.state)
            assertEquals(original.planReviews.single().planFingerprint, p.planFingerprint)
        }
    }

    @Test fun reviewRequiresExactChatFingerprintRevisionAndGeneration() {
        open().use { store ->
            createChat(store)
            val p = saved(store.createPlanReview(proposal(store)))
            assertEquals(WorkspacePlanReviewWriteResult.Rejected(
                WorkspacePlanReviewRejection.INVALID_TRANSITION),
                store.transitionPlanReview(WorkspacePlanReviewTransitionRequest(
                    ConversationPlanReviewIntent.Approve(p.context().copy(
                        conversationId = ConversationId("chat-b"))), IdempotencyKey("wrong-chat"),
                )))
            assertEquals(WorkspacePlanReviewWriteResult.Rejected(
                WorkspacePlanReviewRejection.INVALID_TRANSITION),
                store.transitionPlanReview(WorkspacePlanReviewTransitionRequest(
                    ConversationPlanReviewIntent.Approve(p.context().copy(
                        planFingerprint = PlanFingerprint("other"))), IdempotencyKey("wrong-plan"),
                )))
            assertEquals(WorkspacePlanReviewWriteResult.Rejected(
                WorkspacePlanReviewRejection.INVALID_TRANSITION),
                store.transitionPlanReview(WorkspacePlanReviewTransitionRequest(
                    ConversationPlanReviewIntent.Approve(p.context().copy(
                        expectedRevision = 1)), IdempotencyKey("wrong-revision"),
                )))
            assertEquals(WorkspacePlanReviewWriteResult.Unavailable,
                store.transitionPlanReview(WorkspacePlanReviewTransitionRequest(
                    ConversationPlanReviewIntent.Approve(p.context().copy(
                        authorityToken = "other-generation")), IdempotencyKey("wrong-generation"),
                )))
            assertEquals(ConversationPlanReviewState.PENDING,
                store.planReviewSnapshot("review-a")!!.state)
        }
    }

    @Test fun approvingIsDurableButCannotExecuteOrFabricateReceipt() {
        open().use { store ->
            createChat(store)
            val p = saved(store.createPlanReview(proposal(store)))
            val approved = saved(store.transitionPlanReview(
                WorkspacePlanReviewTransitionRequest(
                    ConversationPlanReviewIntent.Approve(p.context()), IdempotencyKey("approve-1"),
                ),
            ))
            assertEquals(ConversationPlanReviewState.APPROVED, approved.state)
            assertEquals(1L, approved.revision)
            assertNull(approved.transactionId)
            assertNull(approved.receipt)
            assertTrue(store.snapshot().workspace.conversations.single().hasPendingReview)
            assertEquals(WorkspacePlanReviewWriteResult.Rejected(
                WorkspacePlanReviewRejection.INVALID_TRANSITION),
                store.transitionPlanReview(WorkspacePlanReviewTransitionRequest(
                    ConversationPlanReviewIntent.BeginApply(
                        approved.context(),
                        ApplyAttemptIdentity(TransactionId("tx"), approved.planFingerprint),
                    ), IdempotencyKey("bypass-apply"),
                )))
        }
    }

    @Test fun exactIdempotencyReplayAndCollisionAreAtomic() {
        open().use { store ->
            createChat(store)
            val create = proposal(store)
            assertFalse((store.createPlanReview(create) as WorkspacePlanReviewWriteResult.Saved).replayed)
            assertTrue((store.createPlanReview(create) as WorkspacePlanReviewWriteResult.Saved).replayed)
            assertEquals(WorkspacePlanReviewWriteResult.IdentityConflict,
                store.createPlanReview(create.copy(reviewText = "Cambiado")))
            val p = store.planReviewSnapshot("review-a")!!
            val approve = WorkspacePlanReviewTransitionRequest(
                ConversationPlanReviewIntent.Approve(p.context()), IdempotencyKey("approved"),
            )
            assertFalse((store.transitionPlanReview(approve) as WorkspacePlanReviewWriteResult.Saved).replayed)
            assertTrue((store.transitionPlanReview(approve) as WorkspacePlanReviewWriteResult.Saved).replayed)
            assertEquals(3L, store.snapshot().revision)
        }
    }

    @Test fun discardClearsPendingAndAllowsArchive() {
        open().use { store ->
            createChat(store)
            val p = saved(store.createPlanReview(proposal(store)))
            assertEquals(WorkspaceWriteResult.Rejected(WorkspaceWriteRejection.PENDING_REVIEW),
                store.transition(WorkspaceTransitionRequest(
                    ConversationWorkspaceIntent.SetArchived(ConversationId("chat-a"), true),
                    store.snapshot().revision, IdempotencyKey("archive-blocked"),
                )))
            val discarded = saved(store.transitionPlanReview(WorkspacePlanReviewTransitionRequest(
                ConversationPlanReviewIntent.Discard(p.context()), IdempotencyKey("discard"),
            )))
            assertEquals(ConversationPlanReviewState.DISCARDED, discarded.state)
            assertFalse(store.snapshot().workspace.conversations.single().hasPendingReview)
            assertTrue(store.transition(WorkspaceTransitionRequest(
                ConversationWorkspaceIntent.SetArchived(ConversationId("chat-a"), true),
                store.snapshot().revision, IdempotencyKey("archive-allowed"),
            )) is WorkspaceWriteResult.Committed)
        }
    }

    @Test fun alteredEnvelopeIsRejectedBeforeAnyReceiptOrRevisionWrite() {
        open().use { store ->
            createChat(store)
            val p = proposal(store)
            val altered = p.planEnvelope.copyOf()
            altered[altered.lastIndex] = (altered.last().toInt() xor 1).toByte()
            assertEquals(
                WorkspacePlanReviewWriteResult.Rejected(WorkspacePlanReviewRejection.INVALID_PLAN),
                store.createPlanReview(p.copy(planEnvelope = altered)),
            )
            assertEquals(1L, store.snapshot().revision)
            assertEquals(1, store.backup.snapshot().receipts.size)
            assertTrue(store.backup.snapshot().planReviews.isEmpty())
        }
    }

    @Test fun restoringPendingAndApprovalAcrossGenerationRebindsAuthorityToken() {
        val original = open("source.db").use { store ->
            createChat(store)
            val p = saved(store.createPlanReview(proposal(store)))
            saved(store.transitionPlanReview(WorkspacePlanReviewTransitionRequest(
                ConversationPlanReviewIntent.Approve(p.context()), IdempotencyKey("approve-1"),
            )))
            val oldToken = p.authorityToken
            store.backup.snapshot() to oldToken
        }
        open("target.db").use { restored ->
            val verified = restored.backup.restoreOrVerify(original.first)
            assertEquals(original.first, verified)
            val p = restored.planReviewSnapshot("review-a")!!
            assertTrue(p.authorityToken != original.second)
            assertEquals(ConversationPlanReviewState.APPROVED, p.state)
            assertEquals(WorkspacePlanReviewWriteResult.Unavailable,
                restored.transitionPlanReview(WorkspacePlanReviewTransitionRequest(
                    ConversationPlanReviewIntent.Discard(p.context().copy(
                        authorityToken = original.second)), IdempotencyKey("stale-before-restore"),
                )))
            assertEquals(original.first, restored.backup.restoreOrVerify(original.first))
        }
    }

    @Test fun malformedBackupDigestIsRejectedWithoutMutatingFreshTarget() {
        val original = open("source.db").use { source ->
            createChat(source)
            source.createPlanReview(proposal(source))
            source.backup.snapshot()
        }
        val corrupt = original.copy(planReviews = original.planReviews.map {
            it.copy(envelopeSha256 = "0".repeat(64))
        })
        open("target.db").use { target ->
            assertThrows(IllegalStateException::class.java) {
                target.backup.restoreOrVerify(corrupt)
            }
            assertTrue(target.backup.snapshot().conversations.isEmpty())
            assertTrue(target.backup.snapshot().planReviews.isEmpty())
        }
    }

    @Test fun noVerifierRestoreFailsClosedAndRollsBack() {
        val original = open("source.db").use { store ->
            createChat(store)
            store.createPlanReview(proposal(store))
            store.backup.snapshot()
        }
        ConversationWorkspaceStore(file("target.db")).use { target ->
            assertThrows(IllegalStateException::class.java) {
                target.backup.restoreOrVerify(original)
            }
            assertTrue(target.backup.snapshot().conversations.isEmpty())
            assertTrue(target.backup.snapshot().planReviews.isEmpty())
        }
    }

    @Test fun snapshotFailsClosedOnTamperedSQLitePlanBlob() {
        val path = file("tampered.db")
        open("tampered.db").use { store ->
            createChat(store)
            store.createPlanReview(proposal(store))
        }
        BundledSQLiteDriver().open(path).use { db ->
            db.execSQL("UPDATE workspace_plan_review SET envelope = x'ff00'")
        }
        ConversationWorkspaceStore.openExistingWithPlanVerifier(
            path, CanonicalPreparedPlanEnvelopeVerifier,
        ).use { store ->
            assertThrows(IllegalStateException::class.java) { store.backup.snapshot() }
            assertThrows(IllegalStateException::class.java) { store.planReviewSnapshot("review-a") }
        }
    }

    @Test fun concurrentChatsHaveIndependentPendingMetadata() {
        open().use { store ->
            createChat(store)
            createChat(store, "chat-b", 1)
            val first = saved(store.createPlanReview(proposal(store, expected = 2)))
            val second = saved(store.createPlanReview(proposal(
                store, reviewId = "review-b", chat = "chat-b", expected = 3, key = "review-b-create",
            )))
            assertEquals(setOf("chat-a", "chat-b"), store.snapshot().workspace.conversations
                .filter { it.hasPendingReview }.map { it.id.value }.toSet())
            saved(store.transitionPlanReview(WorkspacePlanReviewTransitionRequest(
                ConversationPlanReviewIntent.Discard(first.context()), IdempotencyKey("discard-a"),
            )))
            assertEquals(listOf("chat-b"), store.snapshot().workspace.conversations
                .filter { it.hasPendingReview }.map { it.id.value })
            assertEquals(ConversationPlanReviewState.PENDING,
                store.planReviewSnapshot(second.reviewId)!!.state)
        }
    }

    @Test fun legacySealedSchemaCannotBeSilentlyMigrated() {
        val path = file("v3.db")
        // Create an isolated staged v3 DB by dropping only the new table/index
        // and rolling user_version back; no user V2 data is active.
        open("v3.db").use {}
        BundledSQLiteDriver().open(path).use { db ->
            db.execSQL("DROP TABLE workspace_plan_review")
            db.execSQL("PRAGMA user_version = 3")
        }
        assertThrows(IllegalStateException::class.java) {
            ConversationWorkspaceStore.openExistingWithPlanVerifier(
                path, CanonicalPreparedPlanEnvelopeVerifier,
            )
        }
        open("v3.db").use { migrated ->
            assertEquals(WorkspaceBackupSnapshot.SCHEMA_VERSION, migrated.backup.snapshot().schemaVersion)
        }
    }
    @Test fun capacity64ReturnsTypedOutcomeWithoutEvictingPlanOrReceipt() {
        open("capacity.db").use { store ->
            createChat(store)
            repeat(WorkspaceBackupSnapshot.MAX_PLAN_REVIEWS) { index ->
                val result = store.createPlanReview(proposal(
                    store,
                    reviewId = "review-" + index,
                    expected = store.snapshot().revision,
                    key = "proposal-" + index,
                ))
                assertTrue(result is WorkspacePlanReviewWriteResult.Saved)
            }
            val before = store.backup.snapshot()
            assertEquals(64, before.planReviews.size)
            val revision = store.snapshot().revision
            val result = store.createPlanReview(proposal(
                store, reviewId = "review-overflow",
                expected = revision, key = "proposal-overflow",
            ))
            assertEquals(
                WorkspacePlanReviewWriteResult.Rejected(
                    WorkspacePlanReviewRejection.CAPACITY_EXCEEDED,
                ), result,
            )
            assertEquals(revision, store.snapshot().revision)
            assertEquals(before, store.backup.snapshot())
            assertNull(store.planReviewSnapshot("review-overflow"))
            assertTrue(store.snapshot().workspace.conversations.single().hasPendingReview)
        }
    }

    private fun <T> immediate(block: suspend () -> T): T {
        var completed: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { completed = result }
        })
        return requireNotNull(completed).getOrThrow()
    }

    @Test fun activeReviewPortReadsVerifiedPlanAndOnlyAcceptsHumanDecision() {
        val path = file("human-review.db")
        open("human-review.db").use { store ->
            createChat(store)
            val pending = saved(store.createPlanReview(proposal(store)))
            val found = immediate { store.reviews(ConversationId("chat-a")) }
            assertEquals(listOf(pending), found)
            assertTrue(immediate { store.reviews(ConversationId("other")) }.isEmpty())
            val fakeApply = ConversationPlanReviewIntent.BeginApply(
                pending.context(),
                ApplyAttemptIdentity(TransactionId("forged"), pending.planFingerprint),
            )
            assertEquals(ConversationReviewDecision.Rejected,
                immediate { store.decide(fakeApply, IdempotencyKey("denied-apply")) })
            assertEquals(ConversationPlanReviewState.PENDING, store.planReviewSnapshot("review-a")!!.state)
            val approval = immediate { store.decide(
                ConversationPlanReviewIntent.Approve(pending.context()), IdempotencyKey("human-ok")
            ) } as ConversationReviewDecision.Saved
            assertEquals(ConversationPlanReviewState.APPROVED, approval.review.state)
            assertNull(approval.review.receipt)
            assertNull(approval.review.transactionId)
            val replay = immediate { store.decide(
                ConversationPlanReviewIntent.Approve(pending.context()), IdempotencyKey("human-ok")
            ) } as ConversationReviewDecision.Saved
            assertTrue(replay.replayed)
        }
        ConversationWorkspaceStore.openExistingWithPlanVerifier(
            path, CanonicalPreparedPlanEnvelopeVerifier,
        ).use { reopened ->
            val approved = immediate { reopened.reviews(ConversationId("chat-a")) }.single()
            assertEquals(ConversationPlanReviewState.APPROVED, approved.state)
            assertNull(approved.receipt)
        }
    }

}
