package ar.com.faro.v5.generation

import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverRuntime.Selection
import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverRuntime.Status
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Finance9DevSingleGraphMountTest {
    @TempDir lateinit var root: Path

    private val gate = SingleProcessMaterialWriteGate()
    private val verifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val operation = MaintenanceOperationId("d1b1c2c-operation")
    private val sourceId = StorageGenerationId("d1b1c2c-source")
    private val targetId = StorageGenerationId("d1b1c2c-target")
    private val target = StorageGenerationDescriptor(targetId, "d1b1c2c-target", 20L)

    private fun origin(): ActiveGenerationPointer {
        FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(sourceId, "d1b1c2c-source", 10L),
        )
        return checkNotNull(pointer.compareAndSet(null, sourceId))
    }

    private fun acquire() = FaroV5Finance9DevCutoverRuntime.acquire(root, gate, verifier)

    private fun activate(host: FaroV5Finance9DevCutoverRuntime, origin: ActiveGenerationPointer) =
        host.start(operation, target, origin, 30L) { true }.also { assertEquals(Status.ACTIVE_NINE, it.status) }

    @Test fun committedLeaseOwnsExactlyOneHealthyGraph() {
        val origin = origin()
        acquire().use { host ->
            val selection = activate(host, origin)
            val graph = host.openCommittedFinance9(selection)
            assertTrue(host.isCurrent(selection))
            assertEquals(9, graph.reality.maintenance.inspect().schemaVersion)
            assertEquals(targetId.value, graph.generationId)
            assertTrue(graph.workspace != null && graph.verifyHealthy())
            assertThrows(IllegalStateException::class.java) { host.openCommittedFinance9(selection) }
            assertThrows(IllegalStateException::class.java) { host.recover() }
            assertThrows(IllegalStateException::class.java) { acquire() }
        }
    }

    @Test fun forgedAndStaleSelectionsCannotOpenBeforeOrAfterActivation() {
        val origin = origin()
        acquire().use { host ->
            val forged = Selection(Status.ACTIVE_NINE, ActiveGenerationPointer(origin.sequence + 1, targetId))
            assertFalse(host.isCurrent(forged))
            assertThrows(IllegalStateException::class.java) { host.openCommittedFinance9(forged) }
            val selection = activate(host, origin)
            val stale = selection.copy(pointer = selection.pointer.copy(sequence = origin.sequence))
            assertFalse(host.isCurrent(stale))
            assertThrows(IllegalStateException::class.java) { host.openCommittedFinance9(stale) }
            assertTrue(host.openCommittedFinance9(selection).verifyHealthy())
        }
    }

    @Test fun missingReplayFenceDeniesMountWithOtherwiseMatchingSelection() {
        val origin = origin()
        acquire().use { host ->
            val selection = activate(host, origin)
            val directory = root.resolve("bootstrap/replay-fences")
            Files.newDirectoryStream(directory, "fence-*.bin").use { files ->
                Files.move(files.single(), directory.resolve("held-aside"))
            }
            assertFalse(host.isCurrent(selection))
            assertThrows(IllegalStateException::class.java) { host.openCommittedFinance9(selection) }
        }
    }

    @Test fun changedPointerInvalidatesMountedSelection() {
        val origin = origin()
        acquire().use { host ->
            val selection = activate(host, origin)
            host.openCommittedFinance9(selection)
            assertTrue(host.isCurrent(selection))
            checkNotNull(pointer.compareAndSet(targetId, sourceId))
            assertFalse(host.isCurrent(selection))
        }
    }

    @Test fun closingLeaseClosesGraphAndFreshLeaseCanReopen() {
        val origin = origin()
        val host = acquire()
        val (selection, graph) = host.use {
            val selection = activate(it, origin)
            selection to it.openCommittedFinance9(selection)
        }
        assertFalse(host.isCurrent(selection))
        assertThrows(IllegalStateException::class.java) { graph.reality.financePersistence.read() }
        acquire().use {
            val recovered = checkNotNull(it.recover())
            assertEquals(selection, recovered)
            assertTrue(it.openCommittedFinance9(recovered).verifyHealthy())
        }
    }
}
