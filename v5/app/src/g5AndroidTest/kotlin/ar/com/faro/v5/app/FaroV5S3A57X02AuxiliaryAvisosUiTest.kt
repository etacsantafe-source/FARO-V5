package ar.com.faro.v5.app

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.com.faro.v5.delivery.api.*
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real private DEV fixture, canonical Auxiliary restore; no delivery routing or OS dispatch. */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57X02AuxiliaryAvisosUiTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()
    private val unavailable = "Avisos no disponibles. No se consultaron datos."
    private val publicText = "X02 aviso canónico normal"
    private val secret = "X02 contenido sensible nunca visible"

    private fun buttons(root: View): List<Button> = buildList {
        fun visit(v: View) {
            if (v is Button) add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
        }
        visit(root)
    }

    private fun root() = rule.activity.noBackupFilesDir.toPath().resolve("faro-v2-core-dev-root")

    private fun notice(id: String, sensitivity: NoticeSensitivity = NoticeSensitivity.NORMAL,
        relevance: NoticeRelevance = NoticeRelevance.OPEN,
        attention: NoticeAttention = NoticeAttention.UNSEEN) = NoticeSnapshot(
        NoticeId.of(id), NoticeKey.of("key-$id"), SourceRef.of("source-$id"),
        OccurrenceRef.of("occ-$id"), NoticeMateriality.NOTICE, sensitivity,
        NoticeSummary.of(when(id) { "normal" -> publicText; "private" -> secret; else -> "hidden-$id" }),
        DestinationRef.of("avisos"), relevance, attention, 1L,
        if (relevance == NoticeRelevance.RESOLVED) NoticeResolutionReason.SOURCE_RESOLVED else null,
    )

    private lateinit var fixtureFile: java.nio.file.Path

    private fun seed(): List<NoticeSnapshot> {
        // Activity has not opened any Session. Only this test package's private root is reset.
        assertEquals("ar.com.faro.alpha.v2coredev", rule.activity.packageName)
        root().toFile().deleteRecursively()
        FaroV5V2CoreDevAuthority(root()).open().use { session ->
            val graph = session.javaClass.getDeclaredField("graph").apply { isAccessible = true }
                .get(session) as FaroV5GenerationStoreGraph
            fixtureFile = graph.generationDirectory.resolve("auxiliary.db")
        }
        return AuxiliaryDeliveryStore(fixtureFile.toString()).use { store ->
            val rows = listOf(notice("normal"), notice("private", NoticeSensitivity.SENSITIVE),
                notice("resolved", relevance = NoticeRelevance.RESOLVED),
                notice("dismissed", attention = NoticeAttention.DISMISSED))
                .sortedBy { it.noticeKey.value }
            val snapshot = store.backup.snapshot().copy(notices = rows)
            assertEquals(snapshot, store.backup.restoreOrVerify(snapshot))
            assertEquals(0L, store.maintenance.inspect().deliveryIntentCount)
            rows
        }
    }

    private fun openNative(label: String) {
        rule.runOnUiThread {
            val button = buttons(rule.activity.findViewById(android.R.id.content))
                .single { it.text.toString() == label }
            assertTrue(button.isEnabled)
            assertTrue(button.performClick())
        }
        rule.waitUntil(60_000L) {
            runCatching {
                listOf("Enviar", unavailable, publicText).any {
                    rule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
                }
            }.getOrDefault(false)
        }
        // rememberSaveable restores Avisos after recreate; do not wait for Chat.
        if (rule.onAllNodesWithText("Enviar").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithContentDescription("Abrir menú").performClick()
            rule.onNodeWithText("Avisos").performClick()
        }
    }

    private fun awaitText(text: String) {
        rule.waitUntil(30_000L) {
            runCatching { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
    }

    @Test fun x02NoGestureCannotReadAuxiliaryOrInventEmpty() {
        seed()
        openNative("Abrir Chat local de desarrollo")
        awaitText(unavailable)
        rule.onAllNodesWithText(publicText).assertCountEquals(0)
        rule.onAllNodesWithText(secret).assertCountEquals(0)
        rule.onAllNodesWithText("No hay avisos abiertos.").assertCountEquals(0)
        rule.onAllNodesWithText("Ver resumen local").assertCountEquals(0)
        // A grant for Activity does not authorize Auxiliary.
        rule.activityRule.scenario.recreate()
        openNative("Abrir Chat y Actividad local")
        awaitText(unavailable)
        rule.onAllNodesWithText(publicText).assertCountEquals(0)
    }

    @Test fun x02ExplicitAuxiliarySourceRendersAndRevokesOnPause() {
        val before = seed()
        openNative("Consultar avisos registrados (DEV)")
        awaitText(publicText)
        rule.onNodeWithText("Aviso privado").assertExists()
        rule.onAllNodesWithText(secret).assertCountEquals(0)
        rule.onAllNodesWithText("hidden-resolved").assertCountEquals(0)
        rule.onAllNodesWithText("hidden-dismissed").assertCountEquals(0)
        rule.onNodeWithText("Ver resumen local").performClick()
        awaitText("Resumen solicitado: 0 requieren atención y 2 informativos en la página autorizada. Cobertura completa de la fuente autorizada.")
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitText(unavailable)
        rule.onAllNodesWithText(publicText).assertCountEquals(0)
        rule.onAllNodesWithText("Resumen solicitado:", substring = true).assertCountEquals(0)
        rule.activityRule.scenario.recreate()
        openNative("Abrir Chat local de desarrollo")
        awaitText(unavailable)
        rule.onAllNodesWithText(publicText).assertCountEquals(0)
        rule.activityRule.scenario.recreate()
        openNative("Consultar avisos registrados (DEV)")
        awaitText(publicText)
        // Close the host before independently inspecting the same canonical store.
        rule.activityRule.scenario.close()
        AuxiliaryDeliveryStore(fixtureFile.toString()).use { store ->
            assertEquals(before, store.readNotices())
            assertEquals(0L, store.maintenance.inspect().deliveryIntentCount)
        }
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }

}
