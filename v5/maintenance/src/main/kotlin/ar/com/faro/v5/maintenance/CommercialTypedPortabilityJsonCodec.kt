package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.PortableCommercialTypedObservation
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Local-only, bounded JSON for the STAGED Evidence2 commercial section.
 * This is NOT PORTABLE_BUNDLE_V1, NOT a backup, and cannot create/export a file.
 * H2B owns any future preview/approval/egress.
 */
object CommercialTypedPortabilityJsonCodec {
    const val FORMAT_PROFILE = "PORTABLE_COMMERCIAL_TYPED_SECTION_V2_LOCAL"
    const val MAX_JSON_BYTES = 8 * 1024 * 1024
    private const val NOTICE = "NOT A FARO BACKUP / NOT RESTORABLE"

    fun encode(snapshot: CommercialEvidenceStagedTypedPortabilitySnapshot): ByteArray {
        validate(snapshot)
        val json = BoundedJson()
        json.raw("{\"formatProfile\":")
        json.string(FORMAT_PROFILE)
        json.raw(",\"schemaVersion\":2,\"notice\":")
        json.string(NOTICE)
        json.raw(",\"sourceWatermark\":")
        json.string(snapshot.sourceWatermark)
        json.raw(",\"records\":[")
        snapshot.records.forEachIndexed { index, item ->
            if (index > 0) json.raw(",")
            val r = item.observation
            json.raw("{\"productKey\":")
            json.string(r.productKey)
            json.raw(",\"merchant\":")
            json.string(r.merchant)
            json.raw(",\"currencyCode\":")
            json.string(r.currencyCode)
            json.raw(",\"amountMinor\":")
            json.number(r.amountMinor)
            json.raw(",\"sourceKind\":")
            json.string(r.sourceKind)
            json.raw(",\"sourceRef\":")
            json.string(r.sourceRef)
            json.raw(",\"observedAtEpochMillis\":")
            json.nullableNumber(r.observedAtEpochMillis)
            json.raw(",\"acquiredAtEpochMillis\":")
            json.number(r.acquiredAtEpochMillis)
            json.raw(",\"terms\":")
            val terms = item.terms
            if (terms == null) json.raw("null") else writeTerms(json, terms)
            json.raw("}")
        }
        json.raw("]}")
        return json.bytes()
    }

    private fun writeTerms(json: BoundedJson, t: CommercialEvidenceTypedTerms) {
        json.raw("{\"localityKind\":")
        json.string(t.localityKind.name)
        json.raw(",\"localityCode\":")
        json.nullableString(t.localityCode)
        json.raw(",\"issuedAtEpochMillis\":")
        json.nullableNumber(t.issuedAtEpochMillis)
        json.raw(",\"validFromEpochMillis\":")
        json.nullableNumber(t.validFromEpochMillis)
        json.raw(",\"validUntilEpochMillis\":")
        json.nullableNumber(t.validUntilEpochMillis)
        json.raw(",\"presentationQuantityThousandths\":")
        json.nullableNumber(t.presentationQuantityThousandths)
        json.raw(",\"presentationUnit\":")
        json.nullableString(t.presentationUnit?.name)
        json.raw(",\"packCount\":")
        json.nullableNumber(t.packCount?.toLong())
        json.raw(",\"paymentMethod\":")
        json.string(t.paymentMethod.name)
        json.raw(",\"eligibility\":")
        json.string(t.eligibility.name)
        json.raw(",\"conditions\":")
        json.nullableString(t.conditions)
        json.raw(",\"promotionKind\":")
        json.string(t.promotionKind.name)
        json.raw(",\"promotionValue\":")
        json.nullableNumber(t.promotionValue)
        json.raw(",\"promotionSourceRef\":")
        json.nullableString(t.promotionSourceRef)
        json.raw("}")
    }

    private val ORDER = compareBy<PortableCommercialTypedObservation>(
        { it.observation.productKey },
        { it.observation.merchant },
        { it.observation.currencyCode },
        { it.observation.observedAtEpochMillis ?: Long.MAX_VALUE },
        { it.observation.acquiredAtEpochMillis },
        { it.observation.amountMinor },
        { it.observation.sourceKind },
        { it.observation.sourceRef },
        { it.terms != null },
        { it.terms?.fingerprint() ?: "" },
    )

    private fun validate(s: CommercialEvidenceStagedTypedPortabilitySnapshot) {
        require(s.schemaVersion == 2)
        require(s.records == s.records.sortedWith(ORDER)) { "commercial records are not canonical" }
        val hash = CanonicalHashBuilder.domain("faro-portability-commercial-v2")
            .string("schemaVersion", "2")
            .string("count", s.records.size.toString())
        s.records.forEachIndexed { index, record ->
            val r = record.observation
            val p = "record[$index]."
            hash.string(p + "productKey", r.productKey)
                .string(p + "merchant", r.merchant)
                .string(p + "currencyCode", r.currencyCode)
                .string(p + "amountMinor", r.amountMinor.toString())
                .string(p + "sourceKind", r.sourceKind)
                .string(p + "sourceRef", r.sourceRef)
                .string(p + "observed.present", (r.observedAtEpochMillis != null).toString())
                .string(p + "observed", r.observedAtEpochMillis?.toString() ?: "")
                .string(p + "acquired", r.acquiredAtEpochMillis.toString())
                .string(p + "terms.present", (record.terms != null).toString())
                .string(p + "terms.fingerprint", record.terms?.fingerprint() ?: "")
        }
        require(hash.sha256Hex() == s.sourceWatermark) { "commercial source watermark mismatch" }
    }

    private class BoundedJson {
        private val buffer = ByteArrayOutputStream()
        fun bytes(): ByteArray = buffer.toByteArray()
        private fun write(bytes: ByteArray) {
            check(bytes.size <= MAX_JSON_BYTES - buffer.size()) { "commercial JSON size bound exceeded" }
            buffer.write(bytes)
        }
        fun raw(value: String) {
            require(value.all { it.code < 128 })
            write(value.toByteArray(StandardCharsets.US_ASCII))
        }
        fun number(n: Long) = raw(n.toString())
        fun nullableNumber(n: Long?) { if (n == null) raw("null") else number(n) }
        fun nullableString(s: String?) { if (s == null) raw("null") else string(s) }

        fun string(value: String) {
            raw("\"")
            var start = 0
            var i = 0
            while (i < value.length) {
                val ch = value[i]
                val escaped = when (ch) {
                    '"' -> "\\\""
                    '\\' -> "\\\\"
                    '\b' -> "\\b"
                    '\u000C' -> "\\f"
                    '\n' -> "\\n"
                    '\r' -> "\\r"
                    '\t' -> "\\t"
                    else -> if (ch.code < 0x20) {
                        "\\u" + ch.code.toString(16).padStart(4, '0')
                    } else null
                }
                if (escaped != null) {
                    segment(value, start, i)
                    raw(escaped)
                    i++
                    start = i
                } else if (ch.isHighSurrogate()) {
                    require(i + 1 < value.length && value[i + 1].isLowSurrogate()) {
                        "unpaired high surrogate"
                    }
                    i += 2
                } else {
                    require(!ch.isLowSurrogate()) { "unpaired low surrogate" }
                    i++
                }
            }
            segment(value, start, value.length)
            raw("\"")
        }
        private fun segment(value: String, start: Int, end: Int) {
            if (start == end) return
            check(end - start <= MAX_JSON_BYTES - buffer.size()) { "commercial JSON size bound exceeded" }
            write(value.substring(start, end).toByteArray(StandardCharsets.UTF_8))
        }
    }
}
