package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.store.reality.RealityStore
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant

internal object S4W01F1FinanceReviewOperations {
    data class Preview(val objectId: String, val text: String)

    fun preview(op: OperationId, payload: CanonicalPayload): Preview? = when (op) {
        FinanceOperations.TRANSFER_RECORD -> FinanceTransferRecordPayload.decode(payload)?.let {
            val warning = if (it.balanceWarningAccepted) " Advertencia de saldo aceptada." else ""
            Preview(it.transferId.value, "Registrar transferencia de ${money(it.amountMinor, it.currency)} " +
                "de ${it.fromAccountId.value} a ${it.toAccountId.value}. ${it.concept.value}. " +
                "Fecha efectiva ${Instant.ofEpochMilli(it.effectiveAtEpochMillis)}.$warning")
        }
        FinanceOperations.DEBT_CREATE, FinanceOperations.SCHEDULE_CREATE, FinanceOperations.PLAN_ITEM_CREATE ->
            FinanceCommitmentCreatePayload.decode(op, payload)?.let(::declaration)
        FinanceOperations.DEBT_PAYMENT_RECORD, FinanceOperations.PLAN_ITEM_EXECUTE ->
            FinanceCommitmentPaymentPayload.decode(op, payload)?.let {
                Preview(it.planItemId.value, "Registrar pago de ${money(it.expense.amountMinor, it.currency)} " +
                    "desde ${it.expense.accountId.value} para cuota ${it.planItemId.value}. " +
                    "Monto ya pagado ${money(it.expectedPreviouslyPaidMinor, it.currency)}. " +
                    "${it.expense.concept.value}. Categoría ${it.expense.category.value}. " +
                    "Fecha efectiva ${Instant.ofEpochMilli(it.expense.effectiveAtEpochMillis)}.")
            }
        FinanceOperations.SCHEDULE_UPDATE, FinanceOperations.SCHEDULE_SET_ACTIVE,
        FinanceOperations.SCHEDULE_DELETE, FinanceOperations.PLAN_ITEM_CANCEL ->
            FinanceCommitmentLifecyclePayload.decode(op, payload)?.let(::lifecycle)
        FinanceOperations.BUDGET_SET, FinanceOperations.BUDGET_CLEAR ->
            FinanceBudgetChangePayload.decode(op, payload)?.let(::budget)
        else -> null
    }

    fun bindings(reality: RealityStore): List<OperationBindingPort> =
        listOf(FinanceTransferRecordBinding(reality.finance9TransferWriteForDevOnly())) +
            FinanceCommitmentCreateBinding.all(reality.finance9CommitmentWriteForDevOnly()) +
            FinanceCommitmentPaymentBinding.all(reality.finance9CommitmentPaymentForDevOnly()) +
            FinanceCommitmentLifecycleBinding.all(reality.finance9CommitmentLifecycleForDevOnly()) +
            FinanceBudgetBinding.all(reality.finance9BudgetWriteForDevOnly())

    fun revalidators(reality: RealityStore): List<OperationRevalidationPort> =
        FinanceOperations.ACTIVE_W01_ADDITIONAL_REVIEW.map { id ->
            if (id == FinanceOperations.TRANSFER_RECORD) FinanceTransferRecordRevalidation(FinanceSnapshotReadApi(reality.financePersistence::read))
            else object : OperationRevalidationPort {
                override val operationId = id
                override fun revalidate(preparedOperation: PreparedOperation): OperationRevalidationResult =
                    if (preview(id, preparedOperation.payload) != null) OperationRevalidationResult.Valid
                    else OperationRevalidationResult.Rejected(
                        OperationRevalidationFailure.PRECONDITION_FAILED, "finance_w01_payload_invalid",
                    )
            }
        }

    private fun declaration(command: FinanceCommitmentCreate): Preview = when (command) {
        is FinanceCommitmentCreate.Debt -> Preview(command.id.value,
            "Declarar deuda ${command.title} con ${command.creditor} por ${money(command.principalMinor, command.currency)}.")
        is FinanceCommitmentCreate.Schedule -> Preview(command.id.value,
            "Crear calendario ${command.id.value} con ${scheduleDetails(command)}.")
        is FinanceCommitmentCreate.PlanItem -> Preview(command.id.value,
            "Declarar cuota ${command.id.value} por ${money(command.expectedMinor, command.currency)} " +
                "con vencimiento ${command.dueDate}." + (command.debtId?.let { " Deuda ${it.value}." } ?: ""))
    }

    private fun lifecycle(command: FinanceCommitmentLifecycle): Preview = when (command) {
        is FinanceCommitmentLifecycle.Edit -> Preview(command.id.value,
            "Editar calendario ${command.id.value}, versión ${command.expectedVersion}, con ${scheduleDetails(command.replacement)}.")
        is FinanceCommitmentLifecycle.SetActive -> Preview(command.id.value,
            (if (command.active) "Reactivar" else "Pausar") + " calendario ${command.id.value}, versión ${command.expectedVersion}.")
        is FinanceCommitmentLifecycle.Retire -> Preview(command.id.value,
            "Retirar calendario ${command.id.value}, versión ${command.expectedVersion}.")
        is FinanceCommitmentLifecycle.Cancel -> Preview(command.id.value, "Cancelar cuota ${command.id.value}.")
    }

    private fun scheduleDetails(schedule: FinanceCommitmentCreate.Schedule): String {
        val frequency = if (schedule.recurrence == FinanceScheduleRecurrence.ONCE) "única" else "mensual"
        val debt = schedule.debtId?.let { ", deuda ${it.value}" }.orEmpty()
        return "cuota ${money(schedule.installmentMinor, schedule.currency)} $frequency desde ${schedule.startPeriod}, " +
            "${schedule.totalCount?.let { "$it ocurrencias" } ?: "sin cantidad fija"}$debt"
    }

    private fun budget(command: FinanceBudgetChange): Preview {
        val key = command.key
        val identity = "${key.period}:${key.currency.value}:${key.category.value}"
        val scope = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val action = command.limitMinor?.let { "Fijar límite en ${money(it, key.currency)}" }
            ?: "Quitar límite en ${key.currency.value}"
        return Preview("budget-$scope", "$action para ${key.category.value}, período ${key.period}.")
    }

    private fun money(minor: Long, currency: FinanceCurrencyCode): String =
        if (currency.value in setOf("ARS", "USD")) "${currency.value} ${BigDecimal.valueOf(minor, 2)}"
        else "${currency.value} $minor unidades menores"
}
