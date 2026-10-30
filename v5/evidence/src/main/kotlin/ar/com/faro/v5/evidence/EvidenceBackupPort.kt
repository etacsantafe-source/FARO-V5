package ar.com.faro.v5.evidence

data class CommercialEvidenceBackupSnapshot(
    val schemaVersion: Int,
    val records: List<CommercialEvidenceRecord>,
) {
    init {
        require(schemaVersion >= 0)
        require(records == records.sortedBy { it.evidenceId.value }) {
            "Commercial Evidence backup records must be canonical"
        }
        require(records.map { it.evidenceId.value }.distinct().size == records.size)
        require(records.map { it.idempotencyKey.value }.distinct().size == records.size)
    }
}

interface CommercialEvidenceBackupPort {
    fun snapshot(): CommercialEvidenceBackupSnapshot
    fun restoreOrVerify(snapshot: CommercialEvidenceBackupSnapshot): CommercialEvidenceBackupSnapshot
}

data class WebEvidenceBackupSnapshot(
    val schemaVersion: Int,
    val fetchRecords: List<WebEvidenceRecord>,
    val searchRecords: List<WebSearchEvidenceRecord>,
) {
    init {
        require(schemaVersion >= 0)
        require(fetchRecords == fetchRecords.sortedBy { it.evidenceId.value }) {
            "Web fetch Evidence backup records must be canonical"
        }
        require(searchRecords == searchRecords.sortedBy { it.evidenceId.value }) {
            "Web search Evidence backup records must be canonical"
        }
        require(fetchRecords.map { it.evidenceId.value }.distinct().size == fetchRecords.size)
        require(fetchRecords.map { it.idempotencyKey.value }.distinct().size == fetchRecords.size)
        require(searchRecords.map { it.evidenceId.value }.distinct().size == searchRecords.size)
        require(searchRecords.map { it.idempotencyKey.value }.distinct().size == searchRecords.size)
    }
}

interface WebEvidenceBackupPort {
    fun snapshot(): WebEvidenceBackupSnapshot
    fun restoreOrVerify(snapshot: WebEvidenceBackupSnapshot): WebEvidenceBackupSnapshot
}
