package ar.com.faro.v5.app

import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2C1B1A: actual Android Application / process UID / app-private pointer and
 * local visible Button clicks. Fixtures contain no SQLite / prices / products.
 * These are scoped consent bits only, NOT Household/Commercial owner grants.
 */
@RunWith(AndroidJUnit4::class)
class S4W04CommercialReadConsentIssuerAndroidTest {
    @get:Rule val rule = ActivityScenarioRule(FaroV5W04CommercialDevActivity::class.java)

    private val fixtureDirs = mutableListOf<File>()

    private data class Fixture(
        val root: File,
        val pointer: FileActiveGenerationPointer,
        val active: ActiveGenerationPointer,
    )

    private fun fixture(activity: FaroV5W04CommercialDevActivity): Fixture {
        val root = File(activity.application.filesDir, "w04-issuer-test-" + UUID.randomUUID())
        assertTrue(root.mkdirs())
        fixtureDirs.add(root)
        val pointer = FileActiveGenerationPointer(root.toPath())
        val active = pointer.compareAndSet(null, StorageGenerationId("dev-w04-a"))
        assertNotNull(active)
        return Fixture(root, pointer, active!!)
    }

    private fun issuer(
        activity: FaroV5W04CommercialDevActivity,
        f: Fixture,
        currentEpoch: () -> String? = { "epoch-one" },
        ready: () -> Boolean = { true },
    ) = FaroV5W04CommercialDevReadConsentIssuer.openForTrustedDevHost(
        activity, f.root.toPath(), f.active, "need-one", "sku-one",
        "epoch-one", currentEpoch, ready,
    )

