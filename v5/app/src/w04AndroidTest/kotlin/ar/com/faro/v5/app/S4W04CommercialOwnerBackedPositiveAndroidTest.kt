package ar.com.faro.v5.app

import android.database.sqlite.SQLiteDatabase
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.com.faro.v5.evidence.*
import ar.com.faro.v5.generation.*
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F2C1B1B1B1A: POSITIVE Android API35 in the actual isolated offline DEV app.
 * This test SEEDS real SQLite/Evidence files with test-only historical rows;
 * it never substitutes an injected callback for the two source owner scopes.
 * Button.performClick is an instrumented local UI gesture, NOT proof that a
 * human is authenticated in a productive installation.
 */
@RunWith(AndroidJUnit4::class)
class S4W04CommercialOwnerBackedPositiveAndroidTest {
    @get:Rule val rule = ActivityScenarioRule(FaroV5W04CommercialDevActivity::class.java)
    private val fixtureRoots = mutableListOf<File>()
    private val need = HouseholdNeedId("need-yerba")
    private val product = CommercialProductKey("sku-typed")
    private val epoch = "w04-android-owner-epoch"

    private data class Fixture(
        val root: File,
        val pinned: ActiveGenerationPointer,
        val staged: StagedStorageGeneration,
        val gate: SingleProcessMaterialWriteGate,
    )

