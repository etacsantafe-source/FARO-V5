package ar.com.faro.v5.evidence

import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder

data class PortableCommercialObservation(
    val productKey: String,
    val merchant: String,
    val currencyCode: String,
    val amountMinor: Long,
    val sourceKind: String,
    val sourceRef: String,
    val observedAtEpochMillis: Long?,
    val acquiredAtEpochMillis: Long,
)

data class CommercialEvidencePortabilitySnapshot(
    val schemaVersion: Int,
    val sourceWatermark: String,
    val records: List<PortableCommercialObservation>,
) {
    init {
        require(schemaVersion >= 0)
        require(sourceWatermark.matches(Regex("[0-9a-f]{64}")))
        require(records.size <= MAX_RECORDS)
    }

    companion object {
        const val MAX_RECORDS = 100_000
    }
}

fun interface CommercialEvidencePortabilityPort {
    fun snapshot(): CommercialEvidencePortabilitySnapshot
}

data class PortableWebFetchRecord(
    val requestedUrl: String,
    val finalUrl: String,
    val method: String,
    val statusCode: Int,
    val mediaType: String?,
    val redirectCount: Int,
    val acquiredAtEpochMillis: Long,
    val contentSha256Hex: String?,
    val contentBytes: Int?,
)

data class PortableWebSearchHit(
    val title: String,
    val url: String,
    val snippet: String,
    val score: Double?,
)

data class PortableWebSearchRecord(
    val query: String,
    val maxResults: Int,
    val providerId: String,
    val acquiredAtEpochMillis: Long,
    val hits: List<PortableWebSearchHit>,
)

data class WebEvidencePortabilitySnapshot(
    val schemaVersion: Int,
    val sourceWatermark: String,
    val fetchRecords: List<PortableWebFetchRecord>,
    val searchRecords: List<PortableWebSearchRecord>,
) {
    init {
        require(schemaVersion >= 0)
        require(sourceWatermark.matches(Regex("[0-9a-f]{64}")))
        require(fetchRecords.size <= MAX_RECORDS)
        require(searchRecords.size <= MAX_RECORDS)
    }

    companion object {
        const val MAX_RECORDS = 100_000
    }
}

fun interface WebEvidencePortabilityPort {
    fun snapshot(): WebEvidencePortabilitySnapshot
}

internal object EvidencePortabilityProjection {
    fun commercial(
        schemaVersion: Int,
        records: List<CommercialEvidenceRecord>,
    ): CommercialEvidencePortabilitySnapshot {
        val portable =
            records.map { record ->
                PortableCommercialObservation(
                    productKey = record.observation.productKey.value,
                    merchant = record.observation.merchant.value,
                    currencyCode = record.observation.currencyCode.value,
                    amountMinor = record.observation.amountMinor,
                    sourceKind = record.observation.sourceKind.name,
                    sourceRef = record.observation.sourceRef,
                    observedAtEpochMillis = record.observation.observedAtEpochMillis,
                    acquiredAtEpochMillis = record.acquiredAtEpochMillis,
                )
            }.sortedWith(
                compareBy<PortableCommercialObservation>(
                    { it.productKey },
                    { it.merchant },
                    { it.currencyCode },
                    { it.observedAtEpochMillis ?: Long.MAX_VALUE },
                    { it.acquiredAtEpochMillis },
                    { it.amountMinor },
                    { it.sourceKind },
                    { it.sourceRef },
                )
            )
        require(portable.size <= CommercialEvidencePortabilitySnapshot.MAX_RECORDS)
        val hash =
            CanonicalHashBuilder
                .domain("faro-portability-commercial-v1")
                .string("schemaVersion", schemaVersion.toString())
                .string("count", portable.size.toString())
        portable.forEachIndexed { index, record ->
            val prefix = "record[$index]."
            hash.string(prefix + "productKey", record.productKey)
                .string(prefix + "merchant", record.merchant)
                .string(prefix + "currencyCode", record.currencyCode)
                .string(prefix + "amountMinor", record.amountMinor.toString())
                .string(prefix + "sourceKind", record.sourceKind)
                .string(prefix + "sourceRef", record.sourceRef)
                .string(prefix + "observed.present", (record.observedAtEpochMillis != null).toString())
                .string(prefix + "observed", record.observedAtEpochMillis?.toString() ?: "")
                .string(prefix + "acquired", record.acquiredAtEpochMillis.toString())
        }
        return CommercialEvidencePortabilitySnapshot(
            schemaVersion = schemaVersion,
            sourceWatermark = hash.sha256Hex(),
            records = portable,
        )
    }

