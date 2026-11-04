package ar.com.faro.v5.app

import ar.com.faro.v5.control.search.CanonicalSearchCoordinator
import ar.com.faro.v5.control.search.SearchProjectionKey
import ar.com.faro.v5.control.search.SearchProjectionQuery
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5WorkspaceSearchReadSourceTest {
    @Test
    fun realPersistedWorkspaceConversationIsFoundAndResolvedThroughOwner() = runBlocking {
        val db = Files.createTempDirectory("faro-v5-s3-workspace-search")
            .resolve("workspace.db")
        ConversationWorkspaceStore(db.toString()).use { store ->
            val id = ConversationId("conversation-unicode-ñ")
            store.createAndSelect(id, 1000L, IdempotencyKey("create-1"))
            val source = FaroV5WorkspaceSearchReadSource(
                "g1", store, { "g1" }, { "acl-1" }, { true },
            )
            val result = CanonicalSearchCoordinator(source, null)
                .search(SearchProjectionQuery(FaroV5WorkspaceSearchReadSource.NAMESPACE, "NUEVO", 10))
            assertEquals(CanonicalSearchCoordinator.Coverage.COMPLETE, result.coverage)
            assertEquals(1, result.hits.size)
            assertEquals("Nuevo chat", result.hits.single().title)
            assertEquals(
                ar.com.faro.v5.control.search.SearchCanonicalDocument.Kind.CONVERSATION,
                result.hits.single().kind,
            )
            assertTrue(result.hits.single().key.documentId.startsWith("c:"))
            assertEquals(id, source.resolveAuthorizedConversation(result.hits.single().key))
            assertEquals(
                result.hits.single().key.documentId,
                source.readAuthorized(FaroV5WorkspaceSearchReadSource.NAMESPACE, 10).documents.single().key.documentId,
            )
        }
    }

    @Test
    fun revocationOrGenerationSwitchMakesSourceUnavailableAndDeniesNavigation() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            val id = ConversationId("conversation-1")
            store.createAndSelect(id, 100L, IdempotencyKey("create-1"))
            var generation = "g1"
            var acl: String? = "acl-2"
            var ready = true
            val source = FaroV5WorkspaceSearchReadSource(
                "g1", store, { generation }, { acl }, { ready },
            )
            val query = SearchProjectionQuery(FaroV5WorkspaceSearchReadSource.NAMESPACE, "nuevo", 5)
            val coordinator = CanonicalSearchCoordinator(source, null)
            val key = coordinator.search(query).hits.single().key
            acl = null
            assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
                coordinator.search(query).coverage)
            assertNull(source.resolveAuthorizedConversation(key))
            acl = "acl-3"
            generation = "g2"
            assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE,
                coordinator.search(query).coverage)
            generation = "g1"
            ready = false
            assertNull(source.resolveAuthorizedConversation(key))
            assertNull(source.resolveAuthorizedConversation(SearchProjectionKey("other", "c:1")))
        }
    }

    @Test
    fun closedCanonicalStoreCannotProduceAFalseEmptySearch() {
        val store = ConversationWorkspaceStore(":memory:")
        val source = FaroV5WorkspaceSearchReadSource(
            "g1", store, { "g1" }, { "acl-1" }, { true },
        )
        store.close()
        val result = CanonicalSearchCoordinator(source, null)
            .search(SearchProjectionQuery(FaroV5WorkspaceSearchReadSource.NAMESPACE, "missing", 10))
        assertEquals(CanonicalSearchCoordinator.Coverage.UNAVAILABLE, result.coverage)
        assertTrue(result.hits.isEmpty())
    }
}
