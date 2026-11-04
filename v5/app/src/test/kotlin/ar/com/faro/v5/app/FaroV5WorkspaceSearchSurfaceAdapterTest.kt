package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchOpenResult
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5WorkspaceSearchSurfaceAdapterTest {
    private fun source(
        store: ConversationWorkspaceStore,
        generation: () -> String?,
        acl: () -> String?,
    ) = FaroV5WorkspaceSearchReadSource("g1", store, generation, acl) { true }

    @Test
    fun realWorkspaceHitUsesOpaqueKeyAndOwnerSelection() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            val older = ConversationId("older-secret-id")
            val newer = ConversationId("newer-secret-id")
            store.createAndSelect(older, 100L, IdempotencyKey("create-older"))
            store.createAndSelect(newer, 200L, IdempotencyKey("create-newer"))
            // V4.4 parity: an empty active chat is REUSED, not duplicated.
            assertEquals(older, store.readSnapshot().activeConversationId)
            assertEquals(1, store.readSnapshot().conversations.size)
            val adapter = FaroV5WorkspaceSearchSurfaceAdapter(
                source(store, { "g1" }, { "acl-1" }), store, MutableSharedFlow(),
            )
            val result = adapter.search("nuevo")
            assertEquals(FaroV5SearchCoverage.COMPLETE, result.coverage)
            assertEquals(1, result.hits.size)
            val match = result.hits.single()
            assertEquals(100L, match.asOfEpochMillis)
            assertTrue(match.opaqueId.startsWith("c:"))
            assertNotEquals(older.value, match.opaqueId)
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, adapter.openAuthorized(match))
            assertEquals(older, store.readSnapshot().activeConversationId)
        }
    }

    @Test
    fun independentlyRevokedAclOrGenerationSwitchRejectsSearchAndOpen() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            val id = ConversationId("still-protected")
            store.createAndSelect(id, 100L, IdempotencyKey("create"))
            var acl: String? = "acl-1"
            var generation: String? = "g1"
            val adapter = FaroV5WorkspaceSearchSurfaceAdapter(
                source(store, { generation }, { acl }), store, MutableSharedFlow(),
            )
            val hit = adapter.search("nuevo").hits.single()
            // Revoke without issuing a new search that would clear the page.
            acl = null
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(hit))
            // Regrant under a distinct epoch. The OLD implementation would
            // CONFIRM this hit after rereading the same title and timestamp.
            acl = "acl-2"
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(hit))
            val freshHit = adapter.search("nuevo").hits.single()
            assertEquals(hit.opaqueId, freshHit.opaqueId)
            assertNotEquals(hit.issuanceId, freshHit.issuanceId)
            // Even after a fresh authorized search, the older result remains denied.
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(hit))
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, adapter.openAuthorized(freshHit))
            generation = "g2"
            assertEquals(FaroV5SearchCoverage.UNAVAILABLE, adapter.search("nuevo").coverage)
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(freshHit))
        }
    }

    @Test
    fun laterSearchDoesNotReviveOldHitUnderUnchangedAuthority() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            store.createAndSelect(ConversationId("same-canonical-target"), 100L, IdempotencyKey("new"))
            val adapter = FaroV5WorkspaceSearchSurfaceAdapter(
                source(store, { "g1" }, { "acl-1" }), store, MutableSharedFlow(),
            )
            val old = adapter.search("nuevo").hits.single()
            val fresh = adapter.search("nuevo").hits.single()
            assertEquals(old.opaqueId, fresh.opaqueId)
            assertNotEquals(old.issuanceId, fresh.issuanceId)
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(old))
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, adapter.openAuthorized(fresh))
        }
    }

    @Test
    fun ownerInvalidationRevokesPreviouslyPublishedHitEvenWithSameStamp() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            store.createAndSelect(ConversationId("owner-event-target"), 100L, IdempotencyKey("new"))
            val ownerEvents = MutableSharedFlow<Unit>()
            val adapter = FaroV5WorkspaceSearchSurfaceAdapter(
                source(store, { "g1" }, { "acl-1" }), store, ownerEvents,
            )
            val subscriber = launch(start = CoroutineStart.UNDISPATCHED) {
                adapter.invalidations.collect()
            }
            try {
                val old = adapter.search("nuevo").hits.single()
                ownerEvents.emit(Unit)
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(old))
                val fresh = adapter.search("nuevo").hits.single()
                assertNotEquals(old.issuanceId, fresh.issuanceId)
                assertEquals(FaroV5SearchOpenResult.CONFIRMED, adapter.openAuthorized(fresh))
            } finally {
                subscriber.cancel()
            }
        }
    }

    @Test
    fun tamperedOrStaleHitCannotSelectConversation() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            val id = ConversationId("protected-target")
            store.createAndSelect(id, 100L, IdempotencyKey("create"))
            val adapter = FaroV5WorkspaceSearchSurfaceAdapter(
                source(store, { "g1" }, { "acl-1" }), store, MutableSharedFlow(),
            )
            val hit = adapter.search("nuevo").hits.single()
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE,
                adapter.openAuthorized(hit.copy(title = "Falso")))
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE,
                adapter.openAuthorized(hit.copy(namespace = "untrusted")))
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE,
                adapter.openAuthorized(hit.copy(opaqueId = "c:unknown")))
        }
    }
}
