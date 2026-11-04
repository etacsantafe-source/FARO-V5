package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.store.reality.*
import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchOpenResult
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import kotlinx.coroutines.flow.MutableSharedFlow
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
            val adapter = FaroV5WorkspaceSearchSurfaceAdapter(
                source(store, { "g1" }, { "acl-1" }), store, MutableSharedFlow(),
            )
            val result = adapter.search("nuevo")
            assertEquals(FaroV5SearchCoverage.COMPLETE, result.coverage)
            assertEquals(2, result.hits.size)
            val match = result.hits.first { it.asOfEpochMillis == 100L }
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
            acl = null
            assertEquals(FaroV5SearchCoverage.UNAVAILABLE, adapter.search("nuevo").coverage)
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(hit))
            acl = "acl-2"
            generation = "g2"
            assertEquals(FaroV5SearchCoverage.UNAVAILABLE, adapter.search("nuevo").coverage)
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(hit))
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
            assertEquals(FaroV5SearchOpenResult.STALE,
                adapter.openAuthorized(hit.copy(title = "Falso")))
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE,
                adapter.openAuthorized(hit.copy(namespace = "untrusted")))
            assertEquals(FaroV5SearchOpenResult.STALE,
                adapter.openAuthorized(hit.copy(opaqueId = "c:unknown")))
        }
    }
}
