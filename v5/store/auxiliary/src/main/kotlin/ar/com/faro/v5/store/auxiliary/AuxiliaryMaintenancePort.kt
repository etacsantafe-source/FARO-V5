package ar.com.faro.v5.store.auxiliary

data class AuxiliaryMaintenanceInspection(
    val schemaVersion: Int,
    val integrityHealthy: Boolean,
    val foreignKeysHealthy: Boolean,
    val noticeCount: Long,
    val deliveryIntentCount: Long,
) {
    init {
        require(schemaVersion >= 0) { "Auxiliary schema version must be non-negative" }
        require(noticeCount >= 0L) { "Auxiliary notice count must be non-negative" }
        require(deliveryIntentCount >= 0L) {
            "Auxiliary delivery-intent count must be non-negative"
        }
    }

    val freshForSemanticMigration: Boolean
        get() = noticeCount == 0L && deliveryIntentCount == 0L
}

fun interface AuxiliaryMaintenancePort {
    fun inspect(): AuxiliaryMaintenanceInspection
}
