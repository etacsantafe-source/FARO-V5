package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRestoreDisposition
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold11StagedFilePort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household11ReceiptSql11PortTest {
    @TempDir lateinit var root: Path
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private val pointer get() = FileActiveGenerationPointer(root)
    private val op = MaintenanceOperationId("w05-a1b2b-sql11")
    private val empty = Household11ReceiptBackupSection(0L, emptyList())

    private fun port(
        host: () -> Unit = {},
        beforeCommit: () -> Unit = {},
        afterCapture: () -> Unit = {},
    ) = FaroV5Household11ReceiptSql11Port(root, gate, host, beforeCommit, afterCapture)

    private fun origin() = pointer.read() ?: checkNotNull(
        pointer.compareAndSet(null, StorageGenerationId("w05-sql11-origin")),
    )

    private fun stage(id: String = "w05-sql11-stage"): StagedStorageGeneration {
        val active = origin()
        val staged = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "household11-sql11", 100L),
        )
        FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("w05-sql11-finance-" + id), staged, active, "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(staged, pointer).close()
        val reality = staged.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(reality)
        return staged
    }

    private fun row(
        id: String,
        intent: String,
        quantity: Int,
        revision: Long,
    ) = Household11ReceiptBackupRow(
        id, intent, "purchase-1", "yerba", quantity, "UNIT",
        0L, 1000L, revision,
    )

    private fun section(revision: Long = 2L) = Household11ReceiptBackupSection(
        revision, listOf(row("r-01", "intent-01", 7_000, 1L),
            row("r-02", "intent-02", 3_000, 2L)),
    )

    private fun file(stage: StagedStorageGeneration) =
        stage.directory.resolve(GenerationStoreFiles.REALITY)

    private fun sql(file: Path, command: String) {
        BundledSQLiteDriver().open(file.toString()).use { it.execSQL(command) }
    }

    @Test fun emptySql11RoundTripsWithoutFabricatingStock() {
        val source = stage()
        val before = Files.readAllBytes(file(source))
        assertEquals(empty, port().captureSealed(op, source, origin()))
        assertEquals(RealityHousehold11ReceiptRestoreDisposition.ALREADY_IDENTICAL,
            port().restoreOrVerifySealed(op, source, origin(), empty))
        assertEquals(empty, port().captureSealed(op, source, origin()))
        assertArrayEquals(before, Files.readAllBytes(file(source)))
    }

    @Test fun readsTwoGenuineSqlRowsAndPreservesPartialPurchaseQuantities() {
        val source = stage()
        sql(file(source), "UPDATE household_stock_receipt_state SET revision=2 WHERE singleton_id=1")
        sql(file(source), "INSERT INTO household_stock_receipt VALUES " +
            "('r-02','intent-02','purchase-1','yerba',3000,'UNIT',0,1000,2)," +
            "('r-01','intent-01','purchase-1','yerba',7000,'UNIT',0,1000,1)")
        val captured = port().captureSealed(op, source, origin())
        assertEquals(section(), captured)
        assertArrayEquals(FaroV5Household11ReceiptBackupSectionCodec.encode(captured),
            FaroV5Household11ReceiptBackupSectionCodec.encode(section()))
    }

    @Test fun restoreToEmptyStageAndReplayIsIdempotent() {
        val target = stage()
        assertEquals(RealityHousehold11ReceiptRestoreDisposition.RESTORED,
            port().restoreOrVerifySealed(op, target, origin(), section()))
        assertEquals(RealityHousehold11ReceiptRestoreDisposition.ALREADY_IDENTICAL,
            port().restoreOrVerifySealed(op, target, origin(), section()))
        assertEquals(section(), port().captureSealed(op, target, origin()))
        val reality = file(target)
        assertEquals(2L, BundledSQLiteDriver().open(reality.toString()).use { db ->
            db.prepare("SELECT count(*) FROM household_stock_receipt").use {
                check(it.step())
                it.getLong(0)
            }
        })
    }

    @Test fun revisedEmptySql11SectionRemainsExplicit() {
        val target = stage()
        val revisedEmpty = Household11ReceiptBackupSection(9L, emptyList())
        assertEquals(RealityHousehold11ReceiptRestoreDisposition.RESTORED,
            port().restoreOrVerifySealed(op, target, origin(), revisedEmpty))
        assertEquals(revisedEmpty, port().captureSealed(op, target, origin()))
        assertEquals(RealityHousehold11ReceiptRestoreDisposition.ALREADY_IDENTICAL,
            port().restoreOrVerifySealed(op, target, origin(), revisedEmpty))
    }

    @Test fun nonemptyOrRevisedTargetsCannotBeOverwritten() {
        val target = stage()
        val revised = Household11ReceiptBackupSection(3L, listOf(row("a", "original", 1_000, 1L)))
        port().restoreOrVerifySealed(op, target, origin(), revised)
        assertThrows(IllegalStateException::class.java) {
            port().restoreOrVerifySealed(op, target, origin(), section())
        }
        assertEquals(revised, port().captureSealed(op, target, origin()))
        val other = stage("w05-sql11-revised-empty")
        port().restoreOrVerifySealed(op, other, origin(), Household11ReceiptBackupSection(2L, emptyList()))
        assertThrows(IllegalStateException::class.java) {
            port().restoreOrVerifySealed(op, other, origin(), section())
        }
    }

    @Test fun invalidReceiptsFailBeforeSqlWrites() {
        val target = stage()
        val cases = listOf(
            Household11ReceiptBackupSection(0L, listOf(row("a", "i1", 1, 1L))),
            Household11ReceiptBackupSection(1L, listOf(row("a", "i1", 0, 1L))),
            Household11ReceiptBackupSection(1L, listOf(row("a", "i1", 1, 2L))),
            Household11ReceiptBackupSection(2L, listOf(row("a", "same", 1, 1L),
                row("b", "same", 2, 2L))),
            Household11ReceiptBackupSection(2L, listOf(row("a", "i1", 1, 1L),
                row("a", "i2", 2, 2L))),
            Household11ReceiptBackupSection(1L, listOf(row("a", "i1", 1, 1L).copy(unit = "BAD"))),
            Household11ReceiptBackupSection(1L, listOf(row("a", "i1", 1, 1L).copy(recordedAtMs = -1))),
        )
        for (bad in cases) {
            assertThrows(IllegalArgumentException::class.java) {
                port().restoreOrVerifySealed(op, target, origin(), bad)
            }
        }
        assertEquals(empty, port().captureSealed(op, target, origin()))
    }

    @Test fun wrongSqlSchemaAndTriggersFailClosed() {
        val first = stage()
        sql(file(first), "PRAGMA user_version = 12")
        assertThrows(IllegalStateException::class.java) {
            port().captureSealed(op, first, origin())
        }
        val second = stage("w05-sql11-trigger")
        sql(file(second), "CREATE TRIGGER hostile_receipt AFTER INSERT ON household_stock_receipt " +
            "BEGIN SELECT 1; END")
        assertThrows(IllegalStateException::class.java) {
            port().captureSealed(op, second, origin())
        }
        assertThrows(IllegalStateException::class.java) {
            port().restoreOrVerifySealed(op, second, origin(), section())
        }
    }

    @Test fun hardlinksSidecarsAndActiveGenerationCannotReceiveRows() {
        val target = stage()
        val reality = file(target)
        val alias = Files.createLink(root.resolve("w05-hardlink.db"), reality)
        assertThrows(IllegalStateException::class.java) {
            port().captureSealed(op, target, origin())
        }
        Files.delete(alias)
        val sidecar = reality.resolveSibling(reality.fileName.toString() + "-wal")
        Files.writeString(sidecar, "untrusted")
        assertThrows(IllegalStateException::class.java) {
            port().restoreOrVerifySealed(op, target, origin(), section())
        }
        Files.delete(sidecar)
        val active = origin()
        val changed = checkNotNull(pointer.compareAndSet(active.generationId, target.descriptor.generationId))
        assertThrows(IllegalStateException::class.java) {
            port().restoreOrVerifySealed(op, target, changed, section())
        }
    }

    @Test fun revokedHostAndPointerAbaBlockCapture() {
        val target = stage()
        assertThrows(IllegalStateException::class.java) {
            port(host = { error("closed dev host lease revoked") }).captureSealed(op, target, origin())
        }
        val original = origin()
        val afterRead = {
            val alternate = StorageGenerationId("w05-pointer-alternate")
            checkNotNull(pointer.compareAndSet(original.generationId, alternate))
            checkNotNull(pointer.compareAndSet(alternate, original.generationId))
            Unit
        }
        assertThrows(IllegalStateException::class.java) {
            port(afterCapture = afterRead).captureSealed(op, target, original)
        }
        assertEquals(empty, port().captureSealed(op, target, checkNotNull(pointer.read())))
    }

    @Test fun injectedFailureBeforeCommitRollsBackAndSafeRetryWorks() {
        val target = stage()
        val before = port().captureSealed(op, target, origin())
        assertThrows(IllegalStateException::class.java) {
            port(beforeCommit = { error("simulate crash before COMMIT") })
                .restoreOrVerifySealed(op, target, origin(), section())
        }
        assertEquals(before, port().captureSealed(op, target, origin()))
        assertEquals(RealityHousehold11ReceiptRestoreDisposition.RESTORED,
            port().restoreOrVerifySealed(op, target, origin(), section()))
        assertEquals(section(), port().captureSealed(op, target, origin()))
    }
}
