package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household14ActiveDevReadHostTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val principal = "local-human"
    private val pointer get() = FileActiveGenerationPointer(root)

    @Test
    fun readsStockForTheAdmittedPrincipalWithoutChangingTheFiveStores() {
        val selected = prepare()
        val directory = FileStorageGenerationLayout(root).requireStaged(selected.generationId).directory
        val original = GenerationStoreFiles.WORKSPACE_V2.associateWith { Files.readAllBytes(directory.resolve(it)) }
        val expected = Household14ActiveDevStockSnapshot(selected, 5, listOf(
            Household14StockPosition("yerba", "UNIT", 3_000, 1_000),
        ))
        reopen(selected).use {
            assertEquals(expected, it.readStock())
            assertEquals(expected, it.readStock())
        }
        FaroV5Household14ActiveDevReadHost.reopen(root, selected, gate, "other-owner") { it == "other-owner" }.use {
            assertTrue(checkNotNull(it.readStock()).positions.isEmpty())
        }
        original.forEach { (name, bytes) -> assertArrayEquals(bytes, Files.readAllBytes(directory.resolve(name))) }
        assertEquals(selected, pointer.read())
    }

    @Test
    fun reopensAndReadsACommittedReleaseInWal() {
        val selected = prepare()
        val stage = FileStorageGenerationLayout(root).requireStaged(selected.generationId)
        val file = stage.directory.resolve(GenerationStoreFiles.REALITY)
        val mainBytes = Files.readAllBytes(file)
        BundledSQLiteDriver().open(file.toString()).use { fixture ->
            fixture.execSQL("PRAGMA wal_autocheckpoint = 0")
            fixture.execSQL("""
                INSERT INTO household_stock_reservation_event
                VALUES ('release-2','release-intent-2','RELEASE','reserve','yerba','UNIT',
                    1000,'local-human',3005,6)
            """.trimIndent())
            val wal = file.resolveSibling(file.fileName.toString() + "-wal")
            assertTrue(Files.size(wal) > 32L)
            assertArrayEquals(mainBytes, Files.readAllBytes(file))
            reopen(selected).use {
                val stock = checkNotNull(it.readStock())
                assertEquals(6L, stock.revision)
                assertEquals(Household14StockPosition("yerba", "UNIT", 3_000, 0), stock.positions.single())
            }
            assertEquals(selected, pointer.read())
        }
    }

    @Test
    fun refusesSql13AndSql14WithoutTheCompletedTicket() {
        val staged = prepare(complete = false)
        val original = checkNotNull(pointer.read())
        assertThrows(IllegalStateException::class.java) { reopen(original).close() }
        assertThrows(IllegalStateException::class.java) { reopen(staged).close() }
        val manufactured = checkNotNull(pointer.compareAndSet(original.generationId, staged.generationId))
        assertEquals(staged, manufactured)
        assertThrows(IllegalStateException::class.java) { reopen(manufactured).close() }
    }

    @Test
    fun revocationAndOccupiedRootFailClosed() {
        val selected = prepare()
        var admitted = true
        reopen(selected) { admitted && it == principal }.use { host ->
            assertEquals(1, checkNotNull(host.readStock()).positions.size)
            assertThrows(IllegalStateException::class.java) { reopen(selected).close() }
            admitted = false
            assertNull(host.readStock())
        }
        assertThrows(IllegalStateException::class.java) { reopen(selected) { false }.close() }
        reopen(selected).use { assertEquals(1, checkNotNull(it.readStock()).positions.size) }
    }

    @Test
    fun abaInvalidatesAnOpenHostAndRejectsReopen() {
        val selected = prepare()
        reopen(selected).use { host ->
            val alternate = StorageGenerationId("alternate-pointer")
            checkNotNull(pointer.compareAndSet(selected.generationId, alternate))
            val aba = checkNotNull(pointer.compareAndSet(alternate, selected.generationId))
            assertNull(host.readStock())
            assertEquals(selected.sequence + 2, aba.sequence)
        }
        assertThrows(IllegalStateException::class.java) { reopen(checkNotNull(pointer.read())).close() }
    }

    private fun prepare(complete: Boolean = true): ActiveGenerationPointer =
        FaroV5Household14Evidence2DevJournaledCutoverTest().also { it.root = root }
            .prepareSql14SelectionForReadHost(complete)

    private fun reopen(selected: ActiveGenerationPointer, admitted: (String) -> Boolean = { it == principal }) =
        FaroV5Household14ActiveDevReadHost.reopen(root, selected, gate, principal, admitted)
}
