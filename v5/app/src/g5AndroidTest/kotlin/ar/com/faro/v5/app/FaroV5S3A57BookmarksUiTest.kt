package ar.com.faro.v5.app

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X04/X05: two real UI end-to-end Android API35 emulator tests.
 * An explicit human-equivalent View click admits each fresh read; never use
 * a mocked owner, a retained Activity, a productive package, or a physical G15.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57BookmarksUiTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()

    /**
     * The emulator is disposable; the physical DEV install is NOT. This gate
     * aborts before touching data on every non-emulator device. The target is
     * exactly the isolated DEV test root, never the app sandbox as a whole.
     * onCreate does not open stores; each store opens only after a native click.
     * JUnit launches a fresh Activity for every test, so no prior store is held.
     */
    @Before
    fun isolateSyntheticEmulatorScenario() {
        assertEquals("ar.com.faro.alpha.v2coredev", rule.activity.packageName)
        assertTrue("Synthetic reset is emulator-only",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val parent = rule.activity.noBackupFilesDir.canonicalFile
        val root = File(parent, "faro-v2-core-dev-root")
        assertEquals("Exact isolated root required", parent, root.canonicalFile.parentFile)
        assertFalse("Do not follow a substituted filesystem link",
            Files.isSymbolicLink(root.toPath()))
        if (root.exists()) assertTrue("Cannot reset disposable DEV test root", root.deleteRecursively())
        assertFalse("DEV test root remains", root.exists())
    }

    private fun nativeViews(): List<View> {
        var result: List<View> = emptyList()
        rule.runOnUiThread {
            val root = rule.activity.findViewById<View>(android.R.id.content)
            val output = mutableListOf<View>()
            fun visit(view: View) {
                output.add(view)
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) visit(view.getChildAt(i))
                }
            }
            visit(root)
            result = output
        }
        return result
    }

    private fun nativeLabels(): List<String> =
        nativeViews().filterIsInstance<TextView>().map { it.text.toString() }

    private fun clickNative(label: String) {
        rule.runOnUiThread {
            val root = rule.activity.findViewById<View>(android.R.id.content)
            val found = mutableListOf<Button>()
            fun visit(view: View) {
                if (view is Button && view.text.toString() == label) found.add(view)
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) visit(view.getChildAt(i))
                }
            }
            visit(root)
            assertEquals("Exactly one active native action: " + label, 1, found.size)
            assertTrue("Native action disabled: " + label, found.single().isEnabled)
            assertTrue("Native click rejected: " + label, found.single().performClick())
        }
    }

    private fun awaitNative(label: String) {
        rule.waitUntil(60_000L) { nativeLabels().any { it == label } }
        assertTrue("Missing authorized native state: " + label, nativeLabels().contains(label))
    }

    private fun awaitChat() {
        rule.waitUntil(60_000L) {
            runCatching {
                rule.onAllNodesWithText("Buscar en esta conversación", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodesWithText("Enviar").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    /** Bounded actual scroll; never assert action availability by the USER body alone. */
    private fun awaitRealBookmarkAction(body: String) {
        fun count() = runCatching {
            rule.onAllNodesWithText("Guardar / Quitar", useUnmergedTree = true)
                .fetchSemanticsNodes().size
        }.getOrDefault(-1)
        if (count() != 1) {
            // The timeline is LazyColumn: after send the card may be partially
            // outside the visible viewport, even when its body is present.
            repeat(4) {
                if (count() == 1) return@repeat
                val scrolls = rule.onAllNodes(hasScrollAction(), useUnmergedTree = true)
                val total = scrolls.fetchSemanticsNodes().size
                if (total > 0) {
                    scrolls[total - 1].performTouchInput { swipeUp() }
                    rule.waitForIdle()
                }
            }
        }
        try {
            rule.waitUntil(15_000L) { count() == 1 }
        } catch (failure: ComposeTimeoutException) {
            val messageNodes = runCatching {
                rule.onAllNodesWithText(body, useUnmergedTree = true).fetchSemanticsNodes().size
            }.getOrDefault(-1)
            val editorCount = runCatching {
                rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
            }.getOrDefault(-1)
            throw AssertionError(
                "Bookmark UI action not rendered after bounded gestures; " +
                    "actionCount=" + count() + "; syntheticMessageCount=" + messageNodes +
                    "; editorCount=" + editorCount,
                failure,
            )
        }
        assertEquals("Exactly one true Bookmark action", 1, count())
    }

    private fun safeNativeReviewSnapshot(expectedReview: String): String {
        val allowed = setOf(
            "Cargando revisión…", "No disponible", "No disponible. Volvé a abrir Guardados.",
            "Referencias activas", "Referencias quitadas", "Confirmando…",
            "Guardados actualizado", "Resultado pendiente de verificar", "Vacío",
        )
        val matched = nativeLabels().filter { it in allowed }.distinct()
        return "fixedNativeStates=" + matched.joinToString(",") +
            "; modalTitle=" + dialogHasExact("Confirmar Guardados") +
            "; expectedReview=" + dialogHasExact(expectedReview)
    }

    private fun sendAndSaveSyntheticMessage(): String {
        clickNative("Abrir Chat local de desarrollo")
        awaitChat()
        val body = "X04X05EmulatorSynthetic-" + UUID.randomUUID()
        val editors = rule.onAllNodes(hasSetTextAction())
        assertTrue("Expected Search and canonical Compose editor",
            editors.fetchSemanticsNodes().size >= 2)
        editors[editors.fetchSemanticsNodes().lastIndex].performTextInput(body)
        rule.onNodeWithText("Enviar").assertIsEnabled()
        rule.onNodeWithText("Enviar").performClick()
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onNodeWithText("Enviar").assertIsNotEnabled()
                rule.onAllNodesWithText(body, useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        rule.onAllNodesWithText(body, useUnmergedTree = true).assertCountEquals(1)
        awaitRealBookmarkAction(body)
        rule.onNodeWithText("Guardar / Quitar", useUnmergedTree = true).performClick()
        rule.waitUntil(30_000L) {
            runCatching {
                rule.onAllNodesWithText("Guardar mensaje").fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        rule.onNodeWithText("Guardar mensaje").assertExists()
        rule.onAllNodesWithText("Guardados actualizado").assertCountEquals(0)
        rule.onNodeWithText("Confirmar").performClick()
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onAllNodesWithText("Guardados actualizado").fetchSemanticsNodes().size == 1
            }.getOrDefault(false)
        }
        rule.onNodeWithText("Guardados actualizado").assertExists()
        rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
        return body
    }

    private fun reopenNativeBookmarks(expected: String, body: String) {
        // No View or Session from the previous Activity survives recreation.
        rule.activityRule.scenario.recreate()
        assertEquals("ar.com.faro.alpha.v2coredev", rule.activity.packageName)
        clickNative("Consultar Guardados locales (DEV)")
        awaitNative(expected)
        val labels = nativeLabels()
        assertTrue("Only one synthetic reference expected", labels.contains("Referencia 1"))
        assertEquals("Unexpected extra reference in isolated DEV sandbox",
            0, labels.count { it.matches(Regex("Referencia [2-9][0-9]*")) })
        assertTrue("Native metadata leaked a message body", labels.none { it.contains(body) })
        assertTrue("Native Guardados must not expose Apply", labels.none { it == "Aplicar cambios" })
    }

    private fun dialogHasExact(value: String): Boolean {
        val root = InstrumentationRegistry.getInstrumentation().uiAutomation
            .rootInActiveWindow ?: return false
        return root.findAccessibilityNodeInfosByText(value).any {
            it.text?.toString() == value
        }
    }

    private fun confirmRealNativeReview(expectedReview: String) {
        try {
            rule.waitUntil(30_000L) {
                // Detect an authorization failure separately from UiAutomation
                // visibility; keep all diagnostics fixed-label and metadata-only.
                if (nativeLabels().any {
                    it == "No disponible. Volvé a abrir Guardados."
                }) throw AssertionError("Native Bookmark mutation rejected: " +
                    safeNativeReviewSnapshot(expectedReview))
                dialogHasExact("Confirmar Guardados") && dialogHasExact(expectedReview)
            }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError(
                "Native Bookmark review not observed: " +
                    safeNativeReviewSnapshot(expectedReview), failure,
            )
        }
        assertTrue("Native review title absent", dialogHasExact("Confirmar Guardados"))
        assertTrue("Unexpected native mutation review", dialogHasExact(expectedReview))
        val root = checkNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
        )
        val choices = root.findAccessibilityNodeInfosByText("Confirmar").filter {
            it.text?.toString() == "Confirmar" &&
                it.className?.toString() == "android.widget.Button" && it.isClickable
        }
        assertEquals("Exactly one native positive review button", 1, choices.size)
        assertTrue("Native accessibility click not accepted",
            choices.single().performAction(AccessibilityNodeInfo.ACTION_CLICK))
        awaitNative("Guardados actualizado")
    }

    private fun assertOriginalSource(body: String) {
        clickNative("Abrir fuente")
        awaitChat()
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onAllNodesWithText(body, useUnmergedTree = true)
                    .fetchSemanticsNodes().size == 1 &&
                    rule.onAllNodesWithText("Enviar").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.onAllNodesWithText(body, useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithText("Enviar").assertIsNotEnabled()
        rule.onAllNodesWithText("Aplicar cambios").assertCountEquals(0)
    }

    @Test
    fun x04RealSaveConfirmNativeReadAndOpenOriginalChat() {
        val body = sendAndSaveSyntheticMessage()
        reopenNativeBookmarks("Referencias activas", body)
        assertOriginalSource(body)
        // Scopes the proof to original canonical content, not physical scroll pixels.
    }

    @Test
    fun x05RealRemoveExplicitRecoveryRestoreAndReopenCanonicalSource() {
        val body = sendAndSaveSyntheticMessage()
        reopenNativeBookmarks("Referencias activas", body)
        clickNative("Quitar referencia")
        confirmRealNativeReview("Quitar referencia de Guardados")
        // prepare() clears rows: a fresh read, not vanished rows, proves removal.
        clickNative("Ver activos")
        awaitNative("Vacío")
        assertFalse("Removed reference must not remain active",
            nativeLabels().contains("Referencia 1"))
        clickNative("Ver quitados")
        awaitNative("Referencias quitadas")
        assertTrue("Explicit recovery must return the synthetic item",
            nativeLabels().contains("Referencia quitada 1"))
        clickNative("Restaurar")
        confirmRealNativeReview("Restaurar referencia quitada")
        reopenNativeBookmarks("Referencias activas", body)
        assertOriginalSource(body)
    }
}
