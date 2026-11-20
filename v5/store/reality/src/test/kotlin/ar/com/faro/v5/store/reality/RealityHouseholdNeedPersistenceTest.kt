package ar.com.faro.v5.store.reality

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.RealityTransactionScope
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.contracts.TransactionId
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.owner.household.spi.HouseholdNeedChange
import ar.com.faro.v5.owner.household.spi.HouseholdNeedConflict
import ar.com.faro.v5.owner.household.spi.HouseholdNeedFields
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class RealityHouseholdNeedPersistenceTest {
    @TempDir lateinit var root: Path
    private val id = HouseholdNeedId("yerba")
    private val fields = HouseholdNeedFields(
        HouseholdNeedName("Yerba"), HouseholdNeedQuantity(750, HouseholdNeedUnit.GRAM),
        HouseholdNeedPriority.NORMAL, null,
    )
    private val empty = HouseholdNeedsSnapshot(0, emptyList())

    @Test
    fun createsEditsCompletesAndRemovesAcrossReopensWithoutChangingOtherOwners() {
        val file = stage()
        val changed = fields.copy(
            name = HouseholdNeedName("Café molido"),
            quantity = HouseholdNeedQuantity(1_250, HouseholdNeedUnit.KILOGRAM),
            priority = HouseholdNeedPriority.HIGH,
            dueDate = LocalDate.of(2026, 12, 4),
        )
        open(file).use { store ->
            val first = mutate(store, 0, HouseholdNeedChange.Add(id, fields))
            assertEquals(1L, first.needs.single().revision)
            val edited = mutate(store, 1, HouseholdNeedChange.Edit(id, 1, changed)).needs.single()
            assertEquals(changed.name, edited.name)
            assertEquals(changed.quantity, edited.quantity)
            assertEquals(changed.priority, edited.priority)
            assertEquals(changed.dueDate, edited.dueDate)
        }
        open(file).use { store ->
            assertEquals(changed.name, store.householdNeedsPersistenceForTrustedDev().read().needs.single().name)
            val done = mutate(store, 2, HouseholdNeedChange.Complete(id, 2)).needs.single()
            assertEquals(HouseholdNeedStatus.COMPLETED, done.status)
            assertEquals(3L, done.revision)
            assertEquals(HouseholdNeedsSnapshot(4, emptyList()), mutate(store, 3, HouseholdNeedChange.Remove(id, 3)))
        }
        open(file).use { assertEquals(HouseholdNeedsSnapshot(4, emptyList()), it.householdNeedsPersistenceForTrustedDev().read()) }
        openSql(file).use { db ->
            for (table in listOf("finance_state", "objective_state", "matter_state")) {
                db.prepare("SELECT revision FROM " + table).use {
                    assertTrue(it.step())
                    assertEquals(7L, it.getLong(0))
                }
            }
        }
        assertEquals(4L, RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(file).revision)
    }

    @Test
    fun rejectsStaleListAndRowVersionsAndDuplicateCompletion() {
        open(stage()).use { store ->
            mutate(store, 0, HouseholdNeedChange.Add(id, fields))
            val done = mutate(store, 1, HouseholdNeedChange.Complete(id, 1))
            assertThrows(HouseholdNeedConflict::class.java) { mutate(store, 1, HouseholdNeedChange.Remove(id, 2)) }
            assertThrows(HouseholdNeedConflict::class.java) { mutate(store, 2, HouseholdNeedChange.Remove(id, 1)) }
            assertThrows(HouseholdNeedConflict::class.java) { mutate(store, 2, HouseholdNeedChange.Add(id, fields)) }
            assertThrows(HouseholdNeedConflict::class.java) { mutate(store, 2, HouseholdNeedChange.Complete(id, 2)) }
            assertEquals(done, store.householdNeedsPersistenceForTrustedDev().read())
        }
    }

    @Test
    fun rollsBackOnInterruptedCommitAndWhenFenceChanges() {
        val file = stage()
        var live = true
        open(file) { live }.use { store ->
            val port = store.householdNeedsPersistenceForTrustedDev()
            assertThrows(IllegalStateException::class.java) {
                store.run<Unit>(TransactionId("interrupted")) {
                    port.mutate(0, HouseholdNeedChange.Add(id, fields), it)
                    error("injected interruption")
                }
            }
            assertEquals(empty, port.read())
            assertThrows(IllegalStateException::class.java) {
                store.run(TransactionId("revoked")) {
                    port.mutate(0, HouseholdNeedChange.Add(id, fields), it)
                    live = false
                    AtomicApplyTransactionDecision.Commit(Unit)
                }
            }
        }
        open(file).use { assertEquals(empty, it.householdNeedsPersistenceForTrustedDev().read()) }
    }

    @Test
    fun rejectsScopeFromAnotherRealityAndAnExpiredScope() {
        open(stage("left.db")).use { left ->
            open(stage("right.db")).use { right ->
                val port = left.householdNeedsPersistenceForTrustedDev()
                lateinit var expired: RealityTransactionScope
                left.run(TransactionId("expired")) {
                    expired = it
                    AtomicApplyTransactionDecision.Rollback(Unit)
                }
                left.run(TransactionId("expired")) {
                    assertThrows(IllegalStateException::class.java) { port.mutate(0, HouseholdNeedChange.Add(id, fields), expired) }
                    right.run(TransactionId("expired")) { foreign ->
                        assertThrows(IllegalStateException::class.java) { port.mutate(0, HouseholdNeedChange.Add(id, fields), foreign) }
                        AtomicApplyTransactionDecision.Rollback(Unit)
                    }
                    AtomicApplyTransactionDecision.Rollback(Unit)
                }
                assertEquals(empty, port.read())
            }
        }
    }

    @Test
    fun admitsOnlyOneOfTwoConcurrentChangesAtTheSameRevision() {
        open(stage()).use { store ->
            val pool = Executors.newFixedThreadPool(2)
            val start = CountDownLatch(1)
            try {
                val results = listOf("first", "second").map { token ->
                    pool.submit<Boolean> {
                        check(start.await(5, TimeUnit.SECONDS))
                        try {
                            mutate(store, 0, HouseholdNeedChange.Add(HouseholdNeedId(token), fields))
                            true
                        } catch (_: HouseholdNeedConflict) {
                            false
                        }
                    }
                }
                start.countDown()
                assertEquals(1, results.count { it.get(10, TimeUnit.SECONDS) })
                val snapshot = store.householdNeedsPersistenceForTrustedDev().read()
                assertEquals(1L, snapshot.revision)
                assertEquals(1, snapshot.needs.size)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun refusesOutOfRangeDatesAndAH10SSnapshotThatCannotBeBackedUp() {
        val file = stage()
        assertThrows(IllegalArgumentException::class.java) { HouseholdNeedQuantity(0, HouseholdNeedUnit.GRAM) }
        assertThrows(IllegalArgumentException::class.java) {
            HouseholdNeedQuantity(Int.MAX_VALUE.toLong() + 1, HouseholdNeedUnit.GRAM)
        }
        open(file).use { store ->
            val invalid = fields.copy(dueDate = LocalDate.of(10_000, 1, 1))
            assertThrows(IllegalArgumentException::class.java) { mutate(store, 0, HouseholdNeedChange.Add(id, invalid)) }
            assertEquals(empty, store.householdNeedsPersistenceForTrustedDev().read())
        }
        openSql(file).use { db ->
            db.execSQL("""
                WITH RECURSIVE ids(n) AS (VALUES(1) UNION ALL SELECT n+1 FROM ids WHERE n<8192)
                INSERT INTO household_need SELECT printf('seed-%04d',n),'A',1,'UNIT','LOW','OPEN',0,NULL FROM ids
            """.trimIndent())
        }
        open(file).use { store ->
            assertThrows(IllegalArgumentException::class.java) { mutate(store, 0, HouseholdNeedChange.Add(id, fields)) }
            val snapshot = store.householdNeedsPersistenceForTrustedDev().read()
            assertEquals(0L, snapshot.revision)
            assertEquals(8192, snapshot.needs.size)
        }
    }

    private fun mutate(store: RealityStore, revision: Long, change: HouseholdNeedChange): HouseholdNeedsSnapshot =
        store.run(TransactionId("household-test")) {
            AtomicApplyTransactionDecision.Commit(store.householdNeedsPersistenceForTrustedDev().mutate(revision, change, it))
        }

    private fun stage(name: String = "reality.db"): Path {
        val file = root.resolve(name)
        RealityStore(file.toString(), planningStage8 = true).close()
        openSql(file).use { db ->
            RealityFinance9Schema.installOnStagedEightConnection(db, "prepare-nine", "a".repeat(64), 1L)
            RealityHousehold10Schema.installOnStagedNineConnection(db)
            for (table in listOf("finance_state", "objective_state", "matter_state")) {
                db.execSQL("UPDATE " + table + " SET revision=7")
            }
        }
        return file
    }

    private fun openSql(file: Path) = BundledSQLiteDriver().open(file.toString()).also { it.execSQL("PRAGMA foreign_keys=ON") }

    private fun open(file: Path, live: () -> Boolean = { true }) = RealityStore(
        file.toString(), materialWriteGate = SingleProcessMaterialWriteGate(), planningStage8 = true,
        householdActive10Dev = live, householdNeedsWritableDev = true,
    )
}
