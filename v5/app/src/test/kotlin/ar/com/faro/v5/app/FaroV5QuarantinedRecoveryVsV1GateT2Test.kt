package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.LegacyV1HostGenerationAdmission
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Proves recovery evidence cannot bypass V1-only pre-switch host admission. */
class FaroV5QuarantinedRecoveryVsV1GateT2Test {
    @TempDir lateinit var root: Path
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val journal by lazy { FileBootstrapJournal(root) }
    private val operation = MaintenanceOperationId("restore-v2-v1-fence")
    private val previous = StorageGenerationId("v1-host-active")
    private val target = StorageGenerationId("restore-target")
    private val probe by lazy { FaroV5QuarantinedV2RecoveryProbe(layout, journal, pointer) }
    private val v1Gate by lazy { LegacyV1HostGenerationAdmission(layout) }

    private fun descriptor(id: StorageGenerationId) =
        StorageGenerationDescriptor(id, "schema-five-owner", 23L)

    private fun prepare(v2: Boolean, partial: Boolean = false) {
        val old = layout.stage(descriptor(previous))
        GenerationStoreFiles.LEGACY_V1.forEach {
            Files.write(old.directory.resolve(it), byteArrayOf(1))
        }
        checkNotNull(pointer.compareAndSet(null, previous))
        val new = if (v2) layout.stageWorkspaceV2(descriptor(target))
            else layout.stage(descriptor(target))
        val storeNames = if (v2 && !partial) GenerationStoreFiles.WORKSPACE_V2
            else GenerationStoreFiles.LEGACY_V1
        storeNames.forEach { Files.write(new.directory.resolve(it), byteArrayOf(1)) }
        journal.create(BootstrapJournalEntry(
            operation, MaintenanceOperationKind.RESTORE, previous,
            target, BootstrapPhase.VALIDATED
        ))
    }

    @Test fun validV2EvidenceNeverGrantsLegacyHostCutover() {
        prepare(v2 = true)
        val before = pointer.read()
        val journalBefore = journal.read(operation)
        assertEquals(V2QuarantinedRecoveryEvidence.Candidate(target.value),
            probe.inspect(operation))
        assertFalse(v1Gate.canOpenAsActive(MaintenanceOperationKind.RESTORE, target))
        assertEquals(before, pointer.read())
        assertEquals(journalBefore, journal.read(operation))
    }

    @Test fun legacyV1HostStillAcceptsOriginalFourStoreRestore() {
        prepare(v2 = false)
        val before = pointer.read()
        assertEquals(V2QuarantinedRecoveryEvidence.Blocked, probe.inspect(operation))
        assertTrue(v1Gate.canOpenAsActive(MaintenanceOperationKind.RESTORE, target))
        assertEquals(before, pointer.read())
    }

    @Test fun interruptedV2CannotEnterEitherHostAndDoesNotMaterializeWorkspace() {
        prepare(v2 = true, partial = true)
        val before = pointer.read()
        assertEquals(V2QuarantinedRecoveryEvidence.Blocked, probe.inspect(operation))
        assertFalse(v1Gate.canOpenAsActive(MaintenanceOperationKind.RESTORE, target))
        val staged = layout.requireStaged(target)
        assertFalse(Files.exists(staged.directory.resolve(GenerationStoreFiles.WORKSPACE)))
        assertEquals(before, pointer.read())
    }
}
