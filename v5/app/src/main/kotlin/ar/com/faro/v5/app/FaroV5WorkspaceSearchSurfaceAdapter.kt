package ar.com.faro.v5.app

import ar.com.faro.v5.control.search.CanonicalSearchCoordinator
import ar.com.faro.v5.control.search.SearchAuthorityStamp
import ar.com.faro.v5.control.search.SearchProjectionKey
import ar.com.faro.v5.control.search.SearchProjectionQuery
import ar.com.faro.v5.kernel.contracts.ConversationSelectionResult
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceSelectionPort
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchHit
import ar.com.faro.v5.surface.FaroV5SearchOpenResult
import ar.com.faro.v5.surface.FaroV5SearchPage
import ar.com.faro.v5.surface.FaroV5SearchReadPort
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Staged S3.A2 composition adapter. Searches only the genuine Workspace owner.
 *
 * Construction is NOT permission to expose it in shipping: the caller MUST pass
 * a source backed by an independently observable ACL epoch, active generation,
 * real recovery admission and invalidations from ALL those authority owners.
 * No ACL owner => do not mount this adapter. No default fake ACL stamp.
 */
class FaroV5WorkspaceSearchSurfaceAdapter(
    private val source: FaroV5WorkspaceSearchReadSource,
    private val selection: ConversationWorkspaceSelectionPort,
    upstreamInvalidations: Flow<Unit>,
) : FaroV5SearchReadPort {
    private val searcher = CanonicalSearchCoordinator(source, null)

    // Never accept a hit from another page, even if an ACL regrant produces
    // identical titles and opaque locators. This is provenance, NOT read authority.
    private data class Publication(
        val authority: SearchAuthorityStamp,
        val issuedHits: Set<FaroV5SearchHit>,
        val query: String,
    )
    private val publicationLock = Any()
    private var searchSerial = 0L
    private var publication: Publication? = null
    private var confirmedMessageTarget: ConversationMessageId? = null

    override fun takeConfirmedMessageTarget(): ConversationMessageId? =
        synchronized(publicationLock) {
            val issued = confirmedMessageTarget
            confirmedMessageTarget = null
            issued
        }

    // Only the *real owner's* invalidation flow can revoke visible content.
    // A transient recovery/pointer change also invalidates in-flight queries,
    // even when the eventual authority stamp becomes equal again.
    override val invalidations: Flow<Unit> = upstreamInvalidations.onEach {
        synchronized(publicationLock) {
            searchSerial += 1
            publication = null
            confirmedMessageTarget = null
        }
    }

    override suspend fun search(query: String): FaroV5SearchPage {
        val requestSerial = synchronized(publicationLock) {
            searchSerial += 1
            publication = null
            confirmedMessageTarget = null
            searchSerial
        }
        return try {
        val before = source.authorityStamp()
        val result = searcher.search(
            SearchProjectionQuery(FaroV5WorkspaceSearchReadSource.NAMESPACE, query, 50),
        )
        val coverage = when (result.coverage) {
            CanonicalSearchCoordinator.Coverage.COMPLETE -> FaroV5SearchCoverage.COMPLETE
            CanonicalSearchCoordinator.Coverage.PARTIAL -> FaroV5SearchCoverage.PARTIAL
            CanonicalSearchCoordinator.Coverage.UNAVAILABLE -> FaroV5SearchCoverage.UNAVAILABLE
        }
        // A regrant to a different ACL or an intervening revision cannot
        // authorize previously observed titles; compare the FULL stamp.
        if (before == null || before != source.authorityStamp() ||
            coverage == FaroV5SearchCoverage.UNAVAILABLE) {
            unavailable()
        } else {
            val issuanceId = UUID.randomUUID().toString()
            val page = FaroV5SearchPage(
                coverage = coverage,
                hits = result.hits.map { row ->
                    FaroV5SearchHit(
                        namespace = row.key.namespace,
                        opaqueId = row.key.documentId,
                        title = row.title,
                        canonicalSource = FaroV5WorkspaceSearchReadSource.NAMESPACE,
                        asOfEpochMillis = row.asOfEpochMillis,
                        issuanceId = issuanceId,
                    )
                },
                indexDegraded = result.isIndexDegraded,
            )
            // The older concurrent query can never re-publish after a newer
            // query starts. The real owner stamp is still validated separately.
            val published = synchronized(publicationLock) {
                if (searchSerial != requestSerial) false
                else {
                    publication = Publication(before, page.hits.toSet(), query)
                    true
                }
            }
            if (published && before == source.authorityStamp()) page
            else unavailable()
        }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            unavailable()
        }
    }

    /**
     * Search hits are locators, NOT mutation authority. Re-read canonical
     * metadata and resolve the opaque key with fresh generation/ACL checks;
     * only the existing selected-generation write gate may mutate selection.
     */
    override suspend fun openAuthorized(hit: FaroV5SearchHit): FaroV5SearchOpenResult {
        synchronized(publicationLock) { confirmedMessageTarget = null }
        return try {
        val namespace = FaroV5WorkspaceSearchReadSource.NAMESPACE
        if (hit.namespace != namespace || hit.canonicalSource != namespace) {
            FaroV5SearchOpenResult.UNAVAILABLE
        } else {
            val before = source.authorityStamp()
                ?: return FaroV5SearchOpenResult.UNAVAILABLE
            val issuedQuery = publishedQuery(hit, before)
                ?: return FaroV5SearchOpenResult.UNAVAILABLE
            val key = SearchProjectionKey(hit.namespace, hit.opaqueId)
            val snapshot = source.readAuthorized(namespace, CanonicalSearchCoordinator.MAX_CANONICAL_DOCUMENTS)
            if (before != snapshot.authority || !sameAuthority(before, source.authorityStamp())) {
                FaroV5SearchOpenResult.UNAVAILABLE
            } else {
                val canonical = snapshot.documents.firstOrNull {
                    it.key.namespace == key.namespace && it.key.documentId == key.documentId
                }
                if (canonical == null) {
                    if (snapshot.isComplete) FaroV5SearchOpenResult.STALE
                    else FaroV5SearchOpenResult.UNAVAILABLE
                } else if (canonical.title != hit.title ||
                    canonical.asOfEpochMillis != hit.asOfEpochMillis) {
                    FaroV5SearchOpenResult.STALE
                } else {
                    val id = source.resolveAuthorizedConversation(key)
                        ?: return FaroV5SearchOpenResult.UNAVAILABLE
                    if (!sameAuthority(before, source.authorityStamp()) ||
                        !isPublished(hit, before)) {
                        FaroV5SearchOpenResult.UNAVAILABLE
                    } else {
                        val outcome = selection.select(id, IdempotencyKey(UUID.randomUUID().toString()))
                        val after = source.authorityStamp()
                        // Selection may legitimately increment Workspace revision.
                        // Generation and ACL epoch, however, must remain unchanged.
                        if (!sameAuthority(before, after) || !isPublished(hit, before)) {
                            FaroV5SearchOpenResult.UNAVAILABLE
                        } else when (outcome) {
                            is ConversationSelectionResult.Saved ->
                                if (outcome.snapshot.activeConversationId == id) {
                                    val target = source.resolveAuthorizedMessageTarget(id, issuedQuery)
                                    if (!sameAuthority(before, source.authorityStamp())) {
                                        FaroV5SearchOpenResult.UNAVAILABLE
                                    } else {
                                        val stillPublished = synchronized(publicationLock) {
                                            val current = publication
                                            if (current?.authority == before &&
                                                hit in current.issuedHits) {
                                                confirmedMessageTarget = target
                                                true
                                            } else false
                                        }
                                        if (stillPublished) FaroV5SearchOpenResult.CONFIRMED
                                        else FaroV5SearchOpenResult.UNAVAILABLE
                                    }
                                } else FaroV5SearchOpenResult.UNAVAILABLE
                            is ConversationSelectionResult.Stale ->
                                FaroV5SearchOpenResult.STALE
                            else -> FaroV5SearchOpenResult.UNAVAILABLE
                        }
                    }
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        FaroV5SearchOpenResult.UNAVAILABLE
    }
    }

    private fun publishedQuery(
        hit: FaroV5SearchHit,
        authority: SearchAuthorityStamp,
    ): String? = synchronized(publicationLock) {
        publication?.takeIf { it.authority == authority && hit in it.issuedHits }?.query
    }

    private fun isPublished(
        hit: FaroV5SearchHit,
        authority: SearchAuthorityStamp,
    ): Boolean = synchronized(publicationLock) {
        val last = publication
        last != null && last.authority == authority && hit in last.issuedHits
    }

    private fun sameAuthority(
        before: SearchAuthorityStamp,
        current: SearchAuthorityStamp?,
    ): Boolean = current != null &&
        before.generationId == current.generationId && before.aclEpoch == current.aclEpoch

    private fun unavailable(): FaroV5SearchPage =
        FaroV5SearchPage(FaroV5SearchCoverage.UNAVAILABLE, emptyList(), true)
}
