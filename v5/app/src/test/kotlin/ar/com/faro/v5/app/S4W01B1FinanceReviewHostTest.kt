package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W01B1FinanceReviewHostTest {
    @TempDir lateinit var root: Path

    private inner class Case(val label: String, withAccount: Boolean = false) : AutoCloseable {
        val path = root.resolve("$label-workspace.db").toString()
        val workspace = ConversationWorkspaceStore.withPlanVerifier(
            path, CanonicalPreparedPlanEnvelopeVerifier,
        )
        val chat = ConversationId("chat-$label")
        val existingAccount = FinanceAccountSnapshot(
            FinanceAccountId.of("account-1"), FinanceAccountDisplayName.of("Principal"),
            FinanceCurrencyCode.of("ARS"), FinanceAccountNature.BANK, 100L,
        )
        var snapshot = FinanceSnapshot(
            accounts = if (withAccount) listOf(existingAccount) else emptyList(),
            revision = if (withAccount) 1L else 0L,
        )
        var source: () -> FinanceSnapshot = { snapshot }
        var generation: String = "gen-$label"
        var ready: Boolean = true
        var foreground: Boolean = true
        var foregroundCallback: () -> Boolean = { foreground }
        val gate = SingleProcessMaterialWriteGate()
        val host = S4W01B1FinanceReviewHost(
            "gen-$label", workspace,
            FinanceSnapshotReadApi { source() },
            BuiltInContractCatalog.create(),
            gate,
            { generation },
            { ready },
            { foregroundCallback() },
        )
        init {
            assertTrue(runBlocking {
                workspace.createAndSelect(chat, 100L, IdempotencyKey("create-$label"))
            } is ConversationSelectionResult.Saved)
        }
        fun account() = FinanceAccountCreate(
            FinanceAccountId.of("account-new"), FinanceAccountDisplayName.of("Ahorros"),
            FinanceCurrencyCode.of("ARS"), FinanceAccountNature.CASH, 0L,
        )
        fun entry(amount: Long = 20L) = FinanceEntryRecord(
            FinanceEntryId.of("entry-1"), existingAccount.accountId, amount,
            FinanceEntryKind.EXPENSE, FinanceEntryConcept.of("Compra"),
            FinanceEntryCategory.of("Alimentos"), 1000L, 2000L,
        )
        fun accountReview(key: String = "human:account:$label",
            value: FinanceAccountCreate = account(),
            explicit: Boolean = true) =
            host.createAccountPending(chat, value, explicit, key)
        fun entryReview(key: String = "human:entry:$label",
            value: FinanceEntryRecord = entry()) =
            host.createEntryPending(chat, value, key)
        fun reviews() = runBlocking { workspace.reviews(chat) }
        fun seedAcceptedUserMessage() {
            val observed = requireNotNull(runBlocking { workspace.readTimeline(chat) })
            val result = runBlocking {
                workspace.writeTimeline(ConversationTimelineWriteRequest(
                    chat, observed.authorityToken, observed.revision,
                    IdempotencyKey("seed-accepted-$label"),
                    ConversationTimelineIntent.AcceptUser(ConversationMessage(
                        ConversationMessageId("seed-user-$label"), chat,
                        ConversationTurnId("seed-turn-$label"),
                        ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
                        "Mensaje aceptado de prueba", 101L,
                    )),
                ))
            }
            assertTrue(result is ConversationTimelineWriteResult.Saved)
        }
        override fun close() { workspace.close() }
    }

    @Test fun approvedHumanClickCreatesPendingReviewOnly() {
        val p = "pending-workspace.db"
        Case("pending").use { f ->
            val outcome = f.accountReview() as WorkspacePlanReviewWriteResult.Saved
            assertFalse(outcome.replayed)
            assertEquals(ConversationPlanReviewState.PENDING, outcome.review.state)
            assertNull(outcome.review.receipt)
            assertNull(outcome.review.transactionId)
            assertEquals(1, f.reviews().size)
            assertTrue(f.workspace.snapshot().workspace.conversations.single().hasPendingReview)
        }
        ConversationWorkspaceStore.openExistingWithPlanVerifier(
            root.resolve("pending-workspace.db").toString(),
            CanonicalPreparedPlanEnvelopeVerifier,
        ).use { reopened ->
            assertEquals(1, runBlocking { reopened.reviews(ConversationId("chat-pending")) }.size)
        }
    }

    @Test fun deniedForegroundRequestNeverCreatesReview() {
        Case("no-foreground").use { f ->
            f.foreground = false
            assertNull(f.accountReview())
            assertTrue(f.reviews().isEmpty())
            assertNull(f.accountReview(explicit = false))
        }
    }

    @Test fun generationOrHostRevocationDeniesWithoutWrite() {
        Case("gen").use { f ->
            f.generation = "different"
            assertNull(f.accountReview())
            f.generation = "gen-gen"
            f.ready = false
            assertNull(f.accountReview())
            assertTrue(f.reviews().isEmpty())
        }
    }

    @Test fun staleFinanceSnapshotRejectsBeforeReview() {
        Case("stale").use { f ->
            var reads = 0
            f.source = {
                reads += 1
                if (reads == 1) f.snapshot else f.snapshot.copy(revision = 1L)
            }
            assertNull(f.accountReview())
            assertTrue(f.reviews().isEmpty())
        }
    }

    @Test fun repeatSameIntentReturnsOnePendingReview() {
        Case("replay", withAccount = true).use { f ->
            val first = f.entryReview() as WorkspacePlanReviewWriteResult.Saved
            val second = f.entryReview() as WorkspacePlanReviewWriteResult.Saved
            assertFalse(first.replayed)
            assertTrue(second.replayed)
            assertEquals(first.review.reviewId, second.review.reviewId)
            assertEquals(1, f.reviews().size)
            assertEquals(ConversationPlanReviewState.PENDING, f.reviews().single().state)
        }
    }

    @Test fun sameIntentDifferentPayloadFailsClosed() {
        Case("collision", withAccount = true).use { f ->
            assertTrue(f.entryReview() is WorkspacePlanReviewWriteResult.Saved)
            assertNull(f.entryReview(value = f.entry(99L)))
            assertEquals(1, f.reviews().size)
        }
    }

    @Test fun workspaceRevisionAndChatSelectionAreRechecked() {
        Case("selection").use { f ->
            f.seedAcceptedUserMessage()
            val other = ConversationId("other-chat")
            assertTrue(runBlocking {
                f.workspace.createAndSelect(other, 200L, IdempotencyKey("create-other"))
            } is ConversationSelectionResult.Saved)
            assertNull(f.accountReview())
            assertTrue(f.reviews().isEmpty())
        }
        Case("drift").use { f ->
            f.seedAcceptedUserMessage()
            var checked = 0
            f.foregroundCallback = {
                checked += 1
                if (checked == 2) {
                    runBlocking {
                        f.workspace.createAndSelect(
                            ConversationId("interposed"), 200L,
                            IdempotencyKey("create-interposed"),
                        )
                    }
                }
                true
            }
            assertNull(f.accountReview())
            assertTrue(f.reviews().isEmpty())
        }
    }
}
