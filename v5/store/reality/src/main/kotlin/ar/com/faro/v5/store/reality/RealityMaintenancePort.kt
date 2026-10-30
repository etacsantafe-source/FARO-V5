package ar.com.faro.v5.store.reality

import ar.com.faro.v5.owner.finance.api.FinanceAccountDisplayName
import ar.com.faro.v5.owner.finance.api.FinanceAccountId
import ar.com.faro.v5.owner.finance.api.FinanceAccountNature
import ar.com.faro.v5.owner.finance.api.FinanceCurrencyCode
import ar.com.faro.v5.owner.finance.api.FinanceEntryCategory
import ar.com.faro.v5.owner.finance.api.FinanceEntryConcept
import ar.com.faro.v5.owner.finance.api.FinanceEntryId
import ar.com.faro.v5.owner.finance.api.FinanceEntryKind
import ar.com.faro.v5.owner.finance.api.FinanceEntryRecord
import ar.com.faro.v5.owner.profile.api.DeclaredDisplayName

data class RealityMaintenanceInspection(
    val schemaVersion: Int,
    val integrityHealthy: Boolean,
    val foreignKeysHealthy: Boolean,
    val profilePresent: Boolean,
    val financeAccountCount: Long,
    val financeEntryCount: Long,
    val applyAttemptCount: Long,
    val executionReceiptCount: Long,
    val outboxEffectCount: Long,
    val profileRevision: Long = 0L,
    val financeRevision: Long = 0L,
) {
    init {
        require(schemaVersion >= 0) { "Reality schema version must be non-negative" }
        listOf(
            financeAccountCount,
            financeEntryCount,
            applyAttemptCount,
            executionReceiptCount,
            outboxEffectCount,
            profileRevision,
            financeRevision,
        ).forEach { count ->
            require(count >= 0L) { "Reality maintenance counts must be non-negative" }
        }
    }

    val freshForSemanticMigration: Boolean
        get() =
            !profilePresent &&
                financeAccountCount == 0L &&
                financeEntryCount == 0L &&
                applyAttemptCount == 0L &&
                executionReceiptCount == 0L &&
                outboxEffectCount == 0L &&
                profileRevision == 0L &&
                financeRevision == 0L
}

fun interface RealityMaintenancePort {
    fun inspect(): RealityMaintenanceInspection
}

data class RealityMigrationAccount(
    val accountId: String,
    val displayName: String,
    val currencyCode: String,
    val nature: String,
    val openingBalanceMinor: Long,
) {
    init {
        require(FinanceAccountId.of(accountId).value == accountId) {
            "migration account id must already be canonical"
        }
        require(FinanceAccountDisplayName.of(displayName).value == displayName) {
            "migration account display name must already be canonical"
        }
        require(FinanceCurrencyCode.of(currencyCode).value == currencyCode) {
            "migration currency code must already be canonical"
        }
        FinanceAccountNature.valueOf(nature)
        require(openingBalanceMinor >= 0L) {
            "migration opening balance must be non-negative"
        }
    }
}

data class RealityMigrationEntry(
    val entryId: String,
    val accountId: String,
    val amountMinor: Long,
    val kind: String,
    val concept: String,
    val category: String,
    val effectiveAtEpochMillis: Long,
    val recordedAtEpochMillis: Long,
    val provenanceLabel: String?,
) {
    init {
        val typed =
            FinanceEntryRecord(
                entryId = FinanceEntryId.of(entryId),
                accountId = FinanceAccountId.of(accountId),
                amountMinor = amountMinor,
                kind = FinanceEntryKind.valueOf(kind),
                concept = FinanceEntryConcept.of(concept),
                category = FinanceEntryCategory.of(category),
                effectiveAtEpochMillis = effectiveAtEpochMillis,
                recordedAtEpochMillis = recordedAtEpochMillis,
                provenanceLabel = provenanceLabel,
            )
        require(typed.entryId.value == entryId) {
            "migration entry id must already be canonical"
        }
        require(typed.accountId.value == accountId) {
            "migration entry account id must already be canonical"
        }
        require(typed.concept.value == concept) {
            "migration entry concept must already be canonical"
        }
        require(typed.category.value == category) {
            "migration entry category must already be canonical"
        }
    }
}

data class RealitySemanticMigrationData(
    val declaredDisplayName: String?,
    val accounts: List<RealityMigrationAccount>,
    val entries: List<RealityMigrationEntry>,
) {
    init {
        declaredDisplayName?.let { raw ->
            require(DeclaredDisplayName.of(raw).value == raw) {
                "migration profile display name must already be canonical"
            }
        }
        require(accounts.map { it.accountId }.distinct().size == accounts.size) {
            "migration cannot contain duplicate finance account ids"
        }
        require(entries.map { it.entryId }.distinct().size == entries.size) {
            "migration cannot contain duplicate finance entry ids"
        }
        val accountIds = accounts.map { it.accountId }.toSet()
        require(entries.all { it.accountId in accountIds }) {
            "migration finance entries must reference imported accounts"
        }
    }

    val isEmpty: Boolean
        get() = declaredDisplayName == null && accounts.isEmpty() && entries.isEmpty()
}

interface RealitySemanticMigrationPort {
    fun snapshot(): RealitySemanticMigrationData

    fun importFresh(data: RealitySemanticMigrationData): RealitySemanticMigrationData
}
