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
internal object FaroV5InChatSearchPolicy {
    const val MAX_QUERY_CHARS = 200

    fun matchingMessageIds(
        timeline: ConversationTimelineSnapshot,
        query: String,
    ): List<ConversationMessageId> {
        if (query.length > MAX_QUERY_CHARS) return emptyList()
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }.distinct()
        if (words.isEmpty()) return emptyList()
        return timeline.messages.asSequence()
            .filter { message ->
                (message.role == ConversationMessageRole.USER &&
                    message.state == ConversationMessageState.ACCEPTED) ||
                (message.role == ConversationMessageRole.FARO &&
                    message.state == ConversationMessageState.COMPLETED)
            }
            .filter { message ->
                val body = normalize(message.text)
                words.all { word -> body.contains(word) }
            }
            .map { it.id }
            .toList()
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
