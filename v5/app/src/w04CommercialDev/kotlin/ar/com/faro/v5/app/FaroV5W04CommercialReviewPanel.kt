package ar.com.faro.v5.app

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Coverage
import ar.com.faro.v5.app.S4W04CommercialReviewPresentation.Decision
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.BenefitKind
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Text-only Android presentation of an ALREADY-verified D4 decision.
 * No owner, network, checkout, priced quote, Apply or real snapshot is opened.
 */
internal class FaroV5W04CommercialReviewPanel(context: Context) : ScrollView(context) {
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    init {
        isFillViewport = true
        addView(root)
        clearForRevocation()
    }

    /** Caller in W04-F must independently establish foreground and BOTH owner grants. */
    internal fun renderAuthorized(decision: Decision?, authorizedNow: () -> Boolean) {
        clearForRevocation()
        if (decision !is Decision.Display || !runCatching(authorizedNow).getOrDefault(false)) return
        renderDisplay(decision)
        // Fail closed if source authorization changed during native view creation.
        if (!runCatching(authorizedNow).getOrDefault(false)) clearForRevocation()
    }

    fun clearForRevocation() {
        root.removeAllViews()
        heading("SmartCompra · revisión comercial")
        text("Sin revisión comercial autorizada.")
        text("Los datos se mostrarán solamente después de una lectura local validada.")
        text("No hay precios actuales, compras, pagos ni movimientos de stock.")
    }

    private fun renderDisplay(display: Decision.Display) {
        root.removeAllViews()
        heading("Revisión comercial histórica · solo lectura")
        text("No es una cotización vigente ni una autorización de compra.")
        text("Corte histórico: " + formatEpoch(display.asOfEpochMillis))
        text("Lectura revisada: " + formatEpoch(display.checkedAtEpochMillis))
        text("Cobertura: " + if (display.coverage == Coverage.PARTIAL) "PARCIAL" else "Observaciones seleccionadas")
        text("Necesidades sin precio comparable: " + display.missingNeedIds.size)
        text("Necesidades sin promoción modelada: " + display.unmodeledNeedIds.size)
        text("Importes por comercio y moneda. Nunca se suman ARS y USD.")
        for (group in display.groups) {
            val key = group.key
            heading(key.merchant + " · " + key.currencyCode)
            text("Localidad registrada: " + key.localityCode)
            text("Medio de pago declarado: " + key.paymentMethod.name)
            text("Importe HISTÓRICO observado: " +
                money(key.currencyCode, group.observedHistoricalSubtotalMinor))
            group.scenario?.let { scenario ->
                heading("Escenario WHAT_IF hipotético")
                text("Base modelada: " + money(key.currencyCode, scenario.modeledBaseMinor))
                text("Descuento hipotético: " +
                    money(key.currencyCode, scenario.hypotheticalDiscountMinor))
                text("Después del supuesto descuento: " +
                    money(key.currencyCode, scenario.hypotheticalAfterMinor))
                text("Cobertura del escenario: " + scenario.scope.name +
                    ". No es un precio pagado ni un descuento confirmado.")
            } ?: text("Sin escenario promocional modelado para este grupo.")
            if (group.unverifiedBenefits.isNotEmpty()) {
                heading("Condiciones y beneficios NO verificados")
                group.unverifiedBenefits.forEach { note ->
                    val name = when (note.kind) {
                        BenefitKind.CASHBACK_FUTURE -> "Reintegro futuro"
                        BenefitKind.INSTALLMENT_CONDITION -> "Condición de cuotas"
                        BenefitKind.BANK_ELIGIBILITY -> "Elegibilidad bancaria"
                        BenefitKind.OTHER_UNVERIFIED -> "Otra condición"
                    }
                    text(name + " · declarada, sin verificar. No es dinero recibido.")
                    note.details?.let { text("Detalle literal: " + it) }
                    note.sourceRef?.let { text("Referencia literal: " + it) }
                }
            }
            group.lines.forEachIndexed { index, line ->
                heading("Artículo del carrito " + (index + 1))
                text("Cantidad: " + line.requestedPackCount + " paquetes. Nombre no verificado en esta vista.")
                text("Importe histórico de la línea: " + money(key.currencyCode, line.estimatedMinor))
                text("Observado: " + formatEpoch(line.observedAtEpochMillis))
                text("Fuente histórica literal: " + line.sourceRef)
            }
        }
        heading("Advertencias importantes")
        text("Historial y promociones hipotéticas NO son precios actuales.")
        text("Reintegros, bancos y cuotas declarados NO son beneficios confirmados.")
        text("Este resumen no modifica Finanzas ni el inventario.")
        text("Antes de cualquier acción se requiere nueva validación y revisión humana.")
    }

    private fun heading(value: String) {
        root.addView(TextView(context).apply {
            text = value
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            isAccessibilityHeading = true
            setPadding(0, dp(14), 0, dp(5))
            autoLinkMask = 0
            linksClickable = false
        })
    }

    private fun text(value: String) {
        root.addView(TextView(context).apply {
            text = value
            textSize = 16f
            setPadding(0, dp(4), 0, dp(4))
            autoLinkMask = 0
            linksClickable = false
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun money(currency: String, minor: Long): String =
        if (currency == "ARS" || currency == "USD") {
            currency + " " + BigDecimal.valueOf(minor, 2).toPlainString()
        } else {
            currency + " " + minor + " unidades menores (escala no certificada)"
        }

    private fun formatEpoch(epoch: Long): String = runCatching {
        Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrDefault("Fecha no representable")
}
