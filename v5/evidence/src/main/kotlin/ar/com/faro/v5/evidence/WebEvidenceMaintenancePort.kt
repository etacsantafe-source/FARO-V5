package ar.com.faro.v5.evidence

data class WebEvidenceMaintenanceInspection(
    val schemaVersion: Int,
    val integrityHealthy: Boolean,
    val foreignKeysHealthy: Boolean,
    val fetchRecordCount: Long,
    val searchRecordCount: Long,
) {
    init {
        require(schemaVersion >= 0)
        require(fetchRecordCount >= 0L)
        require(searchRecordCount >= 0L)
    }
}

fun interface WebEvidenceMaintenancePort {
    fun inspect(): WebEvidenceMaintenanceInspection
}
