package ar.com.faro.v5.app

import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.com.faro.v5.app.S4W04CommercialReviewPresentation.Decision
import ar.com.faro.v5.app.S4W04CommercialReviewPresentation.GroupDisplay
import ar.com.faro.v5.app.S4W04CommercialReviewPresentation.ScenarioDisclosure
import ar.com.faro.v5.app.S4W04CommercialReviewPresentation.ScenarioScope
import ar.com.faro.v5.app.S4W04CommercialLiveReadRecheck.Coverage
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.BenefitKind
import ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotes.Notice
import ar.com.faro.v5.app.S4W04ExplicitObservationTrace.TracedLine
import ar.com.faro.v5.app.S4W04ObservedCartBudget.GroupKey
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI fixtures only. Does NOT certify a live commercial owner/host. */
@RunWith(AndroidJUnit4::class)
class S4W04CommercialAndroidTest {
    @get:Rule val rule = ActivityScenarioRule(FaroV5W04CommercialDevActivity::class.java)

    @Test fun t01IsolatedOfflineLaunchStartsWithoutCommercialReview() {
        rule.scenario.onActivity { activity ->
            assertEquals("ar.com.faro.alpha.w04commercialdev", activity.packageName)
            val text = visibleText(activity)
            assertTrue(text.contains("Sin revisión comercial autorizada."))
            assertFalse(text.contains("Comercio 1"))
            assertTrue(descendants(activity.window.decorView).none { it is Button })
        }
    }

    @Test fun t02ReadOnlyHistoricalWhatIfAndUnverifiedBenefitsKeepCurrenciesSeparate() {
        rule.scenario.onActivity { activity ->
            activity.reviewPanel.renderAuthorized(fixture()) { true }
            val all = visibleText(activity)
            assertTrue(all.contains("ARS 200.00"))
            assertTrue(all.contains("USD 30.00"))
            assertTrue(all.contains("ARS 150.00"))
            assertTrue(all.contains("WHAT_IF hipotético"))
            assertTrue(all.contains("Reintegro futuro · declarada, sin verificar"))
            assertTrue(all.contains("Cobertura: PARCIAL"))
            assertTrue(all.contains("Necesidades sin precio comparable: 1"))
            assertFalse(all.contains("Total global"))
            assertFalse(all.contains("Pagar ahora"))
            assertTrue(descendants(activity.window.decorView).none { it is Button })
        }
    }

    @Test fun t03PauseAndRejectedDecisionErasePreviouslyPresentedData() {
        rule.scenario.onActivity { activity ->
            activity.reviewPanel.renderAuthorized(fixture()) { true }
            assertTrue(visibleText(activity).contains("Comercio 1"))
            activity.reviewPanel.renderAuthorized(Decision.Rejected(
                S4W04CommercialReviewPresentation.Rejection.INVALID_TRACE
            )) { true }
            assertFalse(visibleText(activity).contains("Comercio 1"))
            activity.reviewPanel.renderAuthorized(fixture()) { true }
        }
        rule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.scenario.onActivity { activity ->
            assertTrue(visibleText(activity).contains("Sin revisión comercial autorizada."))
            assertFalse(visibleText(activity).contains("Comercio 1"))
        }
    }

    @Test fun t04Font200HeadingsAndLiteralUnclickableSource() {
        rule.scenario.onActivity { activity ->
            assertTrue(activity.resources.configuration.fontScale >= 1.95f)
            activity.reviewPanel.renderAuthorized(fixture()) { true }
            val views = descendants(activity.window.decorView).filterIsInstance<TextView>().toList()
            assertTrue(views.any { it.isAccessibilityHeading &&
                it.text.toString().contains("Revisión comercial histórica") })
            val source = views.first { it.text.toString().contains("<b>https://ejemplo.invalid</b>") }
            assertEquals(0, source.autoLinkMask)
            assertFalse(source.linksClickable)
            val spans = (source.text as? Spanned)
                ?.getSpans(0, source.text.length, ClickableSpan::class.java).orEmpty()
            assertTrue(spans.isEmpty())
            assertTrue(activity.reviewPanel.isVerticalScrollBarEnabled)
            assertTrue(views.none { it.text.toString().contains("Autorizar compra") })
        }
    }

    private fun fixture(): Decision.Display {
        val ars = GroupKey("ARS", "Comercio 1", "AR-SF", CommercialPaymentMethod.DEBIT)
        val usd = GroupKey("USD", "Comercio 2", "AR-SF", CommercialPaymentMethod.CASH)
        val arsLine = TracedLine(
            HouseholdNeedId("need-001"), "sku-1", ars, 900L,
            "<b>https://ejemplo.invalid</b>", 2, 10000L, 20000L,
        )
        val usdLine = TracedLine(
            HouseholdNeedId("need-002"), "sku-2", usd, 950L,
            "historial:manual", 1, 3000L, 3000L,
        )
        return Decision.Display(
            "a".repeat(64), "b".repeat(64), "draft-test", "history-test",
            1000L, 1100L, Coverage.PARTIAL,
            listOf(
                GroupDisplay(
                    ars, 1, 20000L,
                    ScenarioDisclosure(20000L, 5000L, 15000L, ScenarioScope.ALL_MODELED),
                    listOf(Notice(BenefitKind.CASHBACK_FUTURE, "sin acreditar", "declarado:usuario")),
                    listOf(arsLine),
                ),
                GroupDisplay(usd, 1, 3000L, null, emptyList(), listOf(usdLine)),
            ),
            listOf(HouseholdNeedId("need-003")),
            listOf(HouseholdNeedId("need-003")),
        )
    }

    private fun visibleText(activity: FaroV5W04CommercialDevActivity): String =
        descendants(activity.window.decorView).filterIsInstance<TextView>()
            .map { it.text.toString() }.joinToString("\n")

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            yieldAll(descendants(view.getChildAt(i)))
        }
    }
}
