package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchOpenResult
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FaroV5FederatedSearchSurfaceAdapterTest {
    private class Fixture : AutoCloseable {
        val workspace = ConversationWorkspaceStore(":memory:")
        val reality = RealityStore(":memory:")
        var generation: String? = "g1"
        var workspaceAcl: String? = "workspace-grant-1"
        var financeAcl: String? = "finance-grant-1"
        var objectiveAcl: String? = "objectives-grant-1"
        var matterAcl: String? = "matters-grant-1"
        val events = MutableSharedFlow<Unit>()
        val workspaceSource = FaroV5WorkspaceSearchReadSource(
            "g1", workspace, { generation }, { workspaceAcl }, { true },
        )
        val workspaceAdapter = FaroV5WorkspaceSearchSurfaceAdapter(
            workspaceSource, workspace, events,
        )
        val financeSource = FaroV5FinanceSearchReadSource(
            "g1", FinanceSnapshotReadApi { reality.financePersistence.read() },
            { generation }, { financeAcl }, { true },
        )
        val objectiveSource = FaroV5ObjectivesSearchReadSource(
            "g1", reality.objectivePersistence,
            { generation }, { objectiveAcl }, { true },
        )
        val matterSource = FaroV5MattersSearchReadSource(
            "g1", reality.matterPersistence,
            { generation }, { matterAcl }, { true },
        )
        val search = FaroV5FederatedSearchSurfaceAdapter(
            workspaceSource, workspaceAdapter, financeSource, objectiveSource,
            matterSource, events,
        )

        init {
            runBlocking {
                workspace.createAndSelect(
                    ConversationId("private-chat-1"), 100L, IdempotencyKey("create-chat"),
                )
            }
            reality.run(TransactionId("tx-four-real-owners")) { scope ->
                val account = FinanceAccountId.of("private-account-1")
                reality.financePersistence.createAccount(
                    FinanceAccountCreate(
                        account, FinanceAccountDisplayName.of("Cuenta privada"),
                        FinanceCurrencyCode.of("ARS"), FinanceAccountNature.BANK,
                    ), scope,
                )
                reality.financePersistence.recordEntry(
                    FinanceEntryRecord(
                        entryId = FinanceEntryId.of("private-finance-entry-1"),
                        accountId = account,
                        amountMinor = 123_456L,
                        kind = FinanceEntryKind.EXPENSE,
                        concept = FinanceEntryConcept.of("Gasto nuevo"),
                        category = FinanceEntryCategory.of("Hogar"),
                        effectiveAtEpochMillis = 100L,
                        recordedAtEpochMillis = 200L,
                    ), scope,
                )
                reality.objectivePersistence.upsert(
                    ObjectiveRecord(
                        ObjectiveId.of("private-objective-1"),
                        ObjectiveTitle.of("Objetivo nuevo"),
                        ObjectiveDetail.of("Reservar ahorro"),
                        ObjectiveStatus.ACTIVE, 300L,
                    ), scope,
                )
                reality.matterPersistence.upsert(
                    MatterRecord(
                        MatterId.of("private-matter-1"),
                        MatterTitle.of("Asunto nuevo"),
                        MatterDetail.of("Resolver trámite"),
                        MatterStatus.WAITING, 400L, 350L,
                    ), scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }
        }

        override fun close() {
            workspace.close()
            reality.close()
        }
    }

    @Test
    fun fourRealOwnersReturnTypedResultsAndOwnerVerifiedReadOnlyDetails() = runBlocking {
        Fixture().use { f ->
            val page = f.search.search("nuevo")
            assertEquals(FaroV5SearchCoverage.COMPLETE, page.coverage)
            assertEquals(4, page.hits.size)
            assertEquals(setOf(
                FaroV5WorkspaceSearchReadSource.NAMESPACE,
                FaroV5FinanceSearchReadSource.NAMESPACE,
                FaroV5ObjectivesSearchReadSource.NAMESPACE,
                FaroV5MattersSearchReadSource.NAMESPACE,
            ), page.hits.map { it.namespace }.toSet())
            for (domain in page.hits.filter {
                it.namespace != FaroV5WorkspaceSearchReadSource.NAMESPACE
            }) {
                assertEquals(FaroV5SearchOpenResult.CONFIRMED, f.search.openAuthorized(domain))
                val target = checkNotNull(f.search.takeConfirmedDomainTarget())
                assertEquals(domain.canonicalSource, target.canonicalSource)
                assertEquals(domain.title, target.title)
                assertNull(f.search.takeConfirmedDomainTarget())
                assertFalse(target.summary.contains("123456"))
                assertFalse(target.summary.contains("private-account"))
            }
            val chat = page.hits.single {
                it.namespace == FaroV5WorkspaceSearchReadSource.NAMESPACE
            }
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, f.search.openAuthorized(chat))
            assertNull(f.search.takeConfirmedDomainTarget())
        }
    }

    @Test
    fun tamperedHitOrPreviousPageCannotNavigateAnyOwner() = runBlocking {
        Fixture().use { f ->
            val old = f.search.search("nuevo").hits.single {
                it.namespace == FaroV5ObjectivesSearchReadSource.NAMESPACE
            }
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE,
                f.search.openAuthorized(old.copy(title = "Otro objetivo")))
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE,
                f.search.openAuthorized(old.copy(opaqueId = "o:" + "a".repeat(64))))
            val new = f.search.search("nuevo").hits.single {
                it.namespace == FaroV5ObjectivesSearchReadSource.NAMESPACE
            }
            assertEquals(old.opaqueId, new.opaqueId)
            assertTrue(old.issuanceId != new.issuanceId)
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, f.search.openAuthorized(old))
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, f.search.openAuthorized(new))
        }
    }

    @Test
    fun revokedFinanceSuppressesOnlyFinanceAndMarksPartialCoverage() = runBlocking {
        Fixture().use { f ->
            val previous = f.search.search("nuevo").hits.single {
                it.namespace == FaroV5FinanceSearchReadSource.NAMESPACE
            }
            f.financeAcl = null
            val partial = f.search.search("nuevo")
            assertEquals(FaroV5SearchCoverage.PARTIAL, partial.coverage)
            assertEquals(3, partial.hits.size)
            assertTrue(partial.hits.none {
                it.namespace == FaroV5FinanceSearchReadSource.NAMESPACE
            })
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE,
                f.search.openAuthorized(previous))
            f.financeAcl = "finance-grant-2"
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE,
                f.search.openAuthorized(previous))
        }
    }

    @Test
    fun pointerSwitchRevokesAllSourcesWithoutFalseEmptyComplete() = runBlocking {
        Fixture().use { f ->
            val old = f.search.search("nuevo").hits.single()
            f.generation = "unexpected-generation"
            val unavailable = f.search.search("nuevo")
            assertEquals(FaroV5SearchCoverage.UNAVAILABLE, unavailable.coverage)
            assertTrue(unavailable.hits.isEmpty())
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, f.search.openAuthorized(old))
        }
    }

    @Test
    fun objectiveRevisionChangeDeniesPreviouslyPublishedHit() = runBlocking {
        Fixture().use { f ->
            val old = f.search.search("nuevo").hits.single {
                it.namespace == FaroV5ObjectivesSearchReadSource.NAMESPACE
            }
            f.reality.run(TransactionId("tx-objective-revise")) { scope ->
                f.reality.objectivePersistence.upsert(
                    ObjectiveRecord(
                        ObjectiveId.of("private-objective-1"),
                        ObjectiveTitle.of("Objetivo nuevo"),
                        ObjectiveDetail.of("Revisado después de buscar"),
                        ObjectiveStatus.ACTIVE, 500L,
                    ), scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, f.search.openAuthorized(old))
            val current = f.search.search("nuevo").hits.single {
                it.namespace == FaroV5ObjectivesSearchReadSource.NAMESPACE
            }
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, f.search.openAuthorized(current))
            assertTrue(checkNotNull(f.search.takeConfirmedDomainTarget()).summary.contains("Revisado"))
        }
    }

    @Test
    fun invalidationEventInvalidatesIssuedPageEvenWhenGrantStringIsUnchanged() = runBlocking {
        Fixture().use { f ->
            val subscriber = launch(start = CoroutineStart.UNDISPATCHED) {
                f.search.invalidations.collect()
            }
            try {
                val old = f.search.search("nuevo").hits.single {
                    it.namespace == FaroV5MattersSearchReadSource.NAMESPACE
                }
                f.events.emit(Unit)
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, f.search.openAuthorized(old))
                val newer = f.search.search("nuevo").hits.single {
                    it.namespace == FaroV5MattersSearchReadSource.NAMESPACE
                }
                assertEquals(FaroV5SearchOpenResult.CONFIRMED, f.search.openAuthorized(newer))
            } finally {
                subscriber.cancel()
            }
        }
    }
}
