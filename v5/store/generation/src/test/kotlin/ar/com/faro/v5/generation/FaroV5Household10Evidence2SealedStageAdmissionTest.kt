package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Format admission only; never performs LIVE cutover or FBS2 serialization. */
class FaroV5Household10Evidence2SealedStageAdmissionTest {
    @TempDir lateinit var root: Path
    private val pointer get()=FileActiveGenerationPointer(root)
    private val gate=MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private data class Fixture(
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val pinned: ActiveGenerationPointer,
    )
    private fun desc(name: String)=StorageGenerationDescriptor(
        StorageGenerationId(name),"f2b2a-sealed-typed",100L
    )
    private fun fixture(): Fixture {
        val origin=checkNotNull(pointer.compareAndSet(null,StorageGenerationId("f2b2a-origin")))
        val source=FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(desc("f2b2a-source"))
        FaroV5Finance9StagedMigration(root,gate).migrate(
            MaintenanceOperationId("f2b2a-finance9"),source,origin,"a".repeat(64),1000L
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(source,pointer).close()
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(
            source.directory.resolve(GenerationStoreFiles.REALITY)
        )
        val selected=checkNotNull(pointer.compareAndSet(origin.generationId,source.descriptor.generationId))
        val target=FileStorageGenerationLayout(root).stageWorkspaceV2(desc("f2b2a-target"))
        GenerationStoreFiles.WORKSPACE_V2.forEach { name ->
            Files.copy(source.directory.resolve(name),target.directory.resolve(name))
        }
        FaroV5CommercialEvidence2StagedUpgrade(root,gate,{}).upgrade(
            MaintenanceOperationId("f2b2a-typed-upgrade"),target,selected
        )
        return Fixture(source,target,selected)
    }
    private fun typed(stage: StagedStorageGeneration) =
        FaroV5GenerationStoreGraph.requireSealedHousehold10Evidence2ArchiveStage(stage)
    private fun legacy(stage: StagedStorageGeneration) =
        FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(stage)
    private fun file(stage: StagedStorageGeneration,name: String)=stage.directory.resolve(name)

    @Test fun t01AdmitsSealedTypedHouseholdFiveStoreWithoutTouchingSourceOrPointer() {
        val f=fixture()
        val source=GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(file(f.source,it))
        }
        val target=GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(file(f.target,it))
        }
        typed(f.target)
        legacy(f.source)
        assertEquals(f.pinned,pointer.read())
        source.forEach { (name,data) -> assertArrayEquals(data,Files.readAllBytes(file(f.source,name))) }
        target.forEach { (name,data) -> assertArrayEquals(data,Files.readAllBytes(file(f.target,name))) }
    }

    @Test fun t02RequiresExactEvidence2TypedAndPreservesLegacySchemaOneAdmission() {
        val f=fixture()
        assertThrows(IllegalStateException::class.java) { typed(f.source) }
        assertThrows(IllegalStateException::class.java) { legacy(f.target) }
        typed(f.target)
        legacy(f.source)
        assertEquals(f.pinned,pointer.read())
    }

    @Test fun t03RejectMissingTypedTableOrUnsupportedEvidenceVersion() {
        val f=fixture()
        BundledSQLiteDriver().open(file(f.target,GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use {
            it.execSQL("DROP TABLE commercial_evidence_quote_terms")
        }
        assertThrows(IllegalStateException::class.java) { typed(f.target) }
        BundledSQLiteDriver().open(file(f.target,GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use {
            it.execSQL("PRAGMA user_version = 99")
        }
        assertThrows(IllegalStateException::class.java) { typed(f.target) }
    }

    @Test fun t04RejectUnexpectedTriggerAndOrphanTypedForeignKey() {
        val f=fixture()
        val evidence=file(f.target,GenerationStoreFiles.COMMERCIAL_EVIDENCE)
        BundledSQLiteDriver().open(evidence.toString()).use { db ->
            db.execSQL("CREATE TRIGGER f2b2a_unexpected AFTER INSERT ON commercial_evidence BEGIN SELECT 1; END")
        }
        assertThrows(IllegalStateException::class.java) { typed(f.target) }
        BundledSQLiteDriver().open(evidence.toString()).use { db ->
            db.execSQL("DROP TRIGGER f2b2a_unexpected")
            db.execSQL("PRAGMA foreign_keys=OFF")
            db.execSQL("INSERT INTO commercial_evidence_quote_terms(evidence_id,terms_fingerprint,"+
                "locality_kind,payment_method,eligibility,promotion_kind) VALUES ("+
                "'orphan', '"+("a".repeat(64))+"', 'UNKNOWN', 'UNKNOWN', 'UNKNOWN', 'UNKNOWN')")
        }
        assertThrows(IllegalStateException::class.java) { typed(f.target) }
    }

    @Test fun t05RejectSidecarSymlinkAndHardlinkInsteadOfTrustingIt() {
        val f=fixture()
        val sidecar=file(f.target,GenerationStoreFiles.REALITY+"-wal")
        Files.createFile(sidecar)
        assertThrows(IllegalStateException::class.java) { typed(f.target) }
        Files.delete(sidecar)
        val original=file(f.target,GenerationStoreFiles.WEB_EVIDENCE)
        Files.delete(original)
        Files.createSymbolicLink(original,file(f.source,GenerationStoreFiles.WEB_EVIDENCE))
        assertThrows(IllegalStateException::class.java) { typed(f.target) }
        Files.delete(original)
        Files.createLink(original,file(f.source,GenerationStoreFiles.WEB_EVIDENCE))
        assertThrows(IllegalStateException::class.java) { typed(f.target) }
    }

    @Test fun t06RejectReality10AndAuxiliary4MismatchesWithoutMovingPointer() {
        val f=fixture()
        val reality=file(f.target,GenerationStoreFiles.REALITY)
        BundledSQLiteDriver().open(reality.toString()).use { it.execSQL("PRAGMA user_version=9") }
        assertThrows(IllegalStateException::class.java) { typed(f.target) }
        BundledSQLiteDriver().open(reality.toString()).use { it.execSQL("PRAGMA user_version=10") }
        val auxiliary=file(f.target,GenerationStoreFiles.AUXILIARY)
        BundledSQLiteDriver().open(auxiliary.toString()).use { it.execSQL("PRAGMA user_version=3") }
        assertThrows(IllegalStateException::class.java) { typed(f.target) }
        assertEquals(f.pinned,pointer.read())
    }
}
