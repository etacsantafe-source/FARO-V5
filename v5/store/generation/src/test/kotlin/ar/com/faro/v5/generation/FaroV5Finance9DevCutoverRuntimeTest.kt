package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverRuntime.Status
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.BootstrapTransitionPolicy
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Finance9DevCutoverRuntimeTest {
    @TempDir lateinit var root: Path

    private val gate = SingleProcessMaterialWriteGate()
    private val verifier = PreparedPlanEnvelopeVerificationPort { _, _ -> false }
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val journal by lazy { FileBootstrapJournal(root) }
    private val operation = MaintenanceOperationId("d1b1c2b-operation")
    private val originId = StorageGenerationId("d1b1c2b-origin")
    private val targetId = StorageGenerationId("d1b1c2b-target")
    private val target = StorageGenerationDescriptor(targetId, "d1b1c2b-target", 20L)

    private fun seedPlanningEight(): Pair<ActiveGenerationPointer, Path> {
        val staged = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(originId, "d1b1c2b-source", 10L),
        )
        val reality = staged.directory.resolve(GenerationStoreFiles.REALITY)
        return checkNotNull(pointer.compareAndSet(null, originId)) to reality
    }

    private fun acquire(
        fault: (GenerationSwitchFaultPoint) -> Unit = {},
        afterAuthorization: () -> Unit = {},
    ) = FaroV5Finance9DevCutoverRuntime.acquire(root, gate, verifier, fault, afterAuthorization)

    private fun runExternalLockProbe(): Int {
        val script = """
            import errno, fcntl, sys
            with open(sys.argv[1], 'r+b') as handle:
                try:
                    fcntl.lockf(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except OSError as error:
                    if error.errno in (errno.EACCES, errno.EAGAIN):
                        sys.exit(42)
                    raise
        """.trimIndent()
        val child = ProcessBuilder("python3", "-c", script, root.resolve("finance9-dev-root.lock").toString())
            .redirectErrorStream(true)
            .start()
        try {
            assertTrue(child.waitFor(10, TimeUnit.SECONDS), "OS lock probe timed out")
            return child.exitValue()
        } finally {
            if (child.isAlive) child.destroyForcibly()
        }
    }

    @Test fun exclusiveRootSpansBothJvmAndRealOsProcess() {
        acquire().use {
            assertThrows(IllegalStateException::class.java) { acquire() }
            assertEquals(42, runExternalLockProbe())
        }
        assertEquals(0, runExternalLockProbe())
        acquire().use { assertNull(it.recover()) }
    }

    @Test fun explicitConfirmationActivatesAndReacquisitionReadsSameDurableChoice() {
        val (origin, _) = seedPlanningEight()
        val expected = ActiveGenerationPointer(origin.sequence + 1, targetId)
        acquire().use {
            val result = it.start(operation, target, origin, 30L) { true }
            assertEquals(Status.ACTIVE_NINE, result.status)
            assertEquals(expected, result.pointer)
        }
        acquire().use {
            assertEquals(expected, it.recover()?.pointer)
            assertEquals(Status.ACTIVE_NINE, it.recover()?.status)
            assertEquals(BootstrapPhase.COMPLETED, journal.read(operation)?.phase)
        }
    }

    @Test fun noInitialConfirmationLeavesNoIntentOrJournal() {
        val (origin, _) = seedPlanningEight()
        acquire().use {
            assertThrows(IllegalStateException::class.java) {
                it.start(operation, target, origin, 30L) { false }
            }
            assertNull(it.recover())
        }
        assertEquals(origin, pointer.read())
        assertNull(journal.read(operation))
        assertFalse(Files.exists(root.resolve("bootstrap/finance9-dev-cutover-v1.bin")))
    }

    @Test fun lostForegroundAfterCopyRetainsOriginAndAllowsNewAttempt() {
        val (origin, _) = seedPlanningEight()
        var checks = 0
        acquire().use {
            val result = it.start(operation, target, origin, 30L) { ++checks == 1 }
            assertEquals(Status.ORIGIN_RETAINED, result.status)
            assertEquals(BootstrapPhase.ABORTED_PRE_SWITCH, journal.read(operation)?.phase)
        }
        acquire().use {
            assertEquals(origin, it.recover()?.pointer)
            val retry = it.start(
                MaintenanceOperationId("d1b1c2b-retry"),
                target.copy(generationId = StorageGenerationId("d1b1c2b-retry-stage")),
                origin, 31L,
            ) { true }
            assertEquals(Status.ACTIVE_NINE, retry.status)
        }
    }

    @Test fun prepareOnlyRecordNeverPromotesAfterRestart() {
        val (origin, _) = seedPlanningEight()
        var checks = 0
        acquire().use {
            assertThrows(IllegalStateException::class.java) {
                it.start(operation, target, origin, 30L) {
                    if (++checks == 2) error("interrupted without authorizing switch")
                    true
                }
            }
        }
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
        acquire().use {
            assertEquals(Status.ORIGIN_RETAINED, it.recover()?.status)
            assertEquals(origin, pointer.read())
            assertEquals(BootstrapPhase.ABORTED_PRE_SWITCH, journal.read(operation)?.phase)
        }
    }

    @Test fun durableAuthorizationRecoversAfterPointerCasWithoutNewConfirmation() {
        val (origin, _) = seedPlanningEight()
        acquire(fault = { point ->
            if (point == GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE) {
                error("interrupted after CAS")
            }
        }).use {
            assertThrows(IllegalStateException::class.java) {
                it.start(operation, target, origin, 30L) { true }
            }
        }
        val expected = ActiveGenerationPointer(origin.sequence + 1, targetId)
        assertEquals(expected, pointer.read())
        assertEquals(BootstrapPhase.SWITCH_PREPARED, journal.read(operation)?.phase)
        acquire().use {
            assertEquals(expected, it.recover()?.pointer)
            assertEquals(BootstrapPhase.COMPLETED, journal.read(operation)?.phase)
        }
    }

    @Test fun sourceDriftDuringSwitchPreparedCannotPromoteOldCopy() {
        val (origin, reality) = seedPlanningEight()
        acquire(afterAuthorization = { error("interrupted after authorization record") }).use {
            assertThrows(IllegalStateException::class.java) {
                it.start(operation, target, origin, 30L) { true }
            }
        }
        val prior = checkNotNull(journal.read(operation))
        check(journal.compareAndSet(prior, BootstrapTransitionPolicy.transition(prior, BootstrapPhase.SWITCH_PREPARED)))
        BundledSQLiteDriver().open(reality.toString()).use {
            it.execSQL("UPDATE finance_state SET revision = 1 WHERE singleton_id = 1")
        }
        acquire().use {
            assertThrows(IllegalStateException::class.java) { it.recover() }
        }
        assertEquals(origin, pointer.read())
        assertEquals(BootstrapPhase.SWITCH_PREPARED, journal.read(operation)?.phase)
    }

    @Test fun damagedAuthorizationRecordFailsClosed() {
        val (origin, _) = seedPlanningEight()
        acquire(afterAuthorization = { error("interrupted before switching") }).use {
            assertThrows(IllegalStateException::class.java) {
                it.start(operation, target, origin, 30L) { true }
            }
        }
        val ticket = root.resolve("bootstrap/finance9-dev-cutover-v1.bin")
        val bytes = Files.readAllBytes(ticket)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(ticket, bytes)
        acquire().use {
            assertThrows(IllegalStateException::class.java) { it.recover() }
        }
        assertEquals(origin, pointer.read())
        assertEquals(BootstrapPhase.VALIDATED, journal.read(operation)?.phase)
    }
}
