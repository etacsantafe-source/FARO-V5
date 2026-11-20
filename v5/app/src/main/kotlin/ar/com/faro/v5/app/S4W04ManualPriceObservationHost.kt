package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEvidenceExecution
import ar.com.faro.v5.evidence.CommercialEvidenceOperations
import ar.com.faro.v5.evidence.CommercialEvidenceRecord
import ar.com.faro.v5.evidence.CommercialEvidenceRecordOutcome
import ar.com.faro.v5.evidence.CommercialEvidencePayload
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialPriceObservation
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.DependencyStampSet
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot

/**
 * C1a covers an observation ONLY. Known quote terms cannot be discarded or
 * quietly written to sourceRef; C1b must provide a versioned typed schema.
 */
internal data class ManualCommercialQuoteTerms(
    val locality: String? = null,
    val issuedAtEpochMillis: Long? = null,
    val validUntilEpochMillis: Long? = null,
    val presentation: String? = null,
    val paymentMethod: String? = null,
    val eligibility: String? = null,
    val promotion: String? = null,
) {
    val requiresSchemaV2: Boolean get() =
        locality != null || issuedAtEpochMillis != null || validUntilEpochMillis != null ||
            presentation != null || paymentMethod != null || eligibility != null || promotion != null
}

internal data class ManualPriceObservationInput(
    val merchant: CommercialMerchant,
    val currencyCode: CommercialCurrencyCode,
    val amountMinor: Long,
    val sourceRef: String,
    val observedAtEpochMillis: Long?,
    val terms: ManualCommercialQuoteTerms = ManualCommercialQuoteTerms(),
)

/** Not a reusable capability grant, and not a Reality ApplyPlan or receipt. */
internal class PreparedManualPriceObservation internal constructor(
    val candidate: CommercialNeedProductCandidate,
    val prepared: PreparedOperation,
    val localRequestId: String,
)

internal enum class ManualPriceObservationRejection {
    NOT_AUTHORIZED,
    STALE_SELECTION,
    WRONG_CONTRACT,
    REQUIRES_VERSIONED_TERMS,
    INVALID_INPUT,
    NOT_CONFIRMED,
    IDEMPOTENCY_CONFLICT,
    STORE_UNAVAILABLE,
}

internal sealed interface ManualPriceObservationPreparation {
    data class Ready(val draft: PreparedManualPriceObservation) : ManualPriceObservationPreparation
    data class Rejected(val reason: ManualPriceObservationRejection) : ManualPriceObservationPreparation
}

internal sealed interface ManualPriceObservationResult {
    data class Recorded(
        val record: CommercialEvidenceRecord,
        val replayed: Boolean,
        val localityKnown: Boolean = false,
        val quoteValidityKnown: Boolean = false,
        val presentationKnown: Boolean = false,
    ) : ManualPriceObservationResult {
        init {
            require(!localityKnown && !quoteValidityKnown && !presentationKnown) {
                "schema1 cannot certify typed commercial quote terms"
            }
            require(record.observation.sourceKind == CommercialEvidenceSourceKind.MANUAL)
        }
    }

    /** Commit may already exist. Repeat ONLY the same idempotency identity after recovery. */
    data class RecoveryRequired(val idempotencyKey: IdempotencyKey) : ManualPriceObservationResult
    data class Rejected(val reason: ManualPriceObservationRejection) : ManualPriceObservationResult
}

