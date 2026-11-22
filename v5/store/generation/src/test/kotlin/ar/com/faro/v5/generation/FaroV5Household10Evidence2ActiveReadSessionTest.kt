package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEvidenceRecordOutcome
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.CommercialLocalityKind
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPresentationUnit
import ar.com.faro.v5.evidence.CommercialPriceObservation
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.evidence.CommercialEligibility
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** F2B1: ALREADY ACTIVE typed Evidence2 read only, not a cutover engine. */
class FaroV5Household10Evidence2ActiveReadSessionTest {
    @TempDir lateinit var root: Path
    private val gate=SingleProcessMaterialWriteGate()
    private val quiescence=MaterialWriteGateQuiescence(gate)
    private val pointer get()=FileActiveGenerationPointer(root)
    private data class Fixtures(
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val sourcePointer: ActiveGenerationPointer,
    )
    private fun descriptor(name: String)=StorageGenerationDescriptor(
        StorageGenerationId(name),"f2b1-selected-typed-dev",100L
    )
    private fun commercial(stage: StagedStorageGeneration)=stage.directory.resolve(
        GenerationStoreFiles.COMMERCIAL_EVIDENCE
    )
    private fun prepare(): Fixtures {
        val initial=checkNotNull(pointer.compareAndSet(null,StorageGenerationId("f2b1-initial")))
        val source=FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(descriptor("f2b1-source"))
        FaroV5Finance9StagedMigration(root,quiescence).migrate(
            MaintenanceOperationId("f2b1-prepare-nine"),source,initial,"a".repeat(64),1000L
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(source,pointer).close()
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(
            source.directory.resolve(GenerationStoreFiles.REALITY)
        )
        CommercialEvidenceStore(commercial(source).toString(),clock={980L}).use { evidence ->
            assertTrue(evidence.record(
                CommercialPriceObservation(
                    CommercialProductKey("sku-legacy"),CommercialMerchant("Mercado viejo"),
                    CommercialCurrencyCode("ARS"),18000L,CommercialEvidenceSourceKind.MANUAL,
                    "manual:legacy",900L
                ),IdempotencyKey("f2b1-legacy"),PayloadFingerprint("f2b1-legacy-payload")
            ) is CommercialEvidenceRecordOutcome.Committed)
        }
        val selectedSource=checkNotNull(pointer.compareAndSet(
            initial.generationId,source.descriptor.generationId
        ))
        val target=FileStorageGenerationLayout(root).stageWorkspaceV2(descriptor("f2b1-target"))
        GenerationStoreFiles.WORKSPACE_V2.forEach {
            Files.copy(source.directory.resolve(it),target.directory.resolve(it))
        }
        FaroV5CommercialEvidence2StagedUpgrade(root,quiescence,{}).upgrade(
            MaintenanceOperationId("f2b1-upgrade-staged"),target,selectedSource
        )
        CommercialEvidenceStore(commercial(target).toString(),clock={995L},
            stagedTypedTermsV2=true).use { evidence ->
            val terms=CommercialEvidenceTypedTerms(
                localityKind=CommercialLocalityKind.KNOWN,localityCode="AR-SF",
                validFromEpochMillis=700L,validUntilEpochMillis=1200L,
                presentationQuantityThousandths=1000L,
                presentationUnit=CommercialPresentationUnit.UNIT,
                packCount=1,paymentMethod=CommercialPaymentMethod.CASH,
                eligibility=CommercialEligibility.GENERAL,
                promotionKind=CommercialPromotionKind.NONE
            )
            assertTrue(evidence.recordWithTypedTerms(
                CommercialPriceObservation(
                    CommercialProductKey("sku-typed"),CommercialMerchant("Mercado nuevo"),
                    CommercialCurrencyCode("USD"),3400L,CommercialEvidenceSourceKind.MANUAL,
                    "manual:typed",940L
                ),terms,IdempotencyKey("f2b1-typed"),PayloadFingerprint("f2b1-typed-payload")
            ) is CommercialEvidenceRecordOutcome.Committed)
        }
        return Fixtures(source,target,selectedSource)
    }
    private fun selectTarget(f: Fixtures) = checkNotNull(
        pointer.compareAndSet(f.sourcePointer.generationId,f.target.descriptor.generationId)
    )
    private fun open(stage: StagedStorageGeneration, selected: ActiveGenerationPointer,
                     admitted: () -> Boolean = {true}) =
        FaroV5GenerationStoreGraph.openActiveHousehold10Evidence2DevForTrustedHost(
            root,stage,selected,gate,admitted
        )

    @Test fun t01SelectedTypedGenerationReadsBothLegacyAndExplicitTermsUnderOneLease() {
        val f=prepare()
        val sourceBefore=GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(f.source.directory.resolve(it))
        }
        val selected=selectTarget(f) // TEST ONLY: not a certified durable cutover.
        open(f.target,selected).use { session ->
            assertTrue(session.readNeeds { true }.revision >= 0L)
            val source=checkNotNull(session.authorizedCommercialTypedSnapshot({true},{true}))
            assertEquals(2,source.records.size)
            assertTrue(source.records.any { it.terms == null })
            assertTrue(source.records.any { it.terms != null })
        }
        sourceBefore.forEach { (name, bytes) ->
            assertArrayEquals(bytes,Files.readAllBytes(f.source.directory.resolve(name)))
        }
    }

