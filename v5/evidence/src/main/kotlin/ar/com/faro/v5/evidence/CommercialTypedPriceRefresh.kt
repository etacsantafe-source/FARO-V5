package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint

/**
 * Trusted host-provided generation/pointer/foreground identity. Not a capability.
 * In a real host, pointerSequence must change on every pointer change including ABA.
 */
data class CommercialTypedRefreshFence(
    val generationId: String,
    val pointerSequence: Long,
    val hostEpoch: String,
    val principal: String,
) {
    init {
        require(generationId.isNotBlank() && generationId.length <= 160)
        require(pointerSequence > 0L)
        require(hostEpoch.isNotBlank() && hostEpoch.length <= 160)
        require(principal.isNotBlank() && principal.length <= 160)
    }
}

data class CommercialTypedRefreshInput(
    val observation: CommercialPriceObservation,
    val terms: CommercialEvidenceTypedTerms,
    val localRequestId: String,
    val asOfEpochMillis: Long,
)

class PreparedCommercialTypedRefresh internal constructor(
    val input: CommercialTypedRefreshInput,
    val fence: CommercialTypedRefreshFence,
    val baselineWatermark: String,
    val idempotencyKey: IdempotencyKey,
    val payloadFingerprint: PayloadFingerprint,
    val reviewFingerprint: String,
)

enum class CommercialTypedRefreshRejection {
    NOT_AUTHORIZED,
    WRONG_CONTRACT,
    STALE_FENCE,
    INVALID_INPUT,
    NO_EXISTING_HISTORY,
    SOURCE_CHANGED,
    NOT_CONFIRMED,
    STORE_UNAVAILABLE,
    IDENTITY_CONFLICT,
}

sealed interface CommercialTypedRefreshPreparation {
    data class Ready(val draft: PreparedCommercialTypedRefresh) :
        CommercialTypedRefreshPreparation
    data class Rejected(val reason: CommercialTypedRefreshRejection) :
        CommercialTypedRefreshPreparation
}

sealed interface CommercialTypedRefreshResult {
    data class Appended(
        val record: CommercialEvidenceRecord,
        val replayed: Boolean,
        val status: String = "APPENDED_NEW_OBSERVATION_NOT_SUPERSESSION",
    ) : CommercialTypedRefreshResult {
        init { require(record.observation.sourceKind == CommercialEvidenceSourceKind.MANUAL) }
    }
    /** May already be COMMITTED. Reconcile only by the existing owner identity. */
    data class RecoveryRequired(val idempotencyKey: IdempotencyKey) :
        CommercialTypedRefreshResult
    data class Rejected(val reason: CommercialTypedRefreshRejection) :
        CommercialTypedRefreshResult
}

/**
 * C3 STAGED-only adapter over the SAME Evidence2 store and shared owner gate.
 * This is not an ACTIVE Android host, a new writer, an automatic price feed or
 * an authority token. Callers must provide independently trusted admission.
 */
