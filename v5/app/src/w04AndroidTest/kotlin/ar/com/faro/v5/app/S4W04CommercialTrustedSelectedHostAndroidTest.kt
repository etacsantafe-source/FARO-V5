package ar.com.faro.v5.app

import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.evidence.CommercialProductKey
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
 * B1B1B0 real Android DEV sandbox/pointer/gestures, NEGATIVE boundary tests.
 * No staged five-owner SQLite fixture and NO claim of positive live D4 read.
 */
@RunWith(AndroidJUnit4::class)
class S4W04CommercialTrustedSelectedHostAndroidTest {
    @get:Rule val rule = ActivityScenarioRule(FaroV5W04CommercialDevActivity::class.java)
    private val dirs = mutableListOf<File>()

    private data class Fixture(
        val root: File,
        val active: ActiveGenerationPointer,
        val pointer: FileActiveGenerationPointer,
    ) {
        fun staged() = StagedStorageGeneration(
            StorageGenerationDescriptor(active.generationId, "fixture-is-not-five-store", 0L),
            File(root,"generations/fixture").toPath(),
        )
    }

    private fun fixture(a: FaroV5W04CommercialDevActivity): Fixture {
        val root = File(a.application.filesDir, "w04-selected-host-"+UUID.randomUUID())
        assertTrue(root.mkdirs())
        dirs += root
        val pointer = FileActiveGenerationPointer(root.toPath())
        val active = pointer.compareAndSet(null, StorageGenerationId("dev-host-A"))
        assertNotNull(active)
        return Fixture(root, active!!, pointer)
    }

    private fun issuer(a: FaroV5W04CommercialDevActivity, f: Fixture,
        current: () -> String? = { "epoch-1" },
    ) = FaroV5W04CommercialDevReadConsentIssuer.openForTrustedDevHost(
        a, f.root.toPath(), f.active, "need-one", "sku-one",
        "epoch-1", current, { true },
    )!!

