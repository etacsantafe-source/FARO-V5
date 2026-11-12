package ar.com.faro.v5.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.owner.finance.api.*
import ar.com.faro.v5.owner.objectives.api.*
import ar.com.faro.v5.owner.matters.api.*
import ar.com.faro.v5.surface.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** New Reality8 Android proof only. No historical Workspace tests or user data. */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57X06ThreeOwnerSearchUiTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()
    private val query = "X06Canon"
    private val namespaces = setOf(FaroV5FinanceSearchReadSource.NAMESPACE,
        FaroV5ObjectivesSearchReadSource.NAMESPACE, FaroV5MattersSearchReadSource.NAMESPACE)

    private fun seedEight() {
        assertEquals("ar.com.faro.alpha.v2coredev", rule.activity.packageName)
        val root = rule.activity.noBackupFilesDir.toPath().resolve("faro-v2-core-dev-root")
        root.toFile().deleteRecursively()
        FaroV5V2CoreDevAuthority(root).open().close()
        // Explicitly authorized, fresh test-only setup, never production migration.
        FaroV5V2CoreDevAuthority(root)
            .upgradePlanning8AfterExplicitForegroundRequest { true }.close()
        val pinned = requireNotNull(FileActiveGenerationPointer(root).read())
        FaroV5ActiveGenerationGraphOwner.openExistingPlanning8ForCertifiedHost(
            root, pinned.generationId.value, pinned.sequence, SingleProcessMaterialWriteGate(), true,
        ).use { graph ->
            val reality = graph.reality
            assertEquals(8, reality.maintenance.inspect().schemaVersion)
            reality.run(TransactionId("x06-android-seed-eight")) { scope ->
                val account = FinanceAccountId.of("private-x06-account")
                reality.financePersistence.createAccount(FinanceAccountCreate(account,
                    FinanceAccountDisplayName.of("Cuenta privada"), FinanceCurrencyCode.of("ARS"),
                    FinanceAccountNature.BANK), scope)
                reality.financePersistence.recordEntry(FinanceEntryRecord(
                    FinanceEntryId.of("private-x06-entry"), account, 123456L, FinanceEntryKind.EXPENSE,
                    FinanceEntryConcept.of("X06Canon Finanzas"), FinanceEntryCategory.of("Pruebas"),
                    1000L, 2000L), scope)
                reality.objectivePersistence.upsert(ObjectiveRecord(
                    ObjectiveId.of("private-x06-objective"), ObjectiveTitle.of("X06Canon Objetivos"),
                    ObjectiveDetail.of("Objetivo de prueba local"), ObjectiveStatus.ACTIVE, 3000L), scope)
                reality.matterPersistence.upsert(MatterRecord(
                    MatterId.of("private-x06-matter"), MatterTitle.of("X06Canon Asuntos"),
                    MatterDetail.of("Asunto de prueba local"), MatterStatus.WAITING, 4000L, 3500L), scope)
                AtomicApplyTransactionDecision.Commit(Unit)
            }
            assertEquals(1, reality.financePersistence.read().entries.size)
            assertEquals(1, reality.objectivePersistence.read().objectives.size)
            assertEquals(1, reality.matterPersistence.read().matters.size)
            assertTrue(graph.verifyHealthy())
        }
    }

    private fun session(): FaroV5V2CoreDevAuthority.Session {
        var current: FaroV5V2CoreDevAuthority.Session? = null
        rule.runOnUiThread {
            current = rule.activity.javaClass.getDeclaredField("session").apply { isAccessible = true }
                .get(rule.activity) as FaroV5V2CoreDevAuthority.Session
        }
        return requireNotNull(current)
    }

    private fun openNative(restoredSearch: Boolean = false) {
        rule.runOnUiThread {
            fun buttons(v: View): List<Button> = buildList {
                if (v is Button) add(v)
                if (v is ViewGroup) for (i in 0 until v.childCount) addAll(buttons(v.getChildAt(i)))
            }
            val button = buttons(rule.activity.findViewById(android.R.id.content))
                .single { it.text.toString() == "Abrir Chat local de desarrollo" }
            assertTrue(button.isEnabled)
            assertTrue(button.performClick())
        }
        rule.waitUntil(60_000L) {
            runCatching {
                rule.onAllNodesWithText(if (restoredSearch) "Buscar en FARO" else "Enviar",
                    useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        val graph = session().javaClass.getDeclaredField("graph").apply { isAccessible = true }
            .get(session()) as FaroV5GenerationStoreGraph
        assertEquals(8, graph.reality.maintenance.inspect().schemaVersion)
        rule.waitForIdle()
    }

    private fun openSearch() {
        rule.onNodeWithContentDescription("Abrir menú").performClick()
        rule.onNodeWithText("Buscar").performClick()
        rule.waitUntil(30_000L) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1 }
    }

    private fun search(text: String) {
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement(text)
        rule.onAllNodes(hasText("Buscar") and hasClickAction())[0].performClick()
    }

    private fun awaitText(text: String, substring: Boolean = false) {
        rule.waitUntil(30_000L) {
            runCatching { rule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    private fun assertPrivateFieldsAbsent() {
        for (privateValue in listOf("123456", "private-x06-account", "private-x06-entry",
                "private-x06-objective", "private-x06-matter")) {
            rule.onAllNodesWithText(privateValue, substring = true).assertCountEquals(0)
        }
        rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }

    @Test fun x06ThreeCanonicalRealityOwnersSearchFromAndroidWithoutPrivateFields() {
        seedEight()
        openNative()
        val port = session().searchReadPort
        val page = runBlocking { port.search(query) }
        assertEquals(FaroV5SearchCoverage.COMPLETE, page.coverage)
        assertEquals(namespaces, page.hits.map { it.namespace }.toSet())
        assertEquals(3, page.hits.size)
        openSearch()
        for ((title, namespace) in listOf("X06Canon Finanzas" to FaroV5FinanceSearchReadSource.NAMESPACE,
                "X06Canon Objetivos" to FaroV5ObjectivesSearchReadSource.NAMESPACE,
                "X06Canon Asuntos" to FaroV5MattersSearchReadSource.NAMESPACE)) {
            search(title)
            awaitText("Fuente: $namespace", substring = true)
            rule.onNode(hasText("Fuente: $namespace", substring = true) and hasClickAction())
                .assertIsDisplayed().performClick()
            awaitText("Detalle de solo lectura. Las acciones se realizan desde Chat y Review.")
            rule.onNodeWithText("Fuente: $namespace").assertIsDisplayed()
            assertPrivateFieldsAbsent()
            rule.onNodeWithText("Volver a Buscar").performClick()
        }
    }

    @Test fun x06RevokedRealityOwnerHitDoesNotOpenAfterPause() {
        seedEight()
        openNative()
        openSearch()
        search(query)
        awaitText("Fuente: " + FaroV5MattersSearchReadSource.NAMESPACE, substring = true)
        val oldPort = session().searchReadPort
        val oldHit = runBlocking { oldPort.search(query) }.hits.single {
            it.namespace == FaroV5FinanceSearchReadSource.NAMESPACE
        }
        // Prove this exact genuine locator works immediately BEFORE pause.
        assertEquals(FaroV5SearchOpenResult.CONFIRMED, runBlocking { oldPort.openAuthorized(oldHit) })
        assertNotNull(oldPort.takeConfirmedDomainTarget())
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, runBlocking { oldPort.openAuthorized(oldHit) })
        assertNull(oldPort.takeConfirmedDomainTarget())
        rule.waitUntil(30_000L) {
            rule.onAllNodesWithText("Fuente: reality.", substring = true).fetchSemanticsNodes().isEmpty()
        }
        search(query)
        awaitText("Búsqueda no disponible. No se leyeron datos.")
        rule.onAllNodesWithText("No se encontraron resultados en las fuentes consultadas.")
            .assertCountEquals(0)
        assertPrivateFieldsAbsent()
        rule.activityRule.scenario.recreate()
        openNative(restoredSearch = true)
        val freshPort = session().searchReadPort
        assertNotSame(oldPort, freshPort)
        assertEquals(FaroV5SearchOpenResult.UNAVAILABLE, runBlocking { oldPort.openAuthorized(oldHit) })
        val fresh = runBlocking { freshPort.search(query) }
        assertEquals(FaroV5SearchCoverage.COMPLETE, fresh.coverage)
        assertEquals(namespaces, fresh.hits.map { it.namespace }.toSet())
        search("X06Canon Finanzas")
        awaitText("Fuente: " + FaroV5FinanceSearchReadSource.NAMESPACE, substring = true)
        assertPrivateFieldsAbsent()
    }
}
