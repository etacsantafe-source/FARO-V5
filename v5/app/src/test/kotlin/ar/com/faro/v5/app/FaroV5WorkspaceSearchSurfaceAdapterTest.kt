package ar.com.faro.v5.app

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
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun canonicalSpanishMessageAnchorIsConsumedOnceAfterAuthorizedSelection() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            val id = ConversationId("anchor-conversation")
            store.createAndSelect(id, 100L, IdempotencyKey("anchor-create"))
            val before = checkNotNull(store.readTimeline(id))
            val accepted = store.writeTimeline(ConversationTimelineWriteRequest(
                id, before.authorityToken, before.revision, IdempotencyKey("anchor-accept"),
                ConversationTimelineIntent.AcceptUser(ConversationMessage(
                    ConversationMessageId("anchor-message"), id, ConversationTurnId("anchor-turn"),
                    ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
                    "Pagué las expénsas del departamento", 200L,
                )),
            ))
            assertTrue(accepted is ConversationTimelineWriteResult.Saved)
            val adapter = FaroV5WorkspaceSearchSurfaceAdapter(
                source(store, { "g1" }, { "acl-real-test" }), store, MutableSharedFlow(),
            )
            val hit = adapter.search("EXPENSAS").hits.single()
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, adapter.openAuthorized(hit))
            assertEquals(ConversationMessageId("anchor-message"), adapter.takeConfirmedMessageTarget())
            assertNull(adapter.takeConfirmedMessageTarget())
        }
    }

    @Test
    fun revokedSearchResultNeverProducesAStaleMessageAnchor() = runBlocking {
        ConversationWorkspaceStore(":memory:").use { store ->
            val id = ConversationId("revoked-anchor")
            store.createAndSelect(id, 100L, IdempotencyKey("revoked-create"))
            val before = checkNotNull(store.readTimeline(id))
            store.writeTimeline(ConversationTimelineWriteRequest(
                id, before.authorityToken, before.revision, IdempotencyKey("revoked-accept"),
                ConversationTimelineIntent.AcceptUser(ConversationMessage(
                    ConversationMessageId("revoked-message"), id, ConversationTurnId("revoked-turn"),
                    ConversationMessageRole.USER, ConversationMessageState.ACCEPTED,
                    "Expensas comprobadas", 200L,
                )),
            ))
            var acl: String? = "acl-1"
            val adapter = FaroV5WorkspaceSearchSurfaceAdapter(
                source(store, { "g1" }, { acl }), store, MutableSharedFlow(),
            )
            val hit = adapter.search("expensas").hits.single()
            acl = null
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(hit))
            assertNull(adapter.takeConfirmedMessageTarget())
            acl = "acl-2"
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, adapter.openAuthorized(hit))
            assertNull(adapter.takeConfirmedMessageTarget())
        }
    }
}
