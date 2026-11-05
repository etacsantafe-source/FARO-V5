package ar.com.faro.v5.app

import ar.com.faro.v5.control.search.CanonicalSearchCoordinator
import ar.com.faro.v5.control.search.SearchAuthorityStamp
import ar.com.faro.v5.control.search.SearchCanonicalDocument
import ar.com.faro.v5.control.search.SearchCanonicalReadPort
import ar.com.faro.v5.control.search.SearchCanonicalSnapshot
import ar.com.faro.v5.control.search.SearchProjectionKey
import ar.com.faro.v5.owner.finance.api.FinanceEntrySnapshot
import ar.com.faro.v5.owner.finance.api.FinanceSnapshotReadApi
import java.security.MessageDigest

/**
 * G4 FINANCE ONLY: read-only source over actual Reality-owned FinanceSnapshot.
 *
 * This class DOES NOT grant authority. No production host mounts it yet.
 * All three callbacks must come from real owner/recovery/ACL infrastructure;
 * an absent or changed ACL makes the source unavailable, never an empty result.
 * No amounts, account IDs, balances or raw entry IDs enter search documents.
 */
class FaroV5FinanceSearchReadSource(
    private val selectedGenerationId: String,
    private val finance: FinanceSnapshotReadApi,
    private val currentGenerationId: () -> String?,
    private val aclEpoch: () -> String?,
    private val admitted: () -> Boolean,
) : SearchCanonicalReadPort {
    init { require(selectedGenerationId.isNotBlank()) }

    override fun authorityStamp(): SearchAuthorityStamp? {
        if (!admitted() || currentGenerationId() != selectedGenerationId) return null
        val acl = aclEpoch()?.takeIf { it.isNotBlank() } ?: return null
        val revision = finance.read().revision
        if (!admitted() || currentGenerationId() != selectedGenerationId ||
            aclEpoch() != acl) return null
        return SearchAuthorityStamp(selectedGenerationId, acl, revision)
    }

    override fun readAuthorized(
        namespace: String,
        maxDocuments: Int,
    ): SearchCanonicalSnapshot {
        require(namespace == NAMESPACE) { "Finance namespace mismatch" }
        require(maxDocuments in 1..CanonicalSearchCoordinator.MAX_CANONICAL_DOCUMENTS) {
            "Finance scan must be bounded"
        }
        val before = checkNotNull(authorityStamp()) {
            "Finance search read authority unavailable"
        }
        val snapshot = finance.read()
        check(snapshot.revision == before.ownerRevision) {
            "Finance owner revision changed before canonical read"
        }
        val ordered = snapshot.entries.sortedWith(
            compareByDescending<FinanceEntrySnapshot> { it.recordedAtEpochMillis }
                .thenBy { it.entryId.value },
        )
        val valid = ordered.filter { it.recordedAtEpochMillis >= 0L }
        val documents = valid.take(maxDocuments).map { entry ->
            SearchCanonicalDocument(
                SearchProjectionKey(NAMESPACE, "f:" + sha256(entry.entryId.value)),
                SearchCanonicalDocument.Kind.FINANCE_ENTRY,
                entry.concept.value,
                entry.category.value,
                sha256(
                    selectedGenerationId + ":" + snapshot.revision + ":" +
                        entry.entryId.value + ":" + entry.recordedAtEpochMillis,
                ),
                entry.recordedAtEpochMillis,
            )
        }
        check(before == authorityStamp()) {
            "Finance generation, ACL or revision changed during read"
        }
        return SearchCanonicalSnapshot(
            before, documents,
            valid.size == ordered.size && valid.size <= maxDocuments,
        )
    }

    /**
     * Resolve a Finance locator only against the exact originally observed owner
     * generation, ACL grant and revision. This returns a private domain ID, NOT
     * UI navigation authority: the eventual Finance host must also verify
     * issuance/provenance and authorize the destination on its own read path.
     */
    fun resolveAuthorizedEntryId(
        key: SearchProjectionKey,
        expectedAuthority: SearchAuthorityStamp,
    ): ar.com.faro.v5.owner.finance.api.FinanceEntryId? {
        if (key.namespace != NAMESPACE) return null
        return try {
            val before = authorityStamp() ?: return null
            if (before != expectedAuthority) return null
            val snapshot = finance.read()
            if (snapshot.revision != before.ownerRevision) return null
            // Match only the bounded canonical documents that Search could issue,
            // never an ID from a stale local index or a differently ordered row.
            val candidates = snapshot.entries.sortedWith(
                compareByDescending<FinanceEntrySnapshot> { it.recordedAtEpochMillis }
                    .thenBy { it.entryId.value },
            ).filter { it.recordedAtEpochMillis >= 0L }
                .take(CanonicalSearchCoordinator.MAX_CANONICAL_DOCUMENTS)
            val matched = candidates.firstOrNull {
                "f:" + sha256(it.entryId.value) == key.documentId
            }?.entryId
            if (before == authorityStamp()) matched else null
        } catch (_: Exception) {
            null // closed owner / recovery denial is never an empty authorized result
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val NAMESPACE: String = "reality.finance.entry"
    }
}
