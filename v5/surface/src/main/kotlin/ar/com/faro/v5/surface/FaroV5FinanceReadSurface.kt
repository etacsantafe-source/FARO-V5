package ar.com.faro.v5.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/**
 * W01-B3A DEV-only read presentation. NEVER an Apply grant, ledger authority,
 * proof of confirmed opening, prediction, or source of truth. The future app
 * adapter must verify Finance ACL and opening provenance before supplying data.
 */
enum class FaroV5FinanceCoverage {
    READY, EMPTY_CONFIRMED, PARTIAL, DENIED, UNAVAILABLE, STALE,
}

enum class FaroV5FinanceAccountCertainty {
    VERIFIED, OPENING_UNVERIFIED, PARTIAL, DENIED,
}

enum class FaroV5FinanceProjectionAvailability {
    NO_DISPONIBLE_HASTA_W08,
}

data class FaroV5FinanceAccountLine(
    val opaqueAccountId: String,
    val label: String,
    val currencyCode: String,
    val certainty: FaroV5FinanceAccountCertainty,
    val confirmedBalanceMinor: Long? = null,
    val sourceRevision: Long? = null,
) {
    init {
        require(opaqueAccountId.isNotBlank() && opaqueAccountId == opaqueAccountId.trim() &&
            opaqueAccountId.length <= 128)
        require(label.isNotBlank() && label == label.trim() && label.length <= 80)
        require(currencyCode.matches(Regex("[A-Z]{3}")))
        if (certainty == FaroV5FinanceAccountCertainty.VERIFIED) {
            // Currency minor-unit exponent is known here ONLY for ARS / USD.
            require(currencyCode == "ARS" || currencyCode == "USD")
            require(confirmedBalanceMinor != null && sourceRevision != null &&
                sourceRevision >= 0L)
        } else {
            require(confirmedBalanceMinor == null && sourceRevision == null) {
                "Unverified Finance accounts must never carry a displayable amount"
            }
        }
    }
}

data class FaroV5FinanceUiSnapshot(
    val coverage: FaroV5FinanceCoverage,
    val generationId: String? = null,
    val pointerSequence: Long? = null,
    val sourceRevision: Long? = null,
    val accounts: List<FaroV5FinanceAccountLine> = emptyList(),
    val projection: FaroV5FinanceProjectionAvailability =
        FaroV5FinanceProjectionAvailability.NO_DISPONIBLE_HASTA_W08,
) {
    init {
        require(projection == FaroV5FinanceProjectionAvailability.NO_DISPONIBLE_HASTA_W08)
        require(accounts.size <= 100 && accounts.map { it.opaqueAccountId }.distinct().size ==
            accounts.size) { "Finance display cannot duplicate an account" }
        when (coverage) {
            FaroV5FinanceCoverage.DENIED,
            FaroV5FinanceCoverage.UNAVAILABLE,
            FaroV5FinanceCoverage.STALE -> require(
                generationId == null && pointerSequence == null &&
                    sourceRevision == null && accounts.isEmpty()
            ) { "Unadmitted Finance read cannot retain balances or identifiers" }
            FaroV5FinanceCoverage.EMPTY_CONFIRMED,
            FaroV5FinanceCoverage.READY,
            FaroV5FinanceCoverage.PARTIAL -> {
                require(!generationId.isNullOrBlank() &&
                    pointerSequence != null && pointerSequence > 0L &&
                    sourceRevision != null && sourceRevision >= 0L)
                require(accounts.all {
                    it.certainty != FaroV5FinanceAccountCertainty.VERIFIED ||
                        it.sourceRevision == sourceRevision
                }) { "Finance account revision must match admitted source revision" }
                if (coverage == FaroV5FinanceCoverage.EMPTY_CONFIRMED) {
                    require(accounts.isEmpty()) { "Confirmed empty requires no accounts" }
                }
                if (coverage == FaroV5FinanceCoverage.READY) {
                    require(accounts.isNotEmpty() && accounts.all {
                        it.certainty == FaroV5FinanceAccountCertainty.VERIFIED
                    }) { "READY must have fully verified accounts" }
                }
            }
        }
    }
}

/**
 * Only an app-owned future adapter may supply this port after Finance ACL,
 * generation pointer and opening provenance validation. null means unknown.
 * Invalidation includes owner revocation, source revision or pointer ABA.
 */
interface FaroV5FinanceReadPort {
    val invalidations: Flow<Unit>
    suspend fun readFinance(): FaroV5FinanceUiSnapshot?
}

