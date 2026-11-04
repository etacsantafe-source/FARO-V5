package ar.com.faro.v5.app

import ar.com.faro.v5.control.search.CanonicalSearchCoordinator
import ar.com.faro.v5.control.search.SearchProjectionKey
import ar.com.faro.v5.control.search.SearchProjectionQuery
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationMessage
import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.kernel.contracts.ConversationMessageRole
import ar.com.faro.v5.kernel.contracts.ConversationMessageState
import ar.com.faro.v5.kernel.contracts.ConversationTimelineIntent
import ar.com.faro.v5.kernel.contracts.ConversationTimelineWriteRequest
import ar.com.faro.v5.kernel.contracts.ConversationTimelineWriteResult
import ar.com.faro.v5.kernel.contracts.ConversationTurnId
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

    @Test
    fun committedMessageContentIsSearchedCanonicallyNotFromADisposableIndex() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            val id = ConversationId("search-message-content")
            store.createAndSelect(id, 100L, IdempotencyKey("create-search-message"))
            val timeline = checkNotNull(store.readTimeline(id))
            val accepted = ConversationMessage(
                ConversationMessageId("user-message-search"), id,
                ConversationTurnId("search-turn"), ConversationMessageRole.USER,
                ConversationMessageState.ACCEPTED,
                "Pagamos las expensas de noviembre", 150L,
            )
            val saved = store.writeTimeline(ConversationTimelineWriteRequest(
                id, timeline.authorityToken, timeline.revision,
                IdempotencyKey("accept-message-search"),
                ConversationTimelineIntent.AcceptUser(accepted),
            ))
            assertTrue(saved is ConversationTimelineWriteResult.Saved)
            val source = FaroV5WorkspaceSearchReadSource(
                "g1", store, { "g1" }, { "acl-1" }, { true },
            )
            val result = CanonicalSearchCoordinator(source, null).search(
                SearchProjectionQuery(FaroV5WorkspaceSearchReadSource.NAMESPACE, "EXPENSAS", 10),
            )
            assertEquals(CanonicalSearchCoordinator.Coverage.COMPLETE, result.coverage)
            assertEquals(1, result.hits.size)
            assertEquals(CanonicalSearchCoordinator.MatchField.BODY, result.hits.single().matchField)
            assertEquals(id, source.resolveAuthorizedConversation(result.hits.single().key))
        }
    }

    @Test
    fun boundedLongMessageYieldsPartialCoverageInsteadOfFalseAbsence() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            val id = ConversationId("search-long-message")
            store.createAndSelect(id, 100L, IdempotencyKey("create-long-message"))
            val timeline = checkNotNull(store.readTimeline(id))
            val saved = store.writeTimeline(ConversationTimelineWriteRequest(
                id, timeline.authorityToken, timeline.revision,
                IdempotencyKey("accept-long-message"),
                ConversationTimelineIntent.AcceptUser(ConversationMessage(
                    ConversationMessageId("long-message"), id,
                    ConversationTurnId("long-turn"), ConversationMessageRole.USER,
                    ConversationMessageState.ACCEPTED, "z".repeat(5_000), 150L,
                )),
            ))
            assertTrue(saved is ConversationTimelineWriteResult.Saved)
            val source = FaroV5WorkspaceSearchReadSource(
                "g1", store, { "g1" }, { "acl-1" }, { true },
            )
            val result = CanonicalSearchCoordinator(source, null).search(
                SearchProjectionQuery(FaroV5WorkspaceSearchReadSource.NAMESPACE, "no-existe", 10),
            )
            assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, result.coverage)
            assertTrue(result.hits.isEmpty())
            val titleResult = CanonicalSearchCoordinator(source, null).search(
                SearchProjectionQuery(FaroV5WorkspaceSearchReadSource.NAMESPACE, "nuevo", 10),
            )
            assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, titleResult.coverage)
            assertEquals(1, titleResult.hits.size)
        }
    }

    @Test
    fun boundedRecentMessageScanDoesNotClaimCompleteOnLongHistory() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            val id = ConversationId("search-history-bound")
            store.createAndSelect(id, 100L, IdempotencyKey("create-bounded-history"))
            for (index in 1..251) {
                val current = checkNotNull(store.readTimeline(id))
                val text = when (index) {
                    1 -> "tesoro antiguo que no debe certificarse como ausente"
                    251 -> "pago reciente confirmado"
                    else -> "nota numero " + index
                }
                val saved = store.writeTimeline(ConversationTimelineWriteRequest(
                    id, current.authorityToken, current.revision,
                    IdempotencyKey("append-bounded-" + index),
                    ConversationTimelineIntent.AcceptUser(ConversationMessage(
                        ConversationMessageId("bounded-message-" + index), id,
                        ConversationTurnId("bounded-turn-" + index),
                        ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
                        text, 200L + index,
                    )),
                ))
                assertTrue(saved is ConversationTimelineWriteResult.Saved)
            }
            val source = FaroV5WorkspaceSearchReadSource(
                "g1", store, { "g1" }, { "acl-test-only" }, { true },
            )
            val coordinator = CanonicalSearchCoordinator(source, null)
            val missingFromRecent = coordinator.search(
                SearchProjectionQuery(FaroV5WorkspaceSearchReadSource.NAMESPACE, "tesoro", 10),
            )
            assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, missingFromRecent.coverage)
            assertTrue(missingFromRecent.hits.isEmpty())
            val foundInRecent = coordinator.search(
                SearchProjectionQuery(FaroV5WorkspaceSearchReadSource.NAMESPACE, "pago reciente", 10),
            )
            assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, foundInRecent.coverage)
            assertEquals(1, foundInRecent.hits.size)
            assertEquals(id, source.resolveAuthorizedConversation(foundInRecent.hits.single().key))
        }
    }


    @Test
    fun oversizedRecentMessageKeepsBoundedPrefixAndReportsPartialTail() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            val id = ConversationId("search-long-prefix")
            store.createAndSelect(id, 100L, IdempotencyKey("create-prefix"))
            val timeline = checkNotNull(store.readTimeline(id))
            val saved = store.writeTimeline(ConversationTimelineWriteRequest(
                id, timeline.authorityToken, timeline.revision,
                IdempotencyKey("accept-prefix"),
                ConversationTimelineIntent.AcceptUser(ConversationMessage(
                    ConversationMessageId("long-prefix-message"), id,
                    ConversationTurnId("prefix-turn"), ConversationMessageRole.USER,
                    ConversationMessageState.ACCEPTED,
                    "expensas pendientes " + "x".repeat(4_900) + " remanente-no-visible", 150L,
                )),
            ))
            assertTrue(saved is ConversationTimelineWriteResult.Saved)
            val source = FaroV5WorkspaceSearchReadSource(
                "g1", store, { "g1" }, { "test-only-acl" }, { true },
            )
            val coordinator = CanonicalSearchCoordinator(source, null)
            val visible = coordinator.search(
                SearchProjectionQuery(FaroV5WorkspaceSearchReadSource.NAMESPACE, "expensas", 10),
            )
            assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, visible.coverage)
            assertEquals(1, visible.hits.size)
            assertEquals(CanonicalSearchCoordinator.MatchField.BODY,
                visible.hits.single().matchField)
            assertEquals(id, source.resolveAuthorizedConversation(visible.hits.single().key))
            val omitted = coordinator.search(
                SearchProjectionQuery(FaroV5WorkspaceSearchReadSource.NAMESPACE, "remanente-no-visible", 10),
            )
            assertEquals(CanonicalSearchCoordinator.Coverage.PARTIAL, omitted.coverage)
            assertTrue(omitted.hits.isEmpty())
            val text = store.readSearchableConversationText(id, 256)
            assertEquals(256, text.text.length)
            assertTrue(!text.complete)
        }
    }

}
