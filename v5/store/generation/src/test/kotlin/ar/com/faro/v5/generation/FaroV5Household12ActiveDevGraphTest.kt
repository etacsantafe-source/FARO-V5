package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold11StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold12StagedFilePort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household12ActiveDevGraphTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val pointer get() = FileActiveGenerationPointer(root)

    private data class Fixture(val origin: ActiveGenerationPointer, val target: StagedStorageGeneration) {
        val reality: Path get() = target.directory.resolve(GenerationStoreFiles.REALITY)
    }

    @Test
    fun selectedSql12ReadsConfirmedPurchaseAndPartialReceipt() {
        val fixture = prepare()
        val selected = select(fixture)
        val bytes = Files.readAllBytes(fixture.reality)
        open(selected).use { session ->
            val snapshot = checkNotNull(session.readPurchaseReceipts())
            assertEquals(10_000, snapshot.purchases.single().quantityThousandths)
            assertEquals(7_000, snapshot.receipts.receipts.single().quantityThousandths)
            assertEquals("MANUAL_EXPLICIT", snapshot.purchases.single().provenance)
        }
        assertArrayEquals(bytes, Files.readAllBytes(fixture.reality))
        assertEquals(selected, pointer.read())
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openActiveHousehold10Evidence2DevForTrustedHost(
                root, fixture.target, selected, gate, { true },
            ).close()
        }
    }

    @Test
    fun readsCommittedFramesFromRealWal() {
        val fixture = prepare()
        val selected = select(fixture)
        BundledSQLiteDriver().open(fixture.reality.toString()).use { db ->
            db.execSQL("PRAGMA wal_autocheckpoint = 0")
            db.execSQL("INSERT INTO household_confirmed_purchase VALUES " +
                "('purchase-wal','yerba',3000,'UNIT','fixture-human',1001,'MANUAL_EXPLICIT',1)")
            val wal = fixture.reality.resolveSibling(fixture.reality.fileName.toString() + "-wal")
            assertTrue(Files.size(wal) > 32L)
            open(selected).use { session ->
                val snapshot = checkNotNull(session.readPurchaseReceipts())
                assertEquals(listOf("purchase", "purchase-wal"), snapshot.purchases.map { it.purchaseRef })
            }
        }
    }

    @Test
    fun rootLeaseIsExclusiveAndReopensAfterClose() {
        val selected = select(prepare())
        open(selected).use {
            assertThrows(IllegalStateException::class.java) { open(selected).close() }
        }
        open(selected).use { assertEquals(1, it.readPurchaseReceipts()?.purchases?.size) }
        open(selected).use { assertEquals(1L, it.readPurchaseReceipts()?.receipts?.revision) }
    }

    @Test
    fun rejectsPointerAbaAcrossReads() {
        val selected = select(prepare())
        open(selected).use { session ->
            assertEquals(1, session.readPurchaseReceipts()?.purchases?.size)
            val temporary = StorageGenerationId("w05-b3a-temporary")
            checkNotNull(pointer.compareAndSet(selected.generationId, temporary))
            val aba = checkNotNull(pointer.compareAndSet(temporary, selected.generationId))
            assertEquals(selected.sequence + 2, aba.sequence)
            assertNull(session.readPurchaseReceipts())
        }
        assertThrows(IllegalStateException::class.java) { open(selected).close() }
    }

    @Test
    fun revokeDuringReadDeniesSnapshotAndReleasesLease() {
        val selected = select(prepare())
        var remaining: Int? = null
        val admission = {
            remaining?.let { count ->
                remaining = count - 1
                count > 0
            } ?: true
        }
        open(selected, admission).use { session ->
            assertEquals(1, session.readPurchaseReceipts()?.purchases?.size)
            remaining = 2
            assertNull(session.readPurchaseReceipts())
        }
        open(selected).use { assertEquals(1, it.readPurchaseReceipts()?.purchases?.size) }
    }

    @Test
    fun rejectsSelectedSql11WithoutMigratingIt() {
        val fixture = prepare(sql12 = false)
        val selected = select(fixture)
        val before = Files.readAllBytes(fixture.reality)
        assertThrows(IllegalStateException::class.java) { open(selected).close() }
        assertArrayEquals(before, Files.readAllBytes(fixture.reality))
    }

    @Test
    fun rejectsEvidence1ForTypedActiveGraph() {
        val fixture = prepare(evidence2 = false)
        val selected = select(fixture)
        assertThrows(IllegalStateException::class.java) { open(selected).close() }
    }

    @Test
    fun rejectsUnselectedStageAndAliasedActiveFile() {
        val fixture = prepare()
        val expected = ActiveGenerationPointer(fixture.origin.sequence + 1, fixture.target.descriptor.generationId)
        assertThrows(IllegalStateException::class.java) { open(expected).close() }
        val selected = select(fixture)
        Files.createLink(root.resolve("alternate-reality.db"), fixture.reality)
        assertThrows(IllegalStateException::class.java) { open(selected).close() }
    }

    private fun open(expected: ActiveGenerationPointer, admission: () -> Boolean = { true }) =
        FaroV5Household12ActiveDevReadHost.reopen(root, expected, gate, admission)

    private fun select(fixture: Fixture): ActiveGenerationPointer =
        checkNotNull(pointer.compareAndSet(fixture.origin.generationId, fixture.target.descriptor.generationId))

    private fun prepare(sql12: Boolean = true, evidence2: Boolean = true): Fixture {
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("w05-b3a-initial")))
        val stage = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("w05-b3a-target"), "sql12-graph-dev-fixture", 100L),
        )
        FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
            MaintenanceOperationId("w05-b3a-finance"), stage, origin, "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        val fixture = Fixture(origin, stage)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(fixture.reality)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(fixture.reality)
        if (sql12) {
            RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(fixture.reality) {}
            BundledSQLiteDriver().open(fixture.reality.toString()).use { db ->
                db.execSQL("INSERT INTO household_confirmed_purchase VALUES " +
                    "('purchase','yerba',10000,'UNIT','fixture-human',1000,'MANUAL_EXPLICIT',1)")
                db.execSQL("INSERT INTO household_stock_receipt VALUES " +
                    "('receipt','intent','purchase','yerba',7000,'UNIT',0,1001,1)")
                db.execSQL("UPDATE household_stock_receipt_state SET revision=1 WHERE singleton_id=1")
            }
        }
        if (evidence2) {
            CommercialEvidenceStore(
                fileName = stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
                stagedTypedTermsV2 = true, stagedSemanticBackupV2 = true,
            ).close()
        }
        return fixture
    }
}
