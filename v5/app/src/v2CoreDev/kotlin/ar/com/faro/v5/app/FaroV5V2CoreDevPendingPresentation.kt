package ar.com.faro.v5.app

/**
 * DEV-only, data-minimal display of existing locally stored due facts.
 * Absolutely no opaque ID, title, provenance payload, amount or Home global total.
 */
internal object FaroV5V2CoreDevPendingPresentation {
    const val unavailable =
        "No disponible: se requiere autorización vigente y una generación Reality8 activa. " +
        "No se migraron datos ni se supuso cero Pendientes."

    fun render(page: FaroV5N031ThreeOwnerDuePage?): String {
        if (page == null) return unavailable
        val intro = "Vencimientos registrados (Reality8: Asuntos, Recordatorios y Plan mensual). " +
            "Ventana de hasta 7 días; no representa todos los Pendientes.\n"
        val scope = if (page.completeWithinStoredDues) {
            "Cobertura completa solo dentro de la ventana almacenada; sin expandir recurrencias.\n"
        } else {
            "Cobertura parcial: puede haber vencimientos adicionales; no equivale a cero.\n"
        }
        if (page.items.isEmpty()) {
            return intro + scope + if (page.completeWithinStoredDues) {
                "Sin vencimientos registrados en estas tres fuentes y ventana."
            } else {
                "No hay elementos visibles en esta página parcial; total desconocido."
            }
        }
        val lines = page.items.map { fact ->
            val label = when (fact.source) {
                FaroV5N031DueSource.MATTER_DUE -> "Asunto"
                FaroV5N031DueSource.REMINDER_OCCURRENCE -> "Recordatorio"
                FaroV5N031DueSource.MONTHLY_PLAN_ITEM -> "Plan mensual"
            }
            val day = fact.dueOn.dayOfMonth.toString().padStart(2, '0')
            val month = fact.dueOn.monthValue.toString().padStart(2, '0')
            "$label — $day/$month/${fact.dueOn.year}"
        }
        return intro + scope + lines.joinToString(separator = "\n")
    }
}
