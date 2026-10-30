package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.BootstrapTransitionPolicy
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Extra tests: durable process re-open and stale admission, not the existing C107 five cases. */
class FaroV5RestoreJournalReopenT2Test {
    @TempDir lateinit var root: Path
    private val id = MaintenanceOperationId("restore-reopen-c108")
    private val old = StorageGenerationId("active-previous")
    private val dest = StorageGenerationId("staged-next")

    private class Lease : MaterialWriteQuiescencePort {
        var enters = 0
        var exits = 0
        var held = false

        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T {
            check(!held) { "recursive write lease" }
            enters++
            held = true
            return try {
                block()
            } finally {
                held = false
                exits++
            }
        }
    }

    private fun entry(
        id: MaintenanceOperationId = this.id,
        previous: StorageGenerationId? = old,
        target: StorageGenerationId = dest,
    ) = BootstrapJournalEntry(
        operationId = id,
        kind = MaintenanceOperationKind.RESTORE,
        previousGenerationId = previous,
        targetGenerationId = target,
        phase = BootstrapPhase.STAGED,
    )

    private fun pointAtPrevious(): ActiveGenerationPointerPort =
        FileActiveGenerationPointer(root).also {
            check(it.compareAndSet(null, old) != null)
        }

    private fun admit(
        lease: Lease,
        expected: StorageGenerationId = dest,
    ) = FaroV5RestoreJournalAdmission(
        FileBootstrapJournal(root), FileActiveGenerationPointer(root), expected, lease,
    )

    @Test fun reopenFromDiskPreservesAuthorityAndExactStagedAdmission() {
        pointAtPrevious()
        FileBootstrapJournal(root).create(entry())
        // Reconstruct ALL ports, as at process restart. No in-memory journal sharing.
        val lease = Lease()
        var calls = 0
        assertEquals("restored", admit(lease).withMaterialWritesQuiesced(id) {
            assertTrue(lease.held)
            calls++
            "restored"
        })
        assertEquals(1, calls)
        assertEquals(1, lease.enters)
        assertEquals(1, lease.exits)
        assertEquals(dest, FileBootstrapJournal(root).read(id)!!.targetGenerationId)
        assertEquals(old, FileActiveGenerationPointer(root).read()!!.generationId)
    }

    @Test fun durablePhaseAdvanceToValidatedStopsStagingReplay() {
        pointAtPrevious()
        val original = entry()
        FileBootstrapJournal(root).create(original)
        val validated = BootstrapTransitionPolicy.transition(original, BootstrapPhase.VALIDATED)
        assertTrue(FileBootstrapJournal(root).compareAndSet(original, validated))
        val lease = Lease()
        assertThrows(IllegalStateException::class.java) {
            admit(lease).withMaterialWritesQuiesced(id) {
                error("validated restore must not run a staging block")
            }
        }
        assertEquals(1, lease.exits)
        assertEquals(BootstrapPhase.VALIDATED, FileBootstrapJournal(root).read(id)!!.phase)
    }

    @Test fun durableAbortedCompetingOperationDoesNotBlockValidRestore() {
        pointAtPrevious()
        val otherId = MaintenanceOperationId("older-aborted-restore")
        val other = entry(
            id = otherId,
            target = StorageGenerationId("older-staged-target"),
        ).copy(phase = BootstrapPhase.PREPARING)
        FileBootstrapJournal(root).create(other)
        val aborted = BootstrapTransitionPolicy.transition(other, BootstrapPhase.ABORTED_PRE_SWITCH)
        assertTrue(FileBootstrapJournal(root).compareAndSet(other, aborted))
        FileBootstrapJournal(root).create(entry())
        val lease = Lease()
        assertEquals(123, admit(lease).withMaterialWritesQuiesced(id) { 123 })
        assertEquals(1, lease.exits)
        assertEquals(listOf(id), FileBootstrapJournal(root).pending().map { it.operationId })
    }

    @Test fun wrongExpectedGenerationCannotBorrowJournalAuthorization() {
        pointAtPrevious()
        FileBootstrapJournal(root).create(entry())
        val lease = Lease()
        assertThrows(IllegalStateException::class.java) {
            admit(lease, StorageGenerationId("wrong-target")).withMaterialWritesQuiesced(id) {
                error("wrong target must never execute")
            }
        }
        assertEquals(1, lease.exits)
        assertEquals(old, FileActiveGenerationPointer(root).read()!!.generationId)
    }

    @Test fun nullPreviousGenerationRefusesNonemptyActivePointer() {
        pointAtPrevious()
        FileBootstrapJournal(root).create(entry(previous = null))
        val lease = Lease()
        assertThrows(IllegalStateException::class.java) {
            admit(lease).withMaterialWritesQuiesced(id) {
                error("active pointer mismatch must never execute")
            }
        }
        assertEquals(1, lease.exits)
    }
}
