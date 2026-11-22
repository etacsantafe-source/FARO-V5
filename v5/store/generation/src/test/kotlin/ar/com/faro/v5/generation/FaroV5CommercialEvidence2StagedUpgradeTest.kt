package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEvidenceRecordOutcome
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialPriceObservation
import ar.com.faro.v5.evidence.CommercialProductKey
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** F2A is a real five-SQLite isolated STAGED migration test, NOT an ACTIVE cutover. */
class FaroV5CommercialEvidence2StagedUpgradeTest {
    @TempDir lateinit var root: Path
    private val pointer get() = FileActiveGenerationPointer(root)
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private data class Fixture(
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val active: ActiveGenerationPointer,
    )

    private fun descriptor(name: String) =
        StorageGenerationDescriptor(StorageGenerationId(name), "f2a-typed-copy-dev", 100L)

    private fun stage(name: String = "case"): Fixture {
        val old = pointer.read() ?: checkNotNull(
            pointer.compareAndSet(null, StorageGenerationId("f2a-original")),
        )
        val source = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            descriptor("$name-source"),
        )
        FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("$name-finance9"), source, old,
            "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(source, pointer).close()
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(
            source.directory.resolve(GenerationStoreFiles.REALITY),
        )
        // A real existing schema1 record is kept; no fake typed terms are supplied.
        CommercialEvidenceStore(
            source.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
            clock = { 1000L },
        ).use { commercial ->
            val outcome = commercial.record(
                CommercialPriceObservation(
                    CommercialProductKey("sku-test"), CommercialMerchant("Mercado prueba"),
                    CommercialCurrencyCode("ARS"), 15000L,
                    CommercialEvidenceSourceKind.MANUAL, "manual:legacy", 900L,
                ),
                IdempotencyKey("$name-record"), PayloadFingerprint("$name-record-fp"),
            )
            assertTrue(outcome is CommercialEvidenceRecordOutcome.Committed)
        }
        val selected = checkNotNull(
            pointer.compareAndSet(old.generationId, source.descriptor.generationId),
        )
        val target = FileStorageGenerationLayout(root).stageWorkspaceV2(descriptor("$name-target"))
        GenerationStoreFiles.WORKSPACE_V2.forEach { file ->
            Files.copy(source.directory.resolve(file), target.directory.resolve(file))
        }
        return Fixture(source, target, selected)
    }

    private fun upgrade(f: Fixture, closed: () -> Unit = {}, after: () -> Unit = {}) =
        FaroV5CommercialEvidence2StagedUpgrade(root, gate, closed, after).upgrade(
            MaintenanceOperationId("f2a-upgrade"), f.target, f.active,
        )

    private fun version(file: Path): Long = BundledSQLiteDriver().open(file.toString()).use { db ->
        db.prepare("PRAGMA user_version").use { cursor ->
            check(cursor.step())
            cursor.getLong(0)
        }
    }

    private fun commercial(stage: StagedStorageGeneration) =
        stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE)

    @Test fun t01UpgradeExactFiveStoreCopyPreservesSourceAndLegacyTermsAbsence() {
        val f=stage()
        val sourceBefore=GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(f.source.directory.resolve(it))
        }
        val targetBefore=GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(f.target.directory.resolve(it))
        }
        assertEquals(f.target,upgrade(f))
        assertEquals(f.active,pointer.read())
        assertEquals(1L,version(commercial(f.source)))
        assertEquals(2L,version(commercial(f.target)))
        for(name in GenerationStoreFiles.WORKSPACE_V2) {
            assertArrayEquals(sourceBefore.getValue(name),Files.readAllBytes(f.source.directory.resolve(name)))
            if(name!=GenerationStoreFiles.COMMERCIAL_EVIDENCE)
                assertArrayEquals(targetBefore.getValue(name),Files.readAllBytes(f.target.directory.resolve(name)))
        }
        CommercialEvidenceStore(commercial(f.target).toString(),stagedTypedTermsV2=true).use {
            val rows=it.stagedTypedBackup.snapshot().rows
            assertEquals(1,rows.size)
            assertNull(rows.single().terms)
            assertEquals("manual:legacy",rows.single().record.observation.sourceRef)
        }
    }

    @Test fun t02RejectActiveAndForgedTargetWithoutMigration() {
        val f=stage()
        assertThrows(IllegalStateException::class.java) { upgrade(f.copy(target=f.source)) }
        assertThrows(IllegalStateException::class.java) {
            upgrade(f.copy(target=f.target.copy(directory=root.resolve("forged"))))
        }
        assertEquals(1L,version(commercial(f.target)))
    }

    @Test fun t03RejectDivergentCopiedEvidenceWithNoDataLoss() {
        val f=stage()
        CommercialEvidenceStore(commercial(f.target).toString(),clock={ 1020L }).use {
            it.record(CommercialPriceObservation(
                CommercialProductKey("sku-other"),CommercialMerchant("Mercado adicional"),
                CommercialCurrencyCode("USD"),4500L,CommercialEvidenceSourceKind.MANUAL,
                "manual:unexpected",910L,
            ),IdempotencyKey("other-record"),PayloadFingerprint("other-fp"))
        }
        assertThrows(IllegalStateException::class.java) { upgrade(f) }
        assertEquals(1L,version(commercial(f.target)))
        assertEquals(1L,version(commercial(f.source)))
    }

    @Test fun t04RejectHardlinkAliasBeforeOpeningAnyStore() {
        val f=stage()
        Files.delete(commercial(f.target))
        Files.createLink(commercial(f.target),commercial(f.source))
        assertThrows(IllegalStateException::class.java) { upgrade(f) }
        assertEquals(1L,version(commercial(f.source)))
    }

    @Test fun t05RejectSidecarsAndSymlinksInsteadOfTrustingThem() {
        val f=stage()
        val sidecar=f.target.directory.resolve(GenerationStoreFiles.REALITY+"-wal")
        Files.createFile(sidecar)
        assertThrows(IllegalStateException::class.java) { upgrade(f) }
        Files.delete(sidecar)
        val file=f.target.directory.resolve(GenerationStoreFiles.WEB_EVIDENCE)
        Files.delete(file)
        Files.createSymbolicLink(file,f.source.directory.resolve(GenerationStoreFiles.WEB_EVIDENCE))
        assertThrows(IllegalStateException::class.java) { upgrade(f) }
        assertEquals(1L,version(commercial(f.source)))
    }

    @Test fun t06RejectPointerABAAfterSchemaUpgradeWithoutCutover() {
        val f=stage()
        assertThrows(IllegalStateException::class.java) {
            upgrade(f, after={
                val other=StorageGenerationId("f2a-transient")
                checkNotNull(pointer.compareAndSet(f.active.generationId,other))
                checkNotNull(pointer.compareAndSet(other,f.active.generationId))
            })
        }
        assertEquals(2L,version(commercial(f.target)))
        assertEquals(1L,version(commercial(f.source)))
        assertNotEquals(f.active.sequence,checkNotNull(pointer.read()).sequence)
    }

    @Test fun t07RevokedClosedHostAdmissionRejectsBeforeMutation() {
        val f=stage()
        var checks=0
        assertThrows(IllegalStateException::class.java) {
            upgrade(f,closed={check(++checks==1){"closed DEV lease revoked"}})
        }
        assertEquals(1L,version(commercial(f.target)))
        assertEquals(f.active,pointer.read())
    }

    @Test fun t08RejectTriggerAndUnknownSchemaVersionBeforeUpgrade() {
        val f=stage()
        BundledSQLiteDriver().open(commercial(f.target).toString()).use {
            it.execSQL("CREATE TRIGGER surprise AFTER INSERT ON commercial_evidence BEGIN SELECT 1; END")
        }
        assertThrows(IllegalStateException::class.java) {upgrade(f)}
        assertEquals(1L,version(commercial(f.target)))
        BundledSQLiteDriver().open(commercial(f.target).toString()).use {
            it.execSQL("DROP TRIGGER surprise")
            it.execSQL("PRAGMA user_version = 99")
        }
        assertThrows(IllegalStateException::class.java) {upgrade(f)}
        assertEquals(99L,version(commercial(f.target)))
        assertEquals(1L,version(commercial(f.source)))
    }
}
