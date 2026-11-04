package ar.com.faro.v5.surface

import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationMessageRole
import ar.com.faro.v5.kernel.contracts.ConversationMessageState
import ar.com.faro.v5.kernel.contracts.ConversationTimelineSnapshot
import java.text.Normalizer
import java.util.Locale

/**
 * S3.A2 in-chat search over the SAME canonical, already-authorized timeline
 * snapshot as the visible Chat. Never reads a disposable index or a draft.
 * The caller owns live generation/readiness admission and refresh; a failed
 * canonical read must not render an old result as a new successful query.
 */
internal data class FaroV5InChatSearchResult(
    val messageIds: List<ConversationMessageId>,
    val complete: Boolean,
)

internal object FaroV5InChatSearchPolicy {
    const val MAX_QUERY_CHARS = 200
    private const val MAX_SCANNED_MESSAGES = 250
    private const val MAX_SCANNED_CHARS = 64_000

    /** Legacy test helper; UI MUST use search() to preserve coverage status. */
    fun matchingMessageIds(timeline: ConversationTimelineSnapshot, query: String) =
        search(timeline, query).messageIds

    fun search(timeline: ConversationTimelineSnapshot, query: String): FaroV5InChatSearchResult {
        if (query.length > MAX_QUERY_CHARS) return FaroV5InChatSearchResult(emptyList(), false)
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }.distinct()
        if (words.isEmpty()) return FaroV5InChatSearchResult(emptyList(), true)

        val messages = timeline.messages
        val first = (messages.size - MAX_SCANNED_MESSAGES).coerceAtLeast(0)
        var complete = first == 0
        var charsScanned = 0
        val foundNewestFirst = mutableListOf<ConversationMessageId>()

        // Bound work on the UI thread: process at most the latest 250 messages
        // and 64k canonical characters. Do not claim a definitive absence if
        // older or oversized messages could hold further matches.
        for (i in messages.lastIndex downTo first) {
            val message = messages[i]
            val eligible = (message.role == ConversationMessageRole.USER &&
                message.state == ConversationMessageState.ACCEPTED) ||
                (message.role == ConversationMessageRole.FARO &&
                    message.state == ConversationMessageState.COMPLETED)
            if (!eligible) continue
            if (charsScanned + message.text.length > MAX_SCANNED_CHARS) {
                complete = false
                break
            }
            charsScanned += message.text.length
            val body = normalize(message.text)
            if (words.all { word -> body.contains(word) }) {
                foundNewestFirst += message.id
            }
        }
        return FaroV5InChatSearchResult(foundNewestFirst.asReversed(), complete)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
