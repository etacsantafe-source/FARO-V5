package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.owner.reminders.api.*
import java.time.Instant

/**
 * W03-D1 read-only SOURCE candidates for Auxiliary Avisos.
 * This is NOT durable Notice routing, POST_NOTIFICATIONS, or OS delivery.
 * Auxiliary owns route/seen/dismiss under a separate owner policy in W03-D2.
 */
internal object S4W03ReminderLocalNoticeProjection {
    fun candidates(
        source: ReminderSnapshot,
        fromEpochMillis: Long,
        untilEpochMillis: Long,
        maxCandidates: Int = 64,
        linkedMatterVisible: (String) -> Boolean = { false },
    ): List<NoticeCandidate> {
        require(fromEpochMillis >= 0L && untilEpochMillis > fromEpochMillis)
        require(maxCandidates in 1..128)
        val seen = source.occurrences.associateBy { it.id }
        val result = ArrayList<NoticeCandidate>()
        for (rule in source.rules.sortedBy { it.id.value }) {
            val linkId = rule.linkedMatterId
            if (!rule.enabled ||
                (linkId != null && !linkedMatterVisible(linkId))
            ) continue
            val due = ReminderCivilExpansion.expand(
                rule, fromEpochMillis, untilEpochMillis, offset = 0, limit = 128,
            )
            for (candidate in due) {
                val history = seen[candidate.id]
                if (history != null &&
                    (history.status == ReminderOccurrenceStatus.ACKNOWLEDGED ||
                        history.status == ReminderOccurrenceStatus.CANCELLED)
                ) continue
                result.add(NoticeCandidate(
                    noticeKey = NoticeKey.of("reminder:" + candidate.id.value),
                    sourceRef = SourceRef.of("reminder:" + rule.id.value),
                    occurrenceRef = OccurrenceRef.of(candidate.id.value),
                    materiality = NoticeMateriality.NOTICE,
                    sensitivity = if (linkId == null)
                        NoticeSensitivity.NORMAL else NoticeSensitivity.SENSITIVE,
                    summary = NoticeSummary.of(rule.title),
                    destinationRef = DestinationRef.of("faro:avisos.local"),
                ))
                if (result.size >= maxCandidates) return result
            }
        }
        return result
    }
}
