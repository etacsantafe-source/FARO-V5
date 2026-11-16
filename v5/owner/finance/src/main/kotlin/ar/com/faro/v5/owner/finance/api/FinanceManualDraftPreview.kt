package ar.com.faro.v5.owner.finance.api

/**
 * W01-A. Inert result for a human-entered Finance draft.
 *
 * Never a grant, Workspace APPROVED Review, ApplyPlan, receipt, or a persisted
 * ledger result. The source revision is informational until a trusted Finance
 * host rechecks owner ACL, current generation and dependencies at Apply time.
 */
sealed interface FinanceManualDraftPreview {
    val sourceRevision: Long

    data class Account(
        val create: FinanceAccountCreate,
        override val sourceRevision: Long,
    ) : FinanceManualDraftPreview

    data class Entry(
        val record: FinanceEntryRecord,
        val accountCurrency: FinanceCurrencyCode,
        val balanceAfterIfCommittedMinor: Long,
        override val sourceRevision: Long,
    ) : FinanceManualDraftPreview
}

/**
 * Pure manual draft validation over a supplied Finance snapshot. There are
 * deliberately no storage, Apply, I/O, Workspace or authorization ports here.
 */
object FinanceManualDraftPreviews {
    fun account(
        accountIdRaw: String,
        displayNameRaw: String,
        currencyRaw: String,
        nature: FinanceAccountNature,
        explicitOpeningBalanceMinor: Long?,
        snapshot: FinanceSnapshot,
    ): FinanceManualDraftPreview.Account? = runCatching {
        // A missing opening is UNKNOWN, not an implicit 0. An explicitly
        // confirmed 0 remains valid.
        val opening = requireNotNull(explicitOpeningBalanceMinor)
        require(opening >= 0L)
        val id = FinanceAccountId.of(accountIdRaw)
        require(snapshot.accounts.none { it.accountId == id })
        FinanceManualDraftPreview.Account(
            create = FinanceAccountCreate(
                accountId = id,
                displayName = FinanceAccountDisplayName.of(displayNameRaw),
                currency = FinanceCurrencyCode.of(currencyRaw),
                nature = nature,
                openingBalanceMinor = opening,
            ),
            sourceRevision = snapshot.revision,
        )
    }.getOrNull()

    fun entry(
        entryIdRaw: String,
        accountIdRaw: String,
        amountMinor: Long,
        kind: FinanceEntryKind,
        conceptRaw: String,
        categoryRaw: String,
        effectiveAtEpochMillis: Long,
        recordedAtEpochMillis: Long,
        snapshot: FinanceSnapshot,
    ): FinanceManualDraftPreview.Entry? = runCatching {
        val id = FinanceEntryId.of(entryIdRaw)
        require(snapshot.entries.none { it.entryId == id })
        val accountId = FinanceAccountId.of(accountIdRaw)
        val account = requireNotNull(snapshot.accounts.singleOrNull {
            it.accountId == accountId
        })
        val record = FinanceEntryRecord(
            entryId = id,
            accountId = accountId,
            amountMinor = amountMinor,
            kind = kind,
            concept = FinanceEntryConcept.of(conceptRaw),
            category = FinanceEntryCategory.of(categoryRaw),
            effectiveAtEpochMillis = effectiveAtEpochMillis,
            recordedAtEpochMillis = recordedAtEpochMillis,
        )
        // Preview-only, exact arithmetic. Overflow must DENY; never wrap
        // or report this number as a confirmed balance.
        val existing = snapshot.ledgerMinor(accountId)
        val projected = when (kind) {
            FinanceEntryKind.INCOME -> Math.addExact(existing, record.amountMinor)
            FinanceEntryKind.EXPENSE -> Math.subtractExact(existing, record.amountMinor)
        }
        FinanceManualDraftPreview.Entry(
            record = record,
            accountCurrency = account.currency,
            balanceAfterIfCommittedMinor = projected,
            sourceRevision = snapshot.revision,
        )
    }.getOrNull()
}