    private fun tap(a: FaroV5W04CommercialDevActivity,
        issuer: FaroV5W04CommercialDevReadConsentIssuer,
        household: Boolean,
        commercial: Boolean,
    ) {
        val panel = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val h = Button(a).apply { text = "Leer necesidad" }
        val c = Button(a).apply { text = "Leer precios históricos" }
        panel.addView(h); panel.addView(c)
        a.addContentView(panel, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        assertTrue(issuer.bindTrustedLocalConsentButtons(h,c))
        if (household) assertTrue(h.performClick())
        if (commercial) assertTrue(c.performClick())
    }

    private val realCatalog get() = BuiltInContractCatalog.create()
    private val emptyCatalog = object : OperationContractLookupPort {
        override fun current(operationId: OperationId): OperationContractDescriptor? = null
    }
    private val need = HouseholdNeedId("need-one")
    private val product = CommercialProductKey("sku-one")
    private val gate = SingleProcessMaterialWriteGate()

    private fun mount(
        a: FaroV5W04CommercialDevActivity,
        f: Fixture,
        consent: FaroV5W04CommercialDevReadConsentIssuer,
        catalog: OperationContractLookupPort = realCatalog,
        root: File = f.root,
        pointer: ActiveGenerationPointer = f.active,
        currentEpoch: () -> String? = { "epoch-1" },
        needPolicy: (String, OperationId, HouseholdNeedId) -> Boolean = { _,_,_ -> false },
        commercePolicy: (String, OperationId, CommercialProductKey) -> Boolean = { _,_,_ -> false },
    ) = FaroV5W04CommercialDevTrustedSelectedHost.openForTrustedDevHost(
        a,consent,root.toPath(),f.staged(),pointer,need,product,
        gate,catalog,"epoch-1",currentEpoch,{true},needPolicy,commercePolicy,
    )

    @After fun cleanup() {
        dirs.forEach { it.deleteRecursively() }
        dirs.clear()
    }

    @Test fun t01LauncherHasNoOwnerReadGateOrCommercialDisplay() {
        rule.scenario.onActivity { a ->
            assertFalse(a.hasTrustedConsentIssuer())
            assertFalse(a.hasTrustedGate())
            assertNull(a.readSelectedForTrustedHost("need-one","sku-one") { true })
        }
    }

    @Test fun t02BothLocalTapsWithoutN033N034OwnerGrantsCannotOpenSource() {
        rule.scenario.onActivity { a ->
            val f = fixture(a)
            val consent = issuer(a,f)
            assertTrue(a.attachTrustedConsentIssuer(consent))
            tap(a,consent, true,true)
            assertTrue(consent.householdConsented("need-one"))
            assertTrue(consent.commercialConsented("sku-one"))
            assertNull(mount(a,f,consent))
            assertFalse(a.hasTrustedGate())
            assertFalse(File(f.root,"finance9-dev-root.lock").exists())
        }
    }

    @Test fun t03SingleLocalConsentNeverSubstitutesForSecondDomainGrant() {
        rule.scenario.onActivity { a ->
            val f=fixture(a)
            val consent=issuer(a,f)
            assertTrue(a.attachTrustedConsentIssuer(consent))
            tap(a,consent, true,false)
            assertTrue(consent.householdConsented("need-one"))
            assertFalse(consent.commercialConsented("sku-one"))
            assertNull(mount(a,f,consent,
                needPolicy={_,_,_->true},commercePolicy={_,_,_->true}))
            assertFalse(a.hasTrustedGate())
            assertFalse(File(f.root,"finance9-dev-root.lock").exists())
        }
    }

    @Test fun t04InvalidOperationCatalogAndThrowingOwnerPolicyFailClosed() {
        rule.scenario.onActivity { a ->
            val f=fixture(a)
            val consent=issuer(a,f)
            assertTrue(a.attachTrustedConsentIssuer(consent))
            tap(a,consent,true,true)
            assertNull(mount(a,f,consent,catalog=emptyCatalog,
                needPolicy={_,_,_->true},commercePolicy={_,_,_->true}))
            assertNull(mount(a,f,consent,
                needPolicy={_,_,_-> error("N033 owner refused") },
                commercePolicy={_,_,_->true}))
            assertFalse(a.hasTrustedGate())
            assertFalse(File(f.root,"finance9-dev-root.lock").exists())
        }
    }

    @Test fun t05DifferentRootStalePointerOrUnsealedGenerationNoRead() {
        rule.scenario.onActivity { a ->
            val f=fixture(a)
            val consent=issuer(a,f)
            assertTrue(a.attachTrustedConsentIssuer(consent))
            tap(a,consent,true,true)
            val foreign=File(a.application.filesDir,
                "foreign-test-"+UUID.randomUUID())
            assertTrue(foreign.mkdirs())
            dirs += foreign
            assertNull(mount(a,f,consent,root=foreign,
                needPolicy={_,_,_->true},commercePolicy={_,_,_->true}))
            assertTrue(consent.isRevoked())
            assertFalse(a.hasTrustedGate())

            val newFixture=fixture(a)
            var epoch="epoch-1"
            val newConsent=issuer(a,newFixture,current={epoch})
            tap(a,newConsent,true,true)
            epoch="other-epoch"
            assertFalse(newConsent.selectedSourceBindingAdmitted(
                newFixture.root.toPath(),newFixture.active,"need-one","sku-one"))
            epoch="epoch-1"
            assertFalse(newConsent.selectedSourceBindingAdmitted(
                newFixture.root.toPath(),newFixture.active,"need-one","sku-one"))

            // A valid consent + catalog + permissive callback is STILL not
            // enough to open an unsealed Household10/Evidence2 generation.
            val third=fixture(a)
            val thirdConsent=issuer(a,third)
            tap(a,thirdConsent,true,true)
            assertNull(mount(a,third,thirdConsent,
                needPolicy={_,_,_->true},commercePolicy={_,_,_->true}))
            assertFalse(a.hasTrustedGate())
        }
    }
}
