package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint

data class CommercialEvidenceMaintenanceInspection(
    val schemaVersion: Int,
    val integrityHealthy: Boolean,
    val foreignKeysHealthy: Boolean,
    val recordCount: Long,
) {
    init {
        require(schemaVersion >= 0) {
            "Commercial Evidence schema version must be non-negative"
        }
        require(recordCount >= 0L) {
            "Commercial Evidence record count must be non-negative"
        }
    }

    val freshForSemanticMigration: Boolean
        get() = recordCount == 0L
}

fun interface CommercialEvidenceMaintenancePort {
    fun inspect(): CommercialEvidenceMaintenanceInspection
}

data class CommercialEvidenceMigrationRecord(
    val sourceIdentityFingerprint: String,
    val productKey: String,
    val merchant: String,
    val currencyCode: String,
    val amountMinor: Long,
    val sourceRef: String,
    val observedAtEpochMillis: Long?,
    val acquiredAtEpochMillis: Long,
) {
    init {
        require(sourceIdentityFingerprint.matches(Regex("[0-9a-f]{64}"))) {
            "commercial migration source identity must be a lowercase SHA-256"
        }
        CommercialPriceObservation(
            productKey = CommercialProductKey(productKey),
            merchant = CommercialMerchant(merchant),
            currencyCode = CommercialCurrencyCode(currencyCode),
            amountMinor = amountMinor,
            sourceKind = CommercialEvidenceSourceKind.IMPORT,
            sourceRef = sourceRef,
            observedAtEpochMillis = observedAtEpochMillis,
        )
        require(acquiredAtEpochMillis >= 0L) {
            "commercial migration acquisition time must be non-negative"
        }
    }
}

object CommercialEvidenceMigrationIdentity {
    private const val IDEMPOTENCY_PREFIX = "legacy-migration:"

    fun toStored(
        source: CommercialEvidenceMigrationRecord,
    ): CommercialEvidenceRecord =
        CommercialEvidenceRecord(
            evidenceId = CommercialEvidenceId("legacy-" + source.sourceIdentityFingerprint),
            idempotencyKey =
                IdempotencyKey(IDEMPOTENCY_PREFIX + source.sourceIdentityFingerprint),
            payloadFingerprint = payloadFingerprint(source),
            observation =
                CommercialPriceObservation(
                    productKey = CommercialProductKey(source.productKey),
                    merchant = CommercialMerchant(source.merchant),
                    currencyCode = CommercialCurrencyCode(source.currencyCode),
                    amountMinor = source.amountMinor,
                    sourceKind = CommercialEvidenceSourceKind.IMPORT,
                    sourceRef = source.sourceRef,
                    observedAtEpochMillis = source.observedAtEpochMillis,
                ),
            acquiredAtEpochMillis = source.acquiredAtEpochMillis,
        )

    fun fromStored(
        stored: CommercialEvidenceRecord,
    ): CommercialEvidenceMigrationRecord {
        check(stored.observation.sourceKind == CommercialEvidenceSourceKind.IMPORT) {
            "semantic migration target contains non-import commercial evidence"
        }
        check(stored.idempotencyKey.value.startsWith(IDEMPOTENCY_PREFIX)) {
            "semantic migration target contains non-migration idempotency identity"
        }
        val sourceIdentity =
            stored.idempotencyKey.value.removePrefix(IDEMPOTENCY_PREFIX)
        val source =
            CommercialEvidenceMigrationRecord(
                sourceIdentityFingerprint = sourceIdentity,
                productKey = stored.observation.productKey.value,
                merchant = stored.observation.merchant.value,
                currencyCode = stored.observation.currencyCode.value,
                amountMinor = stored.observation.amountMinor,
                sourceRef = stored.observation.sourceRef,
                observedAtEpochMillis = stored.observation.observedAtEpochMillis,
                acquiredAtEpochMillis = stored.acquiredAtEpochMillis,
            )
        check(stored.evidenceId == toStored(source).evidenceId) {
            "commercial migration evidence identity mismatch"
        }
        check(stored.payloadFingerprint == payloadFingerprint(source)) {
            "commercial migration payload fingerprint mismatch"
        }
        return source
    }

    private fun payloadFingerprint(
        source: CommercialEvidenceMigrationRecord,
    ): PayloadFingerprint =
        PayloadFingerprint(
            CanonicalHashBuilder
                .domain("commercial-evidence-migration-record-v1")
                .string("productKey", source.productKey)
                .string("merchant", source.merchant)
                .string("currencyCode", source.currencyCode)
                .string("amountMinor", source.amountMinor.toString())
                .string("sourceRef", source.sourceRef)
                .string(
                    "observedAtEpochMillis",
                    source.observedAtEpochMillis?.toString() ?: "null",
                )
                .string("acquiredAtEpochMillis", source.acquiredAtEpochMillis.toString())
                .sha256Hex()
        )
}

interface CommercialEvidenceSemanticMigrationPort {
    fun snapshot(): List<CommercialEvidenceMigrationRecord>

    fun importFresh(
        records: List<CommercialEvidenceMigrationRecord>,
    ): List<CommercialEvidenceMigrationRecord>
}
