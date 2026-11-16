package ar.com.faro.v5.owner.finance

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.finance.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FinanceManualReviewedProposalTest {
    private val accountId = FinanceAccountId.of("account-existing")
    private val currency = FinanceCurrencyCode.of("ARS")
    private val goodIntent = "human:finance:intent-123"
    private fun prepareAccount(
        preview: FinanceManualDraftPreview.Account,
        humanIntentKey: String,
        readSnapshot: () -> FinanceSnapshot?,
        catalog: OperationContractLookupPort,
        confirmedHumanInput: FinanceAccountCreate = accountPreview().create,
    ): PreparedOperation? = FinanceManualReviewedProposal.prepareAccount(
        preview, confirmedHumanInput, humanIntentKey, readSnapshot, catalog,
    )

    private fun prepareEntry(
        preview: FinanceManualDraftPreview.Entry,
        humanIntentKey: String,
        readSnapshot: () -> FinanceSnapshot?,
        catalog: OperationContractLookupPort,
        confirmedHumanInput: FinanceEntryRecord = entryPreview().record,
    ): PreparedOperation? = FinanceManualReviewedProposal.prepareEntry(
        preview, confirmedHumanInput, humanIntentKey, readSnapshot, catalog,
    )

    private val empty = FinanceSnapshot(emptyList(), revision = 3L)

    private fun snapshot() = FinanceSnapshot(
        accounts = listOf(
            FinanceAccountSnapshot(
                accountId = accountId, displayName = FinanceAccountDisplayName.of("Principal"),
                currency = currency, nature = FinanceAccountNature.BANK,
                openingBalanceMinor = 100L
            )
        ),
        revision = 3L,
    )

    private fun descriptor(id: OperationId): OperationContractDescriptor =
        OperationContractDescriptor(
            operationId = id, capabilityId = FinanceOperations.CAPABILITY_ID,
            canonicalExecutor = FinanceOperations.EXECUTOR_ID,
            executionProfile = ExecutionProfile.REALITY_MUTATION,
            contractVersion = ContractVersion("metadata-v1"),
            contractFingerprint = ContractFingerprint("a".repeat(64)),
            exposure = ContractExposure.BINDABLE,
        )

    private fun catalog(): OperationContractLookupPort =
        OperationContractLookupPort { id -> descriptor(id) }

    private fun accountPreview(s: FinanceSnapshot = empty): FinanceManualDraftPreview.Account =
        requireNotNull(FinanceManualDraftPreviews.account(
            accountIdRaw = "new-account", displayNameRaw = "Ahorros",
            currencyRaw = "ARS", nature = FinanceAccountNature.CASH,
            explicitOpeningBalanceMinor = 0L, snapshot = s,
        ))

    private fun entryPreview(s: FinanceSnapshot = snapshot()): FinanceManualDraftPreview.Entry =
        requireNotNull(FinanceManualDraftPreviews.entry(
            entryIdRaw = "entry-new", accountIdRaw = accountId.value,
            amountMinor = 25L, kind = FinanceEntryKind.EXPENSE,
            conceptRaw = "Comida", categoryRaw = "Alimentos",
            effectiveAtEpochMillis = 1000L, recordedAtEpochMillis = 2000L,
            snapshot = s,
        ))

    @Test fun validAccountProposalIsCanonicalButInert() {
        val result = prepareAccount(accountPreview(), goodIntent, { empty }, catalog())
        assertNotNull(result)
        assertEquals(FinanceOperations.ACCOUNT_CREATE, result?.contract?.operationId)
        assertEquals(accountPreview().create, FinanceAccountCreatePayload.decode(result!!.payload))
        assertEquals(setOf(empty.accountsDependencyStamp()), result.dependencyStamps.asSet())
        assertEquals(IdempotencyKey(goodIntent), result.idempotencyKey)
    }

    @Test fun validEntryProposalPinsFinanceAccountAndRevision() {
        val before = snapshot()
        val result = prepareEntry(entryPreview(before), goodIntent, { before }, catalog())
        assertNotNull(result)
        assertEquals(entryPreview(before).record, FinanceEntryRecordPayload.decode(result!!.payload))
        assertEquals(
            setOf(before.accountsDependencyStamp(), before.accountDependencyStamp(accountId)),
            result.dependencyStamps.asSet(),
        )
    }

    @Test fun missingCatalogDenies() {
        val none = OperationContractLookupPort { null }
        assertNull(prepareAccount(accountPreview(), goodIntent, { empty }, none))
        assertNull(prepareEntry(entryPreview(), goodIntent, { snapshot() }, none))
    }

    @Test fun spoofedCapabilityExecutorOrProfileDenies() {
        val fake = OperationContractLookupPort { id ->
            descriptor(id).copy(canonicalExecutor = ExecutorId("household"))
        }
        assertNull(prepareAccount(accountPreview(), goodIntent, { empty }, fake))
        val wrongProfile = OperationContractLookupPort { id ->
            descriptor(id).copy(executionProfile = ExecutionProfile.READ_ONLY)
        }
        assertNull(prepareEntry(entryPreview(), goodIntent, { snapshot() }, wrongProfile))
    }

    @Test fun internalOnlyOrWrongMetadataVersionDenies() {
        val internal = OperationContractLookupPort { id ->
            descriptor(id).copy(exposure = ContractExposure.INTERNAL_ONLY)
        }
        val badVersion = OperationContractLookupPort { id ->
            descriptor(id).copy(contractVersion = ContractVersion("1"))
        }
        assertNull(prepareAccount(accountPreview(), goodIntent, { empty }, internal))
        assertNull(prepareAccount(accountPreview(), goodIntent, { empty }, badVersion))
    }

    @Test fun revisionDriftOrChangedCatalogDuringRecheckDenies() {
        var read = 0
        val source = {
            read += 1
            if (read == 1) empty else empty.copy(revision = 4L)
        }
        assertNull(prepareAccount(accountPreview(), goodIntent, source, catalog()))
        var lookupCount = 0
        val changing = OperationContractLookupPort { id ->
            lookupCount += 1
            descriptor(id).copy(contractFingerprint = ContractFingerprint((if (lookupCount == 1) "a" else "b").repeat(64)))
        }
        assertNull(prepareAccount(accountPreview(), goodIntent, { empty }, changing))
    }

    @Test fun forgedAccountPreviewAndDuplicateAccountDenied() {
        val good = accountPreview()
        val forged = good.copy(create = good.create.copy(openingBalanceMinor = 77L))
        assertNull(prepareAccount(forged, goodIntent, { empty }, catalog()))
        val present = FinanceSnapshot(
            listOf(FinanceAccountSnapshot(good.create.accountId, good.create.displayName,
                good.create.currency, good.create.nature, 0L)), revision = good.sourceRevision
        )
        assertNull(prepareAccount(good, goodIntent, { present }, catalog()))
    }

    @Test fun missingEntryAccountDeniedWithoutMutation() {
        val original = snapshot()
        val missing = FinanceSnapshot(emptyList(), revision = original.revision)
        assertNull(prepareEntry(entryPreview(original), goodIntent, { missing }, catalog()))
    }

    @Test fun spoofedEntryCurrencyOrProjectedBalanceDenied() {
        val before = snapshot()
        val normal = entryPreview(before)
        assertNull(prepareEntry(
            normal.copy(accountCurrency = FinanceCurrencyCode.of("USD")),
            goodIntent, { before }, catalog(),
        ))
        assertNull(prepareEntry(
            normal.copy(balanceAfterIfCommittedMinor = 1L),
            goodIntent, { before }, catalog(),
        ))
    }

    @Test fun invalidIntentKeysDenied() {
        for (invalid in listOf("", "short", "bad intent", "bad\nintent", "👀")) {
            assertNull(prepareAccount(accountPreview(), invalid, { empty }, catalog()))
        }
    }

    @Test fun forgedAmountOrOverflowCannotBePromoted() {
        val before = snapshot()
        val valid = entryPreview(before)
        val altered = valid.copy(record = valid.record.copy(amountMinor = 999L))
        assertNull(prepareEntry(altered, goodIntent, { before }, catalog()))
        val max = before.copy(accounts = before.accounts.map { it.copy(openingBalanceMinor = Long.MAX_VALUE) })
        assertNull(prepareEntry(valid, goodIntent, { max }, catalog()))
    }

    @Test fun repeatedIntentIsDeterministicAndSourceIsNeverMutated() {
        val before = snapshot()
        val copy = before.copy()
        val one = prepareEntry(entryPreview(before), goodIntent, { before }, catalog())
        val two = prepareEntry(entryPreview(before), goodIntent, { before }, catalog())
        assertNotNull(one)
        assertEquals(one, two)
        assertEquals(before, copy)
        assertTrue(before.entries.isEmpty())
    }
}