object FaroV5FinanceSurfacePolicy {
    @JvmStatic
    fun overview(snapshot: FaroV5FinanceUiSnapshot?): String = when (snapshot?.coverage) {
        FaroV5FinanceCoverage.READY ->
            "Cuentas verificadas desde Finanzas. No se mezclan monedas."
        FaroV5FinanceCoverage.EMPTY_CONFIRMED ->
            "No hay cuentas. La fuente autorizada confirmó esta lectura."
        FaroV5FinanceCoverage.PARTIAL ->
            "Cobertura parcial: algunos datos de Finanzas no están verificados."
        FaroV5FinanceCoverage.DENIED ->
            "Lectura de Finanzas no autorizada. Ningún saldo disponible."
        FaroV5FinanceCoverage.STALE ->
            "Lectura vencida. Volvé a consultar Finanzas."
        FaroV5FinanceCoverage.UNAVAILABLE, null ->
            "Finanzas no disponible. No se muestran saldos supuestos."
    }

    /** An unknown or unconfirmed amount must NEVER be formatted as 0. */
    @JvmStatic
    fun balanceText(row: FaroV5FinanceAccountLine): String =
        if (row.certainty != FaroV5FinanceAccountCertainty.VERIFIED) {
            when (row.certainty) {
                FaroV5FinanceAccountCertainty.OPENING_UNVERIFIED ->
                    "Apertura sin verificar. Saldo no disponible."
                FaroV5FinanceAccountCertainty.PARTIAL ->
                    "Saldo parcial o no disponible."
                FaroV5FinanceAccountCertainty.DENIED ->
                    "Lectura de esta cuenta no autorizada."
                FaroV5FinanceAccountCertainty.VERIFIED ->
                    error("Handled above")
            }
        } else {
            val minor = requireNotNull(row.confirmedBalanceMinor)
            // Scale 2 is explicitly authorized only for ARS/USD by the constructor.
            row.currencyCode + " " + BigDecimal.valueOf(minor, 2)
                .setScale(2).toPlainString()
        }

    @JvmStatic
    fun confirmedNegative(row: FaroV5FinanceAccountLine): Boolean =
        row.certainty == FaroV5FinanceAccountCertainty.VERIFIED &&
            requireNotNull(row.confirmedBalanceMinor) < 0L

    @JvmStatic
    fun currencies(snapshot: FaroV5FinanceUiSnapshot): List<String> =
        snapshot.accounts.map { it.currencyCode }.distinct().sorted()

    @JvmStatic
    fun projectionMessage(): String =
        "Proyección todavía no disponible. Se incorporará en W08, sin valores simulados."
}

/**
 * DORMANT: nobody mounts this screen in the shipping Shell/CompositionRoot.
 * No buttons, no grant, no Apply, no SQL or currency conversion.
 * Owner invalidation clears stale sensitive values before re-reading.
 */
@Composable
fun FaroV5FinanceReadScreen(
    readPort: FaroV5FinanceReadPort?,
    modifier: Modifier = Modifier,
) {
    val snapshot by produceState<FaroV5FinanceUiSnapshot?>(
        initialValue = null, key1 = readPort,
    ) {
        if (readPort == null) {
            value = null
        } else {
            readPort.invalidations.onStart { emit(Unit) }.collectLatest {
                value = null
                value = try {
                    withContext(Dispatchers.IO) { readPort.readFinance() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(FaroV5Spacing.large),
        verticalArrangement = Arrangement.spacedBy(FaroV5Spacing.medium),
    ) {
        Text(
            "Finanzas",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(FaroV5FinanceSurfacePolicy.overview(snapshot))
        val authorized = snapshot?.takeIf {
            it.coverage == FaroV5FinanceCoverage.READY ||
                it.coverage == FaroV5FinanceCoverage.PARTIAL
        }
        authorized?.let { page ->
            FaroV5FinanceSurfacePolicy.currencies(page).forEach { currency ->
                Text(
                    "Cuentas en " + currency,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                )
                page.accounts.filter { it.currencyCode == currency }.forEach { account ->
                    val label = account.label + ": " +
                        FaroV5FinanceSurfacePolicy.balanceText(account)
                    Text(
                        label,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (FaroV5FinanceSurfacePolicy.confirmedNegative(account))
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Text(FaroV5FinanceSurfacePolicy.projectionMessage())
    }
}