    /**
     * Bundled SQLite stages use SQLITE_OPEN_NOFOLLOW. Android filesDir can
     * contain a symlink in its ancestry (/data/user/0 -> /data/data), so the
     * physical stage path must start from the CANONICAL private root. Never
     * disable NOFOLLOW in Reality, and never use external storage.
     */
    private fun canonicalPrivateFixtureRoot(
        activity: FaroV5W04CommercialDevActivity,
        prefix: String,
    ): File {
        val privateRoot = activity.application.filesDir.toPath().toRealPath()
        check(Files.isDirectory(privateRoot, NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(privateRoot))
        val created = File(privateRoot.toFile(), prefix + UUID.randomUUID())
        check(created.mkdirs())
        val resolved = created.toPath().toRealPath()
        check(resolved != privateRoot && resolved.startsWith(privateRoot) &&
            Files.isDirectory(resolved, NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(resolved))
        val root = resolved.toFile()
        fixtureRoots += root
        return root
    }

    /** Real five-store source prepared just like the certified F2C0 JVM test. */
    private fun realFixture(activity: FaroV5W04CommercialDevActivity): Fixture {
        val root = canonicalPrivateFixtureRoot(activity, "w04-real-owner-")
        val path = root.toPath()
        val gate = SingleProcessMaterialWriteGate()
        val quiescence = MaterialWriteGateQuiescence(gate)
        val pointer = FileActiveGenerationPointer(path)
        val initial = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("initial-w04-android"),
        ))
        fun descriptor(id: String) = StorageGenerationDescriptor(
            StorageGenerationId(id), "w04-positive-five-store-dev", 100L,
        )
        val source = FaroV5WorkspaceV2StageOwner(path)
            .stageFreshPlanning8AndVerify(descriptor("w04-source"))
        FaroV5Finance9StagedMigration(path, quiescence).migrate(
            MaintenanceOperationId("w04-positive-finance9"),
            source, initial, "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(source, pointer).close()
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(
            source.directory.resolve(GenerationStoreFiles.REALITY),
        )
        SQLiteDatabase.openDatabase(
            source.directory.resolve(GenerationStoreFiles.REALITY).toString(),
            null, SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.execSQL("UPDATE household_need_state SET revision=3")
            db.execSQL(
                "INSERT INTO household_need VALUES ('need-yerba','Yerba',1000,'UNIT','NORMAL','OPEN',2,NULL)"
            )
            db.execSQL(
                "INSERT INTO household_need VALUES ('need-other','Leche',1000,'LITER','HIGH','OPEN',3,NULL)"
            )
        }
        fun evidence(s: StagedStorageGeneration) =
            s.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE)
        CommercialEvidenceStore(evidence(source).toString(), clock = { 950L }).use { store ->
            assertTrue(store.record(
                CommercialPriceObservation(
                    CommercialProductKey("sku-legacy"), CommercialMerchant("Historial viejo"),
                    CommercialCurrencyCode("ARS"), 20000L,
                    CommercialEvidenceSourceKind.MANUAL, "manual:old", 900L,
                ),
                IdempotencyKey("w04-old"), PayloadFingerprint("w04-old-fingerprint"),
            ) is CommercialEvidenceRecordOutcome.Committed)
        }
        val pinnedSource = checkNotNull(pointer.compareAndSet(
            initial.generationId, source.descriptor.generationId,
        ))
        val target = FileStorageGenerationLayout(path)
            .stageWorkspaceV2(descriptor("w04-target"))
        GenerationStoreFiles.WORKSPACE_V2.forEach { name ->
            Files.copy(source.directory.resolve(name), target.directory.resolve(name))
        }
        // AndroidTest is outside the generation module. The F2A helper is
        // deliberately internal, so perform the same schema1->schema2 migration
        // with the existing PUBLIC EvidenceStore in this isolated TEST fixture.
        // The owner-backed graph itself still validates all ACTIVE store seals.
        CommercialEvidenceStore(
            evidence(target).toString(), stagedTypedTermsV2 = true,
        ).use { store ->
            val state = store.maintenance.inspect()
            assertEquals(2, state.schemaVersion)
            assertTrue(state.integrityHealthy && state.foreignKeysHealthy)
        }
        CommercialEvidenceStore(
            evidence(target).toString(), clock = { 970L }, stagedTypedTermsV2 = true,
        ).use { store ->
            val terms = CommercialEvidenceTypedTerms(
                localityKind = CommercialLocalityKind.KNOWN, localityCode = "AR-SF",
                validFromEpochMillis = 700L, validUntilEpochMillis = 1200L,
                presentationQuantityThousandths = 1000L,
                presentationUnit = CommercialPresentationUnit.UNIT,
                packCount = 1, paymentMethod = CommercialPaymentMethod.CASH,
                eligibility = CommercialEligibility.GENERAL,
                promotionKind = CommercialPromotionKind.NONE,
            )
            for ((index, sku) in listOf("sku-typed", "sku-other").withIndex()) {
                assertTrue(store.recordWithTypedTerms(
                    CommercialPriceObservation(
                        CommercialProductKey(sku), CommercialMerchant("Comercio local"),
                        CommercialCurrencyCode("ARS"), 12000L + index * 1000L,
                        CommercialEvidenceSourceKind.MANUAL, "manual:$sku", 910L,
                    ),
                    terms, IdempotencyKey("w04-$sku"), PayloadFingerprint("w04-$sku-fingerprint"),
                ) is CommercialEvidenceRecordOutcome.Committed)
            }
        }
        val active = checkNotNull(pointer.compareAndSet(
            pinnedSource.generationId, target.descriptor.generationId,
        ))
        assertEquals(active, pointer.read())
        return Fixture(root, active, target, gate)
    }

    /** No SQLite is mounted or fabricated by the deny-before-open case. */
    private fun unmountedFixture(activity: FaroV5W04CommercialDevActivity): Fixture {
        val root = canonicalPrivateFixtureRoot(activity, "w04-owner-negative-")
        val pinned = checkNotNull(FileActiveGenerationPointer(root.toPath()).compareAndSet(
            null, StorageGenerationId("missing-stage"),
        ))
        val staged = StagedStorageGeneration(
            StorageGenerationDescriptor(pinned.generationId, "unmounted-test", 0L),
            root.toPath().resolve("no-source"),
        )
        return Fixture(root, pinned, staged, SingleProcessMaterialWriteGate())
    }

    private fun consent(activity: FaroV5W04CommercialDevActivity, f: Fixture) =
        checkNotNull(FaroV5W04CommercialDevReadConsentIssuer.openForTrustedDevHost(
            activity, f.root.toPath(), f.pinned, need.value, product.value,
            epoch, { epoch }, { true },
        ))

