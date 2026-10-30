package ar.com.faro.v5.maintenance

@JvmInline
value class StorageGenerationId(val value: String) {
    init {
        require(value.isNotBlank()) { "storage generation id must not be blank" }
        require(value == value.trim()) { "storage generation id must be canonical" }
        require(value.length <= 128) { "storage generation id is too long" }
    }
}

@JvmInline
value class MaintenanceOperationId(val value: String) {
    init {
        require(value.isNotBlank()) { "maintenance operation id must not be blank" }
        require(value == value.trim()) { "maintenance operation id must be canonical" }
        require(value.length <= 160) { "maintenance operation id is too long" }
    }
}

data class StorageGenerationDescriptor(
    val generationId: StorageGenerationId,
    val schemaSetFingerprint: String,
    val createdAtEpochMillis: Long,
) {
    init {
        require(schemaSetFingerprint.isNotBlank()) {
            "storage generation schema fingerprint must not be blank"
        }
        require(schemaSetFingerprint == schemaSetFingerprint.trim()) {
            "storage generation schema fingerprint must be canonical"
        }
        require(schemaSetFingerprint.length <= 256) {
            "storage generation schema fingerprint is too long"
        }
        require(createdAtEpochMillis >= 0L) {
            "storage generation creation time must be non-negative"
        }
    }
}
