package ar.com.faro.v5.app

import ar.com.faro.v5.control.search.SearchAuthorityStamp
import ar.com.faro.v5.control.search.SearchCanonicalDocument
import ar.com.faro.v5.control.search.SearchCanonicalReadPort
import ar.com.faro.v5.control.search.SearchCanonicalSnapshot
import ar.com.faro.v5.control.search.SearchProjectionKey
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.security.MessageDigest

/**
 * S3.A2 genuine Workspace-backed canonical search source; V2 is NOT enabled
 * by constructing this adapter. Callers must supply real recovery admission,
 * active-generation authority and ACL epoch. No ACL source => unavailable.
 *
 * Only authorized Workspace metadata is projected, never drafts, hidden
 * messages or index copies. No search read mutates the canonical owner.
 */
class FaroV5WorkspaceSearchReadSource(
    private val selectedGenerationId: String,
    private val store: ConversationWorkspaceStore,
    private val currentGenerationId: () -> String?,
    private val aclEpoch: () -> String?,
    private val admitted: () -> Boolean,
) : SearchCanonicalReadPort {
    init {
        require(selectedGenerationId.isNotBlank()) { "selected generation required" }
    }

    override fun authorityStamp(): SearchAuthorityStamp? {
        if (!admitted() || currentGenerationId() != selectedGenerationId) return null
        val authorizedAcl = aclEpoch()?.takeIf { it.isNotBlank() } ?: return null
        val revision = store.snapshot().revision
        if (!admitted() || currentGenerationId() != selectedGenerationId ||
            aclEpoch() != authorizedAcl) return null
        return SearchAuthorityStamp(selectedGenerationId, authorizedAcl, revision)
    }

    override fun readAuthorized(
        namespace: String,
        maxDocuments: Int,
    ): SearchCanonicalSnapshot {
        require(namespace == NAMESPACE) { "Workspace search namespace mismatch" }
        require(maxDocuments in 1..500) { "invalid bounded Workspace scan" }
        val before = checkNotNull(authorityStamp()) {
            "Workspace read authority unavailable"
        }
        val state = store.snapshot()
        check(state.revision == before.ownerRevision) {
            "Workspace changed during authorized read"
        }
        // The true owner decides what exists. Archived is not deleted and
        // remains searchable; navigation must revalidate at the point of open.
        val ordered = state.workspace.conversations.sortedWith(
            compareByDescending<ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry> {
                it.lastActivityEpochMillis
            }.thenBy { it.id.value }
        )
        // Fixed on-device budget; a partial content scan is explicitly PARTIAL,
        // never a definitive 'no results'. No disposable index supplies text.
        var remainingTextChars = 64_000
        var allContentCovered = true
        val documents = ordered.take(maxDocuments).map { conversation ->
            val cap = minOf(4_096, remainingTextChars)
            val body = if (cap > 0)
                store.readSearchableConversationText(conversation.id, cap) else null
            if (body == null || !body.complete) allContentCovered = false
            if (body != null) remainingTextChars -= body.text.length
            SearchCanonicalDocument(
                SearchProjectionKey(NAMESPACE, opaqueKey(conversation.id)),
                SearchCanonicalDocument.Kind.CONVERSATION,
                conversation.title,
                body?.text.orEmpty(),
                sha256(
                    selectedGenerationId + ":" + state.revision + ":" +
                        conversation.id.value + ":" + conversation.lastActivityEpochMillis
                ),
                conversation.lastActivityEpochMillis,
            )
        }
        check(before == authorityStamp()) {
            "Workspace generation, ACL or revision changed during read"
        }
        return SearchCanonicalSnapshot(
            before, documents, ordered.size <= maxDocuments && allContentCovered,
        )
    }

    /**
     * A search locator is NEVER open authorization. Resolve it through fresh
     * canonical Workspace data and a fresh ACL/generation check on navigation.
     */
    fun resolveAuthorizedConversation(key: SearchProjectionKey): ConversationId? {
        if (key.namespace != NAMESPACE) return null
        val before = authorityStamp() ?: return null
        val current = store.snapshot()
        if (current.revision != before.ownerRevision) return null
        val resolved = current.workspace.conversations.firstOrNull { conversation ->
            opaqueKey(conversation.id) == key.documentId
        }?.id
        return if (before == authorityStamp()) resolved else null
    }

    private fun opaqueKey(id: ConversationId): String = "c:" + sha256(id.value)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val NAMESPACE: String = "workspace.conversations"
    }
}