    fun web(
        schemaVersion: Int,
        fetchRecords: List<WebEvidenceRecord>,
        searchRecords: List<WebSearchEvidenceRecord>,
    ): WebEvidencePortabilitySnapshot {
        val portableFetch =
            fetchRecords.map { record ->
                PortableWebFetchRecord(
                    requestedUrl = record.requestedUrl,
                    finalUrl = record.finalUrl,
                    method = record.method.name,
                    statusCode = record.statusCode,
                    mediaType = record.mediaType,
                    redirectCount = record.redirectCount,
                    acquiredAtEpochMillis = record.acquiredAtEpochMillis,
                    contentSha256Hex = record.contentSha256Hex,
                    contentBytes = record.contentBytes,
                )
            }.sortedWith(
                compareBy<PortableWebFetchRecord>(
                    { it.acquiredAtEpochMillis },
                    { it.requestedUrl },
                    { it.finalUrl },
                    { it.method },
                    { it.contentSha256Hex ?: "" },
                )
            )
        val portableSearch =
            searchRecords.map { record ->
                PortableWebSearchRecord(
                    query = record.query,
                    maxResults = record.maxResults,
                    providerId = record.providerId,
                    acquiredAtEpochMillis = record.acquiredAtEpochMillis,
                    hits =
                        record.hits.map { hit ->
                            PortableWebSearchHit(
                                title = hit.title,
                                url = hit.url,
                                snippet = hit.snippet,
                                score = hit.score,
                            )
                        },
                )
            }.sortedWith(
                compareBy<PortableWebSearchRecord>(
                    { it.acquiredAtEpochMillis },
                    { it.query },
                    { it.providerId },
                )
            )
        require(portableFetch.size <= WebEvidencePortabilitySnapshot.MAX_RECORDS)
        require(portableSearch.size <= WebEvidencePortabilitySnapshot.MAX_RECORDS)

        val hash =
            CanonicalHashBuilder
                .domain("faro-portability-web-v1")
                .string("schemaVersion", schemaVersion.toString())
                .string("fetchCount", portableFetch.size.toString())
                .string("searchCount", portableSearch.size.toString())

        portableFetch.forEachIndexed { index, record ->
            val prefix = "fetch[$index]."
            hash.string(prefix + "requestedUrl", record.requestedUrl)
                .string(prefix + "finalUrl", record.finalUrl)
                .string(prefix + "method", record.method)
                .string(prefix + "statusCode", record.statusCode.toString())
                .string(prefix + "mediaType.present", (record.mediaType != null).toString())
                .string(prefix + "mediaType", record.mediaType ?: "")
                .string(prefix + "redirectCount", record.redirectCount.toString())
                .string(prefix + "acquired", record.acquiredAtEpochMillis.toString())
                .string(prefix + "contentSha.present", (record.contentSha256Hex != null).toString())
                .string(prefix + "contentSha", record.contentSha256Hex ?: "")
                .string(prefix + "contentBytes.present", (record.contentBytes != null).toString())
                .string(prefix + "contentBytes", record.contentBytes?.toString() ?: "")
        }
        portableSearch.forEachIndexed { index, record ->
            val prefix = "search[$index]."
            hash.string(prefix + "query", record.query)
                .string(prefix + "maxResults", record.maxResults.toString())
                .string(prefix + "providerId", record.providerId)
                .string(prefix + "acquired", record.acquiredAtEpochMillis.toString())
                .string(prefix + "hitCount", record.hits.size.toString())
            record.hits.forEachIndexed { hitIndex, hit ->
                val hitPrefix = prefix + "hit[$hitIndex]."
                hash.string(hitPrefix + "title", hit.title)
                    .string(hitPrefix + "url", hit.url)
                    .string(hitPrefix + "snippet", hit.snippet)
                    .string(hitPrefix + "score.present", (hit.score != null).toString())
                    .string(hitPrefix + "score", hit.score?.toString() ?: "")
            }
        }
        return WebEvidencePortabilitySnapshot(
            schemaVersion = schemaVersion,
            sourceWatermark = hash.sha256Hex(),
            fetchRecords = portableFetch,
            searchRecords = portableSearch,
        )
    }
}
