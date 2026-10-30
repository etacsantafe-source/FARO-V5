package ar.com.faro.v5.store.reality

import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionDecision
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.kernel.contracts.TransactionId
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** C117 staged Reality admission, does not enable V2 graph or restore cutover. */
class RealityMaterialAdmissionT2Test {
    @TempDir lateinit var directory: Path
    private fun file() = directory.resolve("reality-gated.db").toString()

    private fun transact(store: RealityStore, id: String): String =
        store.run(TransactionId(id)) { AtomicApplyTransactionDecision.Commit("committed") }

    @Test fun closedAdmissionRejectsRealityTransactionBeforeSqliteBegin() {
        val gate = SingleProcessMaterialWriteGate()
        RealityStore(file(), materialWriteGate = gate).use { store ->
            gate.closeMaterialWriteAdmission()
            val error = assertThrows(IllegalStateException::class.java) {
                transact(store, "tx-blocked")
            }
            assertTrue(error.message!!.contains("material writes are suspended"))
            assertNull(store.read().displayName)
            assertEquals(0L, store.read().revision)
            assertEquals("ok", store.integrityCheck())
            gate.openMaterialWriteAdmission()
            assertEquals("committed", transact(store, "tx-allowed"))
        }
    }

    @Test fun exclusiveMaintenanceLeaseBlocksRealityWriterUntilReleased() {
        val gate = SingleProcessMaterialWriteGate()
        RealityStore(file(), materialWriteGate = gate).use { store ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val attempted = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val maintenance = pool.submit(Callable<String> {
                    gate.withQuiescedMaterialWrites {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        "released"
                    }
                })
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val pending = pool.submit(Callable<String> {
                    attempted.countDown()
                    transact(store, "tx-queued")
                })
                assertTrue(attempted.await(5, TimeUnit.SECONDS))
                assertThrows(TimeoutException::class.java) {
                    pending.get(150, TimeUnit.MILLISECONDS)
                }
                assertNull(store.read().displayName)
                release.countDown()
                assertEquals("released", maintenance.get(5, TimeUnit.SECONDS))
                assertEquals("committed", pending.get(5, TimeUnit.SECONDS))
                assertEquals("ok", store.integrityCheck())
            } finally {
                release.countDown()
                pool.shutdownNow()
            }
        }
    }

    @Test fun maintenanceOwnerCanReenterRealityForRollbackAndCommit() {
        val gate = SingleProcessMaterialWriteGate()
        RealityStore(file(), materialWriteGate = gate).use { store ->
            val outcome = gate.withQuiescedMaterialWrites {
                val rollback = store.run(TransactionId("tx-rollback")) {
                    AtomicApplyTransactionDecision.Rollback("rolled-back")
                }
                assertEquals("rolled-back", rollback)
                transact(store, "tx-maintenance")
            }
            assertEquals("committed", outcome)
            assertEquals(0L, store.read().revision)
            assertEquals("ok", store.integrityCheck())
        }
    }
}
