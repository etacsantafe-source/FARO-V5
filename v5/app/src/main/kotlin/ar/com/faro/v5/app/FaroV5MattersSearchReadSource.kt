package ar.com.faro.v5.app

import ar.com.faro.v5.control.search.CanonicalSearchCoordinator
import ar.com.faro.v5.control.search.SearchAuthorityStamp
import ar.com.faro.v5.control.search.SearchCanonicalDocument
import ar.com.faro.v5.control.search.SearchCanonicalReadPort
import ar.com.faro.v5.control.search.SearchCanonicalSnapshot
import ar.com.faro.v5.control.search.SearchProjectionKey
import ar.com.faro.v5.owner.matters.api.MatterId
import ar.com.faro.v5.owner.matters.api.MatterRecord
import ar.com.faro.v5.owner.matters.spi.MatterPersistencePort
import java.security.MessageDigest

/**
 * Genuine Reality-backed Matters source; never mistake attention projections
 * for the persistent Matter owner or treat index locators as read permissions.
 */
class FaroV5MattersSearchReadSource(
    private val selectedGenerationId: String,
    private val matters: MatterPersistencePort,
    private val currentGenerationId: () -> String?,
    private val aclEpoch: () -> String?,
    private val admitted: () -> Boolean,
) : SearchCanonicalReadPort {
    init { require(selectedGenerationId.isNotBlank()) }

    override fun authorityStamp(): SearchAuthorityStamp? {
        if (!admitted() || currentGenerationId() != selectedGenerationId) return null
        val epoch = aclEpoch()?.takeIf { it.isNotBlank() } ?: return null
        val revision = matters.read().revision
        if (!admitted() || currentGenerationId() != selectedGenerationId ||
            aclEpoch() != epoch) return null
        return SearchAuthorityStamp(selectedGenerationId, epoch, revision)
    }

    override fun readAuthorized(namespace: String, maxDocuments: Int): SearchCanonicalSnapshot {
        require(namespace == NAMESPACE) { "Matters namespace mismatch" }
        require(maxDocuments in 1..CanonicalSearchCoordinator.MAX_CANONICAL_DOCUMENTS)
        val before = checkNotNull(authorityStamp()) { "Matters search authority unavailable" }
        val snapshot = matters.read()
        check(snapshot.revision == before.ownerRevision) { "Matters revision changed" }
        val ordered = snapshot.matters.sortedWith(
            compareByDescending<MatterRecord> { it.updatedAtEpochMillis }.thenBy { it.id.value },
        )
        val documents = ordered.take(maxDocuments).map { record ->
            SearchCanonicalDocument(
                SearchProjectionKey(NAMESPACE, "m:" + hash(record.id.value)),
                SearchCanonicalDocument.Kind.MATTER,
                record.title.value,
                record.detail.value + " " + record.status.name,
                hash(selectedGenerationId + ":" + snapshot.revision + ":" +
                    record.id.value + ":" + record.updatedAtEpochMillis),
                record.updatedAtEpochMillis,
            )
        }
        check(before == authorityStamp()) { "Matters authority changed mid-query" }
        return SearchCanonicalSnapshot(before, documents, ordered.size <= maxDocuments)
    }

    /** Owner ID only; neither a UI route nor cross-owner mutation authority. */
    fun resolveAuthorizedId(
        key: SearchProjectionKey,
        expectedAuthority: SearchAuthorityStamp,
    ): MatterId? {
        if (key.namespace != NAMESPACE) return null
        return try {
            val before = authorityStamp() ?: return null
            if (before != expectedAuthority) return null
            val snap = matters.read()
            if (snap.revision != before.ownerRevision) return null
            val matched = snap.matters.sortedWith(
                compareByDescending<MatterRecord> { it.updatedAtEpochMillis }
                    .thenBy { it.id.value },
            ).take(CanonicalSearchCoordinator.MAX_CANONICAL_DOCUMENTS)
                .firstOrNull { "m:" + hash(it.id.value) == key.documentId }?.id
            if (before == authorityStamp()) matched else null
        } catch (_: Exception) {
            null
        }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val NAMESPACE = "reality.matter.record"
    }
}
