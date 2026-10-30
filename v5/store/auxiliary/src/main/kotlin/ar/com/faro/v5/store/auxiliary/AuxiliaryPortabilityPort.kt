package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.delivery.api.NoticeSnapshot
import ar.com.faro.v5.kernel.contracts.CanonicalHashBuilder

data class PortableNoticeRecord(
    val noticeKey: String,
    val occurrenceRef: String,
    val materiality: String,
    val sensitivity: String,
    val summary: String,
    val relevance: String,
    val attention: String,
    val revision: Long,
    val resolutionReason: String?,
)

data class AuxiliaryPortabilitySnapshot(
    val schemaVersion: Int,
    val sourceWatermark: String,
    val notices: List<PortableNoticeRecord>,
) {
    init {
        require(schemaVersion >= 0)
        require(sourceWatermark.matches(Regex("[0-9a-f]{64}")))
        require(notices.size <= MAX_NOTICES)
    }

    companion object {
        const val MAX_NOTICES = 100_000
    }
}

fun interface AuxiliaryPortabilityPort {
    fun snapshot(): AuxiliaryPortabilitySnapshot
}

internal object AuxiliaryPortabilityProjection {
    fun fromStored(
        schemaVersion: Int,
        notices: List<NoticeSnapshot>,
    ): AuxiliaryPortabilitySnapshot {
        val portable =
            notices.map { notice ->
                PortableNoticeRecord(
                    noticeKey = notice.noticeKey.value,
                    occurrenceRef = notice.occurrenceRef.value,
                    materiality = notice.materiality.name,
                    sensitivity = notice.sensitivity.name,
                    summary = notice.summary.value,
                    relevance = notice.relevance.name,
                    attention = notice.attention.name,
                    revision = notice.revision,
                    resolutionReason = notice.resolutionReason?.name,
                )
            }.sortedWith(compareBy<PortableNoticeRecord>({ it.noticeKey }, { it.occurrenceRef }))
        require(portable.size <= AuxiliaryPortabilitySnapshot.MAX_NOTICES)

        val hash =
            CanonicalHashBuilder
                .domain("faro-portability-notices-v1")
                .string("schemaVersion", schemaVersion.toString())
                .string("count", portable.size.toString())
        portable.forEachIndexed { index, notice ->
            val prefix = "notice[$index]."
            hash.string(prefix + "noticeKey", notice.noticeKey)
                .string(prefix + "occurrenceRef", notice.occurrenceRef)
                .string(prefix + "materiality", notice.materiality)
                .string(prefix + "sensitivity", notice.sensitivity)
                .string(prefix + "summary", notice.summary)
                .string(prefix + "relevance", notice.relevance)
                .string(prefix + "attention", notice.attention)
                .string(prefix + "revision", notice.revision.toString())
                .string(prefix + "resolution.present", (notice.resolutionReason != null).toString())
                .string(prefix + "resolution", notice.resolutionReason ?: "")
        }

        return AuxiliaryPortabilitySnapshot(
            schemaVersion = schemaVersion,
            sourceWatermark = hash.sha256Hex(),
            notices = portable,
        )
    }
}
