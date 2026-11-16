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

class S4W01B2FinanceApprovedScopeTest {
    @TempDir lateinit var root: Path
    private val official = BuiltInContractCatalog.create()

    private fun prepared(
        label: String,
        kind: String = "account",
        override: (PreparedOperation) -> PreparedOperation = { it },
    ): PreparedOperation {
        val id = if (kind == "entry") FinanceOperations.ENTRY_RECORD
            else FinanceOperations.ACCOUNT_CREATE
        val contract = requireNotNull(official.current(id))
        val payload = if (kind == "entry") FinanceEntryRecordPayload.payload(
            FinanceEntryRecord(
                FinanceEntryId.of("entry-" + label), FinanceAccountId.of("account-1"),
                25L, FinanceEntryKind.EXPENSE,
                FinanceEntryConcept.of("Alimentos"), FinanceEntryCategory.of("Compras"),
                1_000L, 2_000L,
            ),
        ) else FinanceAccountCreatePayload.payload(
            FinanceAccountCreate(
                FinanceAccountId.of("new-" + label),
                FinanceAccountDisplayName.of("Ahorros"),
                FinanceCurrencyCode.of("ARS"), FinanceAccountNature.CASH, 0L,
            ),
        )
        return override(PreparedOperation(
            contract, payload, DependencyStampSet.empty(),
            IdempotencyKey("human:scope:" + label),
        ))
    }

    private inner class Rig(
        val label: String,
        kind: String = "account",
        approved: Boolean = true,
        change: (PreparedOperation) -> PreparedOperation = { it },
        multiNode: Boolean = false,
    ) : AutoCloseable {
        val pointer = FileActiveGenerationPointer(root.resolve("pointer-" + label))
        val generation = StorageGenerationId("generation-" + label)
        val pinned = requireNotNull(pointer.compareAndSet(null, generation))
        val workspace = ConversationWorkspaceStore.withPlanVerifier(
            root.resolve("workspace-" + label + ".db").toString(),
            CanonicalPreparedPlanEnvelopeVerifier,
        )
        val conversation = ConversationId("chat-" + label)
        var hostEpoch: String? = "host-epoch"
        var ready = true
        lateinit var review: ConversationPlanReviewSnapshot
        val plan: PreparedPlan

        init {
            val first = prepared(label, kind, change)
            val one = ApplyPlanNode(PlanNodeId("finance-first"), first, emptySet())
            plan = if (multiNode) {
                PreparedPlan.of(one, ApplyPlanNode(
                    PlanNodeId("finance-second"), first.copy(
                        idempotencyKey = IdempotencyKey("human:scope-second-" + label)
                    ), setOf(one.nodeId)
                ))
            } else PreparedPlan.of(one)
            assertTrue(runBlocking {
                workspace.createAndSelect(
                    conversation, 100L, IdempotencyKey("create-" + label),
                )
            } is ConversationSelectionResult.Saved)
            val token = requireNotNull(runBlocking {
                workspace.readTimeline(conversation)
            }).authorityToken
            val pending = workspace.createPlanReview(WorkspacePlanReviewCreateRequest(
                conversation, "review-" + label, token, workspace.snapshot().revision,
                plan.planFingerprint, "Revisar operación financiera local " + label,
                PreparedPlanEnvelopeCodec.encode(plan),
                IdempotencyKey("review-create-" + label),
            )) as WorkspacePlanReviewWriteResult.Saved
            review = pending.review
            if (approved) {
                review = (runBlocking {
                    workspace.decide(
                        ConversationPlanReviewIntent.Approve(review.context()),
                        IdempotencyKey("approve-" + label),
                    )
                } as ConversationReviewDecision.Saved).review
            }
        }

        fun reader(
            trusted: TrustedConversationApplyReviewPort = workspace,
            selectedCatalog: OperationContractLookupPort = official,
            epoch: () -> String? = { hostEpoch },
        ) = S4W01B2FinanceApprovedScopeReader(
            generation.value, pinned, pointer, trusted, selectedCatalog,
            "host-epoch", epoch, { ready },
        )

        override fun close() { workspace.close() }
    }

    @Test fun approvedAccountReturnsTypedReadOnlyScope() {
        Rig("account-valid").use { f ->
            val s = requireNotNull(f.reader().read(f.review.context()))
            assertEquals(FinanceOperations.ACCOUNT_CREATE, s.operationId)
            assertEquals("new-account-valid", s.objectId)
            assertEquals(FinanceCurrencyCode.of("ARS"), s.accountCurrencyIfDeclared)
            assertEquals(f.review.planFingerprint, s.planFingerprint)
        }
    }

