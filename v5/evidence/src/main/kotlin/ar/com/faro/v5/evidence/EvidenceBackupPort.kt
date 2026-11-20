package ar.com.faro.v5.evidence

data class CommercialEvidenceBackupSnapshot(
    val schemaVersion: Int,
    val records: List<CommercialEvidenceRecord>,
    /** Null for EVERY historical schema1 backup. Required for schema2. */
    val stagedTyped: CommercialEvidenceStagedTypedBackupSnapshot? = null,
) {
    init {
        require(schemaVersion >= 0)
        if (schemaVersion == 2) {
            require(stagedTyped?.schemaVersion == 2 &&
                stagedTyped.rows.map { it.record } == records) {
                "schema2 Commercial backup requires complete canonical typed rows"
            }
        } else {
            require(stagedTyped == null) {
                "legacy Commercial backup cannot carry typed rows"
            }
        }
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

/**
 * C1b2E: isolate schema2 STAGED snapshot/restore from the historical
 * CommercialEvidenceBackupPort. FBS1/FBS2 and active graphs still use v1
 * until an independently audited lossless codec is available.
 *
 * terms == null means the original v1 row had no physical quote-terms row.
 * terms == UNKNOWN is an explicitly stored v2 row; do not conflate them.
 */
data class CommercialEvidenceStagedTypedBackupRow(
    val record: CommercialEvidenceRecord,
    val terms: CommercialEvidenceTypedTerms?,
) {
    val effectiveTerms: CommercialEvidenceTypedTerms
        get() = terms ?: CommercialEvidenceTypedTerms.UNKNOWN
}

data class CommercialEvidenceStagedTypedBackupSnapshot(
    val schemaVersion: Int,
    val rows: List<CommercialEvidenceStagedTypedBackupRow>,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) { "typed Evidence backup requires exact schema2" }
        require(rows.size <= MAX_RECORDS) { "typed Evidence backup exceeds record bound" }
        require(rows == rows.sortedBy { it.record.evidenceId.value }) {
            "typed Evidence backup rows must be canonical"
        }
        require(rows.map { it.record.evidenceId.value }.distinct().size == rows.size) {
            "typed Evidence backup has duplicate IDs"
        }
        require(rows.map { it.record.idempotencyKey.value }.distinct().size == rows.size) {
            "typed Evidence backup has duplicate idempotency keys"
        }
    }

    companion object {
        const val SCHEMA_VERSION: Int = 2
        const val MAX_RECORDS: Int = 100_000
    }
}

interface CommercialEvidenceStagedTypedBackupPort {
    fun snapshot(): CommercialEvidenceStagedTypedBackupSnapshot
    fun restoreOrVerify(
        snapshot: CommercialEvidenceStagedTypedBackupSnapshot,
    ): CommercialEvidenceStagedTypedBackupSnapshot
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