    @Test fun t02NoFallbackBetweenLegacySchemaOneAndTypedSchemaTwoActiveOpeners() {
        val f=prepare()
        assertThrows(IllegalStateException::class.java) { open(f.source,f.sourcePointer).close() }
        assertThrows(IllegalStateException::class.java) { open(f.target,f.sourcePointer).close() }
        val selected=selectTarget(f)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openActiveHousehold10DevForTrustedHost(
                root,f.target,selected,gate,{true}
            ).close()
        }
    }

    @Test fun t03IndependentGrantsAndMidReadRevocationFailClosed() {
        val f=prepare()
        val selected=selectTarget(f)
        open(f.target,selected).use { session ->
            assertNull(session.authorizedCommercialTypedSnapshot({false},{true}))
            assertNull(session.authorizedCommercialTypedSnapshot({true},{false}))
            var checks=0
            assertNull(session.authorizedCommercialTypedSnapshot({true},{++checks==1}))
            assertTrue(session.authorizedCommercialTypedSnapshot({true},{true}) != null)
        }
    }

    @Test fun t04PointerABARejectsReadEvenAfterGenerationIdComesBack() {
        val f=prepare()
        val selected=selectTarget(f)
        open(f.target,selected).use { session ->
            val other=StorageGenerationId("f2b1-temp")
            checkNotNull(pointer.compareAndSet(selected.generationId,other))
            checkNotNull(pointer.compareAndSet(other,selected.generationId))
            assertNull(session.authorizedCommercialTypedSnapshot({true},{true}))
        }
    }

    @Test fun t05SingleRootLeaseDeniesAnotherSessionAndReleasesOnClose() {
        val f=prepare()
        val selected=selectTarget(f)
        open(f.target,selected).use { first ->
            assertThrows(IllegalStateException::class.java) { open(f.target,selected).close() }
            assertTrue(first.authorizedCommercialTypedSnapshot({true},{true}) != null)
        }
        open(f.target,selected).use {
            assertTrue(it.authorizedCommercialTypedSnapshot({true},{true}) != null)
        }
    }

    @Test fun t06CorruptTypedFingerprintAndRevokedOwnerCannotOpen() {
        val f=prepare()
        assertThrows(IllegalStateException::class.java) { open(f.source,f.sourcePointer,{false}).close() }
        BundledSQLiteDriver().open(commercial(f.target).toString()).use {
            it.execSQL("UPDATE commercial_evidence_quote_terms SET terms_fingerprint='ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff'")
        }
        val selected=selectTarget(f)
        assertThrows(IllegalStateException::class.java) { open(f.target,selected).close() }
        assertEquals(selected,pointer.read())
    }
}
