package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ExecutionReceiptTest {
    @Test
    fun `receipt pins receipt transaction and exact plan fingerprint`() {
        val receipt = ExecutionReceipt(
            receiptId = ReceiptId("receipt-1"),
            transactionId = TransactionId("tx-1"),
            planFingerprint = PlanFingerprint("plan-a"),
        )

        assertEquals(ReceiptId("receipt-1"), receipt.receiptId)
        assertEquals(TransactionId("tx-1"), receipt.transactionId)
        assertEquals(PlanFingerprint("plan-a"), receipt.planFingerprint)
    }

    @Test
    fun `different committed transaction is a different receipt by value`() {
        val first = ExecutionReceipt(
            receiptId = ReceiptId("receipt-1"),
            transactionId = TransactionId("tx-1"),
            planFingerprint = PlanFingerprint("plan-a"),
        )
        val second = first.copy(
            receiptId = ReceiptId("receipt-2"),
            transactionId = TransactionId("tx-2"),
        )

        assertNotEquals(first, second)
    }
}
