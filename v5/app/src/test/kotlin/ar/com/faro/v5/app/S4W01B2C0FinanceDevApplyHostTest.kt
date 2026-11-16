package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.*
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.*
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Only ephemeral SQLite in JUnit; NOT a productive Android Finance test. */
class S4W01B2C0FinanceDevApplyHostTest {
    @TempDir lateinit var root: Path
    private val official = BuiltInContractCatalog.create()

    private inner class Rig(val label: String, withPolicy: Boolean = true) : AutoCloseable {
        val dir = root.resolve(label)
        val pointer = FileActiveGenerationPointer(dir.resolve("pointers"))
        val generation = StorageGenerationId("dev-generation-" + label)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val gate = SingleProcessMaterialWriteGate()
        val reality = RealityStore(dir.resolve("reality.db").toString(),
            materialWriteGate = gate)
        val workspace = ConversationWorkspaceStore.withPlanVerifier(
            dir.resolve("workspace.db").toString(),
            CanonicalPreparedPlanEnvelopeVerifier,
        )
        val conversation = ConversationId("chat-" + label)
        var hostEpoch: String? = "dev-host-1"
        var ready = true
        var policyEpoch: String? = if (withPolicy) "dev-policy-1" else null
        var reviewNumber = 0
        val scopes = S4W01B2FinanceApprovedScopeReader(
            generation.value, pinned, pointer, workspace, official,
            "dev-host-1", { hostEpoch }, { ready },
        )
        val scopePort = S4W01B2FinanceApprovedScopeReadPort { scopes.read(it) }
        val policy = S4W01B2FinancePolicyEpochPort { principal, _, observed ->
            if (principal == "local-dev-test" && observed == pinned) policyEpoch else null
        }
        val owner = S4W01B2FinanceObjectGrantOwner(
            "local-dev-test", pinned, pointer, { hostEpoch },
            "dev-host-1", scopePort, policy, { ready },
        )
        val host = S4W01B2C0FinanceDevApplyHost(
            generation.value, pinned, pointer, workspace, reality, gate,
            official, owner, "dev-host-1", { hostEpoch }, { ready },
        )
        init {
            assertTrue(runBlocking {
                workspace.createAndSelect(
                    conversation, 100L, IdempotencyKey("create-chat-" + label),
                )
            } is ConversationSelectionResult.Saved)
        }

        private fun review(
            operation: PreparedOperation,
            approve: Boolean = true,
        ): ConversationPlanReviewSnapshot {
            reviewNumber += 1
            val number = reviewNumber
            val plan = PreparedPlan.of(
                ApplyPlanNode(PlanNodeId("finance"), operation, emptySet()),
            )
            val timeline = requireNotNull(runBlocking {
                workspace.readTimeline(conversation)
            })
            val saved = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                conversation, "finance-review-" + label + "-" + number,
                timeline.authorityToken, workspace.snapshot().revision,
                plan.planFingerprint, "Revisión local DEV para pruebas",
                PreparedPlanEnvelopeCodec.encode(plan),
                IdempotencyKey("create-review-" + label + "-" + number),
            )) as WorkspacePlanReviewWriteResult.Saved
            if (!approve) return saved.review
            return (runBlocking {
                workspace.decide(
                    ConversationPlanReviewIntent.Approve(saved.review.context()),
                    IdempotencyKey("approve-review-" + label + "-" + number),
                )
            } as ConversationReviewDecision.Saved).review
        }

        fun accountReview(
            name: String = "principal", approve: Boolean = true,
            modify: (PreparedOperation) -> PreparedOperation = { it },
        ): ConversationPlanReviewSnapshot {
            val id = FinanceAccountId.of("account-" + name)
            val create = FinanceAccountCreate(
                id, FinanceAccountDisplayName.of("Cuenta " + name),
                FinanceCurrencyCode.of("ARS"), FinanceAccountNature.CASH, 100L,
            )
            val preview = requireNotNull(FinanceManualDraftPreviews.account(
                id.value, create.displayName.value, "ARS", create.nature,
                100L, reality.financePersistence.read(),
            ))
            val prepared = requireNotNull(FinanceManualReviewedProposal.prepareAccount(
                preview, create, "human:account:" + label + ":" + name,
                { reality.financePersistence.read() }, official,
            ))
            return review(modify(prepared), approve)
        }

        fun entryReview(
            id: String = "gasto",
            approve: Boolean = true,
        ): ConversationPlanReviewSnapshot {
            val record = FinanceEntryRecord(
                FinanceEntryId.of("entry-" + id), FinanceAccountId.of("account-principal"),
                25L, FinanceEntryKind.EXPENSE, FinanceEntryConcept.of("Compras"),
                FinanceEntryCategory.of("Alimentos"), 1000L, 2000L,
            )
            val preview = requireNotNull(FinanceManualDraftPreviews.entry(
                record.entryId.value, record.accountId.value, record.amountMinor,
                record.kind, record.concept.value, record.category.value,
                record.effectiveAtEpochMillis, record.recordedAtEpochMillis,
                reality.financePersistence.read(),
            ))
            val prepared = requireNotNull(FinanceManualReviewedProposal.prepareEntry(
                preview, record, "human:entry:" + label + ":" + id,
                { reality.financePersistence.read() }, official,
            ))
            return review(prepared, approve)
        }

        fun apply(review: ConversationPlanReviewSnapshot) =
            host.executeOnlyWithSimulatedDevPolicy(review.context())

        fun snapshot() = reality.financePersistence.read()

        override fun close() {
            owner.close()
            workspace.close()
            reality.close()
        }
    }

    @Test fun approvedAccountCreatesOneRealSqliteReceiptInTempDatabase() {
        Rig("account").use { f ->
            val approved = f.accountReview()
            val result = f.apply(approved) as ConversationApplyHostOutcome.Committed
            assertEquals(ConversationPlanReviewState.COMMITTED, result.review.state)
            assertEquals(result.receipt, result.review.receipt)
            val account = f.snapshot().accounts.single()
            assertEquals("account-principal", account.accountId.value)
            assertEquals(100L, f.snapshot().ledgerMinor(account.accountId))
            assertEquals(result.receipt,
                f.reality.executionReceiptLookup.find(result.receipt.transactionId))
        }
    }

    @Test fun approvedEntryUpdatesExactLocalAccountAfterSeparateReview() {
        Rig("entry").use { f ->
            assertTrue(f.apply(f.accountReview()) is ConversationApplyHostOutcome.Committed)
            val result = f.apply(f.entryReview()) as ConversationApplyHostOutcome.Committed
            assertEquals(1, f.snapshot().accounts.size)
            assertEquals(1, f.snapshot().entries.size)
            assertEquals(75L, f.snapshot().ledgerMinor(FinanceAccountId.of("account-principal")))
            assertEquals(result.receipt,
                f.reality.executionReceiptLookup.find(result.receipt.transactionId))
        }
    }

    @Test fun pendingAndDiscardedNeverWriteReality() {
        Rig("pending").use { f ->
            val pending = f.accountReview(approve = false)
            assertTrue(f.apply(pending) is ConversationApplyHostOutcome.Denied)
            val discarded = runBlocking {
                f.workspace.decide(
                    ConversationPlanReviewIntent.Discard(pending.context()),
                    IdempotencyKey("discard-pending-review"),
                )
            } as ConversationReviewDecision.Saved
            assertTrue(f.apply(discarded.review) is ConversationApplyHostOutcome.Denied)
            assertTrue(f.snapshot().accounts.isEmpty())
            assertEquals(0L, f.snapshot().revision)
        }
    }

    @Test fun absentPolicyOrHostAdmissionDeniesWithoutWrite() {
        Rig("policy", withPolicy = false).use { f ->
            assertTrue(f.apply(f.accountReview()) is ConversationApplyHostOutcome.Denied)
            assertTrue(f.snapshot().accounts.isEmpty())
        }
        Rig("host").use { f ->
            val approved = f.accountReview()
            f.ready = false
            assertTrue(f.apply(approved) is ConversationApplyHostOutcome.Denied)
            assertTrue(f.snapshot().accounts.isEmpty())
        }
    }

    @Test fun spoofedFinanceContractFailsAgainstOfficialCatalog() {
        Rig("spoof").use { f ->
            val forged = f.accountReview(modify = { op ->
                op.copy(contract = op.contract.copy(
                    contractVersion = ContractVersion("1"),
                    contractFingerprint = ContractFingerprint("finance-account-create-v1"),
                ))
            })
            assertTrue(f.apply(forged) is ConversationApplyHostOutcome.Denied)
            assertTrue(f.snapshot().accounts.isEmpty())
        }
    }

    @Test fun pointerAbaOrLifecycleDriftDeniesBeforeSqlite() {
        Rig("aba").use { f ->
            val approved = f.accountReview()
            val away = StorageGenerationId("away-generation")
            assertNotNull(f.pointer.compareAndSet(f.generation, away))
            assertNotNull(f.pointer.compareAndSet(away, f.generation))
            assertTrue(f.apply(approved) is ConversationApplyHostOutcome.Denied)
            assertTrue(f.snapshot().accounts.isEmpty())
        }
        Rig("lifecycle").use { f ->
            val approved = f.accountReview()
            f.hostEpoch = "revoked-epoch"
            assertTrue(f.apply(approved) is ConversationApplyHostOutcome.Denied)
            assertTrue(f.snapshot().accounts.isEmpty())
        }
    }

    @Test fun secondTapOnCommittedReviewNeverAllocatesAnotherReceipt() {
        Rig("repeat").use { f ->
            val approved = f.accountReview()
            val first = f.apply(approved) as ConversationApplyHostOutcome.Committed
            val revision = f.snapshot().revision
            val second = f.apply(approved)
            assertTrue(second is ConversationApplyHostOutcome.Denied)
            assertEquals(revision, f.snapshot().revision)
            assertEquals(1, f.snapshot().accounts.size)
            assertEquals(first.receipt,
                f.reality.executionReceiptLookup.find(first.receipt.transactionId))
        }
    }
}
