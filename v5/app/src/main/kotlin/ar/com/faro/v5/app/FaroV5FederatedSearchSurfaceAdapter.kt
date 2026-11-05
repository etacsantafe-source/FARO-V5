package ar.com.faro.v5.app

import ar.com.faro.v5.control.search.CanonicalSearchCoordinator
import ar.com.faro.v5.control.search.FederatedCanonicalSearchCoordinator
import ar.com.faro.v5.control.search.SearchAuthorityStamp
import ar.com.faro.v5.control.search.SearchCanonicalReadPort
import ar.com.faro.v5.control.search.SearchProjectionKey
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchDomainTarget
import ar.com.faro.v5.surface.FaroV5SearchHit
import ar.com.faro.v5.surface.FaroV5SearchOpenResult
import ar.com.faro.v5.surface.FaroV5SearchPage
import ar.com.faro.v5.surface.FaroV5SearchReadPort
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * G4 DEV-only composition over FOUR genuine canonical owners. Does not own ACL,
 * durable rows or an index. Workspace retains the certified G1-G3 navigation
 * adapter, while Reality targets are READ_ONLY details after fresh revalidation.
 */
class FaroV5FederatedSearchSurfaceAdapter(
    private val workspace: FaroV5WorkspaceSearchReadSource,
    private val workspaceSurface: FaroV5WorkspaceSearchSurfaceAdapter,
    private val finance: FaroV5FinanceSearchReadSource,
    private val objectives: FaroV5ObjectivesSearchReadSource,
    private val matters: FaroV5MattersSearchReadSource,
    upstreamInvalidations: Flow<Unit>,
) : FaroV5SearchReadPort {
    private val sources: Map<String, SearchCanonicalReadPort> = linkedMapOf(
        FaroV5WorkspaceSearchReadSource.NAMESPACE to workspace,
        FaroV5FinanceSearchReadSource.NAMESPACE to finance,
        FaroV5ObjectivesSearchReadSource.NAMESPACE to objectives,
        FaroV5MattersSearchReadSource.NAMESPACE to matters,
    )
    private val federator = FederatedCanonicalSearchCoordinator(
        sources.map { (namespace, port) ->
            FederatedCanonicalSearchCoordinator.Source(namespace, port, null)
        },
    )
    private data class Publication(
        val issued: Set<FaroV5SearchHit>,
        val stamps: Map<String, SearchAuthorityStamp>,
        val serial: Long,
    )
    private data class Confirmed(
        val target: FaroV5SearchDomainTarget,
        val stamp: SearchAuthorityStamp,
        val serial: Long,
    )

    private val lock = Any()
    private var serial = 0L
    private var published: Publication? = null
    private var confirmed: Confirmed? = null

    override val invalidations: Flow<Unit> = upstreamInvalidations.onEach {
        synchronized(lock) {
            serial++
            published = null
            confirmed = null
        }
    }

    override fun takeConfirmedMessageTarget(): ConversationMessageId? =
        workspaceSurface.takeConfirmedMessageTarget()

    override fun takeConfirmedDomainTarget(): FaroV5SearchDomainTarget? {
        val item = synchronized(lock) {
            val current = confirmed
            confirmed = null
            current?.takeIf {
                published?.serial == it.serial &&
                    published?.stamps?.get(it.target.canonicalSource) == it.stamp
            }
        } ?: return null
        return if (safeStamp(sources[item.target.canonicalSource]) == item.stamp) {
            item.target
        } else null
    }

    override suspend fun search(query: String): FaroV5SearchPage {
        val requested = synchronized(lock) {
            serial++
            published = null
            confirmed = null
            serial
        }
        return try {
            // This certified Workspace adapter must actually issue any Workspace
            // hits. Never fabricate issuanceId or imitate its selection protocol.
            val workspacePage = workspaceSurface.search(query)
            val before = sources.mapValues { safeStamp(it.value) }
            val result = federator.search(query, 50)
            val after = sources.mapValues { safeStamp(it.value) }
            val coverageBySource = result.coverageBySource.toMutableMap()
            val good = sources.keys.associateWith { namespace ->
                val original = before[namespace]
                val valid = original != null && original == after[namespace] &&
                    coverageBySource[namespace] != CanonicalSearchCoordinator.Coverage.UNAVAILABLE &&
                    (namespace != FaroV5WorkspaceSearchReadSource.NAMESPACE ||
                        workspacePage.coverage != FaroV5SearchCoverage.UNAVAILABLE)
                if (!valid) {
                    coverageBySource[namespace] = CanonicalSearchCoordinator.Coverage.UNAVAILABLE
                }
                valid
            }
            val workspaceIssued = workspacePage.hits.associateBy {
                it.namespace to it.opaqueId
            }
            val issuance = UUID.randomUUID().toString()
            var omitted = false
            val hits = result.hits.mapNotNull { raw ->
                val namespace = raw.key.namespace
                if (good[namespace] != true) null
                else if (namespace == FaroV5WorkspaceSearchReadSource.NAMESPACE) {
                    val issued = workspaceIssued[namespace to raw.key.documentId]
                    if (issued == null || issued.title != raw.title ||
                        issued.asOfEpochMillis != raw.asOfEpochMillis) {
                        omitted = true
                        null
                    } else issued
                } else {
                    FaroV5SearchHit(
                        namespace = namespace,
                        opaqueId = raw.key.documentId,
                        title = raw.title,
                        canonicalSource = namespace,
                        asOfEpochMillis = raw.asOfEpochMillis,
                        issuanceId = issuance,
                    )
                }
            }
            if (omitted) {
                coverageBySource[FaroV5WorkspaceSearchReadSource.NAMESPACE] =
                    CanonicalSearchCoordinator.Coverage.UNAVAILABLE
            }
            val readable = coverageBySource.values.any {
                it != CanonicalSearchCoordinator.Coverage.UNAVAILABLE
            }
            val complete = !omitted && coverageBySource.values.all {
                it == CanonicalSearchCoordinator.Coverage.COMPLETE
            } && workspacePage.coverage == FaroV5SearchCoverage.COMPLETE
            val level = when {
                !readable -> FaroV5SearchCoverage.UNAVAILABLE
                complete -> FaroV5SearchCoverage.COMPLETE
                else -> FaroV5SearchCoverage.PARTIAL
            }
            val retainedHits = hits.filter {
                coverageBySource[it.namespace] != CanonicalSearchCoordinator.Coverage.UNAVAILABLE
            }.take(50)
            val page = FaroV5SearchPage(
                level,
                if (level == FaroV5SearchCoverage.UNAVAILABLE) emptyList() else retainedHits,
                result.isIndexDegraded || workspacePage.indexDegraded || !complete,
            )
            val saved = synchronized(lock) {
                if (serial != requested) false
                else {
                    published = Publication(
                        page.hits.toSet(),
                        before.filterValues { it != null }.mapValues { it.value!! },
                        requested,
                    )
                    true
                }
            }
            if (!saved || sources.any { (ns, port) ->
                page.hits.any { it.namespace == ns } && safeStamp(port) != before[ns]
            }) unavailable() else page
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            unavailable()
        }
    }

    override suspend fun openAuthorized(hit: FaroV5SearchHit): FaroV5SearchOpenResult {
        synchronized(lock) { confirmed = null }
        return try {
            val publication = synchronized(lock) {
                published?.takeIf { hit in it.issued }
            } ?: return FaroV5SearchOpenResult.UNAVAILABLE
            val source = sources[hit.namespace] ?: return FaroV5SearchOpenResult.UNAVAILABLE
            val stamp = publication.stamps[hit.namespace]
                ?: return FaroV5SearchOpenResult.UNAVAILABLE
            if (hit.canonicalSource != hit.namespace || safeStamp(source) != stamp) {
                return FaroV5SearchOpenResult.UNAVAILABLE
            }
            if (hit.namespace == FaroV5WorkspaceSearchReadSource.NAMESPACE) {
                // G3 adapter owns selection, message anchor, revision postconditions.
                return workspaceSurface.openAuthorized(hit)
            }
            // Re-read canonical authority. Issued hit ID/title/time are locators
            // only, and every opened result must still exist in the real owner.
            val snapshot = source.readAuthorized(hit.namespace, CanonicalSearchCoordinator.MAX_CANONICAL_DOCUMENTS)
            if (snapshot.authority != stamp) return FaroV5SearchOpenResult.UNAVAILABLE
            val canonical = snapshot.documents.firstOrNull { document ->
                document.key.namespace == hit.namespace &&
                    document.key.documentId == hit.opaqueId
            }
            if (canonical == null) {
                return if (snapshot.isComplete) FaroV5SearchOpenResult.STALE
                    else FaroV5SearchOpenResult.UNAVAILABLE
            }
            if (canonical.title != hit.title ||
                canonical.asOfEpochMillis != hit.asOfEpochMillis) {
                return FaroV5SearchOpenResult.STALE
            }
            val key = SearchProjectionKey(hit.namespace, hit.opaqueId)
            val resolved = when (hit.namespace) {
                FaroV5FinanceSearchReadSource.NAMESPACE ->
                    finance.resolveAuthorizedEntryId(key, stamp) != null
                FaroV5ObjectivesSearchReadSource.NAMESPACE ->
                    objectives.resolveAuthorizedId(key, stamp) != null
                FaroV5MattersSearchReadSource.NAMESPACE ->
                    matters.resolveAuthorizedId(key, stamp) != null
                else -> false
            }
            if (!resolved || safeStamp(source) != stamp) {
                return FaroV5SearchOpenResult.UNAVAILABLE
            }
            val target = FaroV5SearchDomainTarget(
                canonicalSource = hit.namespace,
                title = canonical.title,
                summary = canonical.body,
                asOfEpochMillis = canonical.asOfEpochMillis,
            )
            val admitted = synchronized(lock) {
                if (published?.serial != publication.serial ||
                    hit !in (published?.issued ?: emptySet())) false
                else {
                    confirmed = Confirmed(target, stamp, publication.serial)
                    true
                }
            }
            if (!admitted || safeStamp(source) != stamp) {
                synchronized(lock) { confirmed = null }
                FaroV5SearchOpenResult.UNAVAILABLE
            } else FaroV5SearchOpenResult.CONFIRMED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FaroV5SearchOpenResult.UNAVAILABLE
        }
    }

    private fun safeStamp(source: SearchCanonicalReadPort?): SearchAuthorityStamp? =
        source?.let { runCatching { it.authorityStamp() }.getOrNull() }

    private fun unavailable() =
        FaroV5SearchPage(FaroV5SearchCoverage.UNAVAILABLE, emptyList(), true)
}
