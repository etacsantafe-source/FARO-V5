package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** JVM filesystem tests; real V2 SQLite and physical lifecycle remain separate gates. */
class FaroV5QuarantinedV2RecoveryProbeT2Test {
    @TempDir lateinit var root: Path
    private val layout by lazy { FileStorageGenerationLayout(root) }
    private val journal by lazy { FileBootstrapJournal(root) }
    private val pointer by lazy { FileActiveGenerationPointer(root) }
    private val probe by lazy { FaroV5QuarantinedV2RecoveryProbe(layout, journal, pointer) }
    private val predecessor = StorageGenerationId("v1-active")
    private val target = StorageGenerationId("v2-restore-target")
    private val operation = MaintenanceOperationId("restore-v2-probe")

    private fun descriptor(id: StorageGenerationId) =
        StorageGenerationDescriptor(id, "schema-five-store-probe", 5L)

    private fun setup(
        phase: BootstrapPhase = BootstrapPhase.VALIDATED,
        kind: MaintenanceOperationKind = MaintenanceOperationKind.RESTORE,
        v2: Boolean = true,
        complete: Boolean = true,
        withJournal: Boolean = true,
    ) {
        val prior = layout.stage(descriptor(predecessor))
        GenerationStoreFiles.LEGACY_V1.forEach {
            Files.write(prior.directory.resolve(it), byteArrayOf(1))
        }
        assertEquals(predecessor, pointer.compareAndSet(null, predecessor)!!.generationId)
        val staged = if (v2) layout.stageWorkspaceV2(descriptor(target))
            else layout.stage(descriptor(target))
        val names = if (complete) {
            if (v2) GenerationStoreFiles.WORKSPACE_V2 else GenerationStoreFiles.LEGACY_V1
        } else GenerationStoreFiles.LEGACY_V1
        names.forEach { Files.write(staged.directory.resolve(it), byteArrayOf(1)) }
        if (withJournal) journal.create(
            BootstrapJournalEntry(operation, kind, predecessor, target, phase)
        )
    }

    @Test fun validatedMarkedV2IsOnlyAnEvidenceCandidateAndDoesNotMovePointer() {
        setup()
        val before = journal.read(operation)
        val active = pointer.read()
        assertEquals(V2QuarantinedRecoveryEvidence.Candidate(target.value), probe.inspect(operation))
        assertEquals(before, journal.read(operation))
        assertEquals(active, pointer.read())
    }

    @Test fun stagedNotYetValidatedIsBlocked() {
        setup(phase = BootstrapPhase.STAGED)
        assertEquals(V2QuarantinedRecoveryEvidence.Blocked, probe.inspect(operation))
    }

    @Test fun switchedOrAcceptedJournalIsBlocked() {
        setup(phase = BootstrapPhase.SWITCHED)
        assertEquals(V2QuarantinedRecoveryEvidence.Blocked, probe.inspect(operation))
    }

    @Test fun competingPendingJournalIsBlocked() {
        setup()
        journal.create(BootstrapJournalEntry(
            MaintenanceOperationId("other-restore"), MaintenanceOperationKind.RESTORE,
            predecessor, StorageGenerationId("other-target"), BootstrapPhase.STAGED
        ))
        assertEquals(V2QuarantinedRecoveryEvidence.Blocked, probe.inspect(operation))
    }

    @Test fun unmarkedLegacyTargetIsBlocked() {
        setup(v2 = false)
        assertEquals(V2QuarantinedRecoveryEvidence.Blocked, probe.inspect(operation))
    }

    @Test fun incompleteMarkedV2IsBlockedWithoutCreatingWorkspace() {
        setup(complete = false)
        val staged = layout.requireStaged(target)
        assertEquals(V2QuarantinedRecoveryEvidence.Blocked, probe.inspect(operation))
        assertFalse(Files.exists(staged.directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }

    @Test fun migrationAndMissingJournalCannotPresentAsValidatedRestore() {
        setup(kind = MaintenanceOperationKind.MIGRATION)
        assertEquals(V2QuarantinedRecoveryEvidence.Blocked, probe.inspect(operation))
        assertEquals(V2QuarantinedRecoveryEvidence.Blocked,
            probe.inspect(MaintenanceOperationId("missing-operation")))
    }

    @Test fun changedActivePointerPreventsCandidate() {
        setup()
        val other = StorageGenerationId("other-active")
        assertEquals(other, pointer.compareAndSet(predecessor, other)!!.generationId)
        assertEquals(V2QuarantinedRecoveryEvidence.Blocked, probe.inspect(operation))
    }
}
