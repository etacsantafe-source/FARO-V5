package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.AuthorityBoundaryId
import ar.com.faro.v5.kernel.contracts.DependencyKey
import ar.com.faro.v5.kernel.contracts.DependencyStamp
import ar.com.faro.v5.kernel.contracts.DependencyStampValue
import java.util.Locale

@JvmInline
value class FinanceAccountId private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 128

        fun of(raw: String): FinanceAccountId {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) { "finance account id must not be blank" }
            require(normalized.length <= MAX_LENGTH) {
                "finance account id must be at most $MAX_LENGTH characters"
            }
            return FinanceAccountId(normalized)
        }
    }
}

@JvmInline
value class FinanceAccountDisplayName private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH = 80

        fun of(raw: String): FinanceAccountDisplayName {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) { "finance account name must not be blank" }
            require(normalized.length <= MAX_LENGTH) {
                "finance account name must be at most $MAX_LENGTH characters"
            }
            return FinanceAccountDisplayName(normalized)
        }
    }
}

@JvmInline
value class FinanceCurrencyCode private constructor(val value: String) {
    companion object {
        fun of(raw: String): FinanceCurrencyCode {
            val normalized = raw.trim().uppercase(Locale.ROOT)
            require(normalized.matches(Regex("[A-Z]{3}"))) {
                "finance currency code must contain exactly three ASCII letters"
            }
            return FinanceCurrencyCode(normalized)
        }
    }
}

enum class FinanceAccountNature {
    CASH,
    BANK,
    WALLET,
    INVESTMENT,
    OTHER,
}

data class FinanceAccountSnapshot(
    val accountId: FinanceAccountId,
    val displayName: FinanceAccountDisplayName,
    val currency: FinanceCurrencyCode,
    val nature: FinanceAccountNature,
    val openingBalanceMinor: Long,
) {
    init {
        require(openingBalanceMinor >= 0L) {
            "finance opening balance must not be negative"
        }
    }
}

data class FinanceSnapshot(
    val accounts: List<FinanceAccountSnapshot>,
    val revision: Long,
    val entries: List<FinanceEntrySnapshot> = emptyList(),
) {
    init {
        require(revision >= 0L) { "finance revision must be non-negative" }
        require(accounts.map { it.accountId }.distinct().size == accounts.size) {
            "finance snapshot cannot contain duplicate account ids"
        }
        require(entries.map { it.entryId }.distinct().size == entries.size) {
            "finance snapshot cannot contain duplicate entry ids"
        }
        require(entries.all { entry -> accounts.any { it.accountId == entry.accountId } }) {
            "finance snapshot entries must reference existing accounts"
        }
        require(
            entries.all { entry ->
                accounts.single { it.accountId == entry.accountId }.currency == entry.currency
            }
        ) {
            "finance snapshot entry currency must match its account"
        }
    }

    fun accountsDependencyStamp(): DependencyStamp =
        DependencyStamp(
            authorityBoundary = FINANCE_AUTHORITY,
            dependencyKey = FINANCE_ACCOUNTS_KEY,
            observedValue = DependencyStampValue("revision:$revision"),
        )

    fun accountDependencyStamp(accountId: FinanceAccountId): DependencyStamp {
        val account = accounts.singleOrNull { it.accountId == accountId }
            ?: error("finance account dependency requires an existing account")
        return DependencyStamp(
            authorityBoundary = FINANCE_AUTHORITY,
            dependencyKey = accountDependencyKey(accountId),
            observedValue = DependencyStampValue(
                "present:currency=" + account.currency.value
            ),
        )
    }

    fun ledgerMinor(accountId: FinanceAccountId): Long {
        val account = accounts.singleOrNull { it.accountId == accountId }
            ?: error("finance ledger requires an existing account")
        return entries
            .filter { it.accountId == accountId }
            .fold(account.openingBalanceMinor) { balance, entry ->
                when (entry.kind) {
                    FinanceEntryKind.INCOME ->
                        Math.addExact(balance, entry.amountMinor)
                    FinanceEntryKind.EXPENSE ->
                        Math.subtractExact(balance, entry.amountMinor)
                }
            }
    }

    companion object {
        val FINANCE_AUTHORITY = AuthorityBoundaryId("reality.finance")
        val FINANCE_ACCOUNTS_KEY = DependencyKey("finance:accounts")

        fun accountDependencyKey(accountId: FinanceAccountId): DependencyKey =
            DependencyKey("finance:account:" + accountId.value)
    }
}

fun interface FinanceSnapshotReadApi {
    fun read(): FinanceSnapshot
}
