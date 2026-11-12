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
import ar.com.faro.v5.generation.FaroV5GenerationStoreGraph
import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.store.reality.RealityStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X01-only Android integration of a true Reality COMMITTED receipt in Activity.
 * No model, production Apply, fixture DTO, fake owner or backend network.
 */
@RunWith(AndroidJUnit4::class)
class FaroV5S3A57ActivityReceiptComposeUiTest {
    @get:Rule val rule = createAndroidComposeRule<FaroV5V2CoreDevActivity>()

    private fun buttons(root: View): List<Button> = buildList {
        fun visit(v: View) {
            if (v is Button) add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
        }
        visit(root)
    }

    private fun openNative(label: String) {
        rule.runOnUiThread {
            val root = rule.activity.findViewById<View>(android.R.id.content)
            val button = buttons(root).single { it.text.toString() == label }
            assertTrue("Foreground button not enabled", button.isEnabled)
            assertTrue("Native foreground click rejected", button.performClick())
        }
        rule.waitUntil(60_000L) {
            runCatching {
                rule.onAllNodesWithText("Enviar").fetchSemanticsNodes().isNotEmpty() &&
                    rule.onAllNodesWithText("Buscar en esta conversación",
                        useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.waitForIdle()
    }

    private fun activeReality(): RealityStore {
        var reality: RealityStore? = null
        rule.runOnUiThread {
            val a = rule.activity
            val session = a.javaClass.getDeclaredField("session").apply {
                isAccessible = true
            }.get(a) ?: error("V2 DEV real session absent")
            val graph = session.javaClass.getDeclaredField("graph").apply {
                isAccessible = true
            }.get(session) as FaroV5GenerationStoreGraph
            reality = graph.reality
        }
        return requireNotNull(reality)
    }

    @Test fun x01CanonicalRealityReceiptVisibleOnlyAfterForegroundReadAndRevokedOnPause() {
        assertEquals("ar.com.faro.alpha.v2coredev", rule.activity.packageName)
        openNative("Abrir Chat local de desarrollo")
        val reality = activeReality()
        val fingerprint = PlanFingerprint("x01-android-reality-" + UUID.randomUUID())
        val synthetic = object : PreparedPlanView {
            override val applyPlan: ApplyPlan
                get() = error("test-only receipt fixture has no executable plan")
            override val planFingerprint: PlanFingerprint = fingerprint
        }
        val identity = reality.applyAttemptIdentities.resolve(synthetic)
        // A genuine owner transaction writes the durable receipt, not a mock DTO.
        reality.run(identity.transactionId) { scope ->
            reality.commit(scope, identity, emptyList())
            AtomicApplyTransactionDecision.Commit(Unit)
        }
        val receipt = requireNotNull(reality.executionReceiptLookup.find(identity.transactionId))
        assertEquals(identity.transactionId, receipt.transactionId)
        assertEquals(fingerprint, receipt.planFingerprint)
        assertTrue(reality.timedReceiptHistory.query(
            ar.com.faro.v5.store.reality.RealityTimedReceiptQuery()
        ).receipts.any { it.receipt == receipt })

        // The previous session must close. Only a fresh foreground click can
        // grant both source ACLs and mount canonical Activity.
        rule.activityRule.scenario.recreate()
        openNative("Abrir Chat y Actividad local")
        rule.onNodeWithContentDescription("Abrir menú").performClick()
        rule.onNodeWithText("Actividad").performClick()
        rule.waitUntil(45_000L) {
            runCatching {
                rule.onAllNodesWithText("Aplicación confirmada")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.onNodeWithText("Aplicación confirmada").assertExists()
        assertTrue("Commit time provenance not shown",
            rule.onAllNodesWithText("Confirmación registrada (UTC)", substring = true)
                .fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText(receipt.transactionId.value, substring = true)
            .assertCountEquals(0)
        rule.onAllNodesWithText(receipt.receiptId.value, substring = true)
            .assertCountEquals(0)
        rule.onAllNodesWithText(fingerprint.value, substring = true)
            .assertCountEquals(0)

        // Android lifecycle revokes source authority: never show stale Activity.
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(30_000L) {
            runCatching {
                rule.onAllNodesWithText("Actividad no disponible. No se consultaron datos.")
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        rule.onAllNodesWithText("Aplicación confirmada").assertCountEquals(0)
        assertFalse(FaroV5HostCertification.V2_ACTIVE_ENABLED)
    }
}