    private fun taps(
        activity: FaroV5W04CommercialDevActivity,
        issuer: FaroV5W04CommercialDevReadConsentIssuer,
        household: Boolean,
        commercial: Boolean,
    ) {
        val bar = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val h = Button(activity).apply { text = "Leer mi necesidad seleccionada" }
        val c = Button(activity).apply { text = "Leer historial del producto seleccionado" }
        bar.addView(h); bar.addView(c)
        activity.addContentView(bar, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        assertTrue(issuer.bindTrustedLocalConsentButtons(h, c))
        if (household) assertTrue(h.performClick())
        if (commercial) assertTrue(c.performClick())
    }

    private fun mount(
        a: FaroV5W04CommercialDevActivity,
        f: Fixture,
        issuer: FaroV5W04CommercialDevReadConsentIssuer,
    ) = FaroV5W04CommercialDevTrustedSelectedHost.openOwnerBackedForTrustedDevHost(
        a, issuer, f.root.toPath(), f.staged, f.pinned,
        need, product, f.gate, BuiltInContractCatalog.create(),
        epoch, { epoch }, { true },
    )

    @After fun cleanup() {
        fixtureRoots.forEach { it.deleteRecursively() }
        fixtureRoots.clear()
    }

    @Test fun t01AndroidTwoRealTapsReadSelectedActiveHouseholdAndTypedEvidence() {
        rule.scenario.onActivity { a ->
            val f = realFixture(a)
            val issuer = consent(a, f)
            assertTrue(a.attachTrustedConsentIssuer(issuer))
            taps(a, issuer, household = true, commercial = true)
            val host = checkNotNull(mount(a, f, issuer))
            assertTrue(a.hasTrustedGate())
            val selected = checkNotNull(host.readProjectedOnce {
                issuer.householdConsented(need.value) &&
                    issuer.commercialConsented(product.value)
            })
            assertEquals("need-yerba", selected.household.needs.single().id.value)
            assertEquals("Yerba", selected.household.needs.single().name.value)
            assertEquals(3L, selected.household.revision)
            assertEquals("sku-typed", selected.evidence.records.single().observation.productKey)
            assertEquals("AR-SF", selected.evidence.records.single().terms?.localityCode)
            assertEquals(2, selected.evidence.schemaVersion)
            assertTrue(selected.evidence.sourceWatermark.matches(Regex("[0-9a-f]{64}")))
            assertEquals(f.pinned.sequence, selected.fence.pointerSequence)
            assertEquals(epoch, selected.fence.hostEpoch)
            assertFalse(selected.evidence.records.any {
                it.observation.productKey == "sku-other" || it.observation.productKey == "sku-legacy"
            })
            // The one real OS root lease cannot be re-acquired for a second graph.
            assertThrows(IllegalStateException::class.java) {
                FaroV5Household10Evidence2DevSelectionReadPort.openOwnerBackedDev(
                    f.root.toPath(), f.staged, f.pinned, f.gate,
                    "second-dev-test", need, product, epoch,
                    { epoch }, { true }, { true }, { true },
                ).close()
            }
            host.close()
            assertTrue(host.isClosed())
            assertNull(host.readProjectedOnce { true })
        }
    }

    @Test fun t02SingleConsentCannotOpenRealOwnerBackedHost() {
        rule.scenario.onActivity { a ->
            val f = unmountedFixture(a)
            val issuer = consent(a, f)
            assertTrue(a.attachTrustedConsentIssuer(issuer))
            taps(a, issuer, household = true, commercial = false)
            assertTrue(issuer.householdConsented(need.value))
            assertFalse(issuer.commercialConsented(product.value))
            assertNull(mount(a, f, issuer))
            assertFalse(a.hasTrustedGate())
            assertFalse(File(f.root, "finance9-dev-root.lock").exists())
            assertTrue(issuer.isRevoked()) // No retry after a denied positive mount
        }
    }

    @Test fun t03PauseClosesLeasedOwnerSourceAndNeverRestores() {
        lateinit var host: FaroV5W04CommercialDevTrustedSelectedHost
        lateinit var issuer: FaroV5W04CommercialDevReadConsentIssuer
        lateinit var root: File
        rule.scenario.onActivity { a ->
            val f = realFixture(a)
            root = f.root
            issuer = consent(a, f)
            assertTrue(a.attachTrustedConsentIssuer(issuer))
            taps(a, issuer, household = true, commercial = true)
            host = checkNotNull(mount(a, f, issuer))
            assertNotNull(host.readProjectedOnce { true })
        }
        rule.scenario.moveToState(Lifecycle.State.STARTED)
        assertTrue(host.isClosed())
        assertTrue(issuer.isRevoked())
        FileChannel.open(
            File(root, "finance9-dev-root.lock").toPath(),
            StandardOpenOption.CREATE, StandardOpenOption.WRITE,
        ).use { channel ->
            channel.tryLock().use { lock -> assertNotNull(lock) }
        }
        rule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.scenario.onActivity { a ->
            assertFalse(a.hasTrustedGate())
            assertFalse(a.hasTrustedConsentIssuer())
            assertNull(a.readSelectedForTrustedHost(need.value, product.value) { true })
            assertNull(host.readProjectedOnce { true })
        }
    }

    private fun containsPanelText(view: View, needle: String): Boolean {
        if (view is TextView && view.text.toString().contains(needle)) return true
        return view is ViewGroup && (0 until view.childCount).any {
            containsPanelText(view.getChildAt(it), needle)
        }
    }

    @Test fun t04NativeExplicitChoiceRendersRealHistoricalD4EOnlyAfterTap() {
        rule.scenario.onActivity { a ->
            val f = realFixture(a)
            val issuer = consent(a, f)
            assertTrue(a.attachTrustedConsentIssuer(issuer))
            taps(a, issuer, household = true, commercial = true)
            val host = checkNotNull(mount(a, f, issuer))
            assertTrue(host.presentNativeHistoricalChoicesForTrustedDevHost(
                asOfEpochMillis = 1000L,
                maxObservationAgeMillis = 200L,
                requestedPackCount = 2,
                readNow = { 1020L },
            ))
            assertTrue(containsPanelText(a.reviewPanel, "Elegí una observación histórica"))
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
            val choice = checkNotNull(a.reviewPanel.findViewWithTag<Button>(
                "faro.w04.historical.choice.0",
            ))
            assertTrue(choice.text.toString().contains("Seleccionar histórico:"))
            assertTrue(choice.performClick())
            assertTrue(containsPanelText(a.reviewPanel, "Revisión comercial histórica"))
            assertTrue(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado: ARS 240.00"))
            assertTrue(containsPanelText(a.reviewPanel, "No es una cotización vigente"))
            assertFalse(containsPanelText(a.reviewPanel, "Elegí una observación histórica"))
            host.close()
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
        }
    }

    @Test fun t05NoObservationTapOrRevokedHostNeverRendersHistoricalPrice() {
        rule.scenario.onActivity { a ->
            val f = realFixture(a)
            val issuer = consent(a, f)
            assertTrue(a.attachTrustedConsentIssuer(issuer))
            taps(a, issuer, household = true, commercial = true)
            val host = checkNotNull(mount(a, f, issuer))
            assertTrue(host.presentNativeHistoricalChoicesForTrustedDevHost(
                1000L, 200L, 1, readNow = { 1020L },
            ))
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
            val staleChoice = checkNotNull(a.reviewPanel.findViewWithTag<Button>(
                "faro.w04.historical.choice.0",
            ))
            host.close()
            assertTrue(host.isClosed())
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
            // A detached button that still has a saved listener cannot revive the ACL.
            assertTrue(staleChoice.performClick())
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
        }
    }

    @Test fun t06PauseClearsHistoricalD4EAndResumeCannotResurrectSelection() {
        lateinit var host: FaroV5W04CommercialDevTrustedSelectedHost
        lateinit var issuer: FaroV5W04CommercialDevReadConsentIssuer
        lateinit var staleChoice: Button
        rule.scenario.onActivity { a ->
            val f = realFixture(a)
            issuer = consent(a, f)
            assertTrue(a.attachTrustedConsentIssuer(issuer))
            taps(a, issuer, household = true, commercial = true)
            host = checkNotNull(mount(a, f, issuer))
            assertTrue(host.presentNativeHistoricalChoicesForTrustedDevHost(
                1000L, 200L, 1, readNow = { 1020L },
            ))
            staleChoice = checkNotNull(a.reviewPanel.findViewWithTag<Button>(
                "faro.w04.historical.choice.0",
            ))
            assertTrue(staleChoice.performClick())
            assertTrue(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
        }
        rule.scenario.moveToState(Lifecycle.State.STARTED)
        assertTrue(host.isClosed())
        assertTrue(issuer.isRevoked())
        rule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.scenario.onActivity { a ->
            assertFalse(a.hasTrustedGate())
            assertFalse(a.hasTrustedConsentIssuer())
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
            assertNull(a.reviewPanel.findViewWithTag<View>("faro.w04.historical.choice.0"))
            assertTrue(staleChoice.performClick())
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
        }
    }

    @Test fun t07NativeEntryButtonRequiresTapBeforeHistoricalChooser() {
        rule.scenario.onActivity { a ->
            val f = realFixture(a)
            val issuer = consent(a, f)
            assertTrue(a.attachTrustedConsentIssuer(issuer))
            taps(a, issuer, household = true, commercial = true)
            val host = checkNotNull(mount(a, f, issuer))
            assertTrue(host.offerNativeHistoricalEntryForTrustedDevHost(
                1000L, 200L, 2, readNow = { 1020L },
            ))
            assertFalse(containsPanelText(a.reviewPanel, "Elegí una observación histórica"))
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
            val entry = checkNotNull(a.reviewPanel.findViewWithTag<Button>(
                "faro.w04.historical.open",
            ))
            assertTrue(entry.contentDescription.toString().contains("todavía no se selecciona"))
            assertTrue(entry.performClick())
            assertTrue(containsPanelText(a.reviewPanel, "Elegí una observación histórica"))
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
            val choice = checkNotNull(a.reviewPanel.findViewWithTag<Button>(
                "faro.w04.historical.choice.0",
            ))
            assertTrue(choice.performClick())
            assertTrue(containsPanelText(a.reviewPanel, "Revisión comercial histórica"))
            assertTrue(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado: ARS 240.00"))
            assertTrue(containsPanelText(a.reviewPanel, "No es una cotización vigente"))
            host.close()
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
        }
    }

    @Test fun t08ClosedOwnerHostCannotUseDetachedHistoricalEntryButton() {
        rule.scenario.onActivity { a ->
            val f = realFixture(a)
            val issuer = consent(a, f)
            assertTrue(a.attachTrustedConsentIssuer(issuer))
            taps(a, issuer, household = true, commercial = true)
            val host = checkNotNull(mount(a, f, issuer))
            assertTrue(host.offerNativeHistoricalEntryForTrustedDevHost(
                1000L, 200L, 1, readNow = { 1020L },
            ))
            val oldEntry = checkNotNull(a.reviewPanel.findViewWithTag<Button>(
                "faro.w04.historical.open",
            ))
            host.close()
            assertTrue(host.isClosed())
            assertTrue(oldEntry.performClick())
            assertFalse(containsPanelText(a.reviewPanel, "Elegí una observación histórica"))
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
        }
    }

    @Test fun t09InvalidLiveClockAfterNativeEntryTapCannotDisplayHistoricalEstimate() {
        rule.scenario.onActivity { a ->
            val f = realFixture(a)
            val issuer = consent(a, f)
            assertTrue(a.attachTrustedConsentIssuer(issuer))
            taps(a, issuer, household = true, commercial = true)
            val host = checkNotNull(mount(a, f, issuer))
            assertTrue(host.offerNativeHistoricalEntryForTrustedDevHost(
                1000L, 200L, 2, readNow = { 999L },
            ))
            val entry = checkNotNull(a.reviewPanel.findViewWithTag<Button>(
                "faro.w04.historical.open",
            ))
            assertTrue(entry.performClick())
            val choice = checkNotNull(a.reviewPanel.findViewWithTag<Button>(
                "faro.w04.historical.choice.0",
            ))
            assertTrue(choice.performClick())
            assertFalse(containsPanelText(a.reviewPanel, "Importe HISTÓRICO observado:"))
            assertFalse(containsPanelText(a.reviewPanel, "Revisión comercial histórica"))
            host.close()
        }
    }
}
