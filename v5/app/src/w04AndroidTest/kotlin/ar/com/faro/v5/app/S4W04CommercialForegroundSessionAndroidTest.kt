package ar.com.faro.v5.app

import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.com.faro.v5.generation.FaroV5Household10Evidence2DevSelectionReadPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2C1A lifecycle-only verification. NullSource is an inert close counter, NOT
 * an Evidence2 store or a source of production/test prices.
 */
@RunWith(AndroidJUnit4::class)
class S4W04CommercialForegroundSessionAndroidTest {
    @get:Rule val rule = ActivityScenarioRule(FaroV5W04CommercialDevActivity::class.java)

    private class NullSource : FaroV5W04CommercialDevForegroundSessionGate.SelectedReadSource {
        var calls = 0
        var closes = 0
        override fun readSelected(
            needId: String,
            productKey: String,
            explicitlyChosen: () -> Boolean,
        ): FaroV5Household10Evidence2DevSelectionReadPort.SelectedRead? {
            calls++
            explicitlyChosen()
            return null
        }
        override fun close() { closes++ }
    }

    private fun gate(
        source: NullSource,
        epoch: () -> String? = { "session-one" },
        foreground: () -> Boolean = { true },
        host: () -> Boolean = { true },
        household: (String) -> Boolean = { it == "need-one" },
        commercial: (String) -> Boolean = { it == "sku-one" },
    ) = FaroV5W04CommercialDevForegroundSessionGate(
        "session-one", epoch, foreground, host, household, commercial, source,
    )

    @Test fun t01DefaultLauncherHasNoTrustedSessionOrInjectedCommercialData() {
        rule.scenario.onActivity { activity ->
            assertTrue(activity.isForegroundForTrustedRead())
            assertFalse(activity.hasTrustedGate())
            assertNull(activity.readSelectedForTrustedHost("need-one", "sku-one") { true })
            val visible = text(activity.window.decorView)
            assertTrue(visible.contains("Sin revisión comercial autorizada."))
            assertFalse(visible.contains("Comercio local"))
        }
    }

    @Test fun t02ExplicitChoiceAndIndependentGrantsAreRequiredForEveryRead() {
        val source = NullSource()
        var householdAllowed = true
        var commercialAllowed = true
        val g = gate(source,
            household = { householdAllowed && it == "need-one" },
            commercial = { commercialAllowed && it == "sku-one" })
        assertNull(g.readSelected("need-one", "sku-one") { false })
        assertEquals(0, source.calls)
        assertNull(g.readSelected("need-one", "sku-one") { true })
        assertEquals(1, source.calls)
        commercialAllowed = false
        assertNull(g.readSelected("need-one", "sku-one") { true })
        assertTrue(g.isRevoked())
        assertEquals(1, source.closes)
        commercialAllowed = true
        assertNull(g.readSelected("need-one", "sku-one") { true })
        assertEquals(1, source.calls)
        val missingHousehold = gate(NullSource(), household = { false })
        assertNull(missingHousehold.readSelected("need-one", "sku-one") { true })
        assertTrue(missingHousehold.isRevoked())
    }

    @Test fun t03PauseClosesSessionAndResumeCannotReopenOrRestoreReview() {
        val source = NullSource()
        rule.scenario.onActivity { activity ->
            val g = gate(source, foreground = { activity.isForegroundForTrustedRead() })
            assertTrue(activity.attachTrustedGate(g))
            assertTrue(activity.hasTrustedGate())
            assertNull(activity.readSelectedForTrustedHost("need-one", "sku-one") { true })
            assertEquals(1, source.calls)
        }
        rule.scenario.moveToState(Lifecycle.State.STARTED)
        assertEquals(1, source.closes)
        rule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.scenario.onActivity { activity ->
            assertTrue(activity.isForegroundForTrustedRead())
            assertFalse(activity.hasTrustedGate())
            assertNull(activity.readSelectedForTrustedHost("need-one", "sku-one") { true })
            assertTrue(text(activity.window.decorView).contains("Sin revisión comercial autorizada."))
        }
        assertEquals(1, source.calls)
    }

    @Test fun t04EpochAbaDuplicateAttachAndThrowingCallbackFailClosed() {
        val source = NullSource()
        var epoch = "session-one"
        val g = gate(source, epoch = { epoch })
        assertNull(g.readSelected("need-one", "sku-one") { true })
        epoch = "changed"
        assertNull(g.readSelected("need-one", "sku-one") { true })
        assertEquals(1, source.closes)
        epoch = "session-one" // stale authority must NOT resurrect after ABA
        assertNull(g.readSelected("need-one", "sku-one") { true })
        g.revoke()
        assertEquals(1, source.closes)
        assertEquals(1, source.calls)
        val failClosed = gate(NullSource(), host = { error("revoked") })
        assertNull(failClosed.readSelected("need-one", "sku-one") { true })
        assertTrue(failClosed.isRevoked())
        val first = NullSource()
        val second = NullSource()
        rule.scenario.onActivity { activity ->
            assertTrue(activity.attachTrustedGate(
                gate(first, foreground = { activity.isForegroundForTrustedRead() })))
            assertFalse(activity.attachTrustedGate(gate(second)))
            assertEquals(1, second.closes)
            assertTrue(activity.hasTrustedGate())
        }
    }

    private fun text(view: View): String = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            yieldAll(descendants(view.getChildAt(i)))
        }
    }.filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            yieldAll(descendants(view.getChildAt(i)))
        }
    }
}
