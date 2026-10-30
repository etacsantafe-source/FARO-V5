package ar.com.faro.v5.store.reality

data class RealityCommittedReceiptBackup(
    val receiptId: String,
    val transactionId: String,
    val planFingerprint: String,
) {
    init {
        require(receiptId.isNotBlank()) { "backup receipt id must not be blank" }
        require(transactionId.isNotBlank()) { "backup transaction id must not be blank" }
        require(planFingerprint.isNotBlank()) { "backup plan fingerprint must not be blank" }
    }
}

data class RealityBackupSnapshot(
    val schemaVersion: Int,
    val profileRevision: Long,
    val financeRevision: Long,
    val domain: RealitySemanticMigrationData,
    val committedReceipts: List<RealityCommittedReceiptBackup>,
) {
    init {
        require(schemaVersion >= 0) { "Reality backup schema version must be non-negative" }
        require(profileRevision >= 0L) { "Reality backup profile revision must be non-negative" }
        require(financeRevision >= 0L) { "Reality backup finance revision must be non-negative" }
        require(
            committedReceipts.map { it.receiptId }.distinct().size == committedReceipts.size
        ) { "Reality backup contains duplicate receipt ids" }
        require(
            committedReceipts.map { it.transactionId }.distinct().size == committedReceipts.size
        ) { "Reality backup contains duplicate transaction ids" }
        require(
            committedReceipts.map { it.planFingerprint }.distinct().size == committedReceipts.size
        ) { "Reality backup contains duplicate plan fingerprints" }
        require(
            committedReceipts == committedReceipts.sortedBy { it.transactionId }
        ) { "Reality backup receipts must be in canonical transaction order" }
    }
}

interface RealityBackupPort {
    fun snapshot(): RealityBackupSnapshot

    /**
     * Restores into a fresh target, or verifies that a prior interrupted restore
     * already materialized the exact same snapshot. Any other non-fresh target
     * fails closed.
     */
    fun restoreOrVerify(snapshot: RealityBackupSnapshot): RealityBackupSnapshot
}
