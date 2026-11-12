package ar.com.faro.v5.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
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
class FaroV5S3A57X06FourOwnerSearchUiTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()
    private val query = "FourCanon"
    private val namespaces = setOf("workspace.conversations", FaroV5FinanceSearchReadSource.NAMESPACE,
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
                    FinanceEntryConcept.of("FourCanon Finanzas"), FinanceEntryCategory.of("Pruebas"),
                    1000L, 2000L), scope)
                reality.objectivePersistence.upsert(ObjectiveRecord(
                    ObjectiveId.of("private-x06-objective"), ObjectiveTitle.of("FourCanon Objetivos"),
                    ObjectiveDetail.of("Objetivo de prueba local"), ObjectiveStatus.ACTIVE, 3000L), scope)
                reality.matterPersistence.upsert(MatterRecord(
                    MatterId.of("private-x06-matter"), MatterTitle.of("FourCanon Asuntos"),
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
        rule.onNode(hasText("Buscar") and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
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


    private fun sendCanonicalWorkspace() {
        val message = "FourCanon conversación Workspace"
        val editors = rule.onAllNodes(hasSetTextAction())
        assertTrue("Workspace composer must be real", editors.fetchSemanticsNodes().size >= 2)
        editors[editors.fetchSemanticsNodes().lastIndex].performTextInput(message)
        rule.onNodeWithText("Enviar").assertIsEnabled().performClick()
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onNodeWithText("Enviar").assertIsNotEnabled()
                rule.onAllNodesWithText(message, useUnmergedTree = true).fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        rule.onAllNodesWithText(message, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun x06SingleQueryShowsFourCanonicalOwnersInReality8() {
        seedEight()
        openNative()
        sendCanonicalWorkspace()
        val realPort = session().searchReadPort
        // Complete independent snapshot BEFORE Compose launches its serial-bound request.
        val page = runBlocking { realPort.search(query) }
        assertEquals(FaroV5SearchCoverage.COMPLETE, page.coverage)
        assertEquals(4, page.hits.size)
        assertEquals(namespaces, page.hits.map { it.namespace }.toSet())
        assertEquals(4, page.hits.map { it.namespace }.distinct().size)
        openSearch()
        search(query)
        awaitText("Resultados de las fuentes autorizadas.")
        for (namespace in listOf("workspace.conversations",
                FaroV5FinanceSearchReadSource.NAMESPACE,
                FaroV5ObjectivesSearchReadSource.NAMESPACE,
                FaroV5MattersSearchReadSource.NAMESPACE)) {
            val row = hasText("Fuente: $namespace", substring = true) and hasClickAction()
            // LazyColumn can recycle off-viewport rows: scroll semantically rather than asserting
            // four simultaneous pixels or issuing separate searches.
            val lazyLists = rule.onAllNodes(hasScrollToIndexAction())
            if (lazyLists.fetchSemanticsNodes().isNotEmpty()) {
                lazyLists[0].performScrollToNode(row)
            } else {
                rule.onNode(row).performScrollTo()
            }
            rule.onNode(row).assertIsDisplayed()
        }
        assertPrivateFieldsAbsent()
        rule.onAllNodesWithText("Cobertura parcial: hay resultados que todavía no se pudieron consultar.")
            .assertCountEquals(0)
        rule.onAllNodesWithText("Búsqueda no disponible. No se leyeron datos.").assertCountEquals(0)
    }
}
