package ar.com.faro.v5.app

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import ar.com.faro.v5.kernel.contracts.ConversationApplyHostOutcome
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.owner.finance.api.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

internal class FaroV5W01FinanceForms(
    private val root: LinearLayout,
    private val actions: S4W01F2FinanceSessionActions,
    private val execute: (work: () -> Unit) -> Unit,
    private val deliver: (apply: () -> Unit) -> Unit,
) {
    private val context: Context = root.context
    private val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val status = TextView(context).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    private val overviewText = TextView(context)
    private val zone = ZoneId.systemDefault()
    private var overview: W01FinanceOverview? = null
    private var pending: ConversationPlanReviewSnapshot? = null
    private var busy = false

    fun show() {
        root.addView(header("Operaciones"))
        root.addView(overviewText)
        menu("Nueva cuenta", ::account)
        menu("Ingreso o gasto", ::entry)
        menu("Transferencia interna", ::transfer)
        menu("Deudas, calendarios y cuotas", ::commitments)
        menu("Presupuesto mensual", ::budget)
        root.addView(details)
        root.addView(status)
        refresh()
    }

    private fun menu(label: String, form: () -> Unit) = button(root, label) {
        if (!busy && pending == null) {
            details.removeAllViews()
            status.text = ""
            details.addView(header(label))
            form()
        }
    }

    private fun refresh() = execute {
        val data = actions.overview()
        deliver {
            overview = data
            overviewText.text = if (data == null) "Lectura de Finanzas no disponible." else {
                val accounts = data.finance.accounts.joinToString("\n") { account ->
                    val checked = data.openings?.accounts?.find { it.accountId == account.accountId.value }
                    val balance = checked?.ledgerBalanceMinor?.let { money(it, account.currency) } ?: "Saldo sin verificar"
                    "${account.displayName.value} (${account.currency.value}) · $balance"
                }
                val entries = data.finance.entries.sortedByDescending { it.effectiveAtEpochMillis }.take(12)
                    .joinToString("\n") {
                        val account = data.finance.accounts.first { row -> row.accountId == it.accountId }
                        val kind = if (it.kind == FinanceEntryKind.INCOME) "Ingreso" else "Gasto"
                        "$kind · ${money(it.amountMinor, it.currency)} · ${account.displayName.value} · ${it.concept.value}"
                    }
                val receipts = data.latestReceipts.joinToString("\n") { "Recibo ${it.value}" }
                listOf(if (accounts.isBlank()) "Sin cuentas registradas" else accounts, entries, receipts)
                    .filter(String::isNotBlank).joinToString("\n\n")
            }
        }
    }

    private fun account() {
        val name = field("Nombre de la cuenta")
        val currency = currency()
        val type = choices("Tipo de cuenta", FinanceAccountNature.entries,
            listOf("Efectivo", "Banco", "Billetera", "Inversión", "Otra"))
        val opening = field("Saldo inicial declarado", numeric = true)
        reviewButton {
            W01FinanceDraft.Account(FinanceAccountCreate(FinanceAccountId.of(identity("account")),
                FinanceAccountDisplayName.of(name.value()), currency(), type(), minor(opening)))
        }
    }

    private fun entry() {
        val account = accounts() ?: return
        val kind = choices("Tipo de movimiento", FinanceEntryKind.entries, listOf("Ingreso", "Gasto"))
        val amount = field("Importe", numeric = true)
        val concept = field("Concepto")
        val category = field("Categoría")
        val date = field("Fecha efectiva (AAAA-MM-DD)", LocalDate.now(zone).toString())
        reviewButton {
            W01FinanceDraft.Entry(expense(account(), amount, concept, category, date, kind()))
        }
    }

    private fun transfer() {
        val from = accounts("Cuenta de origen") ?: return
        val to = accounts("Cuenta de destino") ?: return
        val amount = field("Importe", numeric = true)
        val concept = field("Concepto")
        val date = field("Fecha efectiva (AAAA-MM-DD)", LocalDate.now(zone).toString())
        val accept = android.widget.CheckBox(context).apply {
            text = "Acepto registrar aunque el saldo sea insuficiente o esté sin verificar"
            isSaveEnabled = false
        }
        details.addView(accept)
        reviewButton {
            val source = from()
            val destination = to()
            require(source.currency == destination.currency)
            val command = FinanceTransferRecord(FinanceTransferId(identity("transfer")), source.accountId,
                destination.accountId, source.currency, minor(amount), time(date), System.currentTimeMillis(),
                FinanceEntryConcept.of(concept.value()), accept.isChecked)
            operation(FinanceOperations.TRANSFER_RECORD, FinanceTransferRecordPayload.payload(command))
        }
    }

    private fun commitments() {
        val currency = currency()
        val output = TextView(context)
        button(details, "Consultar vencimientos") {
            val selected = currency()
            val now = LocalDate.now(zone)
            val month = YearMonth.from(now)
            execute {
                val data = actions.calendar(FinanceCommitmentCalendarQuery(month.minusMonths(12),
                    month.plusMonths(36), now, selected))
                deliver {
                    output.text = data?.let {
                        val debts = it.debts.map { d -> "Deuda ${d.title} · ${d.id.value}" }
                        val schedules = it.schedules.map { s ->
                            "Calendario ${s.definition.id.value} · versión ${s.version} · " +
                                (if (s.active) "Activo" else "Pausado")
                        }
                        val items = it.items.map { i ->
                            "${i.dueDate} · ${stateLabel(i.state)} · ${money(i.outstandingMinor, selected)} · ${i.id.value}"
                        }
                        (debts + schedules + items).joinToString("\n").ifBlank { "Sin compromisos registrados" }
                    } ?: "Calendario no disponible."
                }
            }
        }
        details.addView(output)
        val sub = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val modes = listOf("Declarar deuda", "Crear calendario", "Editar calendario", "Pausar o reactivar",
            "Retirar calendario", "Declarar cuota", "Registrar pago", "Cancelar cuota")
        for ((index, label) in modes.withIndex()) button(details, label) {
            if (pending == null && !busy) {
                sub.removeAllViews()
                renderCommitmentFields(index, currency, sub)
            }
        }
        details.addView(sub)
    }

    private fun renderCommitmentFields(index: Int, currency: () -> FinanceCurrencyCode, target: LinearLayout) {
        fun field(label: String, initial: String = "", numeric: Boolean = false) =
            field(label, initial, numeric, target)
        fun itemId(entry: EditText) = FinanceCommitmentId(entry.value())
        when (index) {
            0 -> {
                val title = field("Nombre de la deuda")
                val creditor = field("Acreedor")
                val amount = field("Importe inicial", numeric = true)
                reviewButton(target) { declare(FinanceCommitmentCreate.Debt(FinanceCommitmentId(identity("debt")),
                    title.value(), creditor.value(), currency(), minor(amount), System.currentTimeMillis())) }
            }
            1, 2 -> {
                val id = if (index == 2) field("Identificador de calendario") else null
                val version = if (index == 2) field("Versión observada", numeric = true) else null
                val debt = field("Identificador de deuda (opcional)")
                val amount = field("Importe de cada cuota", numeric = true)
                val begin = field("Primer mes (AAAA-MM)", YearMonth.now(zone).toString())
                val count = field("Cantidad de cuotas (vacío si indefinida)", numeric = true)
                val mode = choices("Frecuencia", FinanceScheduleRecurrence.entries, listOf("Una vez", "Mensual"), target)
                reviewButton(target) {
                    val schedule = FinanceCommitmentCreate.Schedule(
                        FinanceCommitmentId(id?.value() ?: identity("schedule")), debt.optionalId(), currency(),
                        minor(amount), YearMonth.parse(begin.value()), mode(),
                        if (mode() == FinanceScheduleRecurrence.ONCE) 1 else count.value().takeIf(String::isNotBlank)?.toInt(),
                        System.currentTimeMillis(),
                    )
                    if (version == null) declare(schedule) else lifecycle(FinanceCommitmentLifecycle.Edit(schedule, version.value().toLong()))
                }
            }
            3, 4 -> {
                val id = field("Identificador de calendario")
                val version = field("Versión observada", numeric = true)
                val active = if (index == 3) choices("Acción", listOf(false, true), listOf("Pausar", "Reactivar"), target) else null
                reviewButton(target) {
                    val now = System.currentTimeMillis()
                    lifecycle(if (active == null) FinanceCommitmentLifecycle.Retire(itemId(id), version.value().toLong(), now)
                        else FinanceCommitmentLifecycle.SetActive(itemId(id), version.value().toLong(), active(), now))
                }
            }
            5 -> {
                val schedule = field("Identificador de calendario (opcional)")
                val version = field("Versión si hay calendario", numeric = true)
                val debt = field("Identificador de deuda (opcional)")
                val amount = field("Importe previsto", numeric = true)
                val due = field("Vencimiento (AAAA-MM-DD)", LocalDate.now(zone).toString())
                val ordinal = field("Número de cuota", "1", numeric = true)
                reviewButton(target) {
                    val reference = schedule.optionalId()?.let { FinanceCommitmentCreate.ScheduleReference(it, version.value().toLong()) }
                    val date = LocalDate.parse(due.value())
                    declare(FinanceCommitmentCreate.PlanItem(
                        if (reference == null) FinanceCommitmentId(identity("item")) else null,
                        reference, debt.optionalId(), YearMonth.from(date), ordinal.value().toInt(), date,
                        currency(), minor(amount), System.currentTimeMillis(),
                    ))
                }
            }
            6 -> {
                val account = accounts("Cuenta del gasto", target) ?: return
                val item = field("Identificador de cuota")
                val debt = field("Identificador de deuda (opcional)")
                val amount = field("Importe pagado", numeric = true)
                val before = field("Importe ya pagado de esa cuota", "0", numeric = true)
                val concept = field("Concepto")
                val category = field("Categoría")
                val date = field("Fecha efectiva (AAAA-MM-DD)", LocalDate.now(zone).toString())
                reviewButton(target) {
                    val selected = account()
                    require(selected.currency == currency())
                    val payment = FinanceCommitmentPayment(itemId(item), debt.optionalId(), selected.currency,
                        minor(before), expense(selected, amount, concept, category, date, FinanceEntryKind.EXPENSE))
                    val op = if (payment.debtId == null) FinanceOperations.PLAN_ITEM_EXECUTE else FinanceOperations.DEBT_PAYMENT_RECORD
                    operation(op, FinanceCommitmentPaymentPayload.payload(op, payment))
                }
            }
            7 -> {
                val item = field("Identificador de cuota")
                reviewButton(target) { lifecycle(FinanceCommitmentLifecycle.Cancel(itemId(item), System.currentTimeMillis())) }
            }
        }
    }

    private fun budget() {
        val currency = currency()
        val month = field("Mes (AAAA-MM)", YearMonth.now(zone).toString())
        val category = field("Categoría")
        val amount = field("Límite mensual", numeric = true)
        fun key() = FinanceBudgetKey(YearMonth.parse(month.value()), FinanceEntryCategory.of(category.value()), currency())
        val result = TextView(context)
        button(details, "Consultar presupuesto") {
            val selected = runCatching { key() }.getOrElse { invalid(); return@button }
            execute {
                val data = actions.budget(FinanceBudgetQuery(selected, zone))
                deliver {
                    fun line(value: Long?) = value?.let { money(it, selected.currency) } ?: "Sin dato"
                    result.text = if (data == null || data.sourceRevision == null) "Presupuesto no disponible."
                        else "Límite: ${line(data.limitMinor)}\nGasto real: ${line(data.actualSpentMinor)}\n" +
                            "Compromisos sin categoría: ${line(data.uncategorizedCommitmentsMinor)}\n" +
                            "Disponible estimado: ${line(data.estimatedAvailableMinor)}"
                }
            }
        }
        details.addView(result)
        fun change(limit: Long?): W01FinanceDraft.Operation {
            val rev = requireNotNull(overview).finance.revision
            val command = FinanceBudgetChange(key(), rev, limit, System.currentTimeMillis())
            val op = if (limit == null) FinanceOperations.BUDGET_CLEAR else FinanceOperations.BUDGET_SET
            return operation(op, FinanceBudgetChangePayload.payload(command))
        }
        reviewButton { change(minor(amount)) }
        button(details, "Revisar retiro del límite") { prepare { change(null) } }
    }

    private fun reviewButton(parent: LinearLayout = details, draft: () -> W01FinanceDraft) =
        button(parent, "Revisar") { prepare(draft) }

    private fun prepare(make: () -> W01FinanceDraft) {
        if (busy || pending != null) return
        val draft = runCatching(make).getOrElse { invalid(); return }
        busy = true
        execute {
            val review = actions.prepare(draft)
            deliver {
                busy = false
                if (review == null) { status.text = "No se pudo preparar la revisión."; return@deliver }
                pending = review
                details.removeAllViews()
                details.addView(header("Revisión del registro"))
                details.addView(TextView(context).apply { text = review.reviewText })
                button(details, "Confirmar registro") { decide(true) }
                button(details, "Descartar revisión") { decide(false) }
            }
        }
    }

    private fun decide(confirm: Boolean) {
        val review = pending ?: return
        if (busy) return
        busy = true
        execute {
            val message = if (confirm) {
                val applied = actions.confirm(review)
                if (applied is ConversationApplyHostOutcome.Committed) "Recibo ${applied.receipt.receiptId.value}"
                else "No se confirmó el registro. Revisá el estado antes de volver a intentar."
            } else if (actions.discard(review)) "Revisión descartada." else "No se pudo descartar."
            deliver {
                pending = null
                busy = false
                details.removeAllViews()
                status.text = message
                refresh()
            }
        }
    }

    private fun accounts(label: String = "Cuenta", target: LinearLayout = details): (() -> FinanceAccountSnapshot)? {
        val items = overview?.finance?.accounts.orEmpty()
        if (items.isEmpty()) { status.text = "Primero registrá una cuenta."; return null }
        return choices(label, items, items.map { "${it.displayName.value} (${it.currency.value})" }, target)
    }

    private fun currency(): () -> FinanceCurrencyCode = choices("Moneda",
        listOf(FinanceCurrencyCode.of("ARS"), FinanceCurrencyCode.of("USD")), listOf("ARS", "USD"))

    private fun <T> choices(label: String, values: List<T>, display: List<String>, parent: LinearLayout = details): () -> T {
        val widget = Spinner(context).apply {
            id = View.generateViewId()
            isSaveEnabled = false
            minimumHeight = dp(48)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, display)
        }
        label(parent, label, widget)
        return { values[widget.selectedItemPosition] }
    }

    private fun field(title: String, initial: String = "", numeric: Boolean = false, parent: LinearLayout = details) =
        EditText(context).apply {
            id = View.generateViewId()
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            minimumHeight = dp(48)
            setSingleLine(true)
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
            setText(initial)
            label(parent, title, this)
        }

    private fun label(parent: LinearLayout, text: String, input: View) {
        parent.addView(TextView(context).apply { this.text = text; labelFor = input.id })
        parent.addView(input)
    }

    private fun button(parent: LinearLayout, label: String, click: () -> Unit) {
        parent.addView(Button(context).apply { text = label; minimumHeight = dp(48); setOnClickListener { click() } })
    }

    private fun header(label: String) = TextView(context).apply { text = label; textSize = 19f; isAccessibilityHeading = true }
    private fun dp(size: Int) = (size * context.resources.displayMetrics.density).toInt()
    private fun invalid() { status.text = "Revisá los datos del formulario." }
    private fun EditText.value() = text.toString().trim()
    private fun EditText.optionalId() = value().takeIf(String::isNotEmpty)?.let(::FinanceCommitmentId)
    private fun minor(field: EditText) = BigDecimal(field.value().replace(',', '.')).movePointRight(2).longValueExact()
    private fun time(field: EditText) = LocalDate.parse(field.value()).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun identity(prefix: String) = "$prefix-${UUID.randomUUID()}"
    private fun money(value: Long, currency: FinanceCurrencyCode) = "${currency.value} ${BigDecimal.valueOf(value, 2)}"
    private fun stateLabel(state: FinanceCommitmentState) = when (state) {
        FinanceCommitmentState.PLANNED -> "Previsto"
        FinanceCommitmentState.DUE -> "Vencido"
        FinanceCommitmentState.PARTIAL -> "Pago parcial"
        FinanceCommitmentState.PAID_CONFIRMED -> "Pagado"
        FinanceCommitmentState.CANCELLED -> "Cancelado"
    }

    private fun expense(account: FinanceAccountSnapshot, amount: EditText, concept: EditText, category: EditText,
                        date: EditText, kind: FinanceEntryKind) =
        FinanceEntryRecord(FinanceEntryId.of(identity("entry")), account.accountId, minor(amount), kind,
            FinanceEntryConcept.of(concept.value()), FinanceEntryCategory.of(category.value()), time(date), System.currentTimeMillis())

    private fun operation(id: OperationId, payload: ar.com.faro.v5.kernel.contracts.CanonicalPayload) =
        W01FinanceDraft.Operation(id, payload)

    private fun declare(command: FinanceCommitmentCreate): W01FinanceDraft.Operation {
        val id = when (command) {
            is FinanceCommitmentCreate.Debt -> FinanceOperations.DEBT_CREATE
            is FinanceCommitmentCreate.PlanItem -> FinanceOperations.PLAN_ITEM_CREATE
            is FinanceCommitmentCreate.Schedule -> FinanceOperations.SCHEDULE_CREATE
        }
        return operation(id, FinanceCommitmentCreatePayload.payload(command))
    }

    private fun lifecycle(command: FinanceCommitmentLifecycle) = operation(
        FinanceCommitmentLifecyclePayload.operation(command), FinanceCommitmentLifecyclePayload.payload(command),
    )
}