    private fun buttons(activity: FaroV5W04CommercialDevActivity): Pair<Button,Button> {
        val bar = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val household = Button(activity).apply { text = "Autorizar lectura de necesidad" }
        val commercial = Button(activity).apply { text = "Autorizar precios históricos" }
        bar.addView(household)
        bar.addView(commercial)
        activity.addContentView(bar, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        assertTrue(household.isAttachedToWindow)
        assertTrue(commercial.isAttachedToWindow)
        return household to commercial
    }

    @After fun cleanup() {
        for (root in fixtureDirs) root.deleteRecursively()
        fixtureDirs.clear()
    }

    @Test fun t01FreshLauncherHasNoIssuerConsentOrLiveSource() {
        rule.scenario.onActivity { activity ->
            assertTrue(activity.isForegroundForTrustedRead())
            assertFalse(activity.hasTrustedConsentIssuer())
            assertFalse(activity.hasTrustedGate())
            assertNull(activity.readSelectedForTrustedHost("need-one","sku-one") { true })
        }
    }

    @Test fun t02RealAndroidSandboxPrivateRootAndTwoIndependentVisibleTaps() {
        rule.scenario.onActivity { activity ->
            val f = fixture(activity)
            val consent = issuer(activity, f)
            assertNotNull(consent)
            consent!!
            assertTrue(activity.attachTrustedConsentIssuer(consent))
            assertFalse(consent.householdConsented("need-one"))
            assertFalse(consent.commercialConsented("sku-one"))
            val (householdButton, commercialButton) = buttons(activity)
            assertTrue(consent.bindTrustedLocalConsentButtons(householdButton, commercialButton))
            assertFalse(consent.householdConsented("need-one"))
            assertTrue(householdButton.performClick())
            assertTrue(consent.householdConsented("need-one"))
            assertFalse(consent.commercialConsented("sku-one"))
            assertTrue(commercialButton.performClick())
            assertTrue(consent.commercialConsented("sku-one"))
            assertFalse(consent.householdConsented("need-other"))
            assertFalse(consent.commercialConsented("sku-other"))
            assertNull(activity.readSelectedForTrustedHost("need-one","sku-one") { true })
            consent.revokeCommercialConsent()
            assertFalse(consent.commercialConsented("sku-one"))
            assertTrue(consent.householdConsented("need-one"))
        }
    }

    @Test fun t03PauseRevokesBothScopesAndResumeNeverRestores() {
        lateinit var old: FaroV5W04CommercialDevReadConsentIssuer
        rule.scenario.onActivity { activity ->
            val consent = issuer(activity, fixture(activity))
            assertNotNull(consent)
            old = consent!!
            assertTrue(activity.attachTrustedConsentIssuer(old))
            val (h, c) = buttons(activity)
            assertTrue(old.bindTrustedLocalConsentButtons(h,c))
            h.performClick()
            c.performClick()
            assertTrue(old.householdConsented("need-one"))
            assertTrue(old.commercialConsented("sku-one"))
        }
        rule.scenario.moveToState(Lifecycle.State.STARTED)
        assertTrue(old.isRevoked())
        assertFalse(old.householdConsented("need-one"))
        assertFalse(old.commercialConsented("sku-one"))
        rule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.scenario.onActivity { activity ->
            assertFalse(activity.hasTrustedConsentIssuer())
            assertFalse(old.householdConsented("need-one"))
            assertFalse(old.commercialConsented("sku-one"))
        }
    }

    @Test fun t04PointerSwitchAndEpochAbaPermanentlyPoisonOldConsent() {
        rule.scenario.onActivity { activity ->
            val f = fixture(activity)
            val consent = issuer(activity, f)!!
            assertTrue(activity.attachTrustedConsentIssuer(consent))
            val (h,c) = buttons(activity)
            assertTrue(consent.bindTrustedLocalConsentButtons(h,c))
            h.performClick(); c.performClick()
            assertTrue(consent.commercialConsented("sku-one"))
            val switched = f.pointer.compareAndSet(
                StorageGenerationId("dev-w04-a"), StorageGenerationId("dev-w04-b"))
            assertNotNull(switched)
            assertFalse(consent.householdConsented("need-one"))
            assertTrue(consent.isRevoked())
            assertNotNull(f.pointer.compareAndSet(
                StorageGenerationId("dev-w04-b"), StorageGenerationId("dev-w04-a")))
            assertFalse(consent.commercialConsented("sku-one"))

            val f2 = fixture(activity)
            var epoch = "epoch-one"
            val second = issuer(activity, f2, currentEpoch = {epoch})!!
            assertFalse(activity.attachTrustedConsentIssuer(second)) // old still mounted until pause
            assertTrue(second.isRevoked())
            val standAlone = issuer(activity, f2, currentEpoch = {epoch})!!
            val (h2,c2) = buttons(activity)
            assertTrue(standAlone.bindTrustedLocalConsentButtons(h2,c2))
            h2.performClick()
            assertTrue(standAlone.householdConsented("need-one"))
            epoch = "epoch-two"
            assertFalse(standAlone.householdConsented("need-one"))
            epoch = "epoch-one"
            assertFalse(standAlone.householdConsented("need-one"))
            assertTrue(standAlone.isRevoked())
        }
    }

    @Test fun t05ForeignRootInvalidScopeThrowingOwnerAndDuplicateAttachFailClosed() {
        rule.scenario.onActivity { activity ->
            val f = fixture(activity)
            assertNull(FaroV5W04CommercialDevReadConsentIssuer.openForTrustedDevHost(
                activity, activity.application.cacheDir.toPath(), f.active,
                "need-one","sku-one","epoch-one",{ "epoch-one" },{true},
            ))
            assertNull(FaroV5W04CommercialDevReadConsentIssuer.openForTrustedDevHost(
                activity, f.root.toPath(), f.active,
                "bad need","sku-one","epoch-one",{ "epoch-one" },{true},
            ))
            assertNull(issuer(activity,f,ready = { error("owner unavailable") }))
            val first = issuer(activity, f)!!
            val second = issuer(activity, f)!!
            assertTrue(activity.attachTrustedConsentIssuer(first))
            assertFalse(activity.attachTrustedConsentIssuer(second))
            assertTrue(second.isRevoked())
            assertFalse(first.isRevoked())
            val (h,c) = buttons(activity)
            assertTrue(first.bindTrustedLocalConsentButtons(h,c))
            h.performClick()
            assertTrue(first.householdConsented("need-one"))
            assertFalse(first.commercialConsented("sku-one"))
            assertFalse(first.bindTrustedLocalConsentButtons(h,c))
            assertFalse(first.commercialConsented("sku-one"))
        }
    }
}
