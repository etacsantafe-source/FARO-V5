package ar.com.faro.v5.app

import android.graphics.Typeface
import android.view.View
import android.widget.*
import ar.com.faro.v5.kernel.contracts.ConversationApplyHostOutcome
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.owner.reminders.api.*
import java.time.*
import java.util.UUID

/** W03 offline DEV: native Asuntos/Recordatorios, explicit Review then Apply. */
internal class FaroV5W03MattersPanel(
    private val activity: FaroV5W03MattersDevActivity,
    private val actions: S4W03OwnerSessionActions,
    private val openChat: () -> Unit,
) : ScrollView(activity) {
    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (16 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
    }
    private var selectedMatter: MatterId? = null
    private var selectedReminder: ReminderRuleId? = null
    private var remindersMode = false
    private var grant = false

    init { isFillViewport = true; addView(content) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); start() }

    fun clearOnPause() {
        grant = false
        selectedMatter = null
        selectedReminder = null
        begin("Lectura revocada")
        text("Volvé a autorizar la consulta local.")
        button("Abrir Asuntos") { openMatters() }
        button("Abrir Recordatorios") { openReminders() }
    }

    private fun start() {
        begin("Asuntos y Recordatorios")
        text("Cada vista solicita un permiso de lectura local.")
        button("Abrir Asuntos") { openMatters() }
        button("Abrir Recordatorios") { openReminders() }
        button("Volver al Chat") { openChat() }
    }
    private fun openMatters() {
        remindersMode = false
        selectedMatter = null
        grant = actions.grantMatterReadAfterExplicitRequest()
        if (grant) loadMatters() else unavailable()
    }
    private fun openReminders() {
        remindersMode = true
        selectedReminder = null
        grant = actions.grantReminderReadAfterExplicitRequest()
        if (grant) loadReminders() else unavailable()
    }
    private fun unavailable() {
        begin("No disponible")
        text("No se validaron la sesión, el permiso o la revisión actual.")
        button("Volver al Chat") { openChat() }
    }
    private fun loadMatters(message: String? = null) {
        if (!grant) return unavailable()
        begin("Asuntos")
        text("Consultando Reality local…")
        work({ actions.matterSnapshot() }) { renderMatters(it, message) }
    }
    private fun renderMatters(s: MatterSnapshot, message: String?) {
        val selected = s.matters.firstOrNull { it.id == selectedMatter }
        begin(selected?.title?.value ?: "Asuntos")
        message?.let(::text)
        button("Recordatorios") { openReminders() }
        if (selected == null) {
            button("Nuevo asunto") { editMatter(s, null) }
            if (s.matters.isEmpty()) text("No hay asuntos.")
            s.matters.sortedWith(compareByDescending<MatterRecord> { it.details.priority }
                .thenBy { it.title.value }).forEach { item ->
                button(item.title.value + " · " + status(item.status)) {
                    selectedMatter = item.id
                    loadMatters()
                }
            }
            return
        }
        button("Volver a Asuntos") { selectedMatter = null; loadMatters() }
        text("Estado: " + status(selected.status) + " · Prioridad " + selected.details.priority.name)
        if (selected.detail.value.isNotBlank()) text(selected.detail.value)
        selected.dueAtEpochMillis?.let { text("Vence: " + date(it)) }
        selected.details.reviewDate?.let { text("Revisión: " + it) }
        button("Editar asunto") { editMatter(s, selected) }
        button("Cambiar estado") { changeStatus(s, selected) }
        button("Cambiar vencimiento") { changeDue(s, selected) }
        val history = selected.details.history
        if (history.size >= 2 && history.last().kind == MatterHistoryKind.STATUS_CHANGED) {
            val previous = history[history.size - 2].resultingStatus
            if (previous in nextMatterStates(selected.status)) button("Revertir último cambio de estado") {
                submitMatter(s, selected, MatterReviewChange.Transition(
                    selected.status, previous, "Revertir evento #" + history.last().sequence,
                ), history.last().sequence)
            }
        }
        heading("Historial")
        if (history.isEmpty()) text("Sin historial.")
        history.forEach {
            text("#" + it.sequence + " · " + it.kind.name + " · " + status(it.resultingStatus) +
                (if (it.reason.isEmpty()) "" else " · " + it.reason))
        }
    }
    private fun editMatter(s: MatterSnapshot, old: MatterRecord?) {
        begin(if (old == null) "Nuevo asunto" else "Editar asunto")
        val title = field("Título", old?.title?.value.orEmpty())
        val detail = field("Detalle", old?.detail?.value.orEmpty())
        val priority = choice("Prioridad", MatterPriority.entries.map { it.name },
            MatterPriority.entries.indexOf(old?.details?.priority ?: MatterPriority.NORMAL))
        val due = field("Vencimiento (AAAA-MM-DD, opcional)", old?.dueAtEpochMillis?.let(::date).orEmpty())
        val reviewDate = field("Revisión (AAAA-MM-DD, opcional)", old?.details?.reviewDate?.toString().orEmpty())
        val reason = if (old == null) null else field("Motivo de edición")
        button("Revisar propuesta") { valid {
            val form = MatterReviewForm(
                MatterTitle.of(title.value()), MatterDetail.of(detail.value()),
                MatterPriority.entries[priority.selectedItemPosition],
                reviewDate.value().takeIf(String::isNotEmpty)?.let(LocalDate::parse),
                due.value().takeIf(String::isNotEmpty)?.let(::epoch),
            )
            val intent = MatterReviewIntent(
                old?.id ?: MatterId.of("matter-" + UUID.randomUUID()), s.revision,
                now(old?.updatedAtEpochMillis),
                if (old == null) MatterReviewChange.Create(form)
                else MatterReviewChange.Edit(form, checkNotNull(reason).value()),
            )
            work({ actions.submitMatter(intent, UUID.randomUUID().toString()) }, ::review)
        } }
        button("Cancelar") { loadMatters() }
    }
    private fun changeStatus(s: MatterSnapshot, old: MatterRecord) {
        begin("Cambiar estado")
        val targets = nextMatterStates(old.status).toList()
        val next = choice("Nuevo estado", targets.map(::status))
        val reason = field("Motivo")
        button("Revisar estado") { valid {
            submitMatter(s, old, MatterReviewChange.Transition(
                old.status, targets[next.selectedItemPosition], reason.value()))
        } }
        button("Cancelar") { loadMatters() }
    }
    private fun changeDue(s: MatterSnapshot, old: MatterRecord) {
        begin("Cambiar vencimiento")
        val due = field("Fecha AAAA-MM-DD (vacío para quitar)", old.dueAtEpochMillis?.let(::date).orEmpty())
        val reason = field("Motivo")
        button("Revisar fecha") { valid {
            submitMatter(s, old, MatterReviewChange.Snooze(
                due.value().takeIf(String::isNotEmpty)?.let(::epoch), reason.value()))
        } }
        button("Cancelar") { loadMatters() }
    }
    private fun submitMatter(s: MatterSnapshot, old: MatterRecord, change: MatterReviewChange,
                             undo: Int? = null) {
        val intent = MatterReviewIntent(old.id, s.revision, now(old.updatedAtEpochMillis), change, undo)
        work({ actions.submitMatter(intent, UUID.randomUUID().toString()) }, ::review)
    }
    private fun loadReminders(message: String? = null) {
        if (!grant) return unavailable()
        begin("Recordatorios")
        text("Consultando reglas locales…")
        work({ actions.reminderSnapshot() }) { renderReminders(it, message) }
    }
    private fun renderReminders(s: ReminderSnapshot, message: String?) {
        val selected = s.rules.firstOrNull { it.id == selectedReminder }
        begin(selected?.title ?: "Recordatorios")
        message?.let(::text)
        button("Asuntos") { openMatters() }
        button("Conciliar avisos locales") {
            begin("Conciliación local de Avisos")
            text("Solo se consultan fechas de Recordatorios; no se envían notificaciones al sistema.")
            button("Confirmar conciliación local") {
                val at = System.currentTimeMillis()
                work({
                    actions.reconcileLocalNoticesAfterExplicitRequest(
                        (at - 86_400_000L).coerceAtLeast(0L),
                        at + 172_800_000L,
                    )
                }) { outcome ->
                    begin("Resultado de Avisos locales")
                    when (outcome) {
                        is S4W03NoticeReconcileResult.Saved ->
                            text("Avisos creados: " + outcome.created +
                                " · resueltos: " + outcome.resolved +
                                (if (outcome.complete) " · cobertura completa" else " · cobertura parcial"))
                        is S4W03NoticeReconcileResult.Partial ->
                            text("Conciliación parcial: " + outcome.reason)
                        S4W03NoticeReconcileResult.Denied ->
                            text("Conciliación denegada: sesión o permiso no vigente.")
                    }
                    button("Volver a Recordatorios") { loadReminders() }
                }
            }
            button("Cancelar") { loadReminders() }
        }
        if (selected == null) {
            button("Nuevo recordatorio") { editReminder(s, null) }
            if (s.rules.isEmpty()) text("No hay recordatorios.")
            s.rules.forEach { item ->
                button(item.title + if (item.enabled) "" else " · Desactivado") {
                    selectedReminder = item.id
                    loadReminders()
                }
            }
            return
        }
        button("Volver a Recordatorios") { selectedReminder = null; loadReminders() }
        text("Fecha: " + selected.anchorLocalDate + " " + selected.anchorLocalTime)
        text("Zona: " + selected.zoneId + " · " + selected.recurrence.name)
        selected.linkedMatterId?.let { text("Asunto vinculado: " + it) }
        button("Editar recordatorio") { editReminder(s, selected) }
        if (selected.enabled) button("Desactivar regla") {
            val intent = S4W03ReminderIntent(s.revision, S4W03ReminderChange.Cancel(
                selected, now(selected.updatedAtEpochMillis)))
            work({ actions.submitReminder(intent, UUID.randomUUID().toString()) }, ::review)
        }
        heading("Ocurrencias")
        s.occurrences.filter { it.ruleId == selected.id }.forEach { occ ->
            text(date(occ.dueAtEpochMillis) + " · " + occ.status.name)
            if (selected.enabled && occ.status == ReminderOccurrenceStatus.PENDING &&
                System.currentTimeMillis() >= occ.dueAtEpochMillis) {
                button("Marcar ocurrencia atendida") {
                    val at = now(maxOf(occ.updatedAtEpochMillis, occ.dueAtEpochMillis))
                    val updated = occ.copy(
                        status = ReminderOccurrenceStatus.ACKNOWLEDGED,
                        acknowledgedAtEpochMillis = at, updatedAtEpochMillis = at,
                    )
                    work({ actions.submitReminder(S4W03ReminderIntent(s.revision,
                        S4W03ReminderChange.Attend(updated)), UUID.randomUUID().toString()) }, ::review)
                }
            }
        }
    }
    private fun editReminder(s: ReminderSnapshot, old: ReminderRule?) {
        begin(if (old == null) "Nuevo recordatorio" else "Editar recordatorio")
        val title = field("Título", old?.title.orEmpty())
        val date = field("Fecha AAAA-MM-DD", old?.anchorLocalDate?.toString() ?: LocalDate.now().toString())
        val time = field("Hora HH:MM", old?.anchorLocalTime?.toString() ?: "09:00")
        val zone = field("Zona horaria", old?.zoneId ?: "America/Argentina/Cordoba")
        val repetition = choice("Frecuencia", ReminderRecurrence.entries.map { it.name },
            ReminderRecurrence.entries.indexOf(old?.recurrence ?: ReminderRecurrence.ONCE))
        val linked = field("ID de asunto (opcional)", old?.linkedMatterId.orEmpty())
        // Matter labels are loaded only after a separately authorized, explicit read.
        // No owner snapshot or opaque Matter ID is fabricated from Reminder data.
        button("Elegir asunto permitido") {
            work({
                if (!actions.grantMatterReadAfterExplicitRequest()) null
                else actions.matterSnapshot()
            }) { available ->
                if (available.matters.isEmpty()) {
                    text("No hay asuntos autorizados para vincular.")
                } else {
                    heading("Asuntos disponibles para vincular")
                    available.matters.sortedBy { it.title.value }.forEach { item ->
                        button("Vincular: " + item.title.value) {
                            linked.setText(item.id.value)
                            text("Seleccionado: " + item.title.value)
                        }
                    }
                }
            }
        }
        button("Revisar recordatorio") { valid {
            val link = linked.value().takeIf(String::isNotEmpty)?.let(MatterId::of)
            val rule = ReminderRule(
                old?.id ?: ReminderRuleId.of("reminder-" + UUID.randomUUID()), title.value(),
                ZoneId.of(zone.value()).id, LocalDate.parse(date.value()),
                LocalTime.parse(time.value()), ReminderRecurrence.entries[repetition.selectedItemPosition],
                old?.enabled ?: true, link?.value, now(old?.updatedAtEpochMillis),
            )
            work({
                if (link != null && (!actions.grantMatterReadAfterExplicitRequest() ||
                    !actions.authorizeMatterReference(link))) null
                else actions.submitReminder(S4W03ReminderIntent(s.revision,
                    if (old == null) S4W03ReminderChange.Create(rule)
                    else S4W03ReminderChange.Update(old, rule)), UUID.randomUUID().toString())
            }, ::review)
        } }
        button("Cancelar") { loadReminders() }
    }
    private fun review(item: ConversationPlanReviewSnapshot) {
        begin("Revisión de cambios")
        text(item.reviewText)
        when (item.state) {
            ConversationPlanReviewState.PENDING -> button("Aprobar propuesta") {
                work({ actions.decideNow(item, true) }, ::review)
            }
            ConversationPlanReviewState.APPROVED -> button("Aplicar cambios") {
                work({ actions.applyNow(item) }) { result ->
                    val outcome = when (result) {
                        is ConversationApplyHostOutcome.Committed -> "Recibo " + result.receipt.receiptId.value
                        ConversationApplyHostOutcome.RecoveryRequired -> "Resultado incierto. Consultar recibo; no repetir Apply."
                        is ConversationApplyHostOutcome.Denied -> "Revisión no disponible."
                    }
                    if (remindersMode) loadReminders(outcome) else loadMatters(outcome)
                }
            }
            else -> text("Esta revisión no se puede aplicar nuevamente.")
        }
        button("Descartar revisión") {
            work({ actions.decideNow(item, false) }) { start() }
        }
        button("Volver al Chat") { openChat() }
    }
    private fun <T : Any> work(block: () -> T?, done: (T) -> Unit) {
        activity.work(block) result@{ outcome ->
            if (!isAttachedToWindow) return@result
            val value = outcome.getOrNull()
            if (value == null) unavailable() else done(value)
        }
    }
    private fun valid(action: () -> Unit) {
        if (runCatching(action).isFailure) text("Revisá campos, permisos, fechas y motivos obligatorios.")
    }
    private fun status(status: MatterStatus): String = when (status) {
        MatterStatus.OPEN -> "Abierto"
        MatterStatus.IN_PROGRESS -> "En curso"
        MatterStatus.WAITING -> "En espera"
        MatterStatus.RESOLVED -> "Resuelto"
        MatterStatus.ARCHIVED -> "Archivado"
    }
    private fun date(timestamp: Long) = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.of("America/Argentina/Cordoba")).toLocalDate().toString()
    private fun epoch(date: String) = LocalDate.parse(date)
        .atStartOfDay(ZoneId.of("America/Argentina/Cordoba")).toInstant().toEpochMilli()
    private fun now(previous: Long?): Long = maxOf(System.currentTimeMillis(),
        previous?.let { Math.incrementExact(it) } ?: 0L)
    private fun begin(title: String) { content.removeAllViews(); scrollTo(0, 0); heading(title) }
    private fun heading(value: String) {
        content.addView(TextView(activity).apply {
            text = value
            textSize = 22f
            isAccessibilityHeading = true
            setTypeface(typeface, Typeface.BOLD)
        })
    }
    private fun text(value: String) { content.addView(TextView(activity).apply { text = value; textSize = 16f }) }
    private fun button(value: String, action: () -> Unit) {
        content.addView(Button(activity).apply { text = value; isAllCaps = false; setOnClickListener { action() } })
    }
    private fun field(label: String, initial: String = ""): EditText {
        val value = EditText(activity).apply {
            hint = label
            setText(initial)
            id = View.generateViewId()
        }
        content.addView(TextView(activity).apply { text = label; labelFor = value.id })
        content.addView(value)
        return value
    }
    private fun EditText.value() = text.toString().trim()
    private fun choice(label: String, values: List<String>, position: Int = 0): Spinner {
        val value = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, values)
            setSelection(maxOf(position, 0))
            id = View.generateViewId()
        }
        content.addView(TextView(activity).apply { text = label; labelFor = value.id })
        content.addView(value)
        return value
    }
}
