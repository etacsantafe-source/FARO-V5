package ar.com.faro.v5.app

import ar.com.faro.v5.control.search.CanonicalSearchCoordinator
import ar.com.faro.v5.control.search.SearchAuthorityStamp
import ar.com.faro.v5.control.search.SearchCanonicalDocument
import ar.com.faro.v5.control.search.SearchCanonicalReadPort
import ar.com.faro.v5.control.search.SearchCanonicalSnapshot
import ar.com.faro.v5.control.search.SearchProjectionKey
import ar.com.faro.v5.owner.objectives.api.ObjectiveId
import ar.com.faro.v5.owner.objectives.api.ObjectiveRecord
import ar.com.faro.v5.owner.objectives.spi.ObjectivePersistencePort
import java.security.MessageDigest

/**
 * Genuine Reality-backed Objective search source, not an index or permission owner.
 * Requires independent host-issued read grant; never auto-authorizes empty owners.
 */
class FaroV5ObjectivesSearchReadSource(
    private val selectedGenerationId: String,
    private val objectives: ObjectivePersistencePort,
    private val currentGenerationId: () -> String?,
    private val aclEpoch: () -> String?,
    private val admitted: () -> Boolean,
) : SearchCanonicalReadPort {
    init { require(selectedGenerationId.isNotBlank()) }

    override fun authorityStamp(): SearchAuthorityStamp? {
        if (!admitted() || currentGenerationId() != selectedGenerationId) return null
        val epoch = aclEpoch()?.takeIf { it.isNotBlank() } ?: return null
        val revision = objectives.read().revision
        if (!admitted() || currentGenerationId() != selectedGenerationId ||
            aclEpoch() != epoch) return null
        return SearchAuthorityStamp(selectedGenerationId, epoch, revision)
    }

    override fun readAuthorized(namespace: String, maxDocuments: Int): SearchCanonicalSnapshot {
        require(namespace == NAMESPACE) { "Objective namespace mismatch" }
        require(maxDocuments in 1..CanonicalSearchCoordinator.MAX_CANONICAL_DOCUMENTS)
        val before = checkNotNull(authorityStamp()) { "Objective search authority unavailable" }
        val snapshot = objectives.read()
        check(snapshot.revision == before.ownerRevision) { "Objective revision changed" }
        val ordered = snapshot.objectives.sortedWith(
            compareByDescending<ObjectiveRecord> { it.updatedAtEpochMillis }.thenBy { it.id.value },
        )
        val documents = ordered.take(maxDocuments).map { record ->
            SearchCanonicalDocument(
                SearchProjectionKey(NAMESPACE, "o:" + hash(record.id.value)),
                SearchCanonicalDocument.Kind.OBJECTIVE,
                record.title.value,
                record.detail.value + " " + record.status.name,
                hash(selectedGenerationId + ":" + snapshot.revision + ":" +
                    record.id.value + ":" + record.updatedAtEpochMillis),
                record.updatedAtEpochMillis,
            )
        }
        check(before == authorityStamp()) { "Objective authority changed mid-query" }
        return SearchCanonicalSnapshot(before, documents, ordered.size <= maxDocuments)
    }

    /** Owner ID only. Caller must independently validate hit issuance and navigation. */
    fun resolveAuthorizedId(
        key: SearchProjectionKey,
        expectedAuthority: SearchAuthorityStamp,
    ): ObjectiveId? {
        if (key.namespace != NAMESPACE) return null
        return try {
            val before = authorityStamp() ?: return null
            if (before != expectedAuthority) return null
            val snap = objectives.read()
            if (snap.revision != before.ownerRevision) return null
            val matched = snap.objectives.sortedWith(
                compareByDescending<ObjectiveRecord> { it.updatedAtEpochMillis }
                    .thenBy { it.id.value },
            ).take(CanonicalSearchCoordinator.MAX_CANONICAL_DOCUMENTS)
                .firstOrNull { "o:" + hash(it.id.value) == key.documentId }?.id
            if (before == authorityStamp()) matched else null
        } catch (_: Exception) {
            null
        }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val NAMESPACE = "reality.objective.record"
    }
}
