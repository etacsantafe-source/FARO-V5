package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
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

class FaroV5RestoreJournalAdmissionT2Test {
    @TempDir lateinit var root: Path
    private val op = MaintenanceOperationId("restore-107")
    private val old = StorageGenerationId("old-generation")
    private val target = StorageGenerationId("new-v2-generation")

    private class Lease : MaterialWriteQuiescencePort {
        var acquired = 0
        var released = 0
        var active = false

        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T {
            check(!active) { "nested material write lease" }
            acquired++
            active = true
            return try {
                block()
            } finally {
                released++
                active = false
            }
        }
    }

    private fun journal() = FileBootstrapJournal(root)
    private fun pointer() = FileActiveGenerationPointer(root)

    private fun entry(
        id: MaintenanceOperationId = op,
        kind: MaintenanceOperationKind = MaintenanceOperationKind.RESTORE,
        phase: BootstrapPhase = BootstrapPhase.STAGED,
        previous: StorageGenerationId? = old,
        dest: StorageGenerationId = target,
    ) = BootstrapJournalEntry(
        operationId = id,
        kind = kind,
        previousGenerationId = previous,
        targetGenerationId = dest,
        phase = phase,
    )

    private fun guard(lease: Lease) =
        FaroV5RestoreJournalAdmission(journal(), pointer(), target, lease)

    private fun pointAtOld() {
        assertTrue(pointer().compareAndSet(null, old) != null)
    }

    @Test fun exactStagedRestoreRunsOnceInsideOneHeldGlobalLease() {
        pointAtOld()
        journal().create(entry())
        val lease = Lease()
        var work = 0
        val out = guard(lease).withMaterialWritesQuiesced(op) {
            assertTrue(lease.active)
            work++
            "staged-only"
        }
        assertEquals("staged-only", out)
        assertEquals(1, work)
        assertEquals(1, lease.acquired)
        assertEquals(1, lease.released)
        assertEquals(old, pointer().read()!!.generationId)
        assertEquals(BootstrapPhase.STAGED, journal().read(op)!!.phase)
    }

    @Test fun missingJournalOrWrongOperationNeverRunsTheRestoreBlock() {
        pointAtOld()
        val lease = Lease()
        var work = 0
        assertThrows(IllegalStateException::class.java) {
            guard(lease).withMaterialWritesQuiesced(op) { work++ }
        }
        assertEquals(0, work)
        assertEquals(1, lease.released)
        journal().create(entry(MaintenanceOperationId("different-operation")))
        assertThrows(IllegalStateException::class.java) {
            guard(lease).withMaterialWritesQuiesced(op) { work++ }
        }
        assertEquals(0, work)
        assertEquals(2, lease.released)
    }

    @Test fun validatedOrMigrationJournalCannotAuthorizeStageWrites() {
        pointAtOld()
        journal().create(entry(kind = MaintenanceOperationKind.MIGRATION))
        val lease = Lease()
        assertThrows(IllegalStateException::class.java) {
            guard(lease).withMaterialWritesQuiesced(op) { error("must not execute") }
        }
        val current = journal().read(op)!!
        // Cannot reinterpret the immutable MIGRATION kind as RESTORE.
        assertEquals(MaintenanceOperationKind.MIGRATION, current.kind)
        assertEquals(1, lease.released)
    }

    @Test fun changedActivePointerRefusesJournalWithoutMutatingIt() {
        pointAtOld()
        journal().create(entry())
        pointer().compareAndSet(old, StorageGenerationId("unexpected-active"))
        val lease = Lease()
        assertThrows(IllegalStateException::class.java) {
            guard(lease).withMaterialWritesQuiesced(op) { error("must not execute") }
        }
        assertEquals(BootstrapPhase.STAGED, journal().read(op)!!.phase)
        assertEquals(1, lease.released)
    }

    @Test fun competingUnresolvedBootstrapJournalBlocksStaging() {
        pointAtOld()
        journal().create(entry())
        journal().create(entry(
            id = MaintenanceOperationId("another-pending"),
            dest = StorageGenerationId("other-target"),
        ))
        val lease = Lease()
        assertThrows(IllegalStateException::class.java) {
            guard(lease).withMaterialWritesQuiesced(op) { error("must not execute") }
        }
        assertEquals(1, lease.acquired)
        assertEquals(1, lease.released)
        assertTrue(journal().pending().size == 2)
    }
}