internal class S4W04ManualPriceObservationHost(
    private val session: FaroV5GenerationStoreGraph.Household10DevApplySession,
    private val catalog: OperationContractLookupPort,
    private val principal: String,
    private val currentSnapshot: () -> HouseholdNeedsSnapshot?,
    private val currentFence: () -> CommercialNeedReadFence?,
    private val householdAdmission: (HouseholdNeedId) -> Boolean,
    private val commercialWriteAdmission: (CommercialProductKey) -> Boolean,
) {
    init { require(principal.isNotBlank()) }

    fun prepare(
        candidate: CommercialNeedProductCandidate,
        input: ManualPriceObservationInput,
        localRequestId: String,
    ): ManualPriceObservationPreparation {
        if (!bothAdmitted(candidate)) return rejected(ManualPriceObservationRejection.NOT_AUTHORIZED)
        if (!selectionCurrent(candidate)) return rejected(ManualPriceObservationRejection.STALE_SELECTION)
        if (input.terms.requiresSchemaV2) {
            return rejected(ManualPriceObservationRejection.REQUIRES_VERSIONED_TERMS)
        }
        if (!validRequestId(localRequestId)) {
            return rejected(ManualPriceObservationRejection.INVALID_INPUT)
        }
        val contract = canonicalRecordContract()
            ?: return rejected(ManualPriceObservationRejection.WRONG_CONTRACT)
        val observation = runCatching {
            CommercialPriceObservation(
                productKey = candidate.productKey,
                merchant = input.merchant,
                currencyCode = input.currencyCode,
                amountMinor = input.amountMinor,
                sourceKind = CommercialEvidenceSourceKind.MANUAL,
                sourceRef = input.sourceRef,
                observedAtEpochMillis = input.observedAtEpochMillis,
            )
        }.getOrNull() ?: return rejected(ManualPriceObservationRejection.INVALID_INPUT)
        val payload = runCatching { CommercialEvidencePayload.payload(observation) }.getOrNull()
            ?: return rejected(ManualPriceObservationRejection.INVALID_INPUT)
        val prepared = PreparedOperation(
            contract = contract,
            payload = payload,
            dependencyStamps = DependencyStampSet.empty(),
            idempotencyKey = stableIdempotency(candidate, localRequestId),
        )
        if (!bothAdmitted(candidate) || !selectionCurrent(candidate)) {
            return rejected(ManualPriceObservationRejection.STALE_SELECTION)
        }
        return ManualPriceObservationPreparation.Ready(
            PreparedManualPriceObservation(candidate, prepared, localRequestId)
        )
    }

    fun confirmAndRecord(
        draft: PreparedManualPriceObservation,
        trustedHumanConfirmed: () -> Boolean,
    ): ManualPriceObservationResult {
        val key = draft.prepared.idempotencyKey
        if (!bothAdmitted(draft.candidate)) {
            return ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.NOT_AUTHORIZED)
        }
        if (!selectionCurrent(draft.candidate)) {
            return ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.STALE_SELECTION)
        }
        val contract = canonicalRecordContract()
            ?: return ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.WRONG_CONTRACT)
        val decoded = CommercialEvidencePayload.decode(draft.prepared.payload)
        if (!validRequestId(draft.localRequestId) ||
            draft.prepared.contract != contract ||
            draft.prepared.dependencyStamps != DependencyStampSet.empty() ||
            draft.prepared.idempotencyKey != stableIdempotency(draft.candidate, draft.localRequestId) ||
            decoded?.productKey != draft.candidate.productKey ||
            decoded?.sourceKind != CommercialEvidenceSourceKind.MANUAL
        ) return ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.INVALID_INPUT)

        fun confirmed() = runCatching { trustedHumanConfirmed() }.getOrDefault(false)
        if (!confirmed()) {
            return ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.NOT_CONFIRMED)
        }
        if (!bothAdmitted(draft.candidate) || !selectionCurrent(draft.candidate)) {
            return ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.STALE_SELECTION)
        }
        // Once the journaled Evidence INSERT commits, no host permission
        // failure may be reported as a rollback. Return recovery-required.
        val execution = runCatching {
            session.authorizedManualEvidenceRecord(
                contract, draft.prepared,
                { admitsHousehold(draft.candidate.need.id) },
                { admitsCommercial(draft.candidate.productKey) },
                ::confirmed,
            )
        }.getOrElse { return ManualPriceObservationResult.RecoveryRequired(key) }
            ?: return ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.NOT_AUTHORIZED)

        return when (execution) {
            is CommercialEvidenceExecution.Applied -> when (val outcome = execution.outcome) {
                is CommercialEvidenceRecordOutcome.Committed ->
                    if (!bothAdmitted(draft.candidate) || !selectionCurrent(draft.candidate)) {
                        ManualPriceObservationResult.RecoveryRequired(key)
                    } else {
                        ManualPriceObservationResult.Recorded(outcome.record, outcome.replayed)
                    }
                is CommercialEvidenceRecordOutcome.RejectedIdentityConflict ->
                    ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.IDEMPOTENCY_CONFLICT)
            }
            else -> ManualPriceObservationResult.Rejected(ManualPriceObservationRejection.STORE_UNAVAILABLE)
        }
    }

    private fun canonicalRecordContract(): OperationContractDescriptor? {
        val op = CommercialEvidenceOperations.RECORD
        return catalog.current(op)?.takeIf {
            it.operationId == op &&
                it.capabilityId == CommercialEvidenceOperations.CAPABILITY_ID &&
                it.canonicalExecutor == CommercialEvidenceOperations.EXECUTOR_ID &&
                it.executionProfile == ExecutionProfile.OBSERVATIONAL_WRITE &&
                it.exposure == ContractExposure.BINDABLE
        }
    }

    private fun admitsHousehold(id: HouseholdNeedId) =
        runCatching { householdAdmission(id) }.getOrDefault(false)

    private fun admitsCommercial(key: CommercialProductKey) =
        runCatching { commercialWriteAdmission(key) }.getOrDefault(false)

    private fun bothAdmitted(candidate: CommercialNeedProductCandidate) =
        admitsHousehold(candidate.need.id) && admitsCommercial(candidate.productKey)

    private fun selectionCurrent(candidate: CommercialNeedProductCandidate): Boolean =
        runCatching {
            S4W04CommercialNeedProductSelection.revalidate(
                candidate, currentSnapshot(), currentFence(),
            ) { admitsHousehold(candidate.need.id) } is CommercialNeedProductDecision.Candidate
        }.getOrDefault(false)

    private fun stableIdempotency(
        candidate: CommercialNeedProductCandidate,
        localRequestId: String,
    ): IdempotencyKey =
        IdempotencyKey(
            CanonicalHashBuilder.domain("w04.c1a.manual.observation")
                .string("principal", principal)
                .string("generation", candidate.readFence.generationId)
                .string("pointerSequence", candidate.readFence.pointerSequence.toString())
                .string("hostEpoch", candidate.readFence.hostEpoch)
                .string("needId", candidate.need.id.value)
                .string("requestId", localRequestId)
                .sha256Hex()
        )

    private fun validRequestId(requestId: String): Boolean =
        requestId.isNotBlank() && requestId.length <= 160 &&
            requestId == requestId.trim() && requestId.none { it.code < 32 || it.code == 127 }

    private fun rejected(reason: ManualPriceObservationRejection) =
        ManualPriceObservationPreparation.Rejected(reason)
}
