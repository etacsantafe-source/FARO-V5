package ar.com.faro.v5.app

import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ar.com.faro.v5.kernel.contracts.ConversationApplyHostOutcome
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState
import ar.com.faro.v5.owner.objectives.api.*
import java.time.LocalDate
import java.util.UUID

internal class FaroV5W02ObjectivesPanel(
    private val activity: FaroV5W02ObjectivesDevActivity,
    private val actions: S4W02ObjectiveSessionActions,
    private val openChat: () -> Unit,
) : ScrollView(activity) {
    private val column = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
    }
    private var selectedId: ObjectiveId? = null

    init {
        isFillViewport = true
        addView(column)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reloadIfAttached()
    }

    fun clearOnPause() {
        column.removeAllViews()
    }

    fun reloadIfAttached() {
        if (isAttachedToWindow) load()
    }

    private fun load(message: String? = null) {
        begin("Objetivos")
        text("Cargando objetivos…")
        work({
            actions.snapshot()?.let { snapshot ->
                val selected = snapshot.objectives.firstOrNull { it.id == selectedId }
                val labels = selected?.details?.plan?.links.orEmpty().associateWith { link ->
                    if (link.owner == ObjectiveLinkOwner.OBJECTIVE) {
                        snapshot.objectives.firstOrNull { it.id.value == link.id }?.title?.value
                    } else (actions.observeReference(link) as? ObjectiveLinkView.Visible)?.label
                }
                snapshot to labels
            }
        }) { (snapshot, labels) -> render(snapshot, labels, message) }
    }

    private fun render(snapshot: ObjectiveSnapshot, labels: Map<ObjectiveLink, String?>, message: String?) {
        val selected = snapshot.objectives.firstOrNull { it.id == selectedId }
        begin(selected?.title?.value ?: "Objetivos")
        message?.let { text(it) }
        if (selected == null) {
            button("Nuevo objetivo") { edit(snapshot, null) }
            if (snapshot.objectives.isEmpty()) text("Todavía no hay objetivos")
            snapshot.objectives.sortedWith(
                compareByDescending<ObjectiveRecord> { it.details.priority }.thenBy { it.title.value },
            ).forEach { record ->
                button(record.title.value) {
                    selectedId = record.id
                    load()
                }
            }
            return
        }

        button("Volver a objetivos") {
            selectedId = null
            load()
        }
        text(statusLabel(selected.status) + " · Prioridad " + priorityLabel(selected.details.priority))
        if (selected.detail.value.isNotBlank()) text(selected.detail.value)
        selected.details.dueDate?.let { text("Fecha objetivo $it") }
        selected.details.target?.let { target -> text("Meta ${quantity(target.value, target.unit)}") }
        selected.confirmedProgress()?.let { progress ->
            val unit = checkNotNull(selected.details.target).unit
            text("Avance confirmado ${quantity(progress.observed, unit)}")
        }
        button("Editar objetivo") { edit(snapshot, selected) }
        if (selected.status != ObjectiveStatus.ARCHIVED) {
            button("Registrar avance") { report(snapshot, selected) }
        }
        button("Cambiar estado") { lifecycle(snapshot, selected) }
        heading("Historial")
        if (selected.details.history.isEmpty() && selected.details.measurements.isEmpty()) text("Sin avances registrados")
        selected.details.measurements.forEach { text("Lectura registrada ${quantity(it.value, it.unit)}") }
        selected.details.history.forEach { item ->
            when (val change = item.change) {
                is ObjectiveHistoryChange.Progress -> {
                    text("Avance #${item.sequence} · " + reportLabel(change.report))
                    button("Corregir avance #${item.sequence}") { report(snapshot, selected, item.sequence, change.report) }
                }
                is ObjectiveHistoryChange.Correction -> {
                    text("Corrección de #${change.originalSequence} · " + reportLabel(change.replacement))
                    text(change.explanation)
                }
                is ObjectiveHistoryChange.Lifecycle -> text(
                    "${statusLabel(change.before)} → ${statusLabel(change.after)} · ${change.explanation}",
                )
            }
        }
        heading("Próximos pasos")
        selected.details.plan.steps.forEach { step ->
            text((if (step.done) "Completado · " else "Pendiente · ") + step.title +
                step.dueDate?.let { " · $it" }.orEmpty())
            button("Editar paso ${step.title}") { stepForm(snapshot, selected, step) }
            if (!step.done) button("Completar ${step.title}") {
                preparePlan(snapshot, selected, ObjectivePlanChange.PutStep(step.copy(done = true)))
            }
        }
        button("Agregar próximo paso") { stepForm(snapshot, selected) }
        heading("Referencias")
        for (link in selected.details.plan.links) {
            val source = sourceLabel(link.owner)
            val label = labels[link]
            text(if (label == null) "$source · Referencia no disponible" else "$source · $label")
            if (label != null) button("Quitar vínculo $label") {
                preparePlan(snapshot, selected, ObjectivePlanChange.SetLink(link, false))
            }
        }
        button("Vincular otro objetivo") { dependency(snapshot, selected) }
        button("Vincular un asunto") { externalReference(snapshot, selected, ObjectiveLinkOwner.MATTER) }
        button("Vincular una cuenta de Finanzas") {
            externalReference(snapshot, selected, ObjectiveLinkOwner.FINANCE)
        }
    }

    private fun edit(snapshot: ObjectiveSnapshot, existing: ObjectiveRecord?) {
        begin(if (existing == null) "Nuevo objetivo" else "Editar objetivo")
        val title = field("Nombre del objetivo", existing?.title?.value.orEmpty())
        val detail = field("Finalidad", existing?.detail?.value.orEmpty())
        val due = field("Fecha objetivo (AAAA-MM-DD)", existing?.details?.dueDate?.toString().orEmpty())
        val target = existing?.details?.target
        val value = field("Meta numérica opcional", target?.value?.toString().orEmpty(), numeric = true)
        val unit = field("Unidad o código de moneda", target?.unit?.label.orEmpty())
        val money = android.widget.CheckBox(activity).apply {
            text = "La meta es monetaria (unidades menores)"
            isChecked = target?.unit?.kind == ObjectiveMetricUnit.Kind.MONETARY
        }
        column.addView(money)
        val priorities = ObjectivePriority.entries
        val priority = choice("Prioridad", priorities.map(::priorityLabel), priorities.indexOf(
            existing?.details?.priority ?: ObjectivePriority.NORMAL,
        ))
        button("Revisar objetivo") {
            fromFields {
                val rawValue = value.input()
                val rawUnit = unit.input()
                require(rawValue.isNotEmpty() || rawUnit.isEmpty()) { "Ingresá una meta o quitá la unidad." }
                val metric = rawValue.takeIf { it.isNotEmpty() }?.let {
                    val kind = if (money.isChecked) ObjectiveMetricUnit.Kind.MONETARY else ObjectiveMetricUnit.Kind.QUANTITY
                    ObjectiveMetric(it.toLong(), ObjectiveMetricUnit(kind, rawUnit))
                }
                val id = existing?.id ?: ObjectiveId.of("obj-" + UUID.randomUUID())
                val form = ObjectiveReviewedEdit(
                    id, ObjectiveTitle.of(title.input()), ObjectiveDetail.of(detail.input()),
                    target = metric, dueDate = dateOrNull(due), priority = priorities[priority.selectedItemPosition],
                )
                val operation = if (existing == null) ObjectiveReviewOperations.CREATE else ObjectiveReviewOperations.UPDATE
                val intent = ObjectiveEditIntent(snapshot.revision, at(existing), form)
                work({ actions.submitEdit(operation, intent) }, ::review)
            }
        }
        button("Cancelar") { load() }
    }

    private fun report(
        snapshot: ObjectiveSnapshot,
        record: ObjectiveRecord,
        original: Int? = null,
        previous: ObjectiveProgressReport? = null,
    ) {
        begin(if (original == null) "Registrar avance" else "Corregir avance #$original")
        val date = field("Fecha del avance (AAAA-MM-DD)", previous?.localDate?.toString() ?: LocalDate.now().toString())
        val explanation = field("Explicación del avance", previous?.explanation.orEmpty())
        val unit = record.details.target?.unit
        val value = unit?.let {
            field("Cantidad confirmada (${it.label})", previous?.reading?.takeIf { reading -> reading.unit == it }
                ?.value?.toString().orEmpty(), numeric = true)
        }
        val reason = original?.let { field("Motivo de la corrección") }
        button("Revisar avance") {
            fromFields {
                val reading = value?.input()?.takeIf { it.isNotEmpty() }?.let {
                    ObjectiveReading(it.toLong(), checkNotNull(unit))
                }
                val report = ObjectiveProgressReport(requireNotNull(dateOrNull(date)), explanation.input(), reading)
                val change = if (original == null) ObjectiveHistoryChange.Progress(report)
                else ObjectiveHistoryChange.Correction(original, report, checkNotNull(reason).input())
                prepareHistory(snapshot, record, change)
            }
        }
        button("Cancelar") { load() }
    }

    private fun lifecycle(snapshot: ObjectiveSnapshot, record: ObjectiveRecord) {
        begin("Cambiar estado")
        val states = nextObjectiveStates(record.status).toList()
        val next = choice("Nuevo estado", states.map(::statusLabel))
        val explanation = field("Motivo del cambio")
        button("Revisar estado") {
            fromFields {
                val change = ObjectiveHistoryChange.Lifecycle(
                    record.status, states[next.selectedItemPosition], explanation.input(),
                )
                prepareHistory(snapshot, record, change)
            }
        }
        button("Cancelar") { load() }
    }

    private fun prepareHistory(snapshot: ObjectiveSnapshot, record: ObjectiveRecord, change: ObjectiveHistoryChange) {
        val entry = ObjectiveHistoryEntry(record.details.history.size + 1, at(record), change)
        work({ actions.submitHistory(ObjectiveHistoryIntent(record.id, snapshot.revision, entry)) }, ::review)
    }

    private fun stepForm(snapshot: ObjectiveSnapshot, record: ObjectiveRecord, existing: ObjectiveNextStep? = null) {
        begin(if (existing == null) "Agregar próximo paso" else "Editar próximo paso")
        val title = field("Nombre del paso", existing?.title.orEmpty())
        val due = field("Fecha del paso (AAAA-MM-DD)", existing?.dueDate?.toString().orEmpty())
        button("Revisar paso") {
            fromFields {
                val step = ObjectiveNextStep(
                    existing?.id ?: "paso-" + UUID.randomUUID(), title.input(), dateOrNull(due), existing?.done ?: false,
                )
                preparePlan(snapshot, record, ObjectivePlanChange.PutStep(step))
            }
        }
        if (existing != null) button("Quitar paso") {
            preparePlan(snapshot, record, ObjectivePlanChange.RemoveStep(existing.id))
        }
        button("Cancelar") { load() }
    }

    private fun dependency(snapshot: ObjectiveSnapshot, record: ObjectiveRecord) {
        begin("Vincular otro objetivo")
        val candidates = snapshot.objectives.filter { it.id != record.id }
        if (candidates.isEmpty()) text("Creá otro objetivo para vincularlo")
        for (candidate in candidates) button(candidate.title.value) {
            preparePlan(snapshot, record, ObjectivePlanChange.SetLink(
                ObjectiveLink(ObjectiveLinkOwner.OBJECTIVE, candidate.id.value), true,
            ))
        }
        button("Cancelar") { load() }
    }

    private fun externalReference(snapshot: ObjectiveSnapshot, record: ObjectiveRecord, owner: ObjectiveLinkOwner) {
        begin("Referencia de ${sourceLabel(owner)}")
        val id = field("Identificador de la referencia")
        button("Autorizar referencia local") {
            fromFields {
                val link = ObjectiveLink(owner, id.input())
                work({
                    if (actions.authorizeReference(link)) actions.observeReference(link) as? ObjectiveLinkView.Visible
                    else null
                }) { visible ->
                    begin("Referencia de ${sourceLabel(owner)}")
                    text(visible.label)
                    button("Revisar vínculo") {
                        preparePlan(snapshot, record, ObjectivePlanChange.SetLink(link, true))
                    }
                    button("Cancelar") { load() }
                }
            }
        }
        button("Cancelar") { load() }
    }

    private fun preparePlan(snapshot: ObjectiveSnapshot, record: ObjectiveRecord, change: ObjectivePlanChange) {
        val intent = ObjectivePlanIntent(record.id, snapshot.revision, at(record), change)
        work({ actions.submitPlan(intent) }, ::review)
    }

    private fun review(review: ConversationPlanReviewSnapshot) {
        begin("Revisión de Objetivos")
        text(review.reviewText)
        when (review.state) {
            ConversationPlanReviewState.PENDING -> button("Confirmar propuesta") {
                work({ actions.decideNow(review, true) }, ::review)
            }
            ConversationPlanReviewState.APPROVED -> button("Aplicar cambios") {
                work({ actions.applyNow(review) }) { result ->
                    when (result) {
                        is ConversationApplyHostOutcome.Committed -> load("Recibo ${result.receipt.receiptId.value}")
                        ConversationApplyHostOutcome.RecoveryRequired -> load("Verificá el resultado en Chat")
                        is ConversationApplyHostOutcome.Denied -> load("La revisión ya no está disponible")
                    }
                }
            }
            else -> Unit
        }
        button("Descartar revisión") { work({ actions.decideNow(review, false) }) { load("Revisión descartada") } }
        button("Ver revisión en Chat") { openChat() }
    }

    private fun <T : Any> work(block: () -> T?, deliver: (T) -> Unit) {
        activity.work(block) result@{ result ->
            if (!isAttachedToWindow) return@result
            val value = result.getOrNull()
            if (value == null) {
                begin("Objetivos")
                text("Operación no disponible. Revisá los datos y los permisos.")
                button("Volver a objetivos") { load() }
            } else deliver(value)
        }
    }

    private fun fromFields(block: () -> Unit) {
        if (runCatching(block).isFailure) text("Revisá los campos obligatorios, la fecha y la cantidad.")
    }

    private fun dateOrNull(view: EditText): LocalDate? =
        view.input().takeIf(String::isNotEmpty)?.let(LocalDate::parse)

    private fun at(record: ObjectiveRecord?): Long = maxOf(
        System.currentTimeMillis(), record?.let { Math.incrementExact(it.updatedAtEpochMillis) } ?: 0L,
    )

    private fun reportLabel(report: ObjectiveProgressReport) =
        report.localDate.toString() + " · " + report.explanation +
            report.reading?.let { " · " + quantity(it.value, it.unit) }.orEmpty()

    private fun quantity(value: Long, unit: ObjectiveMetricUnit): String =
        if (unit.kind == ObjectiveMetricUnit.Kind.MONETARY) "${unit.label} $value unidades menores"
        else "$value ${unit.label}"

    private fun begin(title: String) {
        column.removeAllViews()
        scrollTo(0, 0)
        heading(title)
    }

    private fun heading(label: String) {
        column.addView(TextView(activity).apply {
            text = label
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            isAccessibilityHeading = true
        })
    }

    private fun text(label: String) {
        column.addView(TextView(activity).apply {
            text = label
            textSize = 16f
        })
    }

    private fun button(label: String, action: () -> Unit) {
        column.addView(Button(activity).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        })
    }

    private fun label(label: String, target: View) {
        target.id = View.generateViewId()
        column.addView(TextView(activity).apply {
            text = label
            textSize = 16f
            labelFor = target.id
        })
        column.addView(target)
    }

    private fun field(label: String, value: String = "", numeric: Boolean = false): EditText =
        EditText(activity).apply {
            hint = label
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
            setText(value)
            this@FaroV5W02ObjectivesPanel.label(label, this)
        }

    private fun choice(label: String, values: List<String>, position: Int = 0): android.widget.Spinner =
        android.widget.Spinner(activity).apply {
            adapter = android.widget.ArrayAdapter(
                activity, android.R.layout.simple_spinner_dropdown_item, values,
            )
            setSelection(position)
            this@FaroV5W02ObjectivesPanel.label(label, this)
        }

    private fun EditText.input() = text.toString().trim()

    private fun priorityLabel(priority: ObjectivePriority): String = when (priority) {
        ObjectivePriority.LOW -> "baja"
        ObjectivePriority.NORMAL -> "normal"
        ObjectivePriority.HIGH -> "alta"
    }

    private fun statusLabel(status: ObjectiveStatus): String = when (status) {
        ObjectiveStatus.PLANNED -> "Planificado"
        ObjectiveStatus.ACTIVE -> "Activo"
        ObjectiveStatus.PAUSED -> "Pausado"
        ObjectiveStatus.COMPLETED -> "Completado"
        ObjectiveStatus.ARCHIVED -> "Archivado"
    }

    private fun sourceLabel(owner: ObjectiveLinkOwner): String = when (owner) {
        ObjectiveLinkOwner.OBJECTIVE -> "Objetivos"
        ObjectiveLinkOwner.MATTER -> "Asuntos"
        ObjectiveLinkOwner.FINANCE -> "Finanzas"
    }
}
