package ar.com.faro.v5.owner.finance

import ar.com.faro.v5.owner.finance.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FinanceManualDraftPreviewTest {
    @Test
    fun openingMissingDeniedRatherThanDefaultZero() {
        assertNull(accountPreview(opening = null))
        val explicitZero = accountPreview(opening = 0L)
        assertNotNull(explicitZero)
        assertEquals(0L, explicitZero?.create?.openingBalanceMinor)
    }

    @Test
    fun negativeOpeningDenied() {
        assertNull(accountPreview(opening = -1L))
    }

    @Test
    fun duplicateAccountIdDenied() {
        assertNull(accountPreview(opening = 100L, snapshot = snapshot()))
    }

    @Test
    fun invalidCurrencyDenied() {
        assertNull(accountPreview(opening = 10L, currency = "AR1"))
    }

    @Test
    fun zeroOrNegativeEntryAmountDenied() {
        assertNull(entryPreview(amount = 0L))
        assertNull(entryPreview(amount = -1L))
    }

    @Test
    fun entryAccountMissingDenied() {
        assertNull(entryPreview(accountId = "missing-account"))
        assertNull(entryPreview(snapshot = FinanceSnapshot(emptyList(), revision = 0L)))
    }

    @Test
    fun effectiveTimeAfterRecordedDenied() {
        assertNull(entryPreview(effectiveAt = 3000L, recordedAt = 2000L))
    }

    @Test
    fun duplicateEntryIdDenied() {
        val entry = FinanceEntrySnapshot(
            entryId = FinanceEntryId.of("entry-new"),
            accountId = FinanceAccountId.of("account-existing"),
            amountMinor = 50L,
            kind = FinanceEntryKind.INCOME,
            currency = FinanceCurrencyCode.of("ARS"),
            concept = FinanceEntryConcept.of("Ingreso previo"),
            category = FinanceEntryCategory.of("Ingreso"),
            effectiveAtEpochMillis = 1000L,
            recordedAtEpochMillis = 2000L,
        )
        assertNull(entryPreview(snapshot = snapshot(entries = listOf(entry))))
    }

    @Test
    fun ledgerOverflowDeniedRatherThanWrapped() {
        assertNull(entryPreview(snapshot = snapshot(opening = Long.MAX_VALUE), amount = 1L))
    }

    @Test
    fun validPreviewsDoNotMutateSnapshotOrGrantApply() {
        val before = snapshot()
        val copy = before.copy()
        val account = accountPreview(opening = 0L, snapshot = before)
        val entry = entryPreview(snapshot = before, amount = 20L)
        assertNotNull(account)
        assertNotNull(entry)
        assertEquals(7L, account?.sourceRevision)
        assertEquals(7L, entry?.sourceRevision)
        assertEquals("ARS", entry?.accountCurrency?.value)
        assertEquals(120L, entry?.balanceAfterIfCommittedMinor)
        assertEquals(copy, before)
        assertEquals(0, before.entries.size)
        // There is no mutation, owner-grant, Apply or receipt method in this API.
    }

    private fun accountPreview(
        opening: Long?,
        currency: String = "ARS",
        snapshot: FinanceSnapshot = FinanceSnapshot(emptyList(), revision = 7L),
    ) = FinanceManualDraftPreviews.account(
        accountIdRaw = "account-existing",
        displayNameRaw = "Principal",
        currencyRaw = currency,
        nature = FinanceAccountNature.BANK,
        explicitOpeningBalanceMinor = opening,
        snapshot = snapshot,
    )

    private fun entryPreview(
        accountId: String = "account-existing",
        amount: Long = 20L,
        effectiveAt: Long = 1000L,
        recordedAt: Long = 2000L,
        snapshot: FinanceSnapshot = snapshot(),
    ) = FinanceManualDraftPreviews.entry(
        entryIdRaw = "entry-new",
        accountIdRaw = accountId,
        amountMinor = amount,
        kind = FinanceEntryKind.INCOME,
        conceptRaw = "Ingreso manual",
        categoryRaw = "Otros",
        effectiveAtEpochMillis = effectiveAt,
        recordedAtEpochMillis = recordedAt,
        snapshot = snapshot,
    )

    private fun snapshot(
        opening: Long = 100L,
        entries: List<FinanceEntrySnapshot> = emptyList(),
    ) = FinanceSnapshot(
        accounts = listOf(
            FinanceAccountSnapshot(
                accountId = FinanceAccountId.of("account-existing"),
                displayName = FinanceAccountDisplayName.of("Principal"),
                currency = FinanceCurrencyCode.of("ARS"),
                nature = FinanceAccountNature.BANK,
                openingBalanceMinor = opening,
            )
        ),
        revision = 7L,
        entries = entries,
    )
}
