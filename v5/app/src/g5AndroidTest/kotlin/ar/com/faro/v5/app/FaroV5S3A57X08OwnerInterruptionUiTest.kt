package ar.com.faro.v5.app

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.surface.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real source/adapter + Android semantics; third test uses actual DEV Session.
 * No fake page, hit or snapshot, production fault hook, baseline test or G15. */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57X08OwnerInterruptionUiTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()
    private val token = "X08Canon"

    private class Fixture(val root: Path) : AutoCloseable {
        private val alive = AtomicBoolean(true)
        val pointer = FileActiveGenerationPointer(root)
        val graph: FaroV5GenerationStoreGraph = openSeededGraph()
        val pinned = requireNotNull(pointer.read())
        val grants = List(4) {
            FaroV5V2CoreDevSearchReadAuthority {
                alive.get() && pointer.read() == pinned
            }.also { it.grantAfterVerifiedOpen() }
        }
        private val events = merge(*grants.map { it.invalidations }.toTypedArray())
        private var interruption: (() -> Unit)? = null
        var boundaryReads = 0
            private set
        private val financeView = FinanceSnapshotReadApi {
            val actual = graph.reality.financePersistence.read()
            if (interruption != null && ++boundaryReads == 2) {
                val effect = interruption
                interruption = null
                effect?.invoke()
            }
            actual
        }
        val finance = FaroV5FinanceSearchReadSource(graph.generationId, financeView,
            { pointer.read()?.generationId?.value }, { grants[1].epochOrNull() },
            { alive.get() && pointer.read() == pinned })
        private val workspace = requireNotNull(graph.workspace)
        private val workspaceSource = FaroV5WorkspaceSearchReadSource(graph.generationId,
            workspace, { pointer.read()?.generationId?.value }, { grants[0].epochOrNull() },
            { alive.get() && pointer.read() == pinned })
        private val workspaceSurface = FaroV5WorkspaceSearchSurfaceAdapter(workspaceSource,
            workspace, events)
        private val objectives = FaroV5ObjectivesSearchReadSource(graph.generationId,
            graph.reality.objectivePersistence, { pointer.read()?.generationId?.value },
            { grants[2].epochOrNull() }, { alive.get() && pointer.read() == pinned })
        private val matters = FaroV5MattersSearchReadSource(graph.generationId,
            graph.reality.matterPersistence, { pointer.read()?.generationId?.value },
            { grants[3].epochOrNull() }, { alive.get() && pointer.read() == pinned })
        val search = FaroV5FederatedSearchSurfaceAdapter(workspaceSource, workspaceSurface,
            finance, objectives, matters, events)

        private fun openSeededGraph(): FaroV5GenerationStoreGraph {
            FaroV5V2CoreDevAuthority(root).open().close()
            FaroV5V2CoreDevAuthority(root)
                .upgradePlanning8AfterExplicitForegroundRequest { true }.close()
            val active = requireNotNull(pointer.read())
            val graph = FaroV5ActiveGenerationGraphOwner.openExistingPlanning8ForCertifiedHost(
                root, active.generationId.value, active.sequence, SingleProcessMaterialWriteGate(), true)
            try {
                val reality = graph.reality
                check(reality.maintenance.inspect().schemaVersion == 8)
                reality.run(TransactionId("x08-canonical-seed")) { scope ->
                    val account = FinanceAccountId.of("private-x08-account")
                    reality.financePersistence.createAccount(FinanceAccountCreate(account,
                        FinanceAccountDisplayName.of("Cuenta privada"), FinanceCurrencyCode.of("ARS"),
                        FinanceAccountNature.BANK), scope)
                    reality.financePersistence.recordEntry(FinanceEntryRecord(
                        FinanceEntryId.of("private-x08-entry"), account, 987654L, FinanceEntryKind.EXPENSE,
                        FinanceEntryConcept.of("X08Canon FinanceOnly"), FinanceEntryCategory.of("Pruebas"),
                        1000L, 2000L), scope)
                    reality.objectivePersistence.upsert(ObjectiveRecord(
                        ObjectiveId.of("private-x08-objective"), ObjectiveTitle.of("X08Canon Objetivo"),
                        ObjectiveDetail.of("Dato local"), ObjectiveStatus.ACTIVE, 3000L), scope)
                    reality.matterPersistence.upsert(MatterRecord(
                        MatterId.of("private-x08-matter"), MatterTitle.of("X08Canon Asunto"),
                        MatterDetail.of("Dato local"), MatterStatus.WAITING, 4000L, 3500L), scope)
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
                check(graph.verifyHealthy())
                return graph
            } catch (failure: Throwable) {
                graph.close()
                root.toFile().deleteRecursively()
                throw failure
            }
        }

        fun armSecondCanonicalSnapshot(effect: () -> Unit) {
            boundaryReads = 0
            interruption = effect
        }

        fun closeReadHost() {
            if (alive.compareAndSet(true, false)) {
                grants.forEach { it.close() }
                graph.close()
            }
        }

        override fun close() {
            closeReadHost()
            root.toFile().deleteRecursively()
        }
    }

    private fun fixture() = Fixture(Files.createTempDirectory(rule.activity.cacheDir.toPath(), "x08-real-"))

    private fun validFinanceHit(f: Fixture): FaroV5SearchHit = runBlocking {
        val page = f.search.search(token)
        assertEquals(FaroV5SearchCoverage.COMPLETE, page.coverage)
        assertEquals(3, page.hits.size)
        page.hits.single { it.namespace == FaroV5FinanceSearchReadSource.NAMESPACE }.also {
            assertEquals(FaroV5SearchOpenResult.CONFIRMED, f.search.openAuthorized(it))
            assertNotNull(f.search.takeConfirmedDomainTarget())
        }
    }

    private fun assertOldHitDenied(f: Fixture, hit: FaroV5SearchHit) {
        assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, runBlocking { f.search.openAuthorized(hit) })
        assertNull(f.search.takeConfirmedDomainTarget())
    }

    private fun mountActualSearch(f: Fixture) {
        // The existing :surface bridge owns the compiled Compose ABI.
        rule.runOnUiThread {
            FaroV5SurfaceHost.install(activity = rule.activity,
                searchReadPort = f.search,
                assistantRequestMode = FaroV5AssistantRequestMode.EXPLICIT_CONSULT)
        }
        rule.waitUntil(30_000L) {
            runCatching { rule.onAllNodesWithContentDescription("Abrir menú")
                .fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        rule.onNodeWithContentDescription("Abrir menú").performClick()
        rule.onNodeWithText("Buscar").performClick()
        rule.waitUntil(30_000L) {
            rule.onAllNodesWithText("Buscar en FARO", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1
        }
        rule.waitForIdle()
    }

    private fun query(text: String, expected: String) {
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement(text)
        rule.onNode(hasText("Buscar") and hasClickAction() and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        rule.waitUntil(30_000L) {
            rule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("No se encontraron resultados en las fuentes consultadas.")
            .assertCountEquals(0)
        rule.onAllNodesWithText("Fuente: " + FaroV5FinanceSearchReadSource.NAMESPACE, substring = true)
            .assertCountEquals(0)
        rule.onAllNodesWithText("987654", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("private-x08-", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
    }

    @Test fun x08FinanceGrantRevokedAfterCanonicalReadYieldsPartial() {
        fixture().use { f ->
            val old = validFinanceHit(f)
            f.armSecondCanonicalSnapshot { f.grants[1].close() }
            val denied = runCatching { f.finance.readAuthorized(FaroV5FinanceSearchReadSource.NAMESPACE, 50) }
            assertTrue("A revoked mid-read snapshot must not be published", denied.isFailure)
            assertEquals(2, f.boundaryReads)
            assertOldHitDenied(f, old) // no intervening query invalidated its issuance
            val page = runBlocking { f.search.search(token) }
            assertEquals(FaroV5SearchCoverage.PARTIAL, page.coverage)
            assertEquals(setOf(FaroV5ObjectivesSearchReadSource.NAMESPACE,
                FaroV5MattersSearchReadSource.NAMESPACE), page.hits.map { it.namespace }.toSet())
            mountActualSearch(f)
            query("FinanceOnly", "Cobertura parcial: pueden existir más resultados.")
            query(token, "Cobertura parcial: hay resultados que todavía no se pudieron consultar.")
        }
    }

    @Test fun x08OwnerClosedAfterSnapshotCannotPublishOrNavigate() {
        fixture().use { f ->
            val old = validFinanceHit(f)
            f.armSecondCanonicalSnapshot { f.graph.reality.close() }
            val denied = runCatching { f.finance.readAuthorized(FaroV5FinanceSearchReadSource.NAMESPACE, 50) }
            assertTrue("Closed real SQLite owner must not publish captured snapshot", denied.isFailure)
            assertEquals(2, f.boundaryReads)
            assertOldHitDenied(f, old)
            val page = runBlocking { f.search.search(token) }
            assertEquals(FaroV5SearchCoverage.PARTIAL, page.coverage)
            assertTrue(page.hits.isEmpty()) // Workspace is healthy and authorized, genuinely empty.
            mountActualSearch(f)
            query(token, "Cobertura parcial: pueden existir más resultados.")
            rule.onAllNodesWithText("Búsqueda no disponible. No se leyeron datos.").assertCountEquals(0)
        }
    }

    @Test fun x08DurablePointerABARejectsRealityHitWithoutRegrant() {
        fixture().use { f ->
            f.closeReadHost() // no duplicate open fixture graph/grants
            FaroV5V2CoreDevAuthority(f.root).open().use { actualSession ->
                val port = actualSession.searchReadPort
                val old = runBlocking { port.search(token) }.hits.single {
                    it.namespace == FaroV5FinanceSearchReadSource.NAMESPACE
                }
                assertEquals(FaroV5SearchOpenResult.CONFIRMED, runBlocking { port.openAuthorized(old) })
                assertNotNull(port.takeConfirmedDomainTarget())
                val independentWriter = FileActiveGenerationPointer(f.root)
                val a = requireNotNull(independentWriter.read())
                val b = StorageGenerationId("x08-injected-unopened-generation-B")
                assertNotNull(independentWriter.compareAndSet(a.generationId, b))
                val restored = requireNotNull(independentWriter.compareAndSet(b, a.generationId))
                assertEquals(a.generationId, restored.generationId)
                assertTrue(restored.sequence > a.sequence)
                assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, runBlocking { port.openAuthorized(old) })
                assertNull(port.takeConfirmedDomainTarget())
                val denied = runBlocking { port.search(token) }
                assertEquals(FaroV5SearchCoverage.UNAVAILABLE, denied.coverage)
                assertTrue(denied.hits.isEmpty())
            }
        }
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }
}
