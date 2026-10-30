package ar.com.faro.v5.workspace

import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** C115: bounded Workspace store admission evidence, NOT all five stores. */
class WorkspaceMaterialAdmissionT2Test {
    @TempDir lateinit var directory: Path
    private fun file() = directory.resolve("workspace-gated.db").toString()
    private fun create(store: ConversationWorkspaceStore, key: String) =
        store.create(WorkspaceCreateRequest(
            ConversationWorkspaceEntry(ConversationId("conversation-gated"), "Gated", 100),
            0L, IdempotencyKey(key),
        ))

    @Test fun closedAdmissionRejectsNewWriteBeforeWorkspaceMutexAndNoReceipt() {
        val gate = SingleProcessMaterialWriteGate()
        ConversationWorkspaceStore(file(), materialWriteGate = gate).use { store ->
            gate.closeMaterialWriteAdmission()
            assertThrows(IllegalStateException::class.java) { create(store, "blocked") }
            assertEquals(0L, store.snapshot().revision)
            gate.openMaterialWriteAdmission()
            assertTrue(create(store, "allowed") is WorkspaceWriteResult.Committed)
            assertEquals(1L, store.snapshot().revision)
        }
    }

    @Test fun sharedLeaseBlocksNewWriteUntilExclusiveMaintenanceIsReleased() {
        val gate = SingleProcessMaterialWriteGate()
        ConversationWorkspaceStore(file(), materialWriteGate = gate).use { store ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val maintenance = pool.submit(Callable {
                    gate.withQuiescedMaterialWrites {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        "released"
                    }
                })
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val attempted = CountDownLatch(1)
                val pending = pool.submit(Callable {
                    attempted.countDown()
                    create(store, "after-lease")
                })
                assertTrue(attempted.await(5, TimeUnit.SECONDS))
                assertThrows(TimeoutException::class.java) {
                    pending.get(150, TimeUnit.MILLISECONDS)
                }
                assertEquals(0L, store.snapshot().revision)
                release.countDown()
                assertEquals("released", maintenance.get(5, TimeUnit.SECONDS))
                assertTrue(pending.get(5, TimeUnit.SECONDS) is WorkspaceWriteResult.Committed)
                assertEquals(1L, store.snapshot().revision)
            } finally {
                release.countDown()
                pool.shutdownNow()
            }
        }
    }

    @Test fun maintenanceOwnerCanRestoreInsideItsOwnExclusiveLeaseWithoutDeadlock() {
        val gate = SingleProcessMaterialWriteGate()
        ConversationWorkspaceStore(file(), materialWriteGate = gate).use { store ->
            val result = gate.withQuiescedMaterialWrites {
                create(store, "maintenance-owned")
            }
            assertTrue(result is WorkspaceWriteResult.Committed)
            assertEquals(1L, store.snapshot().revision)
        }
    }
}
