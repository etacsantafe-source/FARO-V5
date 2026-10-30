package ar.com.faro.v5.maintenance

import ar.com.faro.v5.evidence.PortableCommercialObservation
import ar.com.faro.v5.evidence.PortableWebFetchRecord
import ar.com.faro.v5.evidence.PortableWebSearchHit
import ar.com.faro.v5.evidence.PortableWebSearchRecord
import ar.com.faro.v5.owner.finance.api.FinanceAccountSnapshot
import ar.com.faro.v5.owner.finance.api.FinanceEntrySnapshot
import ar.com.faro.v5.owner.profile.api.ProfileSnapshot
import ar.com.faro.v5.store.auxiliary.PortableNoticeRecord
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object PortableBundleJsonWriter {
    private const val NOTICE = "NOT A FARO BACKUP / NOT RESTORABLE"

    fun write(
        output: OutputStream,
        preview: PortabilityPreview,
        source: PortabilitySourceSnapshot,
        generatedAtEpochMillis: Long,
    ) {
        require(preview.scope == source.scope)
        require(generatedAtEpochMillis >= 0L)
        val json = Utf8JsonSink(output)
        json.raw("{\"sections\":{")

        val sectionDigests = mutableListOf<SectionDigest>()
        preview.sections.forEachIndexed { index, sectionPreview ->
            if (index > 0) json.raw(",")
            json.string(sectionPreview.section.wireName)
            json.raw(":")
            val sectionOutput = DigestingPassThroughOutputStream(output)
            val sectionJson = Utf8JsonSink(sectionOutput)
            when (sectionPreview.section) {
                PortabilitySection.PROFILE ->
                    writeProfile(sectionJson, checkNotNull(source.profile))
                PortabilitySection.FINANCE ->
                    writeFinance(sectionJson, checkNotNull(source.finance))
                PortabilitySection.COMMERCIAL_EVIDENCE ->
                    writeCommercial(
                        sectionJson,
                        checkNotNull(source.commercialEvidence).records,
                    )
                PortabilitySection.WEB_EVIDENCE -> {
                    val web = checkNotNull(source.webEvidence)
                    writeWeb(sectionJson, web.fetchRecords, web.searchRecords)
                }
                PortabilitySection.NOTICES ->
                    writeNotices(sectionJson, checkNotNull(source.notices).notices)
            }
            sectionDigests +=
                SectionDigest(
                    section = sectionPreview.section,
                    byteCount = sectionOutput.byteCount,
                    sha256Hex = sectionOutput.sha256Hex(),
                )
        }

        json.raw("},\"manifest\":")
        writeManifest(
            json = json,
            preview = preview,
            sectionDigests = sectionDigests,
            generatedAtEpochMillis = generatedAtEpochMillis,
        )
        json.raw("}")
    }

    private fun writeManifest(
        json: Utf8JsonSink,
        preview: PortabilityPreview,
        sectionDigests: List<SectionDigest>,
        generatedAtEpochMillis: Long,
    ) {
        json.raw("{\"formatProfile\":")
        json.string(preview.formatProfile)
        json.raw(",\"exportSchemaVersion\":")
        json.number(preview.exportSchemaVersion.toLong())
        json.raw(",\"generatedAtEpochMillis\":")
        json.number(generatedAtEpochMillis)
        json.raw(",\"notice\":")
        json.string(NOTICE)
        json.raw(",\"redactionPolicyVersion\":")
        json.string(preview.redactionPolicyVersion)
        json.raw(",\"exportPlanFingerprint\":")
        json.string(preview.exportPlanFingerprint.value)

        json.raw(",\"scope\":[")
        preview.scope.sections.forEachIndexed { index, section ->
            if (index > 0) json.raw(",")
            json.string(section.wireName)
        }
        json.raw("]")

        json.raw(",\"exclusions\":[")
        preview.exclusions.forEachIndexed { index, exclusion ->
            if (index > 0) json.raw(",")
            json.string(exclusion)
        }
        json.raw("]")

        json.raw(",\"warnings\":[")
        preview.warnings.forEachIndexed { index, warning ->
            if (index > 0) json.raw(",")
            json.string(warning.name)
        }
        json.raw("]")

        json.raw(",\"sectionMetadata\":[")
        preview.sections.forEachIndexed { index, section ->
            if (index > 0) json.raw(",")
            val digest = sectionDigests.single { it.section == section.section }
            json.raw("{\"id\":")
            json.string(section.section.wireName)
            json.raw(",\"itemCount\":")
            json.number(section.itemCount)
            json.raw(",\"sourceWatermark\":")
            json.string(section.sourceWatermark)
            json.raw(",\"sensitivity\":")
            json.string(section.sensitivity.name)
            json.raw(",\"jsonBytes\":")
            json.number(digest.byteCount)
            json.raw(",\"jsonSha256\":")
            json.string(digest.sha256Hex)
            json.raw("}")
        }
        json.raw("]}")
    }

    private fun writeProfile(
        json: Utf8JsonSink,
        profile: ProfileSnapshot,
    ) {
        json.raw("{\"displayName\":")
        json.nullableString(profile.displayName?.value)
        json.raw(",\"provenance\":")
        json.nullableString(profile.provenance?.name)
        json.raw(",\"revision\":")
        json.number(profile.revision)
        json.raw("}")
    }

    private fun writeFinance(
        json: Utf8JsonSink,
        finance: ar.com.faro.v5.owner.finance.api.FinanceSnapshot,
    ) {
        json.raw("{\"revision\":")
        json.number(finance.revision)
        json.raw(",\"accounts\":[")
        finance.accounts.forEachIndexed { index, account ->
            if (index > 0) json.raw(",")
            writeFinanceAccount(json, account)
        }
        json.raw("],\"entries\":[")
        finance.entries.forEachIndexed { index, entry ->
            if (index > 0) json.raw(",")
            writeFinanceEntry(json, entry)
        }
        json.raw("]}")
    }

    private fun writeFinanceAccount(
        json: Utf8JsonSink,
        account: FinanceAccountSnapshot,
    ) {
        json.raw("{\"accountId\":")
        json.string(account.accountId.value)
        json.raw(",\"displayName\":")
        json.string(account.displayName.value)
        json.raw(",\"currency\":")
        json.string(account.currency.value)
        json.raw(",\"nature\":")
        json.string(account.nature.name)
        json.raw(",\"openingBalanceMinor\":")
        json.number(account.openingBalanceMinor)
        json.raw("}")
    }

    private fun writeFinanceEntry(
        json: Utf8JsonSink,
        entry: FinanceEntrySnapshot,
    ) {
        json.raw("{\"entryId\":")
        json.string(entry.entryId.value)
        json.raw(",\"accountId\":")
        json.string(entry.accountId.value)
        json.raw(",\"amountMinor\":")
        json.number(entry.amountMinor)
        json.raw(",\"kind\":")
        json.string(entry.kind.name)
        json.raw(",\"currency\":")
        json.string(entry.currency.value)
        json.raw(",\"concept\":")
        json.string(entry.concept.value)
        json.raw(",\"category\":")
        json.string(entry.category.value)
        json.raw(",\"effectiveAtEpochMillis\":")
        json.number(entry.effectiveAtEpochMillis)
        json.raw(",\"recordedAtEpochMillis\":")
        json.number(entry.recordedAtEpochMillis)
        json.raw(",\"provenanceLabel\":")
        json.nullableString(entry.provenanceLabel)
        json.raw("}")
    }

    private fun writeCommercial(
        json: Utf8JsonSink,
        records: List<PortableCommercialObservation>,
    ) {
        json.raw("{\"records\":[")
        records.forEachIndexed { index, record ->
            if (index > 0) json.raw(",")
            json.raw("{\"productKey\":")
            json.string(record.productKey)
            json.raw(",\"merchant\":")
            json.string(record.merchant)
            json.raw(",\"currencyCode\":")
            json.string(record.currencyCode)
            json.raw(",\"amountMinor\":")
            json.number(record.amountMinor)
            json.raw(",\"sourceKind\":")
            json.string(record.sourceKind)
            json.raw(",\"sourceRef\":")
            json.string(record.sourceRef)
            json.raw(",\"observedAtEpochMillis\":")
            json.nullableNumber(record.observedAtEpochMillis)
            json.raw(",\"acquiredAtEpochMillis\":")
            json.number(record.acquiredAtEpochMillis)
            json.raw("}")
        }
        json.raw("]}")
    }

    private fun writeWeb(
        json: Utf8JsonSink,
        fetchRecords: List<PortableWebFetchRecord>,
        searchRecords: List<PortableWebSearchRecord>,
    ) {
        json.raw("{\"fetchRecords\":[")
        fetchRecords.forEachIndexed { index, record ->
            if (index > 0) json.raw(",")
            writeWebFetch(json, record)
        }
        json.raw("],\"searchRecords\":[")
        searchRecords.forEachIndexed { index, record ->
            if (index > 0) json.raw(",")
            writeWebSearch(json, record)
        }
        json.raw("]}")
    }

    private fun writeWebFetch(
        json: Utf8JsonSink,
        record: PortableWebFetchRecord,
    ) {
        json.raw("{\"requestedUrl\":")
        json.string(record.requestedUrl)
        json.raw(",\"finalUrl\":")
        json.string(record.finalUrl)
        json.raw(",\"method\":")
        json.string(record.method)
        json.raw(",\"statusCode\":")
        json.number(record.statusCode.toLong())
        json.raw(",\"mediaType\":")
        json.nullableString(record.mediaType)
        json.raw(",\"redirectCount\":")
        json.number(record.redirectCount.toLong())
        json.raw(",\"acquiredAtEpochMillis\":")
        json.number(record.acquiredAtEpochMillis)
        json.raw(",\"contentSha256\":")
        json.nullableString(record.contentSha256Hex)
        json.raw(",\"contentBytes\":")
        json.nullableNumber(record.contentBytes?.toLong())
        json.raw("}")
    }

    private fun writeWebSearch(
        json: Utf8JsonSink,
        record: PortableWebSearchRecord,
    ) {
        json.raw("{\"query\":")
        json.string(record.query)
        json.raw(",\"maxResults\":")
        json.number(record.maxResults.toLong())
        json.raw(",\"providerId\":")
        json.string(record.providerId)
        json.raw(",\"acquiredAtEpochMillis\":")
        json.number(record.acquiredAtEpochMillis)
        json.raw(",\"hits\":[")
        record.hits.forEachIndexed { index, hit ->
            if (index > 0) json.raw(",")
            writeWebHit(json, hit)
        }
        json.raw("]}")
    }

    private fun writeWebHit(
        json: Utf8JsonSink,
        hit: PortableWebSearchHit,
    ) {
        json.raw("{\"title\":")
        json.string(hit.title)
        json.raw(",\"url\":")
        json.string(hit.url)
        json.raw(",\"snippet\":")
        json.string(hit.snippet)
        json.raw(",\"score\":")
        if (hit.score == null) {
            json.raw("null")
        } else {
            json.raw(hit.score.toString())
        }
        json.raw("}")
    }

    private fun writeNotices(
        json: Utf8JsonSink,
        notices: List<PortableNoticeRecord>,
    ) {
        json.raw("{\"records\":[")
        notices.forEachIndexed { index, notice ->
            if (index > 0) json.raw(",")
            json.raw("{\"noticeKey\":")
            json.string(notice.noticeKey)
            json.raw(",\"occurrenceRef\":")
            json.string(notice.occurrenceRef)
            json.raw(",\"materiality\":")
            json.string(notice.materiality)
            json.raw(",\"sensitivity\":")
            json.string(notice.sensitivity)
            json.raw(",\"summary\":")
            json.string(notice.summary)
            json.raw(",\"relevance\":")
            json.string(notice.relevance)
            json.raw(",\"attention\":")
            json.string(notice.attention)
            json.raw(",\"revision\":")
            json.number(notice.revision)
            json.raw(",\"resolutionReason\":")
            json.nullableString(notice.resolutionReason)
            json.raw("}")
        }
        json.raw("]}")
    }

    private data class SectionDigest(
        val section: PortabilitySection,
        val byteCount: Long,
        val sha256Hex: String,
    )

    private class DigestingPassThroughOutputStream(
        private val delegate: OutputStream,
    ) : OutputStream() {
        private val digest = MessageDigest.getInstance("SHA-256")
        var byteCount: Long = 0L
            private set

        override fun write(value: Int) {
            delegate.write(value)
            digest.update(value.toByte())
            byteCount += 1L
        }

        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            delegate.write(bytes, offset, length)
            digest.update(bytes, offset, length)
            byteCount += length.toLong()
        }

        fun sha256Hex(): String =
            digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class Utf8JsonSink(
        private val output: OutputStream,
    ) {
        fun raw(value: String) {
            require(value.all { it.code < 0x80 }) { "raw JSON token must be ASCII" }
            output.write(value.toByteArray(StandardCharsets.US_ASCII))
        }

        fun number(value: Long) = raw(value.toString())

        fun nullableNumber(value: Long?) {
            if (value == null) raw("null") else number(value)
        }

        fun nullableString(value: String?) {
            if (value == null) raw("null") else string(value)
        }

        fun string(value: String) {
            output.write('"'.code)
            var index = 0
            while (index < value.length) {
                val ch = value[index]
                when (ch) {
                    '"' -> raw("\\\"")
                    '\\' -> raw("\\\\")
                    '\b' -> raw("\\b")
                    '\u000C' -> raw("\\f")
                    '\n' -> raw("\\n")
                    '\r' -> raw("\\r")
                    '\t' -> raw("\\t")
                    else -> {
                        when {
                            ch.code < 0x20 ->
                                raw("\\u" + ch.code.toString(16).padStart(4, '0'))
                            ch.isHighSurrogate() -> {
                                check(
                                    index + 1 < value.length &&
                                        value[index + 1].isLowSurrogate()
                                ) { "JSON string contains unpaired high surrogate" }
                                output.write(
                                    value.substring(index, index + 2)
                                        .toByteArray(StandardCharsets.UTF_8)
                                )
                                index += 1
                            }
                            ch.isLowSurrogate() ->
                                error("JSON string contains unpaired low surrogate")
                            else ->
                                output.write(ch.toString().toByteArray(StandardCharsets.UTF_8))
                        }
                    }
                }
                index += 1
            }
            output.write('"'.code)
        }
    }
}
