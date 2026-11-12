package ar.com.faro.v5.app

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.surface.FaroV5SearchCoverage
import ar.com.faro.v5.surface.FaroV5SearchSurfacePolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Disposable offline emulator only; actual OS configuration and canonical Auxiliary owner. */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57X12AdaptiveSemanticsUiTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val unavailable = "Avisos no disponibles. No se consultaron datos."
    private val publicText = "X12 aviso normal"
    private val secret = "X12 sensible nunca visible"
    private val buttonRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun configure(expanded: Boolean, body: () -> Unit) {
        check(android.os.Build.MODEL.contains("sdk", ignoreCase = true) ||
            android.os.Build.FINGERPRINT.contains("emulator")) { "Disposable emulator required" }
        val font = shell("settings get system font_scale").trim()
        val size = shell("wm size").lineSequence().firstOrNull { it.startsWith("Override size:") }
            ?.substringAfter(":")?.trim()
        val density = shell("wm density").lineSequence().firstOrNull { it.startsWith("Override density:") }
            ?.substringAfter(":")?.trim()
        try {
            shell("settings put system font_scale ${if (expanded) "1.0" else "2.0"}")
            if (expanded) { shell("wm size 1600x1200"); shell("wm density 160") }
            rule.waitUntil(60_000L) {
                val config = rule.activity.resources.configuration
                if (expanded) config.screenWidthDp >= 840 && config.fontScale == 1f
                else config.fontScale >= 1.99f
            }
            body()
        } finally {
            shell("wm size ${size ?: "reset"}")
            shell("wm density ${density ?: "reset"}")
            if (font == "null") shell("settings delete system font_scale")
            else shell("settings put system font_scale $font")
        }
    }

    private fun seed() {
        assertEquals("ar.com.faro.alpha.v2coredev", rule.activity.packageName)
        val root = rule.activity.noBackupFilesDir.toPath().resolve("faro-v2-core-dev-root")
        root.toFile().deleteRecursively()
        val auxiliary = FaroV5V2CoreDevAuthority(root).open().use { session ->
            val graph = session.javaClass.getDeclaredField("graph").apply { isAccessible = true }
                .get(session) as FaroV5GenerationStoreGraph
            graph.generationDirectory.resolve("auxiliary.db")
        }
        AuxiliaryDeliveryStore(auxiliary.toString()).use { store ->
            val notices = listOf(false, true).map { sensitive ->
                val id = if (sensitive) "private" else "normal"
                NoticeSnapshot(NoticeId.of(id), NoticeKey.of("key-$id"), SourceRef.of("source-$id"),
                    OccurrenceRef.of("occ-$id"), NoticeMateriality.NOTICE,
                    if (sensitive) NoticeSensitivity.SENSITIVE else NoticeSensitivity.NORMAL,
                    NoticeSummary.of(if (sensitive) secret else publicText), DestinationRef.of("avisos"),
                    NoticeRelevance.OPEN, NoticeAttention.UNSEEN, 1L, null)
            }.sortedBy { it.noticeKey.value }
            val snapshot = store.backup.snapshot().copy(notices = notices)
            assertEquals(snapshot, store.backup.restoreOrVerify(snapshot))
            assertEquals(0L, store.maintenance.inspect().deliveryIntentCount)
        }
    }

    private fun awaitText(text: String) = rule.waitUntil(30_000L) {
        runCatching { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    private fun native(label: String) {
        rule.runOnUiThread {
            fun buttons(v: View): List<Button> = buildList {
                if (v is Button) add(v)
                if (v is ViewGroup) for (i in 0 until v.childCount) addAll(buttons(v.getChildAt(i)))
            }
            assertTrue(buttons(rule.activity.findViewById(android.R.id.content))
                .single { it.text.toString() == label }.performClick())
        }
        rule.waitUntil(60_000L) {
            runCatching { rule.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    private fun navigate(label: String, expanded: Boolean) {
        if (!expanded) rule.onNodeWithContentDescription("Abrir menú").performClick()
        // Drawer items live in a scrollable parent; headings share their labels.
        rule.onNode(hasText(label) and hasClickAction() and !buttonRole)
            .performScrollTo().performClick()
    }

    private fun visible(node: SemanticsNodeInteraction, interactive: Boolean = false) {
        node.assertIsDisplayed()
        val bounds = node.getUnclippedBoundsInRoot()
        val viewport = rule.onAllNodes(isRoot())[0].getUnclippedBoundsInRoot()
        assertTrue("unclipped target $bounds outside $viewport", bounds.left >= viewport.left &&
            bounds.top >= viewport.top && bounds.right <= viewport.right && bounds.bottom <= viewport.bottom)
        if (interactive) {
            assertTrue("target width from $bounds", bounds.right.value - bounds.left.value >= 48f)
            assertTrue("target height from $bounds", bounds.bottom.value - bounds.top.value >= 48f)
        }
    }

    private fun positive(expanded: Boolean) {
        awaitText(publicText)
        visible(rule.onNode(hasText("Avisos") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)))
        visible(rule.onNodeWithText(publicText))
        rule.onAllNodesWithText(secret).assertCountEquals(0)
        for (id in listOf("source-normal", "occ-normal", "key-normal"))
            rule.onAllNodesWithText(id, substring = true).assertCountEquals(0)
        val summary = rule.onNode(hasText("Ver resumen local") and buttonRole and hasClickAction())
        visible(summary, true)
        summary.performClick()
        awaitText("Resumen solicitado: 0 requieren atención y 2 informativos en la página autorizada. Cobertura completa de la fuente autorizada.")
        if (expanded) rule.onAllNodesWithContentDescription("Abrir menú").assertCountEquals(0)
    }

    private fun evidence(name: String) {
        val config = rule.activity.resources.configuration
        val directory = File(rule.activity.getExternalFilesDir(null), "x12-evidence").apply { mkdirs() }
        File(directory, "$name.txt").writeText("fontScale=${config.fontScale}\nscreenWidthDp=${config.screenWidthDp}\n" +
            shell("wm size") + shell("wm density"))
        val bitmap = checkNotNull(automation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun revoked() {
        awaitText(unavailable)
        rule.onAllNodesWithText(publicText).assertCountEquals(0)
        rule.onAllNodesWithText("Resumen solicitado:", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Ver resumen local").assertCountEquals(0)
    }

    private fun session(): FaroV5V2CoreDevAuthority.Session {
        var current: FaroV5V2CoreDevAuthority.Session? = null
        rule.runOnUiThread {
            current = rule.activity.javaClass.getDeclaredField("session").apply { isAccessible = true }
                .get(rule.activity) as FaroV5V2CoreDevAuthority.Session
        }
        return requireNotNull(current)
    }

    private fun searchWithoutAuxiliaryGrant(expanded: Boolean, revokedExpected: Boolean) {
        navigate("Buscar", expanded)
        awaitText("Buscar en FARO")
        val field = rule.onNode(hasSetTextAction())
        visible(field)
        field.performTextReplacement("X12")
        val search = rule.onNode(hasText("Buscar") and buttonRole and hasClickAction())
        visible(search, true)
        search.performClick()
        if (revokedExpected) {
            awaitText("Búsqueda no disponible. No se leyeron datos.")
        } else {
            // NEW native foreground host grants Search independently. Auxiliary is never a Search source.
            val page = runBlocking { session().searchReadPort.search("X12") }
            assertNotEquals(FaroV5SearchCoverage.UNAVAILABLE, page.coverage)
            assertTrue("Auxiliary notice must not become a Search hit", page.hits.isEmpty())
            awaitText(FaroV5SearchSurfacePolicy.message(page))
        }
        rule.onAllNodesWithText(secret).assertCountEquals(0)
        rule.onAllNodesWithText(publicText).assertCountEquals(0)
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }

    @Test fun x12LargeFontCanonicalAvisosActionsAndRevocation() = configure(false) {
        seed()
        native("Consultar avisos registrados (DEV)")
        navigate("Avisos", false)
        positive(false)
        evidence("font200")
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        revoked()
        searchWithoutAuxiliaryGrant(false, true)
    }

    @Test fun x12ExpandedWindowNativeDrawerAndRestoredSourceIsolation() = configure(true) {
        seed()
        native("Consultar avisos registrados (DEV)")
        navigate("Avisos", true)
        positive(true)
        evidence("expanded")
        val oldSearch = session().searchReadPort
        rule.activityRule.scenario.recreate()
        assertEquals(FaroV5SearchCoverage.UNAVAILABLE, runBlocking { oldSearch.search("X12") }.coverage)
        native("Abrir Chat local de desarrollo")
        // Saved destination remains Avisos; a generic Chat action cannot restore its source grant.
        revoked()
        searchWithoutAuxiliaryGrant(true, false)
        rule.activityRule.scenario.recreate()
        native("Consultar avisos registrados (DEV)")
        // Search may be restored; only the explicit Auxiliary gesture re-admits Avisos.
        navigate("Avisos", true)
        positive(true)
    }
}
