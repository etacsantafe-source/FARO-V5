package ar.com.faro.v5.delivery.api

import ar.com.faro.v5.kernel.contracts.EffectId

private val OPAQUE_REF = Regex("[A-Za-z0-9][A-Za-z0-9._:/-]{0,191}")

@JvmInline
value class NoticeId private constructor(val value: String) {
    companion object {
        fun of(raw: String): NoticeId = NoticeId(requireOpaque("noticeId", raw))
    }
}

@JvmInline
value class NoticeKey private constructor(val value: String) {
    companion object {
        fun of(raw: String): NoticeKey = NoticeKey(requireOpaque("noticeKey", raw))
    }
}

@JvmInline
value class SourceRef private constructor(val value: String) {
    companion object {
        fun of(raw: String): SourceRef = SourceRef(requireOpaque("sourceRef", raw))
    }
}

@JvmInline
value class OccurrenceRef private constructor(val value: String) {
    companion object {
        fun of(raw: String): OccurrenceRef = OccurrenceRef(requireOpaque("occurrenceRef", raw))
    }
}

@JvmInline
value class DestinationRef private constructor(val value: String) {
    companion object {
        fun of(raw: String): DestinationRef = DestinationRef(requireOpaque("destinationRef", raw))
    }
}

@JvmInline
value class DeliveryIntentId private constructor(val value: String) {
    companion object {
        fun of(raw: String): DeliveryIntentId =
            DeliveryIntentId(requireOpaque("deliveryIntentId", raw))
    }
}

@JvmInline
value class NoticeSummary private constructor(val value: String) {
    companion object {
        const val MAX_LENGTH: Int = 512

        fun of(raw: String): NoticeSummary {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) { "notice summary must not be blank" }
            require(normalized.length <= MAX_LENGTH) {
                "notice summary must be at most $MAX_LENGTH characters"
            }
            return NoticeSummary(normalized)
        }
    }
}

enum class NoticeMateriality {
    SILENT,
    NOTICE,
    NEEDS_ATTENTION,
}

enum class NoticeSensitivity {
    NORMAL,
    SENSITIVE,
}

enum class NoticeRelevance {
    OPEN,
    RESOLVED,
}

enum class NoticeResolutionReason {
    SOURCE_RESOLVED,
    EXPIRED,
    REPLACED,
}

enum class NoticeAttention {
    UNSEEN,
    SEEN,
    DISMISSED,
}

enum class DeliverySink {
    ANDROID_NOTIFICATION,
}

enum class DeliveryDesiredAction {
    UPSERT,
    CANCEL,
}

enum class DeliveryEffectState {
    PLANNED,
    SUPPRESSED_POLICY,
    BLOCKED_PERMISSION,
    POSTED_TO_OS,
    EFFECT_UNKNOWN,
    FAILED,
    CANCELLED,
}

data class NoticeCandidate(
    val noticeKey: NoticeKey,
    val sourceRef: SourceRef,
    val occurrenceRef: OccurrenceRef,
    val materiality: NoticeMateriality,
    val sensitivity: NoticeSensitivity,
    val summary: NoticeSummary,
    val destinationRef: DestinationRef,
)

data class NoticeSnapshot(
    val noticeId: NoticeId,
    val noticeKey: NoticeKey,
    val sourceRef: SourceRef,
    val occurrenceRef: OccurrenceRef,
    val materiality: NoticeMateriality,
    val sensitivity: NoticeSensitivity,
    val summary: NoticeSummary,
    val destinationRef: DestinationRef,
    val relevance: NoticeRelevance,
    val attention: NoticeAttention,
    val revision: Long,
    val resolutionReason: NoticeResolutionReason? = null,
) {
    init {
        require(materiality != NoticeMateriality.SILENT) {
            "a durable Notice cannot have SILENT materiality"
        }
        require(revision > 0L) { "notice revision must be positive" }
        require(
            (relevance == NoticeRelevance.OPEN && resolutionReason == null) ||
                (relevance == NoticeRelevance.RESOLVED && resolutionReason != null)
        ) {
            "Notice relevance and resolutionReason must agree"
        }
    }
}

data class DeliveryIntent(
    val deliveryIntentId: DeliveryIntentId,
    val effectId: EffectId,
    val noticeId: NoticeId,
    val sink: DeliverySink,
    val desiredAction: DeliveryDesiredAction,
    val state: DeliveryEffectState,
)

data class NoticeMaterializationIds(
    val noticeId: NoticeId,
    val deliveryIntentId: DeliveryIntentId,
    val effectId: EffectId,
)

sealed interface NoticeResolveResult {
    data class Resolved(
        val notice: NoticeSnapshot,
        val deliveryIntent: DeliveryIntent,
    ) : NoticeResolveResult

    data class AlreadyResolved(
        val notice: NoticeSnapshot,
        val deliveryIntent: DeliveryIntent,
    ) : NoticeResolveResult

    data object NotFound : NoticeResolveResult
}

sealed interface NoticeRouteResult {
    data object SuppressedSilent : NoticeRouteResult

    data class Materialized(
        val notice: NoticeSnapshot,
        val deliveryIntent: DeliveryIntent,
        val created: Boolean,
    ) : NoticeRouteResult
}

private fun requireOpaque(label: String, raw: String): String {
    val normalized = raw.trim()
    require(OPAQUE_REF.matches(normalized)) {
        "$label must be an opaque bounded identifier"
    }
    return normalized
}
