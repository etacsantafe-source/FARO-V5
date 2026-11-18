package ar.com.faro.v5.owner.finance.api

import ar.com.faro.v5.kernel.contracts.CanonicalPayload
import ar.com.faro.v5.kernel.contracts.OperationId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** A reviewed allocation of one recorded expense to a concrete commitment. */
data class FinanceCommitmentPayment(
    val planItemId: FinanceCommitmentId,
    val debtId: FinanceCommitmentId?,
    val currency: FinanceCurrencyCode,
    val expectedPreviouslyPaidMinor: Long,
    val expense: FinanceEntryRecord,
) {
    init {
        require(expectedPreviouslyPaidMinor >= 0)
        require(expense.kind == FinanceEntryKind.EXPENSE)
        require(expense.effectiveAtEpochMillis >= 0)
    }
}

object FinanceCommitmentPaymentPayload {
    fun payload(operationId: OperationId, payment: FinanceCommitmentPayment): CanonicalPayload {
        require(operationId == FinanceOperations.DEBT_PAYMENT_RECORD || operationId == FinanceOperations.PLAN_ITEM_EXECUTE)
        require(operationId != FinanceOperations.DEBT_PAYMENT_RECORD || payment.debtId != null)
        return CanonicalPayload.fromEncoded("${operationId.value}.v1", encode(payment))
    }

    fun decode(operationId: OperationId, payload: CanonicalPayload): FinanceCommitmentPayment? {
        if (payload.formatId != "${operationId.value}.v1") return null
        val bytes = payload.copyBytes()
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val itemId = FinanceCommitmentId(input.readUTF())
                val debtId = if (input.readBoolean()) FinanceCommitmentId(input.readUTF()) else null
                val currency = FinanceCurrencyCode.of(input.readUTF())
                val reviewedPaid = input.readLong()
                val expense = requireNotNull(FinanceEntryRecordPayload.decode(CanonicalPayload.fromEncoded(
                    FinanceEntryRecordPayload.FORMAT_ID, ByteArray(input.available()).also(input::readFully),
                )))
                val payment = FinanceCommitmentPayment(itemId, debtId, currency, reviewedPaid, expense)
                require(this.payload(operationId, payment).copyBytes().contentEquals(bytes))
                payment
            }
        }.getOrNull()
    }

    private fun encode(payment: FinanceCommitmentPayment): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeUTF(payment.planItemId.value)
                output.writeBoolean(payment.debtId != null)
                payment.debtId?.let { output.writeUTF(it.value) }
                output.writeUTF(payment.currency.value)
                output.writeLong(payment.expectedPreviouslyPaidMinor)
                output.write(FinanceEntryRecordPayload.encodeBytes(payment.expense))
            }
            bytes.toByteArray()
        }
}
