package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.owner.objectives.api.*
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * S4 W00-B: real Finance binding + real Objectives persistence via a TEST-ONLY
 * synthetic binding. Never register that fixture as a production operation.
 * Does not certify Android host ACL, Household+Finance, or physical power loss.
 */
class S4W00BMultiOwnerApplyReceiptRecoveryTest {
    @Test
    fun composedApplyCommitsBothOwnersOneReceiptAndReceiptFirstRetrySurvivesReopen() {
        val file = Files.createTempDirectory("faro-w00b-commit").resolve("reality.db")
        val plan = plan("commit")
        lateinit var originalReceipt: ExecutionReceipt
        RealityStore(
            fileName = file.toString(),
            transactionIdFactory = { TransactionId("tx-w00b-commit") },
            receiptIdFactory = { ReceiptId("receipt-w00b-commit") },
        ).use { store ->
            val identity = (retry(store).resolve(plan) as ApplyRetryResolution.Proceed).identity
            val result = ApplyTransactionExecutor(store, store).execute(
                plan, identity, bindings(store)
            )
            assertTrue(result is ApplyTransactionExecution.Completed)
            originalReceipt = (result as ApplyTransactionExecution.Completed).receipt
            assertEquals(ApplyAttemptJournalState.COMMITTED, store.applyAttemptLookup.find(identity.transactionId)?.state)
            assertEquals(originalReceipt, store.executionReceiptLookup.find(identity.transactionId))
            assertEquals(1L, store.financePersistence.read().revision)
            assertEquals(1L, store.objectivePersistence.read().revision)
            assertEquals("ok", store.integrityCheck())
        }
        RealityStore(
            fileName = file.toString(),
            transactionIdFactory = { error("receipt-first must not create another attempt") },
            receiptIdFactory = { error("receipt-first must not create a second receipt") },
        ).use { store ->
            val retry = retry(store).resolve(plan)
            assertTrue(retry is ApplyRetryResolution.AlreadyCommitted)
            assertEquals(originalReceipt, (retry as ApplyRetryResolution.AlreadyCommitted).receipt)
            assertTrue(recover(store).resolve(plan) is ApplyRecoveryResolution.Committed)
            assertEquals("commit", store.financePersistence.read().accounts.single().accountId.value)
            assertEquals(25_000L, store.financePersistence.read().accounts.single().openingBalanceMinor)
            assertEquals(objective(), store.objectivePersistence.read().objectives.single())
            assertEquals(1L, store.financePersistence.read().revision)
            assertEquals(1L, store.objectivePersistence.read().revision)
            assertEquals(1, store.receiptHistory.query(RealityReceiptHistoryQuery()).receipts.size)
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test
    fun secondOwnerBindingRejectionRollsBackFinanceAndEmitsNoReceipt() {
        val plan = plan("reject")
        RealityStore(
            fileName = ":memory:",
            transactionIdFactory = { TransactionId("tx-w00b-reject") },
        ).use { store ->
            val identity = (retry(store).resolve(plan) as ApplyRetryResolution.Proceed).identity
            val result = ApplyTransactionExecutor(store, store).execute(
                plan, identity,
                bindings(store, onObjective = {
                    OperationBindingExecutionResult.Rejected("test-only-objective-denied")
                }),
            )
            assertTrue(result is ApplyTransactionExecution.Rejected)
            assertEquals(ApplyTransactionRejection.BINDING_REJECTED, (result as ApplyTransactionExecution.Rejected).reason)
            assertEquals(0, store.financePersistence.read().accounts.size)
            assertEquals(0, store.objectivePersistence.read().objectives.size)
            assertEquals(0L, store.financePersistence.read().revision)
            assertEquals(0L, store.objectivePersistence.read().revision)
            assertNull(store.executionReceiptLookup.find(identity.transactionId))
            assertEquals(ApplyAttemptJournalState.PREPARED, store.applyAttemptLookup.find(identity.transactionId)?.state)
        }
    }

    @Test
    fun simulatedRevocationAfterBothBindingsRejectsBeforeReceiptAndRollsBackBoth() {
        val plan = plan("revocation")
        RealityStore(
            fileName = ":memory:",
            transactionIdFactory = { TransactionId("tx-w00b-revocation") },
        ).use { store ->
            var allowed = true
            val identity = (retry(store).resolve(plan) as ApplyRetryResolution.Proceed).identity
            val resolved = bindings(store, onObjective = { scope ->
                store.objectivePersistence.upsert(objective(), scope)
                allowed = false
                OperationBindingExecutionResult.Completed
            })
            val auth = ApplyTransactionAuthorizationPort { _, _, _, _ -> allowed }
            val result = ApplyTransactionExecutor(store, store, auth).execute(plan, identity, resolved)
            assertTrue(result is ApplyTransactionExecution.Rejected)
            assertEquals(ApplyTransactionRejection.AUTHORIZATION_REJECTED, (result as ApplyTransactionExecution.Rejected).reason)
            assertEquals(0, store.financePersistence.read().accounts.size)
            assertEquals(0, store.objectivePersistence.read().objectives.size)
            assertNull(store.executionReceiptLookup.find(identity.transactionId))
            assertEquals(ApplyAttemptJournalState.PREPARED, store.applyAttemptLookup.find(identity.transactionId)?.state)
        }
    }

    @Test
    fun interruptedCommitAfterReceiptInsertRollsBackBothAndRecoveryReusesPreparedIdentity() {
        val file = Files.createTempDirectory("faro-w00b-recovery").resolve("reality.db")
        val plan = plan("uncertain")
        lateinit var identity: ApplyAttemptIdentity
        RealityStore(
            fileName = file.toString(),
            transactionIdFactory = { TransactionId("tx-w00b-uncertain") },
            receiptIdFactory = { ReceiptId("receipt-w00b-uncertain") },
        ).use { store ->
            identity = (retry(store).resolve(plan) as ApplyRetryResolution.Proceed).identity
            val uncertainCommits = object : ApplyCommitPort {
                override fun commit(
                    transactionScope: RealityTransactionScope,
                    identity: ApplyAttemptIdentity,
                    outboxIntents: List<OutboxEffectIntent>,
                ): ExecutionReceipt {
                    store.commit(transactionScope, identity, outboxIntents)
                    error("injected lost process after SQL receipt insert before COMMIT")
                }
            }
            val result = ApplyTransactionExecutor(store, uncertainCommits).execute(
                plan, identity, bindings(store)
            )
            assertTrue(result is ApplyTransactionExecution.RecoveryRequired)
            assertEquals(0, store.financePersistence.read().accounts.size)
            assertEquals(0, store.objectivePersistence.read().objectives.size)
            assertNull(store.executionReceiptLookup.find(identity.transactionId))
            assertEquals(ApplyAttemptJournalState.PREPARED, store.applyAttemptLookup.find(identity.transactionId)?.state)
        }
        RealityStore(
            fileName = file.toString(),
            transactionIdFactory = { error("reopen must reuse PREPARED identity") },
            receiptIdFactory = { ReceiptId("receipt-w00b-uncertain") },
        ).use { store ->
            val recovery = recover(store).resolve(plan)
            assertTrue(recovery is ApplyRecoveryResolution.PreparedForRevalidation)
            assertEquals(identity.transactionId, (recovery as ApplyRecoveryResolution.PreparedForRevalidation).attempt.transactionId)
            // Fixture revalidation is deliberate here; a real host must revalidate each authority.
            val result = ApplyTransactionExecutor(store, store).execute(plan, identity, bindings(store))
            assertTrue(result is ApplyTransactionExecution.Completed)
            assertEquals(1L, store.financePersistence.read().revision)
            assertEquals(1L, store.objectivePersistence.read().revision)
            assertEquals(1, store.receiptHistory.query(RealityReceiptHistoryQuery()).receipts.size)
            assertTrue(recover(store).resolve(plan) is ApplyRecoveryResolution.Committed)
            assertEquals("ok", store.integrityCheck())
        }
    }

    @Test
    fun mismatchedCommitReceiptRollsBackBothOwnerWritesAndArtifacts() {
        val plan = plan("wrong-receipt")
        RealityStore(
            fileName = ":memory:",
            transactionIdFactory = { TransactionId("tx-w00b-wrong") },
            receiptIdFactory = { ReceiptId("receipt-w00b-wrong") },
        ).use { store ->
            val identity = (retry(store).resolve(plan) as ApplyRetryResolution.Proceed).identity
            val badCommit = object : ApplyCommitPort {
                override fun commit(
                    transactionScope: RealityTransactionScope,
                    identity: ApplyAttemptIdentity,
                    outboxIntents: List<OutboxEffectIntent>,
                ): ExecutionReceipt =
                    store.commit(transactionScope, identity, outboxIntents).copy(
                        transactionId = TransactionId("tx-not-the-same")
                    )
            }
            val result = ApplyTransactionExecutor(store, badCommit).execute(
                plan, identity, bindings(store)
            )
            assertTrue(result is ApplyTransactionExecution.Rejected)
            assertEquals(ApplyTransactionRejection.COMMIT_RECEIPT_MISMATCH, (result as ApplyTransactionExecution.Rejected).reason)
            assertEquals(0, store.financePersistence.read().accounts.size)
            assertEquals(0, store.objectivePersistence.read().objectives.size)
            assertNull(store.executionReceiptLookup.find(identity.transactionId))
            assertEquals(ApplyAttemptJournalState.PREPARED, store.applyAttemptLookup.find(identity.transactionId)?.state)
            assertEquals("ok", store.integrityCheck())
        }
    }

    private fun plan(label: String): PreparedPlanView {
        val financeContract = OperationContractDescriptor(
            FinanceOperations.ACCOUNT_CREATE,
            FinanceOperations.CAPABILITY_ID,
            FinanceOperations.EXECUTOR_ID,
            ExecutionProfile.REALITY_MUTATION,
            ContractVersion("1"),
            ContractFingerprint("finance-account-create-v1"),
            ContractExposure.BINDABLE,
        )
        val finance = PreparedOperation(
            financeContract,
            FinanceAccountCreatePayload.payload(account(label)),
            DependencyStampSet.of(DependencyStamp(
                FinanceSnapshot.FINANCE_AUTHORITY,
                FinanceSnapshot.FINANCE_ACCOUNTS_KEY,
                DependencyStampValue("revision:0")
            )),
            IdempotencyKey("s4-w00b-finance-" + label),
        )
        // This synthetic descriptor exists only inside JVM tests and is not in catalog180.
        val fixtureContract = OperationContractDescriptor(
            OperationId("s4.w00b.test.objectives.upsert"),
            CapabilityId("N-025"),
            ExecutorId("w00b-test-only-objectives"),
            ExecutionProfile.REALITY_MUTATION,
            ContractVersion("1"),
            ContractFingerprint("w00b-test-objective-only-v1"),
            ContractExposure.BINDABLE,
        )
        val objectivePrepared = PreparedOperation(
            fixtureContract,
            CanonicalPayload.fromEncoded("w00b.fixture.objective.v1", byteArrayOf(1)),
            DependencyStampSet.of(DependencyStamp(
                AuthorityBoundaryId("reality.objectives"),
                DependencyKey("w00b:fixture"),
                DependencyStampValue("revision:0")
            )),
            IdempotencyKey("s4-w00b-objective-" + label),
        )
        val first = PlanNodeId("a-finance")
        val graph = ApplyPlan.of(
            ApplyPlanNode(PlanNodeId("b-objectives"), objectivePrepared, setOf(first)),
            ApplyPlanNode(first, finance, emptySet()),
        )
        return object : PreparedPlanView {
            override val applyPlan = graph
            override val planFingerprint = PlanFingerprint("s4-w00b-two-owner-" + label)
        }
    }

    private fun bindings(
        store: RealityStore,
        onObjective: (RealityTransactionScope) -> OperationBindingExecutionResult = { scope ->
            store.objectivePersistence.upsert(objective(), scope)
            OperationBindingExecutionResult.Completed
        },
    ): ApplyBindingResolution.Resolved {
        val objectiveTestBinding = object : OperationBindingPort {
            override val descriptor = OperationBindingDescriptor(
                operationId = OperationId("s4.w00b.test.objectives.upsert"),
                canonicalExecutor = ExecutorId("w00b-test-only-objectives"),
                executionProfile = ExecutionProfile.REALITY_MUTATION,
            )
            override fun execute(
                preparedOperation: PreparedOperation,
                transactionScope: RealityTransactionScope,
            ): OperationBindingExecutionResult {
                if (preparedOperation.contract.operationId != descriptor.operationId ||
                    preparedOperation.payload.formatId != "w00b.fixture.objective.v1"
                ) return OperationBindingExecutionResult.Rejected("fixture-invalid")
                return onObjective(transactionScope)
            }
        }
        val result = ApplyBindingResolutionGate(
            listOf(FinanceAccountCreateBinding(store.financePersistence), objectiveTestBinding)
        ).resolve(planForBindingsOnly(store))
        // We resolve the binding order by a fresh deterministic fixture; execution uses the
        // caller's plan with the same node identifiers and contract descriptors.
        assertTrue(result is ApplyBindingResolution.Resolved)
        return result as ApplyBindingResolution.Resolved
    }

    private fun planForBindingsOnly(store: RealityStore): PreparedPlanView = plan("bindings")
    private fun retry(store: RealityStore) =
        ApplyRetryReceiptGate(store.applyAttemptIdentities, store.executionReceiptLookup)

    private fun recover(store: RealityStore) =
        ApplyRecoveryResolver(retry(store), store.applyAttemptLookup)

    private fun account(label: String) = FinanceAccountCreate(
        accountId = FinanceAccountId.of(label),
        displayName = FinanceAccountDisplayName.of(label),
        currency = FinanceCurrencyCode.of("ARS"),
        nature = FinanceAccountNature.BANK,
        openingBalanceMinor = 25_000L,
    )

    private fun objective() = ObjectiveRecord(
        id = ObjectiveId.of("w00b-objective"),
        title = ObjectiveTitle.of("Goal"),
        detail = ObjectiveDetail.of("test only"),
        status = ObjectiveStatus.ACTIVE,
        updatedAtEpochMillis = 100L,
    )
}
