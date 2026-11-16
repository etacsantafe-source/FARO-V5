package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.DependencyStampSet
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.PreparedOperation

/**
 * W01-B0: creates a canonical, INERT Finance proposal. It does not grant
 * authorization, write a Review, call Apply or emit a receipt. The caller
 * must separately prove user intent, owner grants and generation freshness.
 */
object FinanceManualReviewedProposal {
    private val keyShape = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{7,159}")
    private val fingerprintShape = Regex("[0-9a-f]{64}")

    private fun verifiedDescriptor(
        id: OperationId,
        catalog: OperationContractLookupPort,
    ): OperationContractDescriptor? {
        val observed = catalog.current(id) ?: return null
        return observed.takeIf {
            it.operationId == id &&
                it.capabilityId == FinanceOperations.CAPABILITY_ID &&
                it.canonicalExecutor == FinanceOperations.EXECUTOR_ID &&
                it.executionProfile == ExecutionProfile.REALITY_MUTATION &&
                it.exposure == ContractExposure.BINDABLE &&
                it.contractVersion.value == "metadata-v1" &&
                fingerprintShape.matches(it.contractFingerprint.value)
        }
    }

    fun prepareAccount(
        preview: FinanceManualDraftPreview.Account,
        confirmedHumanInput: FinanceAccountCreate,
        humanIntentKey: String,
        readSnapshot: () -> FinanceSnapshot?,
        catalog: OperationContractLookupPort,
    ): PreparedOperation? = runCatching {
        require(keyShape.matches(humanIntentKey))
        val before = requireNotNull(readSnapshot())
        require(before.revision == preview.sourceRevision)
        val create = preview.create
        require(create == confirmedHumanInput)
        val validated = FinanceManualDraftPreviews.account(
            accountIdRaw = create.accountId.value,
            displayNameRaw = create.displayName.value,
            currencyRaw = create.currency.value,
            nature = create.nature,
            explicitOpeningBalanceMinor = create.openingBalanceMinor,
            snapshot = before,
        )
        require(validated == preview)
        val contract = requireNotNull(verifiedDescriptor(FinanceOperations.ACCOUNT_CREATE, catalog))
        val operation = PreparedOperation(
            contract = contract,
            payload = FinanceAccountCreatePayload.payload(create),
            dependencyStamps = DependencyStampSet.of(before.accountsDependencyStamp()),
            idempotencyKey = IdempotencyKey(humanIntentKey),
        )
        // Pure fail-closed recheck: still NOT an owner authorization or COMMIT fence.
        require(readSnapshot() == before)
        require(verifiedDescriptor(FinanceOperations.ACCOUNT_CREATE, catalog) == contract)
        operation
    }.getOrNull()

    fun prepareEntry(
        preview: FinanceManualDraftPreview.Entry,
        confirmedHumanInput: FinanceEntryRecord,
        humanIntentKey: String,
        readSnapshot: () -> FinanceSnapshot?,
        catalog: OperationContractLookupPort,
    ): PreparedOperation? = runCatching {
        require(keyShape.matches(humanIntentKey))
        val before = requireNotNull(readSnapshot())
        require(before.revision == preview.sourceRevision)
        val entry = preview.record
        require(entry == confirmedHumanInput)
        val validated = FinanceManualDraftPreviews.entry(
            entryIdRaw = entry.entryId.value,
            accountIdRaw = entry.accountId.value,
            amountMinor = entry.amountMinor,
            kind = entry.kind,
            conceptRaw = entry.concept.value,
            categoryRaw = entry.category.value,
            effectiveAtEpochMillis = entry.effectiveAtEpochMillis,
            recordedAtEpochMillis = entry.recordedAtEpochMillis,
            snapshot = before,
        )
        require(validated == preview)
        val contract = requireNotNull(verifiedDescriptor(FinanceOperations.ENTRY_RECORD, catalog))
        val operation = PreparedOperation(
            contract = contract,
            payload = FinanceEntryRecordPayload.payload(entry),
            dependencyStamps = DependencyStampSet.of(
                before.accountsDependencyStamp(),
                before.accountDependencyStamp(entry.accountId),
            ),
            idempotencyKey = IdempotencyKey(humanIntentKey),
        )
        require(readSnapshot() == before)
        require(verifiedDescriptor(FinanceOperations.ENTRY_RECORD, catalog) == contract)
        operation
    }.getOrNull()
}
