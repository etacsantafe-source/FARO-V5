package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.surface.FaroV5ActivityEventKind
import ar.com.faro.v5.surface.FaroV5ActivitySource
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Genuine two SQLite owner reads with fixture grants, not Android account ACL. */
class FaroV5RealHistoryActivityHostBindingT2Test {
    @TempDir lateinit var root: Path

    private inner class Case(label: String) : AutoCloseable {
        val pointer = FileActiveGenerationPointer(root.resolve(label + "-pointer"))
        val generation = StorageGenerationId("gen-" + label)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val transactionId = TransactionId("tx-" + label)
        val reality = RealityStore(
            root.resolve(label + "-reality.db").toString(),
            transactionIdFactory = { transactionId },
            receiptIdFactory = { ReceiptId("receipt-" + label) },
            receiptClockEpochMillis = { 3000L },
        )
        val workspace = ConversationWorkspaceStore(
            root.resolve(label + "-workspace.db").toString(),
        )
        var hostEpoch: String? = "host-1"
        var ready = true
        var realityGrant: String? = "r-grant-1"
        var workspaceGrant: String? = "w-grant-1"
        var onPolicy: (FaroV5ActivityDomain, String?) -> String? = { _, epoch -> epoch }
        val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val policy = N015TrustedHistoryReadPolicyPort { principal, domain, observed ->
            if (principal != "host-technical-test" || observed != pinned) null
            else onPolicy(domain, when (domain) {
                FaroV5ActivityDomain.REALITY_RECEIPT -> realityGrant
                FaroV5ActivityDomain.WORKSPACE_MESSAGE -> workspaceGrant
            })
        }

        fun seed() {
            val plan = object : PreparedPlanView {
                override val applyPlan: ApplyPlan get() =
                    error("this seed is receipt-only, never a user Apply")
                override val planFingerprint = PlanFingerprint("n015-real-sqlite-plan")
            }
            val identity = reality.applyAttemptIdentities.resolve(plan)
            reality.run(transactionId) { scope ->
                reality.commit(scope, identity, emptyList())
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            val chat = ConversationId("chat-" + generation.value)
            val created = workspace.create(WorkspaceCreateRequest(
                ConversationWorkspaceEntry(chat, "Private conversation", 100L),
                workspace.snapshot().revision, IdempotencyKey("create-" + generation.value),
            ))
            assertTrue(created is WorkspaceWriteResult.Committed)
            writeMessage(chat, "message-one", 4000L)
        }

        fun writeMessage(chat: ConversationId, id: String, time: Long) {
            val before = requireNotNull(workspace.timelineSnapshot(chat))
            val message = ConversationMessage(
                ConversationMessageId(id), chat, ConversationTurnId("turn-" + id),
                ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
                "Highly confidential content for " + id, time,
            )
            val result = workspace.saveTimeline(ConversationTimelineWriteRequest(
                chat, before.authorityToken, before.revision, IdempotencyKey("write-" + id),
                ConversationTimelineIntent.AcceptUser(message),
            ))
            assertTrue(result is ConversationTimelineWriteResult.Saved)
        }

        fun host(
            overridePolicy: N015TrustedHistoryReadPolicyPort? = policy,
        ) = FaroV5RealHistoryActivityHostBinding(
            generationId = generation.value, pinned = pinned,
            activePointer = pointer, reality = reality, workspace = workspace,
            principalId = "host-technical-test",
            expectedHostEpoch = "host-1", hostEpochOrNull = { hostEpoch },
            hostReady = { ready }, readPolicy = overridePolicy, invalidations = events,
        )

        override fun close() {
            runCatching { workspace.close() }
            runCatching { reality.close() }
        }
    }

    @Test fun missingBothOrEitherGrantDeniesWithoutInventingZero() {
        Case("missing-grants").use { f ->
            f.seed()
            f.host(null).use { assertNull(runBlocking { it.readActivity() }) }
            f.workspaceGrant = null
            f.host().use { assertNull(runBlocking { it.readActivity() }) }
            f.workspaceGrant = "w-grant-1"
            f.realityGrant = null
            f.host().use { assertNull(runBlocking { it.readActivity() }) }
            assertEquals(1, f.workspace.readHistoryPage().events.size)
            assertEquals(1, f.reality.timedReceiptHistory.query(
                ar.com.faro.v5.store.reality.RealityTimedReceiptQuery()).receipts.size)
        }
    }

    @Test fun genuineSqliteRealityReceiptAndWorkspaceMessageRenderTypedAndPrivate() {
        Case("real-owners").use { f ->
            f.seed()
            val initialRevision = f.workspace.snapshot().revision
            f.host().use { binding ->
                val page = requireNotNull(runBlocking { binding.readActivity() })
                assertEquals(2, page.entries.size)
                assertEquals(listOf(FaroV5ActivitySource.WORKSPACE_MESSAGE,
                    FaroV5ActivitySource.REALITY_COMMITTED_RECEIPT),
                    page.entries.map { it.source })
                assertEquals(listOf(FaroV5ActivityEventKind.WORKSPACE_USER_MESSAGE,
                    FaroV5ActivityEventKind.APPLY_COMMITTED), page.entries.map { it.kind })
                assertTrue(page.completeWithinSources)
                assertFalse(page.globallyComplete)
                assertTrue(page.entries.none { it.opaqueId.contains("confidential") })
            }
            assertEquals(initialRevision, f.workspace.snapshot().revision)
            assertEquals(1, f.reality.maintenance.inspect().executionReceiptCount)
        }
    }

    @Test fun policyRevocationDuringReadDeniesWholePage() {
        Case("revoke").use { f ->
            f.seed()
            var visits = 0
            f.onPolicy = { source, epoch ->
                if (source == FaroV5ActivityDomain.REALITY_RECEIPT) {
                    visits++
                    if (visits >= 9) "r-revoked" else epoch
                } else epoch
            }
            f.host().use { binding ->
                assertNull(runBlocking { binding.readActivity() })
                assertTrue(visits >= 9)
            }
        }
    }

    @Test fun pointerAbaAndHostEpochRevocationNeverReuseOldAuthority() {
        Case("pointer-aba").use { f ->
            f.seed()
            f.host().use { binding ->
                assertNotNull(runBlocking { binding.readActivity() })
                f.hostEpoch = "host-2"
                assertNull(runBlocking { binding.readActivity() })
                f.hostEpoch = "host-1"
                val other = StorageGenerationId("other-generation")
                assertNotNull(f.pointer.compareAndSet(f.generation, other))
                assertNotNull(f.pointer.compareAndSet(other, f.generation))
                assertNotEquals(f.pinned, f.pointer.read())
                assertNull(runBlocking { binding.readActivity() })
            }
        }
    }

    @Test fun sourceOwnersClosingDeniesAfterSuccessfulRead() {
        Case("closed-owner").use { f ->
            f.seed()
            val binding = f.host()
            assertNotNull(runBlocking { binding.readActivity() })
            binding.close()
            assertNull(runBlocking { binding.readActivity() })
            f.host().use { fresh ->
                assertNotNull(runBlocking { fresh.readActivity() })
                f.workspace.close()
                assertNull(runBlocking { fresh.readActivity() })
            }
        }
    }

    @Test fun boundedFirstPageRetainsPartialCoverageWithoutStoreMutations() {
        Case("limit").use { f ->
            f.seed()
            val chat = ConversationId("chat-" + f.generation.value)
            f.writeMessage(chat, "message-two", 5000L)
            val revBefore = f.workspace.snapshot().revision
            f.host().use { binding ->
                val page = requireNotNull(runBlocking { binding.readActivity() })
                assertEquals(3, page.entries.size)
                assertTrue(page.completeWithinSources)
                assertFalse(page.globallyComplete)
            }
            assertEquals(revBefore, f.workspace.snapshot().revision)
            assertEquals(1, f.reality.maintenance.inspect().executionReceiptCount)
        }
    }
}
