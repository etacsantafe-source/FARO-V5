package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.apply.*
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.kernel.runtime.PreparedPlanEnvelopeCodec
import ar.com.faro.v5.owner.finance.api.FinanceAccountCreateBinding
import ar.com.faro.v5.owner.finance.api.FinanceAccountCreateRevalidation
import ar.com.faro.v5.owner.finance.api.FinanceEntryRecordBinding
import ar.com.faro.v5.owner.finance.api.FinanceEntryRecordRevalidation
import ar.com.faro.v5.owner.finance.api.FinanceOperations
import ar.com.faro.v5.owner.profile.api.ProfileOperations
import ar.com.faro.v5.owner.profile.api.ProfileUpdateDeclaredBinding
import ar.com.faro.v5.owner.profile.api.ProfileUpdateDeclaredRevalidation
import ar.com.faro.v5.store.reality.RealityStore
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/**
 * Real Android host composition for the active V2 five-owner graph.
 *
 * This never accepts a caller-supplied plan or receipt, and cannot execute
 * from a V1-only graph. It is not a UI port or a staged/quarantined executor.
 * Shipping V2 remains disabled until the full F1-F4 activation gate passes.
 */
class FaroV5ActiveReviewedApplySession(
    private val generationId: String,
    private val workspace: TrustedConversationApplyReviewPort,
    private val reality: RealityStore,
    private val gate: MaterialWriteGate,
    private val activeGeneration: Supplier<String>,
    private val recoveryComplete: BooleanSupplier,
) {
    init { require(generationId.isNotBlank()) }

    private fun requireCurrent() {
        check(recoveryComplete.getAsBoolean() && activeGeneration.get() == generationId) {
            "Reviewed Apply is unavailable during recovery or after generation switch"
        }
    }

    private val allowedContracts: Map<OperationId, OperationContractDescriptor> =
        listOf(
            OperationContractDescriptor(
                ProfileOperations.UPDATE_DECLARED,
                ProfileOperations.CAPABILITY_ID,
                ProfileOperations.EXECUTOR_ID,
                ExecutionProfile.REALITY_MUTATION,
                ContractVersion("1"),
                ContractFingerprint("profile-update-declared-v1"),
                ContractExposure.BINDABLE,
            ),
            OperationContractDescriptor(
                FinanceOperations.ACCOUNT_CREATE,
                FinanceOperations.CAPABILITY_ID,
                FinanceOperations.EXECUTOR_ID,
                ExecutionProfile.REALITY_MUTATION,
                ContractVersion("1"),
                ContractFingerprint("finance-account-create-v1"),
                ContractExposure.BINDABLE,
            ),
            OperationContractDescriptor(
                FinanceOperations.ENTRY_RECORD,
                FinanceOperations.CAPABILITY_ID,
                FinanceOperations.EXECUTOR_ID,
                ExecutionProfile.REALITY_MUTATION,
                ContractVersion("1"),
                ContractFingerprint("finance-entry-record-v1"),
                ContractExposure.BINDABLE,
            ),
        ).associateBy { it.operationId }

    private val coordinator: ReviewedConversationApplyCoordinator =
        ReviewedConversationApplyCoordinator(
            workspace = workspace,
            decode = PreparedPlanEnvelopeCodec::decode,
            retry = ApplyRetryReceiptGate(
                reality.applyAttemptIdentities,
                reality.executionReceiptLookup,
            ),
            preflight = ApplyPreflightGate(
                contracts = OperationContractLookupPort { id -> allowedContracts[id] },
                dependencyStamps = reality,
                operationRevalidators = listOf(
                    ProfileUpdateDeclaredRevalidation(),
                    FinanceAccountCreateRevalidation(),
                    FinanceEntryRecordRevalidation(reality.financePersistence),
                ),
            ),
            bindings = ApplyBindingResolutionGate(
                listOf(
                    ProfileUpdateDeclaredBinding(reality),
                    FinanceAccountCreateBinding(reality.financePersistence),
                    FinanceEntryRecordBinding(reality.financePersistence),
                ),
            ),
            executor = ApplyTransactionExecutor(reality, reality),
            attempts = reality.applyAttemptLookup,
            receipts = reality.executionReceiptLookup,
        )

    /** Dormant user-request port; shipping V2 OFF means it cannot execute. */
    @JvmField val port: ConversationApplyRequestPort = object : ConversationApplyRequestPort {
        override suspend fun request(context: ConversationPlanReviewContext): ConversationApplyHostOutcome {
            if (!FaroV5HostCertification.V2_ACTIVE_ENABLED) {
                return ConversationApplyHostOutcome.Denied("v2_not_certified")
            }
            return executeAfterHumanRequest(context)
        }
    }

    fun executeAfterHumanRequest(
        context: ConversationPlanReviewContext,
    ): ConversationApplyHostOutcome = gate.withMaterialWrite {
        requireCurrent()
        val outcome = coordinator.apply(context).toHostOutcome()
        requireCurrent()
        outcome
    }
}

/** Bounded Kotlin adapter: Java composition sees only the neutral host contract. */
internal fun ReviewedConversationApplyResult.toHostOutcome(): ConversationApplyHostOutcome = when (this) {
    is ReviewedConversationApplyResult.Committed -> ConversationApplyHostOutcome.Committed(receipt, review)
    is ReviewedConversationApplyResult.Denied -> ConversationApplyHostOutcome.Denied(reason)
    ReviewedConversationApplyResult.RecoveryRequired -> ConversationApplyHostOutcome.RecoveryRequired
}

