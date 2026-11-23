package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.*
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** F2C0 delta tests for the DEV selected scope; not F2B1 or Android E reruns. */
class FaroV5Household10Evidence2DevSelectionReadPortTest {
    @TempDir lateinit var root: Path

    private val gate = SingleProcessMaterialWriteGate()
    private val quiescence = MaterialWriteGateQuiescence(gate)
    private val pointer get() = FileActiveGenerationPointer(root)
    private var epoch = "epoch-f2c0"
    private var foreground = true
    private var ready = true
    private var householdAllowed = true
    private var evidenceAllowed = true

    private data class Fixture(
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val activeSource: ActiveGenerationPointer,
    )
    private fun descriptor(id: String) = StorageGenerationDescriptor(
        StorageGenerationId(id), "f2c0-typed-isolated-dev", 100L,
    )
    private fun evidence(stage: StagedStorageGeneration) =
        stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE)

    private fun prepare(): Fixture {
        val initial = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("f2c0-initial"),
        ))
        val source = FaroV5WorkspaceV2StageOwner(root)
            .stageFreshPlanning8AndVerify(descriptor("f2c0-source"))
        FaroV5Finance9StagedMigration(root, quiescence).migrate(
            MaintenanceOperationId("f2c0-nine"), source, initial, "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(source, pointer).close()
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(
            source.directory.resolve(GenerationStoreFiles.REALITY),
        )
        BundledSQLiteDriver().open(
            source.directory.resolve(GenerationStoreFiles.REALITY).toString(),
        ).use { db ->
            db.execSQL("UPDATE household_need_state SET revision=3")
            db.execSQL("INSERT INTO household_need VALUES ('need-yerba','Yerba',1000,'UNIT','NORMAL','OPEN',2,NULL)")
            db.execSQL("INSERT INTO household_need VALUES ('need-other','Leche',1000,'LITER','HIGH','OPEN',3,NULL)")
        }
        CommercialEvidenceStore(evidence(source).toString(), clock = { 950L }).use { store ->
            assertTrue(store.record(
                CommercialPriceObservation(
                    CommercialProductKey("sku-legacy"), CommercialMerchant("Historial viejo"),
                    CommercialCurrencyCode("ARS"), 20000L,
                    CommercialEvidenceSourceKind.MANUAL, "manual:old", 900L,
                ),
                IdempotencyKey("f2c0-old"), PayloadFingerprint("f2c0-old-fp"),
            ) is CommercialEvidenceRecordOutcome.Committed)
        }
        val pinnedSource = checkNotNull(pointer.compareAndSet(
            initial.generationId, source.descriptor.generationId,
        ))
        val target = FileStorageGenerationLayout(root).stageWorkspaceV2(descriptor("f2c0-target"))
        GenerationStoreFiles.WORKSPACE_V2.forEach { name ->
            Files.copy(source.directory.resolve(name), target.directory.resolve(name))
        }
        FaroV5CommercialEvidence2StagedUpgrade(root, quiescence, {}).upgrade(
            MaintenanceOperationId("f2c0-evidence-upgrade"), target, pinnedSource,
        )
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
            listOf("sku-typed", "sku-other").forEachIndexed { index, sku ->
                val committed = store.recordWithTypedTerms(
                    CommercialPriceObservation(
                        CommercialProductKey(sku), CommercialMerchant("Comercio local"),
                        CommercialCurrencyCode("ARS"), 12000L + index * 1000L,
                        CommercialEvidenceSourceKind.MANUAL, "manual:$sku", 910L,
                    ),
                    terms, IdempotencyKey("f2c0-$sku"), PayloadFingerprint("f2c0-$sku-fp"),
                )
                assertTrue(committed is CommercialEvidenceRecordOutcome.Committed)
            }
        }
        return Fixture(source, target, pinnedSource)
    }

    private fun select(f: Fixture): ActiveGenerationPointer = checkNotNull(
        pointer.compareAndSet(f.activeSource.generationId, f.target.descriptor.generationId)
    )

    private fun open(
        stage: StagedStorageGeneration,
        selected: ActiveGenerationPointer,
        householdPolicy: (String) -> Boolean = { householdAllowed && it == "need-yerba" },
        commercialPolicy: (String) -> Boolean = { evidenceAllowed && it == "sku-typed" },
    ) = FaroV5Household10Evidence2DevSelectionReadPort.open(
        storageRoot = root,
        staged = stage,
        selected = selected,
        materialWriteGate = gate,
        hostEpoch = "epoch-f2c0",
        currentHostEpoch = { epoch },
        isForeground = { foreground },
        ownerAdmission = { ready },
        householdGrant = householdPolicy,
        commercialGrant = commercialPolicy,
    )
    private fun FaroV5Household10Evidence2DevSelectionReadPort.read() =
        readSelected("need-yerba", "sku-typed") { true }

    @Test fun t01ExactSelectedHouseholdAndOnlyTypedProductUnderOneLease() {
        val f = prepare()
        val selected = select(f)
        open(f.target, selected).use { port ->
            val row = checkNotNull(port.read())
            assertEquals(selected, row.generation)
            assertEquals("need-yerba", row.need.id)
            assertEquals("Yerba", row.need.name)
            assertEquals(3L, row.householdRevision)
            assertEquals(2, row.need.revision)
            assertEquals(1, row.typedHistory.records.size)
            assertEquals("sku-typed", row.typedHistory.records.single().observation.productKey)
            assertEquals("AR-SF", row.typedHistory.records.single().terms?.localityCode)
            assertEquals(2, row.typedHistory.schemaVersion)
            assertTrue(row.typedHistory.sourceWatermark.matches(Regex("[0-9a-f]{64}")))
            assertFalse(row.typedHistory.records.any { it.observation.productKey != "sku-typed" })
        }
    }

    @Test fun t02IndependentGrantsAndExplicitChoiceMustBothBePresent() {
        val f = prepare()
        open(f.target, select(f)).use { port ->
            assertNull(port.readSelected("need-yerba", "sku-typed") { false })
            householdAllowed = false
            assertNull(port.read())
            householdAllowed = true
            evidenceAllowed = false
            assertNull(port.read())
            evidenceAllowed = true
            assertNull(port.readSelected("need-other", "sku-typed") { true })
            assertNull(port.readSelected("need-yerba", "sku-other") { true })
            assertTrue(port.read() != null)
        }
    }

    @Test fun t03MidReadRevokedGrantAndEpochFailClosed() {
        val f = prepare()
        val selected = select(f)
        var checks = 0
        open(f.target, selected, commercialPolicy = { ++checks == 1 }).use { port ->
            assertNull(port.read())
            assertTrue(checks >= 2)
        }
        open(f.target, selected).use { port ->
            epoch = "stale"
            assertNull(port.read())
            epoch = "epoch-f2c0"
            foreground = false
            assertNull(port.read())
            foreground = true
            ready = false
            assertNull(port.read())
        }
    }

    @Test fun t04PointerAbaWithSameIdNeverRestoresAuthority() {
        val f = prepare()
        val selected = select(f)
        open(f.target, selected).use { port ->
            val other = StorageGenerationId("f2c0-aba-intermediate")
            checkNotNull(pointer.compareAndSet(selected.generationId, other))
            checkNotNull(pointer.compareAndSet(other, selected.generationId))
            assertTrue(pointer.read()?.generationId == selected.generationId)
            assertNull(port.read())
        }
    }

    @Test fun t05ExclusiveRootLeaseReleaseAndClosedPortNeverReuse() {
        val f = prepare()
        val selected = select(f)
        val port = open(f.target, selected)
        assertThrows(IllegalStateException::class.java) { open(f.target, selected).close() }
        assertTrue(port.read() != null)
        port.close()
        assertNull(port.read())
        open(f.target, selected).use { assertTrue(it.read() != null) }
    }

    @Test fun t06WrongSchemaStalePointerAndTamperedTermsNeverExposeSource() {
        val f = prepare()
        assertThrows(IllegalStateException::class.java) {
            open(f.source, f.activeSource).close()
        }
        assertThrows(IllegalStateException::class.java) {
            open(f.target, f.activeSource).close()
        }
        val selected = select(f)
        BundledSQLiteDriver().open(evidence(f.target).toString()).use { db ->
            db.execSQL("UPDATE commercial_evidence_quote_terms SET terms_fingerprint='ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff'")
        }
        assertThrows(IllegalStateException::class.java) {
            open(f.target, selected).close()
        }
        assertEquals(selected, pointer.read())
    }

    private fun openOwnerBacked(
        stage: StagedStorageGeneration,
        selected: ActiveGenerationPointer,
        selectedNeed: String = "need-yerba",
        selectedProduct: String = "sku-typed",
        humanDecision: () -> Boolean = { true },
    ) = FaroV5Household10Evidence2DevSelectionReadPort.openOwnerBackedDev(
        storageRoot = root, staged = stage, selected = selected,
        materialWriteGate = gate,
        principal = "local-install:f2c1b1b1b0-jvm",
        selectedNeed = HouseholdNeedId(selectedNeed),
        selectedProduct = CommercialProductKey(selectedProduct),
        hostEpoch = "epoch-f2c0", currentHostEpoch = { epoch },
        isForeground = { foreground }, ownerAdmission = { ready },
        trustedHumanDecision = humanDecision,
    )

    @Test fun t07RealOwnerSourcesShareOneActiveLeaseAndExactTypedSelection() {
        val f = prepare()
        val active = select(f)
        openOwnerBacked(f.target, active).use { port ->
            val row = checkNotNull(port.read())
            assertEquals("need-yerba", row.need.id)
            assertEquals(3L, row.householdRevision)
            assertEquals("sku-typed", row.typedHistory.records.single().observation.productKey)
            assertEquals("AR-SF", row.typedHistory.records.single().terms?.localityCode)
            assertThrows(IllegalStateException::class.java) {
                openOwnerBacked(f.target, active).close()
            }
            assertNull(port.readSelected("need-other", "sku-typed") { true })
            assertNull(port.read()) // wrong object permanently revoked Household owner
        }
    }

    @Test fun t08DeniedHumanDecisionNeverLeavesAnActiveRootLease() {
        val f = prepare()
        val active = select(f)
        assertThrows(IllegalStateException::class.java) {
            openOwnerBacked(f.target, active, humanDecision = { false }).close()
        }
        openOwnerBacked(f.target, active).use { port ->
            assertEquals("need-yerba", checkNotNull(port.read()).need.id)
        }
    }

    @Test fun t09LegacyTermsOrMissingSelectedProductAreNeverGranted() {
        val f = prepare()
        val active = select(f)
        assertThrows(IllegalStateException::class.java) {
            openOwnerBacked(f.target, active, selectedProduct = "sku-legacy").close()
        }
        assertThrows(IllegalStateException::class.java) {
            openOwnerBacked(f.target, active, selectedProduct = "not-recorded").close()
        }
        openOwnerBacked(f.target, active).use {
            assertEquals(1, checkNotNull(it.read()).typedHistory.records.size)
        }
    }

    @Test fun t10HostEpochConsentAndPointerAbaCannotResurrectOwnerGrants() {
        val f = prepare()
        val active = select(f)
        var chosen = true
        openOwnerBacked(f.target, active, humanDecision = { chosen }).use { port ->
            assertTrue(port.read() != null)
            chosen = false
            assertNull(port.read())
            chosen = true
            assertNull(port.read())
        }
        openOwnerBacked(f.target, active).use { port ->
            epoch = "changed"
            assertNull(port.read())
            epoch = "epoch-f2c0"
            assertNull(port.read())
        }
        openOwnerBacked(f.target, active).use { port ->
            val intermediate = StorageGenerationId("f2c1b1b1b0-intermediate")
            checkNotNull(pointer.compareAndSet(active.generationId, intermediate))
            checkNotNull(pointer.compareAndSet(intermediate, active.generationId))
            assertNull(port.read())
        }
    }
}
