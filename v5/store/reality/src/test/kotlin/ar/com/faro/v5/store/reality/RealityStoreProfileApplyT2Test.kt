package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.apply.ApplyBindingResolution
import ar.com.faro.v5.kernel.apply.ApplyBindingResolutionGate
import ar.com.faro.v5.kernel.apply.ApplyPreflightGate
import ar.com.faro.v5.kernel.apply.ApplyPreflightResult
import ar.com.faro.v5.kernel.apply.ApplyRecoveryResolution
import ar.com.faro.v5.kernel.apply.ApplyRecoveryResolver
import ar.com.faro.v5.kernel.apply.ApplyRetryReceiptGate
import ar.com.faro.v5.kernel.apply.ApplyRetryResolution
import ar.com.faro.v5.kernel.apply.ApplyTransactionExecution
import ar.com.faro.v5.kernel.apply.ApplyTransactionExecutor
import ar.com.faro.v5.kernel.contracts.ApplyAttemptIdentity
import ar.com.faro.v5.kernel.contracts.ApplyAttemptJournalState
import ar.com.faro.v5.kernel.contracts.ApplyCommitPort
import ar.com.faro.v5.kernel.contracts.ApplyPlan
import ar.com.faro.v5.kernel.contracts.ApplyPlanNode
import ar.com.faro.v5.kernel.contracts.CapabilityDescriptor
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ContractFingerprint
import ar.com.faro.v5.kernel.contracts.ContractVersion
import ar.com.faro.v5.kernel.contracts.DependencyStampSet
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutionReceipt
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.kernel.contracts.OutboxEffectIntent
import ar.com.faro.v5.kernel.contracts.PlanAuthorizationRef
import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.kernel.contracts.PlanNodeId
import ar.com.faro.v5.kernel.contracts.PreparedPlanView
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.kernel.contracts.ReceiptId
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.kernel.runtime.BindingRegistry
import ar.com.faro.v5.kernel.runtime.CanonicalInputEncoder
import ar.com.faro.v5.kernel.runtime.CapabilitySession
import ar.com.faro.v5.kernel.runtime.ContractAvailability
import ar.com.faro.v5.kernel.runtime.ContractCatalog
import ar.com.faro.v5.kernel.runtime.DependencyStampSource
import ar.com.faro.v5.kernel.runtime.IdempotencyKeySource
import ar.com.faro.v5.kernel.runtime.PreparationResult
import ar.com.faro.v5.kernel.runtime.PreparationRuntime
import ar.com.faro.v5.kernel.runtime.PreparationSpec
import ar.com.faro.v5.kernel.runtime.PreparationValidation
import ar.com.faro.v5.kernel.runtime.PreparationValidator
import ar.com.faro.v5.owner.profile.api.DeclaredDisplayName
import ar.com.faro.v5.owner.profile.api.ProfileDeclaredUpdate
import ar.com.faro.v5.owner.profile.api.ProfileDeclaredUpdatePayload
import ar.com.faro.v5.owner.profile.api.ProfileOperations
import ar.com.faro.v5.owner.profile.api.ProfileUpdateDeclaredBinding
import ar.com.faro.v5.owner.profile.api.ProfileUpdateDeclaredRevalidation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class RealityStoreProfileApplyT2Test {
    @Test
    fun preparedProfileApplyCommitsOnceAndReceiptFirstRetrySurvivesReopen() {
        val db = Files.createTempDirectory("faro-v5-profile-e2e").resolve("reality.db")
        lateinit var plan: PreparedPlanView
        lateinit var committedReceipt: ExecutionReceipt

        RealityStore(
            fileName = db.toString(),
            transactionIdFactory = { TransactionId("tx-profile-e2e") },
            receiptIdFactory = { ReceiptId("receipt-profile-e2e") },
        ).use { store ->
            plan = preparePlan(store, "Diego")

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
            assertEquals("Diego", store.read().displayName!!.value)
            assertEquals(1L, store.read().revision)
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
            assertEquals("Diego", reopened.read().displayName!!.value)
            assertEquals(1L, reopened.read().revision)

            val recovery = recoveryResolver(reopened).resolve(plan)
            assertTrue(recovery is ApplyRecoveryResolution.Committed)
            assertEquals(
                committedReceipt,
                (recovery as ApplyRecoveryResolution.Committed).receipt,
            )
            assertEquals(1L, reopened.read().revision)
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    @Test
    fun uncertainFailureBeforeSqliteCommitReopensPreparedAndRetriesSameIdentity() {
        val db = Files.createTempDirectory("faro-v5-profile-recovery").resolve("reality.db")
        lateinit var plan: PreparedPlanView
        lateinit var originalIdentity: ApplyAttemptIdentity

        RealityStore(
            fileName = db.toString(),
            transactionIdFactory = { TransactionId("tx-profile-recovery") },
            receiptIdFactory = { ReceiptId("receipt-profile-recovery") },
        ).use { store ->
            plan = preparePlan(store, "Diego")
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
            assertNull(store.read().displayName)
            assertEquals(0L, store.read().revision)
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
            receiptIdFactory = { ReceiptId("receipt-profile-recovery") },
        ).use { reopened ->
            val recovery = recoveryResolver(reopened).resolve(plan)
            assertTrue(
                recovery is ApplyRecoveryResolution.PreparedForRevalidation
            )
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
            assertEquals("Diego", reopened.read().displayName!!.value)
            assertEquals(1L, reopened.read().revision)
            assertEquals(
                ApplyAttemptJournalState.COMMITTED,
                reopened.applyAttemptLookup
                    .find(originalIdentity.transactionId)!!
                    .state,
            )

            val finalRecovery = recoveryResolver(reopened).resolve(plan)
            assertTrue(finalRecovery is ApplyRecoveryResolution.Committed)
            assertEquals(1L, reopened.read().revision)
            assertEquals("ok", reopened.integrityCheck())
        }
    }

    private fun preparePlan(
        store: RealityStore,
        rawDisplayName: String,
    ): PreparedPlanView {
        val contract = profileContract()
        val binding = ProfileUpdateDeclaredBinding(store)
        val catalog = ContractCatalog.of(
            capabilities = listOf(
                CapabilityDescriptor(ProfileOperations.CAPABILITY_ID)
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
            session.availability(ProfileOperations.UPDATE_DECLARED),
        )

        val update = ProfileDeclaredUpdate(
            DeclaredDisplayName.of(rawDisplayName)
        )
        val runtime = PreparationRuntime(
            IdempotencyKeySource { IdempotencyKey("profile-intent-e2e") }
        )
        val spec = PreparationSpec(
            operationId = ProfileOperations.UPDATE_DECLARED,
            inputEncoder = object : CanonicalInputEncoder<ProfileDeclaredUpdate> {
                override val formatId: String =
                    ProfileDeclaredUpdatePayload.FORMAT_ID

                override fun encode(input: ProfileDeclaredUpdate): ByteArray =
                    ProfileDeclaredUpdatePayload.encodeBytes(input)
            },
            dependencyStampSource =
                DependencyStampSource<ProfileDeclaredUpdate> {
                    DependencyStampSet.of(store.read().dependencyStamp())
                },
            validator =
                PreparationValidator<ProfileDeclaredUpdate> {
                    PreparationValidation.Valid
                },
        )

        val result = runtime.prepare(
            session = session,
            input = update,
            spec = spec,
        )
        assertTrue(result is PreparationResult.Prepared<*>)
        val prepared =
            (result as PreparationResult.Prepared<ProfileDeclaredUpdate>).operation

        val applyPlan = ApplyPlan.of(
            ApplyPlanNode(
                nodeId = PlanNodeId("profile-update"),
                preparedOperation = prepared,
                dependsOn = emptySet(),
            )
        )
        val fingerprint = PlanFingerprint(
            "profile-plan-" +
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
                listOf(ProfileUpdateDeclaredRevalidation()),
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
            listOf(ProfileUpdateDeclaredBinding(store))
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

    private fun profileContract() =
        OperationContractDescriptor(
            operationId = ProfileOperations.UPDATE_DECLARED,
            capabilityId = ProfileOperations.CAPABILITY_ID,
            canonicalExecutor = ProfileOperations.EXECUTOR_ID,
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("1"),
            contractFingerprint =
                ContractFingerprint("profile-update-declared-v1"),
            exposure = ContractExposure.BINDABLE,
        )
}
