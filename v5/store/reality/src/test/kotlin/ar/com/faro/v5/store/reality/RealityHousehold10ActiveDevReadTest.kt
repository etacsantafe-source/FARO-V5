package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.contracts.TransactionId
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityHousehold10ActiveDevReadTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()

    @Test
    fun readsCommittedWalAndRejectsWriteTransactions() {
        val file = stage()
        database(file).use { fixture ->
            fixture.execSQL("PRAGMA wal_autocheckpoint=0")
            fixture.execSQL("UPDATE household_need_state SET revision=9")
            fixture.execSQL("INSERT INTO household_need VALUES ('b','Leche',1000,'LITER','NORMAL','OPEN',6,NULL)")
            fixture.execSQL("INSERT INTO household_need VALUES ('a','Yerba',500,'GRAM','HIGH','COMPLETED',7,20000)")
            val wal = root.resolve("reality.db-wal")
            val before = Files.readAllBytes(wal)
            assertTrue(before.isNotEmpty())
            active(file).use { store ->
                val rows = store.household10ActiveDevReadForTrustedHost { true }
                assertEquals(9L, rows.revision)
                assertEquals(listOf("a", "b"), rows.needs.map { it.id })
                assertEquals(listOf(500, 1000), rows.needs.map { it.quantityThousandths })
                assertThrows(IllegalStateException::class.java) {
                    store.run<Unit>(TransactionId("denied")) { error("must never enter write block") }
                }
            }
            assertArrayEquals(before, Files.readAllBytes(wal))
        }
        assertEquals(9L, RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(file).revision)
    }

    @Test
    fun preservesEmptyRevisedSnapshotAcrossReopen() {
        val file = stage()
        database(file).use { it.execSQL("UPDATE household_need_state SET revision=18") }
        repeat(2) {
            active(file).use {
                assertEquals(RealityHousehold10Rows(18, emptyList()), it.household10ActiveDevReadForTrustedHost { true })
            }
        }
    }

    @Test
    fun refusesWrongModesAndRevokedReadAdmission() {
        val file = stage()
        val nine = stage("nine.db", migrate = false)
        assertThrows(IllegalStateException::class.java) { active(nine) }
        assertThrows(IllegalStateException::class.java) {
            RealityStore(file.toString(), planningStage8 = true, financeActive9 = true, materialWriteGate = gate)
        }
        RealityStore(file.toString(), planningStage8 = true, householdStage10ReadOnly = true).use {
            assertThrows(IllegalStateException::class.java) { it.household10ActiveDevReadForTrustedHost { true } }
        }
        var current = true
        active(file) { current }.use { store ->
            assertThrows(IllegalStateException::class.java) { store.household10ActiveDevReadForTrustedHost { false } }
            var admissions = 0
            assertThrows(IllegalStateException::class.java) { store.household10ActiveDevReadForTrustedHost { ++admissions == 1 } }
            current = false
            assertThrows(IllegalStateException::class.java) { store.household10ActiveDevReadForTrustedHost { true } }
        }
    }

    @Test
    fun rejectsInvalidTextAndRevisionWithoutRepairingRows() {
        val file = stage()
        database(file).use { db ->
            db.execSQL("UPDATE household_need_state SET revision=1")
            db.execSQL("INSERT INTO household_need VALUES ('a',CAST(x'80' AS TEXT),1,'UNIT','HIGH','OPEN',1,NULL)")
        }
        val before = Files.readAllBytes(file)
        assertThrows(CharacterCodingException::class.java) { RealityStore.requireHealthyHousehold10ActiveDev(file) }
        assertArrayEquals(before, Files.readAllBytes(file))
        database(file).use { it.execSQL("UPDATE household_need SET revision=2,name='Yerba'") }
        active(file).use {
            assertThrows(IllegalArgumentException::class.java) { it.household10ActiveDevReadForTrustedHost { true } }
        }
    }

    private fun stage(name: String = "reality.db", migrate: Boolean = true): Path {
        val file = root.resolve(name)
        RealityStore(file.toString(), planningStage8 = true).close()
        database(file).use {
            RealityFinance9Schema.installOnStagedEightConnection(it, "prepare-nine", "a".repeat(64), 1L)
            if (migrate) RealityHousehold10Schema.installOnStagedNineConnection(it)
        }
        return file
    }

    private fun database(file: Path) = BundledSQLiteDriver().open(file.toString()).also {
        it.execSQL("PRAGMA foreign_keys=ON")
    }

    private fun active(file: Path, fence: () -> Boolean = { true }) = RealityStore(
        file.toString(), materialWriteGate = gate, planningStage8 = true, householdActive10Dev = fence,
    )
}
