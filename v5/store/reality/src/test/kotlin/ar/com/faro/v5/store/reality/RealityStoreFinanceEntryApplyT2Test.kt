package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.*
import ar.com.faro.v5.owner.finance.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RealityStoreFinanceEntryApplyT2Test {
    @Test
    fun preparedEntryCommitsOnceAndReceiptFirstRetrySurvivesReopen() {
        val db = Files.createTempDirectory("faro-v5-finance-entry-e2e")
            .resolve("reality.db")
        val accountId = FinanceAccountId.of("account-primary")
        lateinit var plan: PreparedPlanView
        lateinit var committedReceipt: ExecutionReceipt

        RealityStore(
            fileName = db.toString(),
            transactionIdFactory = { TransactionId("tx-entry-e2e") },
            receiptIdFactory = { ReceiptId("receipt-entry-e2e") },
        ).use { store ->
            seedAccount(store, accountId, openingBalanceMinor = 100_000L)
            plan = preparePlan(
                store = store,
                record = entryRecord(
                    id = "entry-e2e",
                    accountId = accountId,
                    amountMinor = 25_000L,
                    kind = FinanceEntryKind.EXPENSE,
                ),
                idempotencyKey = "entry-e2e-intent",
            )

            val retry = retryGate(store).resolve(plan)
            assertTrue(retry is ApplyRetryResolution.Proceed)
            val identity = (retry as ApplyRetryResolution.Proceed).identity

            assertEquals(ApplyPreflightResult.Accepted, preflight(store, plan))

            val execution = ApplyTransactionExecutor(store, store).execute(
                preparedPlan = plan,
                identity = identity,
                resolvedBindings = bindings(store, plan),
            )

            assertTrue(execution is ApplyTransactionExecution.Completed)
            committedReceipt =
                (execution as ApplyTransactionExecution.Completed).receipt

            val finance = store.financePersistence.read()
            assertEquals(2L, finance.revision)
            assertEquals(1, finance.entries.size)
            assertEquals(75_000L, finance.ledgerMinor(accountId))
            assertEquals(1_000L, finance.entries.single().effectiveAtEpochMillis)
            assertEquals(2_000L, finance.entries.single().recordedAtEpochMillis)
            assertEquals(
                ApplyAttemptJournalState.COMMITTED,
                store.applyAttemptLookup.find(identity.transactionId)!!.state,
            )
        }

        RealityStore(
            fileName = db.toString(),
            transactionIdFactory = {
                error("receipt-first retry must reuse durable transaction identity")
            },
            receiptIdFactory = {
                error("receipt-first retry must not create another receipt")
            },
        ).use { reopened ->
            val retry = retryGate(reopened).resolve(plan)
            assertTrue(retry is ApplyRetryResolution.AlreadyCommitted)
            assertEquals(
                committedReceipt,
                (retry as ApplyRetryResolution.AlreadyCommitted).receipt,
            )

            val finance = reopened.financePersistence.read()
            assertEquals(2L, finance.revision)
            assertEquals(1, finance.entries.size)
            assertEquals(75_000L, finance.ledgerMinor(accountId))

            val recovery = recoveryResolver(reopened).resolve(plan)
            assertTrue(recovery is ApplyRecoveryResolution.Committed)
            assertEquals(
                committedReceipt,
                (recovery as ApplyRecoveryResolution.Committed).receipt,
            )
            assertEquals(1, reopened.financePersistence.read().entries.size)
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test
    fun uncertainFailureBeforeSqliteCommitRollsBackEntryAndRecoversSameIdentity() {
        val db = Files.createTempDirectory("faro-v5-finance-entry-recovery")
            .resolve("reality.db")
        val accountId = FinanceAccountId.of("account-primary")
        lateinit var plan: PreparedPlanView
        lateinit var originalIdentity: ApplyAttemptIdentity

        RealityStore(
            fileName = db.toString(),
            transactionIdFactory = { TransactionId("tx-entry-recovery") },
            receiptIdFactory = { ReceiptId("receipt-entry-recovery") },
        ).use { store ->
            seedAccount(store, accountId, openingBalanceMinor = 100_000L)
            plan = preparePlan(
                store = store,
                record = entryRecord(
                    id = "entry-recovery",
                    accountId = accountId,
                    amountMinor = 40_000L,
                    kind = FinanceEntryKind.INCOME,
                ),
                idempotencyKey = "entry-recovery-intent",
            )

            val retry = retryGate(store).resolve(plan)
            assertTrue(retry is ApplyRetryResolution.Proceed)
            originalIdentity = (retry as ApplyRetryResolution.Proceed).identity
            assertEquals(ApplyPreflightResult.Accepted, preflight(store, plan))

            val failAfterArtifacts = object : ApplyCommitPort {
                override fun commit(
                    transactionScope: RealityTransactionScope,
                    identity: ApplyAttemptIdentity,
                    outboxIntents: List<OutboxEffectIntent>,
                ): ExecutionReceipt {
                    store.commit(transactionScope, identity, outboxIntents)
                    error("simulated process loss before SQLite COMMIT")
                }
            }

            val execution = ApplyTransactionExecutor(
                transactions = store,
                commits = failAfterArtifacts,
            ).execute(
                preparedPlan = plan,
                identity = originalIdentity,
                resolvedBindings = bindings(store, plan),
            )

            assertTrue(execution is ApplyTransactionExecution.RecoveryRequired)
            val finance = store.financePersistence.read()
            assertEquals(1L, finance.revision)
            assertEquals(0, finance.entries.size)
            assertEquals(100_000L, finance.ledgerMinor(accountId))
            assertEquals(
                ApplyAttemptJournalState.PREPARED,
                store.applyAttemptLookup
                    .find(originalIdentity.transactionId)!!
                    .state,
            )
            assertNull(
                store.executionReceiptLookup.find(originalIdentity.transactionId)
            )
        }

        RealityStore(
            fileName = db.toString(),
            transactionIdFactory = {
                error("recovery must reuse PREPARED transaction identity")
            },
            receiptIdFactory = { ReceiptId("receipt-entry-recovery") },
        ).use { reopened ->
            val recovery = recoveryResolver(reopened).resolve(plan)
            assertTrue(recovery is ApplyRecoveryResolution.PreparedForRevalidation)
            val preparedAttempt =
                (recovery as ApplyRecoveryResolution.PreparedForRevalidation).attempt
            assertEquals(originalIdentity.transactionId, preparedAttempt.transactionId)
            assertEquals(originalIdentity.planFingerprint, preparedAttempt.planFingerprint)

            assertEquals(ApplyPreflightResult.Accepted, preflight(reopened, plan))

            val execution = ApplyTransactionExecutor(reopened, reopened).execute(
                preparedPlan = plan,
                identity = ApplyAttemptIdentity(
                    transactionId = preparedAttempt.transactionId,
                    planFingerprint = preparedAttempt.planFingerprint,
                ),
                resolvedBindings = bindings(reopened, plan),
            )

            assertTrue(execution is ApplyTransactionExecution.Completed)
            val finance = reopened.financePersistence.read()
            assertEquals(2L, finance.revision)
            assertEquals(1, finance.entries.size)
            assertEquals(140_000L, finance.ledgerMinor(accountId))

            val finalRecovery = recoveryResolver(reopened).resolve(plan)
            assertTrue(finalRecovery is ApplyRecoveryResolution.Committed)
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    private fun preparePlan(
        store: RealityStore,
        record: FinanceEntryRecord,
        idempotencyKey: String,
    ): PreparedPlanView {
        val contract = financeEntryContract()
        val binding = FinanceEntryRecordBinding(store.financePersistence)
        val catalog = ContractCatalog.of(
            capabilities = listOf(
                CapabilityDescriptor(FinanceOperations.CAPABILITY_ID)
            ),
            operations = listOf(contract),
        )
        val registry = BindingRegistry.of(
            catalog = catalog,
            operationBindings = listOf(binding),
        )
        val session = CapabilitySession.open(registry)
        assertEquals(
            ContractAvailability.AVAILABLE,
            session.availability(FinanceOperations.ENTRY_RECORD),
        )

        val runtime = PreparationRuntime(
            IdempotencyKeySource { IdempotencyKey(idempotencyKey) }
        )
        val spec = PreparationSpec(
            operationId = FinanceOperations.ENTRY_RECORD,
            inputEncoder = object : CanonicalInputEncoder<FinanceEntryRecord> {
                override val formatId: String =
                    FinanceEntryRecordPayload.FORMAT_ID

                override fun encode(input: FinanceEntryRecord): ByteArray =
                    FinanceEntryRecordPayload.encodeBytes(input)
            },
            dependencyStampSource =
                DependencyStampSource<FinanceEntryRecord> { input ->
                    DependencyStampSet.of(
                        store.financePersistence
                            .read()
                            .accountDependencyStamp(input.accountId)
                    )
                },
            validator =
                PreparationValidator<FinanceEntryRecord> {
                    PreparationValidation.Valid
                },
        )

        val result = runtime.prepare(
            session = session,
            input = record,
            spec = spec,
        )
        assertTrue(result is PreparationResult.Prepared<*>)
        val prepared =
            (result as PreparationResult.Prepared<FinanceEntryRecord>).operation

        val applyPlan = ApplyPlan.of(
            ApplyPlanNode(
                nodeId = PlanNodeId("finance-entry-record"),
                preparedOperation = prepared,
                dependsOn = emptySet(),
            )
        )
        val fingerprint = PlanFingerprint(
            "finance-entry-plan-" +
                prepared.payloadFingerprint.value +
                "-" +
                prepared.idempotencyKey.value
        )
        return object : PreparedPlanView {
            override val applyPlan: ApplyPlan = applyPlan
            override val planFingerprint: PlanFingerprint = fingerprint
        }
    }

    private fun preflight(
        store: RealityStore,
        plan: PreparedPlanView,
    ): ApplyPreflightResult {
        val contract = plan.applyPlan
            .nodesInTopologicalOrder()
            .single()
            .preparedOperation
            .contract
        val contractLookup = OperationContractLookupPort { operationId ->
            if (operationId == contract.operationId) contract else null
        }
        return ApplyPreflightGate(
            contracts = contractLookup,
            dependencyStamps = store,
            operationRevalidators =
                listOf(FinanceEntryRecordRevalidation(store.financePersistence)),
        ).validate(
            preparedPlan = plan,
            authorization = PlanAuthorizationRef(plan.planFingerprint),
        )
    }

    private fun bindings(
        store: RealityStore,
        plan: PreparedPlanView,
    ): ApplyBindingResolution.Resolved {
        val resolved = ApplyBindingResolutionGate(
            listOf(FinanceEntryRecordBinding(store.financePersistence))
        ).resolve(plan)
        assertTrue(resolved is ApplyBindingResolution.Resolved)
        return resolved as ApplyBindingResolution.Resolved
    }

    private fun retryGate(store: RealityStore) =
        ApplyRetryReceiptGate(
            identities = store.applyAttemptIdentities,
            receipts = store.executionReceiptLookup,
        )

    private fun recoveryResolver(store: RealityStore) =
        ApplyRecoveryResolver(
            retryGate = retryGate(store),
            attempts = store.applyAttemptLookup,
        )

    private fun financeEntryContract() =
        OperationContractDescriptor(
            operationId = FinanceOperations.ENTRY_RECORD,
            capabilityId = FinanceOperations.CAPABILITY_ID,
            canonicalExecutor = FinanceOperations.EXECUTOR_ID,
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("1"),
            contractFingerprint =
                ContractFingerprint("finance-entry-record-v1"),
            exposure = ContractExposure.BINDABLE,
        )

    private fun seedAccount(
        store: RealityStore,
        accountId: FinanceAccountId,
        openingBalanceMinor: Long,
    ) {
        store.run(TransactionId("seed-" + accountId.value)) { scope ->
            store.financePersistence.createAccount(
                FinanceAccountCreate(
                    accountId = accountId,
                    displayName = FinanceAccountDisplayName.of("Principal"),
                    currency = FinanceCurrencyCode.of("ARS"),
                    nature = FinanceAccountNature.BANK,
                    openingBalanceMinor = openingBalanceMinor,
                ),
                scope,
            )
            AtomicApplyTransactionDecision.Commit(Unit)
        }
    }

    private fun entryRecord(
        id: String,
        accountId: FinanceAccountId,
        amountMinor: Long,
        kind: FinanceEntryKind,
    ) = FinanceEntryRecord(
        entryId = FinanceEntryId.of(id),
        accountId = accountId,
        amountMinor = amountMinor,
        kind = kind,
        concept = FinanceEntryConcept.of("Supermercado"),
        category = FinanceEntryCategory.of("Alimentos"),
        effectiveAtEpochMillis = 1_000L,
        recordedAtEpochMillis = 2_000L,
        provenanceLabel = "Declarado por usuario",
    )
}
