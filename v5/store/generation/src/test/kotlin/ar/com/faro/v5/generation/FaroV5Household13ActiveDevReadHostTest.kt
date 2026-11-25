package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.*
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Only closed DEV root, synthetic pointer selection, SQL13 and no Apply permission. */
class FaroV5Household13ActiveDevReadHostTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val quiescence = MaterialWriteGateQuiescence(gate)
    private val pointer get() = FileActiveGenerationPointer(root)

    private data class Fixture(
        val expected: ActiveGenerationPointer,
        val stage: StagedStorageGeneration,
        val rows: RealityHousehold13CompensationRows?,
    )

    private fun historical() = RealityHousehold13CompensationRows(
        RealityHousehold12PurchaseReceiptRows(
            listOf(RealityHousehold12PurchaseRow(
                "p1", "yerba", 10_000, "UNIT", "human", 1L, "MANUAL_EXPLICIT", 1L)),
            RealityHousehold11ReceiptRows(4L, listOf(
                receipt(1L, 7_000), receipt(2L, 3_000), receipt(4L, 7_000),
            )),
        ),
        listOf(RealityHousehold13CompensationRow(
            "undo-r01", "undo-intent", "r01", "p1", 7_000, "UNIT", "human", 23L, 3L,
        )),
    )

    private fun receipt(revision: Long, quantity: Int) =
        RealityHousehold11ReceiptRow(
            "r0$revision", "i0$revision", "p1", "yerba", quantity, "UNIT", 0L,
            20L + revision, revision,
        )

    private fun fixture(to13: Boolean = true, evidence2: Boolean = true): Fixture {
        val original = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("w05-c2e-start"),
        ))
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(
                StorageGenerationId("w05-c2e-target"), "w05-c2e-active-read-dev", 100L,
            ),
        )
        val stage = FaroV5Finance9StagedMigration(root, quiescence).migrate(
            MaintenanceOperationId("w05-c2e-finance"), fresh, original,
            "a".repeat(64), 1_000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        val file = stage.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(file)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(file)
        RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(file) { }
        if (to13) RealityHousehold13StagedFilePort.migrateTrustedStagedTwelve(file)
        if (evidence2) {
            CommercialEvidenceStore(
                fileName = stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
                stagedTypedTermsV2 = true,
                stagedSemanticBackupV2 = true,
            ).close()
        }
        val data = if (to13) historical() else null
        if (data != null) {
            RealityHousehold13CompensationRowsPort.restoreOrVerifyTrustedSealedStageThirteen(file, data)
        }
        val selected = checkNotNull(pointer.compareAndSet(original.generationId, stage.descriptor.generationId))
        return Fixture(selected, stage, data)
    }

    private fun open(f: Fixture, admitted: () -> Boolean = { true }) =
        FaroV5Household13ActiveDevReadHost.reopen(root, f.expected, gate, admitted)

    @Test
    fun selectedSql13ReadsCompensationHistoryAndReopensWithoutMutation() {
        val f = fixture()
        val data = checkNotNull(f.rows)
        open(f).use { host ->
            val rows = checkNotNull(host.readHistory())
            assertEquals(data, rows)
            assertEquals(17_000, rows.historical.receipts.receipts.sumOf { it.quantityThousandths })
            assertEquals(7_000, rows.compensations.single().quantityThousandths)
        }
        open(f).use { assertEquals(data, it.readHistory()) }
        assertEquals(f.expected, pointer.read())
    }

    @Test
    fun revokedOwnerAdmissionAndFullPointerAbaFailClosed() {
        val f = fixture()
        var admitted = true
        open(f) { admitted }.use { host ->
            assertEquals(f.rows, host.readHistory())
            admitted = false
            assertNull(host.readHistory())
            admitted = true
            val foreign = checkNotNull(pointer.compareAndSet(
                f.expected.generationId, StorageGenerationId("w05-c2e-foreign"),
            ))
            val returned = checkNotNull(pointer.compareAndSet(
                foreign.generationId, f.expected.generationId,
            ))
            assertFalse(returned == f.expected)
            assertNull(host.readHistory())
        }
        assertThrows(IllegalStateException::class.java) { open(f) }
    }

    @Test
    fun exclusiveRootLeaseRejectsConcurrentHostAndClosesCleanly() {
        val f = fixture()
        open(f).use { host ->
            assertEquals(f.rows, host.readHistory())
            assertThrows(IllegalStateException::class.java) { open(f) }
        }
        open(f).use { assertEquals(f.rows, it.readHistory()) }
        assertThrows(IllegalStateException::class.java) { open(f) { false } }
    }

    @Test
    fun selectedHistoricalSql12IsNotSilentlyAdmittedAsSql13() {
        val f = fixture(to13 = false)
        assertThrows(IllegalStateException::class.java) { open(f) }
        assertEquals(f.expected, pointer.read())
        BundledSQLiteDriver().open(
            f.stage.directory.resolve(GenerationStoreFiles.REALITY).toString(),
            SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW,
        ).use { db ->
            assertEquals(12L, db.prepare("PRAGMA user_version").use { stmt ->
                check(stmt.step())
                stmt.getLong(0)
            })
        }
    }

    @Test
    fun evidence1ProfileIsRejectedBeforeExposingSql13Rows() {
        val f = fixture(evidence2 = false)
        assertThrows(IllegalStateException::class.java) { open(f) }
        assertEquals(f.expected, pointer.read())
        BundledSQLiteDriver().open(
            f.stage.directory.resolve(GenerationStoreFiles.REALITY).toString(),
            SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW,
        ).use { db ->
            assertEquals(1L, db.prepare("SELECT count(*) FROM household_stock_compensation").use { stmt ->
                check(stmt.step())
                stmt.getLong(0)
            })
            assertEquals(3L, db.prepare("SELECT count(*) FROM household_stock_receipt").use { stmt ->
                check(stmt.step())
                stmt.getLong(0)
            })
        }
    }

    @Test
    fun committedSql13WalIsObservedWithoutImmutableActiveReads() {
        val f = fixture()
        val file = f.stage.directory.resolve(GenerationStoreFiles.REALITY)
        BundledSQLiteDriver().open(file.toString()).use { writer ->
            writer.execSQL("PRAGMA journal_mode=WAL")
            writer.execSQL("INSERT INTO household_confirmed_purchase VALUES " +
                "('p2','aceite',2000,'UNIT','human',50,'MANUAL_EXPLICIT',2)")
            assertTrue(Files.exists(file.resolveSibling(file.fileName.toString() + "-wal")))
            open(f).use { host ->
                val rows = checkNotNull(host.readHistory())
                assertEquals(2, rows.historical.purchases.size)
                assertEquals(2_000, rows.historical.purchases.single { it.purchaseRef == "p2" }.quantityThousandths)
            }
        }
        open(f).use { assertEquals(2, checkNotNull(it.readHistory()).historical.purchases.size) }
    }
}