    @Test fun approvedEntryScopeDoesNotInventCurrency() {
        Rig("entry-valid", kind = "entry").use { f ->
            val s = requireNotNull(f.reader().read(f.review.context()))
            assertEquals(FinanceOperations.ENTRY_RECORD, s.operationId)
            assertEquals("entry-entry-valid", s.objectId)
            assertEquals(FinanceAccountId.of("account-1"), s.accountId)
            assertNull(s.accountCurrencyIfDeclared)
        }
    }

    @Test fun pendingAndDiscardedReviewsCannotReadScope() {
        Rig("pending", approved = false).use { f ->
            assertNull(f.reader().read(f.review.context()))
            val discarded = runBlocking {
                f.workspace.decide(
                    ConversationPlanReviewIntent.Discard(f.review.context()),
                    IdempotencyKey("discard-pending"),
                )
            } as ConversationReviewDecision.Saved
            assertNull(f.reader().read(discarded.review.context()))
        }
    }

    @Test fun descriptorSpoofAndMissingOfficialContractDeny() {
        Rig("bad-descriptor", change = { op ->
            op.copy(contract = op.contract.copy(canonicalExecutor = ExecutorId("other")))
        }).use { f ->
            assertNull(f.reader().read(f.review.context()))
        }
        Rig("missing-contract").use { f ->
            assertNull(f.reader(selectedCatalog = OperationContractLookupPort { null })
                .read(f.review.context()))
        }
    }

    @Test fun multiNodeAndExternalDependencyCannotBecomeScope() {
        Rig("multiple", multiNode = true).use { f ->
            assertNull(f.reader().read(f.review.context()))
        }
        Rig("unknown-op", change = { op ->
            op.copy(contract = op.contract.copy(operationId = OperationId("other.operation")))
        }).use { f ->
            assertNull(f.reader().read(f.review.context()))
        }
    }

    @Test fun wrongPayloadOrMalformedEntryCannotBecomeScope() {
        Rig("bad-payload", change = { op ->
            op.copy(payload = CanonicalPayload.fromEncoded(
                "not-finance", byteArrayOf(1, 2, 3),
            ))
        }).use { f ->
            assertNull(f.reader().read(f.review.context()))
        }
        Rig("wrong-entry-payload", kind = "entry", change = { op ->
            op.copy(payload = FinanceAccountCreatePayload.payload(
                FinanceAccountCreate(
                    FinanceAccountId.of("fake"), FinanceAccountDisplayName.of("Fake"),
                    FinanceCurrencyCode.of("USD"), FinanceAccountNature.CASH, 0L,
                )
            ))
        }).use { f ->
            assertNull(f.reader().read(f.review.context()))
        }
    }

    @Test fun generationPointerAbaAndHostEpochRevocationDeny() {
        Rig("aba").use { f ->
            val away = StorageGenerationId("another")
            assertNotNull(f.pointer.compareAndSet(f.generation, away))
            assertNotNull(f.pointer.compareAndSet(away, f.generation))
            assertNull(f.reader().read(f.review.context()))
        }
        Rig("epoch").use { f ->
            f.hostEpoch = "revoked"
            assertNull(f.reader().read(f.review.context()))
            f.hostEpoch = "host-epoch"
            f.ready = false
            assertNull(f.reader().read(f.review.context()))
        }
    }

    @Test fun workspaceOrCatalogChangesBetweenDoubleReadsDeny() {
        Rig("workspace-changes").use { f ->
            var calls = 0
            val source = object : TrustedConversationApplyReviewPort {
                override fun load(context: ConversationPlanReviewContext): TrustedApprovedConversationPlan? {
                    calls += 1
                    val actual = f.workspace.load(context) ?: return null
                    if (calls == 1) return actual
                    return TrustedApprovedConversationPlan(
                        actual.review,
                        actual.envelope() + byteArrayOf(0),
                    )
                }
                override fun advance(
                    intent: ConversationPlanReviewIntent, key: IdempotencyKey,
                    attempts: ApplyAttemptLookupPort, receipts: ExecutionReceiptLookupPort,
                ): ConversationReviewDecision = ConversationReviewDecision.Rejected
            }
            assertNull(f.reader(trusted = source).read(f.review.context()))
            assertEquals(2, calls)
        }
        Rig("catalog-changes").use { f ->
            var reads = 0
            val catalog = OperationContractLookupPort { id ->
                reads += 1
                if (reads == 1) official.current(id) else null
            }
            assertNull(f.reader(selectedCatalog = catalog).read(f.review.context()))
            assertTrue(reads >= 2)
        }
    }
}
