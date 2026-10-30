package ar.com.faro.v5.kernel.contracts

data class ExecutionReceipt(
    val receiptId: ReceiptId,
    val transactionId: TransactionId,
    val planFingerprint: PlanFingerprint,
)