class CommercialTypedPriceRefreshService(
    private val store: CommercialEvidenceStore,
    private val catalog: OperationContractLookupPort,
    private val principal: String,
    private val currentFence: () -> CommercialTypedRefreshFence?,
    private val householdAdmission: (CommercialProductKey) -> Boolean,
    private val commercialWriteAdmission: (CommercialProductKey) -> Boolean,
) {
    init { require(principal.isNotBlank() && principal.length <= 160) }

    fun prepare(input: CommercialTypedRefreshInput): CommercialTypedRefreshPreparation {
        val product = input.observation.productKey
        if (input.observation.sourceKind != CommercialEvidenceSourceKind.MANUAL ||
            input.asOfEpochMillis < 0L || !validRequestId(input.localRequestId)
        ) return rejected(CommercialTypedRefreshRejection.INVALID_INPUT)

        val fence = trustedFence()
            ?: return rejected(CommercialTypedRefreshRejection.STALE_FENCE)
        if (!admitted(product)) return rejected(CommercialTypedRefreshRejection.NOT_AUTHORIZED)
        if (!canonicalContract()) return rejected(CommercialTypedRefreshRejection.WRONG_CONTRACT)

        val snapshot = runCatching { store.stagedTypedPortability.snapshot() }
            .getOrNull() ?: return rejected(CommercialTypedRefreshRejection.STORE_UNAVAILABLE)
        val history = runCatching {
            CommercialTypedPriceComparison.project(
                snapshot, product, input.asOfEpochMillis, MAX_OBSERVATION_AGE_MILLIS,
            )
        }.getOrNull() ?: return rejected(CommercialTypedRefreshRejection.STORE_UNAVAILABLE)
        if (history.history.isEmpty()) {
            return rejected(CommercialTypedRefreshRejection.NO_EXISTING_HISTORY)
        }
        if (trustedFence() != fence || !admitted(product)) {
            return rejected(CommercialTypedRefreshRejection.STALE_FENCE)
        }
        val key = idempotency(fence, input.localRequestId)
        val fingerprint = payloadFingerprint(input.observation, input.terms)
        return CommercialTypedRefreshPreparation.Ready(
            PreparedCommercialTypedRefresh(
                input, fence, snapshot.sourceWatermark, key, fingerprint,
                reviewFingerprint(snapshot.sourceWatermark, fence, key, fingerprint),
            )
        )
    }

    fun confirmAndAppend(
        draft: PreparedCommercialTypedRefresh,
        trustedHumanConfirmed: () -> Boolean,
    ): CommercialTypedRefreshResult {
        val product = draft.input.observation.productKey
        if (!admitted(product)) {
            return CommercialTypedRefreshResult.Rejected(
                CommercialTypedRefreshRejection.NOT_AUTHORIZED)
        }
        if (trustedFence() != draft.fence) {
            return CommercialTypedRefreshResult.Rejected(
                CommercialTypedRefreshRejection.STALE_FENCE)
        }
        if (!canonicalContract()) {
            return CommercialTypedRefreshResult.Rejected(
                CommercialTypedRefreshRejection.WRONG_CONTRACT)
        }
        if (draft.input.observation.sourceKind != CommercialEvidenceSourceKind.MANUAL ||
            !validRequestId(draft.input.localRequestId) ||
            draft.idempotencyKey != idempotency(draft.fence, draft.input.localRequestId) ||
            draft.payloadFingerprint != payloadFingerprint(
                draft.input.observation, draft.input.terms) ||
            draft.reviewFingerprint != reviewFingerprint(
                draft.baselineWatermark, draft.fence,
                draft.idempotencyKey, draft.payloadFingerprint)
        ) {
            return CommercialTypedRefreshResult.Rejected(
                CommercialTypedRefreshRejection.INVALID_INPUT)
        }

        fun confirmed() = runCatching { trustedHumanConfirmed() }.getOrDefault(false)
        if (!confirmed()) {
            return CommercialTypedRefreshResult.Rejected(
                CommercialTypedRefreshRejection.NOT_CONFIRMED)
        }

        val snapshot = runCatching { store.stagedTypedPortability.snapshot() }
            .getOrNull() ?: return CommercialTypedRefreshResult.Rejected(
                CommercialTypedRefreshRejection.STORE_UNAVAILABLE)
        val valid = runCatching {
            CommercialTypedPriceComparison.project(
                snapshot, product, draft.input.asOfEpochMillis,
                MAX_OBSERVATION_AGE_MILLIS,
            )
        }.getOrNull()
        if (valid == null || valid.history.isEmpty() ||
            snapshot.sourceWatermark != draft.baselineWatermark) {
            return CommercialTypedRefreshResult.Rejected(
                CommercialTypedRefreshRejection.SOURCE_CHANGED)
        }
        if (trustedFence() != draft.fence || !admitted(product)) {
            return CommercialTypedRefreshResult.Rejected(
                CommercialTypedRefreshRejection.STALE_FENCE)
        }
        if (!confirmed()) {
            return CommercialTypedRefreshResult.Rejected(
                CommercialTypedRefreshRejection.NOT_CONFIRMED)
        }

        // Commit can happen even if the host immediately loses its grant.
        // Never report a possible COMMITTED row as a rolled-back rejection.
        val outcome = runCatching {
            store.recordWithTypedTerms(
                draft.input.observation, draft.input.terms,
                draft.idempotencyKey, draft.payloadFingerprint,
            )
        }.getOrElse {
            return CommercialTypedRefreshResult.RecoveryRequired(draft.idempotencyKey)
        }
        if (trustedFence() != draft.fence || !admitted(product)) {
            return CommercialTypedRefreshResult.RecoveryRequired(draft.idempotencyKey)
        }
        return when (outcome) {
            is CommercialEvidenceRecordOutcome.Committed ->
                CommercialTypedRefreshResult.Appended(outcome.record, outcome.replayed)
            is CommercialEvidenceRecordOutcome.RejectedIdentityConflict ->
                CommercialTypedRefreshResult.Rejected(
                    CommercialTypedRefreshRejection.IDENTITY_CONFLICT)
        }
    }

    private fun canonicalContract(): Boolean = runCatching {
        val operation = CommercialEvidenceOperations.RECORD
        catalog.current(operation)?.let { contract ->
            contract.operationId == operation &&
                contract.capabilityId == CommercialEvidenceOperations.CAPABILITY_ID &&
                contract.canonicalExecutor == CommercialEvidenceOperations.EXECUTOR_ID &&
                contract.executionProfile == ExecutionProfile.OBSERVATIONAL_WRITE &&
                contract.exposure == ContractExposure.BINDABLE
        } == true
    }.getOrDefault(false)

    private fun admitted(product: CommercialProductKey): Boolean =
        runCatching { householdAdmission(product) }.getOrDefault(false) &&
            runCatching { commercialWriteAdmission(product) }.getOrDefault(false)

    private fun trustedFence(): CommercialTypedRefreshFence? =
        runCatching { currentFence() }.getOrNull()?.takeIf { it.principal == principal }

    private fun validRequestId(value: String): Boolean =
        value.isNotBlank() && value.length <= 160 && value == value.trim() &&
            value.none { it.code < 32 || it.code == 127 }

    private fun idempotency(
        fence: CommercialTypedRefreshFence, requestId: String,
    ): IdempotencyKey = IdempotencyKey(
        CanonicalHashBuilder.domain("w04-c3-staged-typed-refresh-idempotency")
            .string("principal", principal)
            .string("generation", fence.generationId)
            .string("sequence", fence.pointerSequence.toString())
            .string("hostEpoch", fence.hostEpoch)
            .string("requestId", requestId)
            .sha256Hex()
    )

    private fun payloadFingerprint(
        observation: CommercialPriceObservation,
        terms: CommercialEvidenceTypedTerms,
    ): PayloadFingerprint = PayloadFingerprint(
        CanonicalHashBuilder.domain("w04-c3-typed-refresh-payload-v1")
            .string("product", observation.productKey.value)
            .string("merchant", observation.merchant.value)
            .string("currency", observation.currencyCode.value)
            .string("amountMinor", observation.amountMinor.toString())
            .string("sourceKind", observation.sourceKind.name)
            .string("sourceRef", observation.sourceRef)
            .string("observedPresent", (observation.observedAtEpochMillis != null).toString())
            .string("observedAt", observation.observedAtEpochMillis?.toString() ?: "")
            .string("termsFingerprint", terms.fingerprint())
            .sha256Hex()
    )

    private fun reviewFingerprint(
        sourceWatermark: String,
        fence: CommercialTypedRefreshFence,
        key: IdempotencyKey,
        payload: PayloadFingerprint,
    ): String = CanonicalHashBuilder.domain("w04-c3-typed-refresh-review-v1")
        .string("sourceWatermark", sourceWatermark)
        .string("fenceGeneration", fence.generationId)
        .string("fenceSequence", fence.pointerSequence.toString())
        .string("fenceEpoch", fence.hostEpoch)
        .string("fencePrincipal", fence.principal)
        .string("idempotency", key.value)
        .string("payloadFingerprint", payload.value)
        .sha256Hex()

    private fun rejected(reason: CommercialTypedRefreshRejection) =
        CommercialTypedRefreshPreparation.Rejected(reason)

    companion object {
        private const val MAX_OBSERVATION_AGE_MILLIS =
            365L * 24L * 60L * 60L * 1000L
    }
}
