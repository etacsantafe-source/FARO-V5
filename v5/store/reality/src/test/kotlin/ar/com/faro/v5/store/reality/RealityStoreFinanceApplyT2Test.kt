package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.apply.ApplyBindingResolution
import ar.com.faro.v5.kernel.apply.ApplyBindingResolutionGate
import ar.com.faro.v5.kernel.apply.ApplyPreflightGate
import ar.com.faro.v5.kernel.apply.ApplyPreflightRejection
import ar.com.faro.v5.kernel.apply.ApplyPreflightResult
import ar.com.faro.v5.kernel.apply.ApplyRecoveryResolution
import ar.com.faro.v5.kernel.apply.ApplyRecoveryResolver
import ar.com.faro.v5.kernel.apply.ApplyRetryReceiptGate
import ar.com.faro.v5.kernel.apply.ApplyRetryResolution
import ar.com.faro.v5.kernel.apply.ApplyTransactionExecution
import ar.com.faro.v5.kernel.apply.ApplyTransactionExecutor
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.*
import ar.com.faro.v5.owner.finance.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RealityStoreFinanceApplyT2Test {
    @Test
    fun preparedAccountCreateCommitsOnceAndReceiptFirstRetrySurvivesReopen() {
        val db = Files.createTempDirectory("faro-v5-finance-e2e").resolve("reality.db")
        lateinit var plan: PreparedPlanView
        lateinit var committedReceipt: ExecutionReceipt

        RealityStore(
            fileName = db.toString(),
            transactionIdFactory = { TransactionId("tx-finance-e2e") },
            receiptIdFactory = { ReceiptId("receipt-finance-e2e") },
        ).use { store ->
            plan = preparePlan(
                store = store,
                create = accountCreate(
                    id = "account-primary",
                    openingBalanceMinor = 125_000L,
                ),
                idempotencyKey = "finance-account-e2e",
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
            assertEquals(1L, finance.revision)
            assertEquals(1, finance.accounts.size)
            assertEquals("account-primary", finance.accounts.single().accountId.value)
            assertEquals(125_000L, finance.accounts.single().openingBalanceMinor)
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
            assertEquals(1L, finance.revision)
            assertEquals(1, finance.accounts.size)
            assertEquals("account-primary", finance.accounts.single().accountId.value)

            val recovery = recoveryResolver(reopened).resolve(plan)
            assertTrue(recovery is ApplyRecoveryResolution.Committed)
            assertEquals(
                committedReceipt,
                (recovery as ApplyRecoveryResolution.Committed).receipt,
            )
            assertEquals(1L, reopened.financePersistence.read().revision)
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test
    fun preparedAccountCreateBecomesStaleAfterConcurrentFinanceRevision() {
        RealityStore(
            fileName = ":memory:",
            transactionIdFactory = { TransactionId("tx-finance-stale") },
        ).use { store ->
            val plan = preparePlan(
                store = store,
                create = accountCreate("account-planned"),
                idempotencyKey = "finance-account-stale",
            )
            val retry = retryGate(store).resolve(plan)
            assertTrue(retry is ApplyRetryResolution.Proceed)

            store.run(TransactionId("tx-concurrent-finance")) { scope ->
                store.financePersistence.createAccount(
                    accountCreate("account-concurrent"),
                    scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }

            val result = preflight(store, plan)
            assertTrue(result is ApplyPreflightResult.Rejected)
            val rejection = result as ApplyPreflightResult.Rejected
            assertEquals(ApplyPreflightRejection.STALE_DEPENDENCY, rejection.reason)
            assertEquals(FinanceSnapshot.FINANCE_AUTHORITY, rejection.authorityBoundary)
            assertEquals(FinanceSnapshot.FINANCE_ACCOUNTS_KEY, rejection.dependencyKey)

            val finance = store.financePersistence.read()
            assertEquals(1L, finance.revision)
            assertEquals(listOf("account-concurrent"), finance.accounts.map { it.accountId.value })
        }
    }

    @Test
    fun uncertainFailureBeforeSqliteCommitReopensPreparedAndRetriesFinanceOnce() {
        val db = Files.createTempDirectory("faro-v5-finance-recovery").resolve("reality.db")
        lateinit var plan: PreparedPlanView
        lateinit var originalIdentity: ApplyAttemptIdentity

        RealityStore(
            fileName = db.toString(),
            transactionIdFactory = { TransactionId("tx-finance-recovery") },
            receiptIdFactory = { ReceiptId("receipt-finance-recovery") },
        ).use { store ->
            plan = preparePlan(
                store = store,
                create = accountCreate(
                    id = "account-recovery",
                    openingBalanceMinor = 50_000L,
                ),
                idempotencyKey = "finance-account-recovery",
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
            assertEquals(0, store.financePersistence.read().accounts.size)
            assertEquals(0L, store.financePersistence.read().revision)
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
            receiptIdFactory = { ReceiptId("receipt-finance-recovery") },
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
            assertEquals(1L, finance.revision)
            assertEquals(1, finance.accounts.size)
            assertEquals("account-recovery", finance.accounts.single().accountId.value)
            assertEquals(50_000L, finance.accounts.single().openingBalanceMinor)

            val finalRecovery = recoveryResolver(reopened).resolve(plan)
            assertTrue(finalRecovery is ApplyRecoveryResolution.Committed)
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    private fun preparePlan(
        store: RealityStore,
        create: FinanceAccountCreate,
        idempotencyKey: String,
    ): PreparedPlanView {
        val contract = financeAccountContract()
        val binding = FinanceAccountCreateBinding(store.financePersistence)
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
            session.availability(FinanceOperations.ACCOUNT_CREATE),
        )

        val runtime = PreparationRuntime(
            IdempotencyKeySource { IdempotencyKey(idempotencyKey) }
        )
        val spec = PreparationSpec(
            operationId = FinanceOperations.ACCOUNT_CREATE,
            inputEncoder = object : CanonicalInputEncoder<FinanceAccountCreate> {
                override val formatId: String =
                    FinanceAccountCreatePayload.FORMAT_ID

                override fun encode(input: FinanceAccountCreate): ByteArray =
                    FinanceAccountCreatePayload.encodeBytes(input)
            },
            dependencyStampSource =
                DependencyStampSource<FinanceAccountCreate> {
                    DependencyStampSet.of(
                        store.financePersistence
                            .read()
                            .accountsDependencyStamp()
                    )
                },
            validator =
                PreparationValidator<FinanceAccountCreate> {
                    PreparationValidation.Valid
                },
        )

        val result = runtime.prepare(
            session = session,
            input = create,
            spec = spec,
        )
        assertTrue(result is PreparationResult.Prepared<*>)
        val prepared =
            (result as PreparationResult.Prepared<FinanceAccountCreate>).operation

        val applyPlan = ApplyPlan.of(
            ApplyPlanNode(
                nodeId = PlanNodeId("finance-account-create"),
                preparedOperation = prepared,
                dependsOn = emptySet(),
            )
        )
        val fingerprint = PlanFingerprint(
            "finance-account-plan-" +
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
                listOf(FinanceAccountCreateRevalidation()),
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
            listOf(FinanceAccountCreateBinding(store.financePersistence))
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

    private fun financeAccountContract() =
        OperationContractDescriptor(
            operationId = FinanceOperations.ACCOUNT_CREATE,
            capabilityId = FinanceOperations.CAPABILITY_ID,
            canonicalExecutor = FinanceOperations.EXECUTOR_ID,
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("1"),
            contractFingerprint =
                ContractFingerprint("finance-account-create-v1"),
            exposure = ContractExposure.BINDABLE,
        )

    private fun accountCreate(
        id: String,
        openingBalanceMinor: Long = 0L,
    ) = FinanceAccountCreate(
        accountId = FinanceAccountId.of(id),
        displayName = FinanceAccountDisplayName.of(
            if (id == "account-primary") "Principal" else id
        ),
        currency = FinanceCurrencyCode.of("ARS"),
        nature = FinanceAccountNature.BANK,
        openingBalanceMinor = openingBalanceMinor,
    )
}
