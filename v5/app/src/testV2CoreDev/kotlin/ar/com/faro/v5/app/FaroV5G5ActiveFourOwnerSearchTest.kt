package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchOpenResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * G5 complementary integrated host proof. All data is test-only in @TempDir.
 * This does not represent Android physical-device certification.
 */
class FaroV5G5ActiveFourOwnerSearchTest {
    @TempDir lateinit var root: Path

    @Test fun ownerTransactionDocumentsSurviveRealDevHostReopenAndNavigateReadOnly() = runBlocking {
        // Initialise the REAL DEV journal, replay fence and five-store generation.
        // Close every connection before the separate legal seed transaction.
        FaroV5V2CoreDevAuthority(root).open().use { }
        val staged = FileStorageGenerationLayout(root).requireStaged(
            StorageGenerationId("v2-core-dev-workspace-1"),
        )
        val realityFile = staged.directory.resolve(GenerationStoreFiles.REALITY)
        RealityStore(realityFile.toString()).use { reality ->
            reality.run(TransactionId("g5-four-owner-offline-fixture")) { scope ->
                val account = FinanceAccountId.of("private-g5-account")
                reality.financePersistence.createAccount(
                    FinanceAccountCreate(
                        account,
                        FinanceAccountDisplayName.of("Cuenta de prueba"),
                        FinanceCurrencyCode.of("ARS"),
                        FinanceAccountNature.BANK,
                    ), scope,
                )
                reality.financePersistence.recordEntry(
                    FinanceEntryRecord(
                        entryId = FinanceEntryId.of("private-g5-entry"),
                        accountId = account,
                        amountMinor = 123456L,
                        kind = FinanceEntryKind.EXPENSE,
                        concept = FinanceEntryConcept.of("G5evidencia Finanzas"),
                        category = FinanceEntryCategory.of("Pruebas"),
                        effectiveAtEpochMillis = 1000L,
                        recordedAtEpochMillis = 2000L,
                    ), scope,
                )
                reality.objectivePersistence.upsert(
                    ObjectiveRecord(
                        ObjectiveId.of("private-g5-objective"),
                        ObjectiveTitle.of("G5evidencia Objetivos"),
                        ObjectiveDetail.of("Objetivo de ensayo offline"),
                        ObjectiveStatus.ACTIVE,
                        3000L,
                    ), scope,
                )
                reality.matterPersistence.upsert(
                    MatterRecord(
                        MatterId.of("private-g5-matter"),
                        MatterTitle.of("G5evidencia Asuntos"),
                        MatterDetail.of("Asunto de ensayo offline"),
                        MatterStatus.WAITING,
                        4000L,
                        3500L,
                    ), scope,
                )
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1, reality.financePersistence.read().entries.size)
            assertEquals(1, reality.objectivePersistence.read().objectives.size)
            assertEquals(1, reality.matterPersistence.read().matters.size)
        }
        // A genuine re-opened DEV host must construct the actual per-owner
        // grants + federated search, not a hand-assembled mock composition.
        val first = FaroV5V2CoreDevAuthority(root).open()
        val oldPort = first.searchReadPort
        val staleHit = try {
            val page = oldPort.search("G5evidencia")
            assertEquals(FaroV5SearchCoverage.COMPLETE, page.coverage)
            assertEquals(3, page.hits.size)
            assertEquals(
                setOf(
                    FaroV5FinanceSearchReadSource.NAMESPACE,
                    FaroV5ObjectivesSearchReadSource.NAMESPACE,
                    FaroV5MattersSearchReadSource.NAMESPACE,
                ),
                page.hits.map { it.namespace }.toSet(),
            )
            page.hits.forEach { hit ->
                assertEquals(FaroV5SearchOpenResult.CONFIRMED, oldPort.openAuthorized(hit))
                val target = checkNotNull(oldPort.takeConfirmedDomainTarget())
                assertEquals(hit.canonicalSource, target.canonicalSource)
                assertEquals(hit.title, target.title)
                assertNull(oldPort.takeConfirmedDomainTarget(), "detail is one-shot")
                assertFalse(target.summary.contains("123456"), "no raw amounts")
                assertFalse(target.summary.contains("private-g5-account"), "no account IDs")
            }
            assertEquals(FaroV5SearchCoverage.COMPLETE,
                oldPort.search("nonexistentg5query987654").coverage)
            assertTrue(oldPort.search("nonexistentg5query987654").hits.isEmpty())
            // Re-publish a real hit for the cold-reopen stale-grant probe.
            oldPort.search("G5evidencia").hits.first()
        } finally {
            first.close()
        }
        assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, oldPort.openAuthorized(staleHit))
        FaroV5V2CoreDevAuthority(root).open().use { reopened ->
            assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, oldPort.openAuthorized(staleHit))
            val newPage = reopened.searchReadPort.search("G5evidencia")
            assertEquals(FaroV5SearchCoverage.COMPLETE, newPage.coverage)
            assertEquals(3, newPage.hits.size)
            assertEquals(FaroV5SearchOpenResult.CONFIRMED,
                reopened.searchReadPort.openAuthorized(newPage.hits.first()))
            assertNotNull(reopened.searchReadPort.takeConfirmedDomainTarget())
        }
    }
}
