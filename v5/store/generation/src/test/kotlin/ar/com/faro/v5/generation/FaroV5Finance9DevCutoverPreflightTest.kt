package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverPreflight.Observation
import ar.com.faro.v5.generation.FaroV5Finance9DevCutoverPreflight.Reason
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Finance9DevCutoverPreflightTest {
    @TempDir lateinit var root: Path

    private val operationId = MaintenanceOperationId("d1b1c0-dev-test")
    private val sourceId = StorageGenerationId("dev-active")
    private val targetId = StorageGenerationId("dev-staged")
    private val journalEntry = BootstrapJournalEntry(
        operationId, MaintenanceOperationKind.RESTORE, sourceId, targetId, BootstrapPhase.VALIDATED,
    )
    private val quiescence = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }

    private fun setup(version: Int = 9, entry: BootstrapJournalEntry? = journalEntry):
        Pair<ActiveGenerationPointer, StagedStorageGeneration> {
        val pointer = checkNotNull(FileActiveGenerationPointer(root).compareAndSet(null, sourceId))
        val descriptor = StorageGenerationDescriptor(targetId, "d1b1c0-stage-fixture", 1000L)
        val owner = FaroV5WorkspaceV2StageOwner(root)
        val staged = if (version == 7) owner.stageFreshAndVerify(descriptor)
            else owner.stageFreshPlanning8AndVerify(descriptor)
        if (version == 9) {
            FaroV5Finance9StagedMigration(root, quiescence)
                .migrate(operationId, staged, pointer, "a".repeat(64), 1000L)
        }
        if (entry != null) FileBootstrapJournal(root).create(entry)
        return pointer to staged
    }

    private fun inspect(active: ActiveGenerationPointer, staged: StagedStorageGeneration) =
        FaroV5Finance9DevCutoverPreflight(root, quiescence).inspect(operationId, staged, active)

    private fun snapshot(): Map<Path, String> = Files.walk(root).use { paths ->
        paths.toList().associate { path ->
            root.relativize(path) to if (Files.isDirectory(path)) "<dir>" else
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
                    .joinToString("") { "%02x".format(it) }
        }
    }

    @Test fun sealedNineWithValidatedRestoreIsInformativeAndReadOnly() {
        val (active, staged) = setup()
        val before = snapshot()
        assertEquals(Observation.OBSERVED_SEALED_STAGED9_ONLY, inspect(active, staged))
        assertEquals(before, snapshot())
    }

    @Test fun missingRootIsNotMaterialized() {
        val missing = root.resolve("missing")
        val staged = StagedStorageGeneration(
            StorageGenerationDescriptor(targetId, "missing-fixture", 1000L), missing,
        )
        val result = FaroV5Finance9DevCutoverPreflight(missing, quiescence)
            .inspect(operationId, staged, ActiveGenerationPointer(sequence = 1L, generationId = sourceId))
        assertEquals(Observation.DENIED(Reason.INSPECTION_FAILED), result)
        assertFalse(Files.exists(missing))
    }

    @Test fun stalePointerAndSameIdAbaAreDenied() {
        val (active, staged) = setup()
        val pointer = FileActiveGenerationPointer(root)
        val other = StorageGenerationId("dev-other")
        checkNotNull(pointer.compareAndSet(sourceId, other))
        assertEquals(Observation.DENIED(Reason.ACTIVE_POINTER_CHANGED), inspect(active, staged))
        checkNotNull(pointer.compareAndSet(other, sourceId))
        assertEquals(Observation.DENIED(Reason.ACTIVE_POINTER_CHANGED), inspect(active, staged))
    }

    @Test fun activeTargetIsDenied() {
        val (_, staged) = setup()
        val active = checkNotNull(FileActiveGenerationPointer(root).compareAndSet(sourceId, targetId))
        assertEquals(Observation.DENIED(Reason.ACTIVE_TARGET), inspect(active, staged))
    }

    @Test fun foreignPathAndDescriptorAreDenied() {
        val (active, staged) = setup()
        val linked = Files.createSymbolicLink(root.resolve("foreign-stage"), staged.directory)
        assertEquals(Observation.DENIED(Reason.STAGE_MISMATCH), inspect(active, staged.copy(directory = linked)))
        val foreign = staged.copy(descriptor = staged.descriptor.copy(createdAtEpochMillis = 2000L))
        assertEquals(Observation.DENIED(Reason.STAGE_MISMATCH), inspect(active, foreign))
    }

    @Test fun invalidFormatMarkerIsDenied() {
        val (active, staged) = setup()
        Files.newDirectoryStream(staged.directory, "generation-format-*.bin").use { markers ->
            Files.writeString(markers.single(), "invalid")
        }
        assertEquals(Observation.DENIED(Reason.INSPECTION_FAILED), inspect(active, staged))
    }

    @Test fun schemaEightAndUnknownAreDeniedWithoutMutation() {
        val (active, staged) = setup(version = 8)
        assertEquals(Observation.DENIED(Reason.INSPECTION_FAILED), inspect(active, staged))
        BundledSQLiteDriver().open(staged.directory.resolve("reality.db").toString()).use {
            it.execSQL("PRAGMA user_version=55")
        }
        val before = snapshot()
        assertEquals(Observation.DENIED(Reason.INSPECTION_FAILED), inspect(active, staged))
        assertEquals(before, snapshot())
    }

    @Test fun schemaSevenIsDenied() {
        val (active, staged) = setup(version = 7)
        assertEquals(Observation.DENIED(Reason.INSPECTION_FAILED), inspect(active, staged))
    }

    @Test fun realAndDanglingSidecarsAreDenied() {
        val (active, staged) = setup()
        val wal = Files.writeString(staged.directory.resolve("reality.db-wal"), "unsealed")
        assertEquals(Observation.DENIED(Reason.SIDECAR_PRESENT), inspect(active, staged))
        Files.delete(wal)
        Files.createSymbolicLink(staged.directory.resolve("workspace.db-shm"), root.resolve("missing"))
        assertEquals(Observation.DENIED(Reason.SIDECAR_PRESENT), inspect(active, staged))
    }

    @Test fun missingWrongPhaseAndCompetingJournalsAreDenied() {
        val (active, staged) = setup(entry = null)
        val before = snapshot()
        assertEquals(Observation.DENIED(Reason.JOURNAL_MISMATCH), inspect(active, staged))
        assertEquals(before, snapshot())
        val journal = FileBootstrapJournal(root)
        val notValidated = journalEntry.copy(phase = BootstrapPhase.STAGED)
        journal.create(notValidated)
        assertEquals(Observation.DENIED(Reason.JOURNAL_MISMATCH), inspect(active, staged))
        check(journal.compareAndSet(notValidated, journalEntry))
        journal.create(journalEntry.copy(
            operationId = MaintenanceOperationId("foreign-operation"),
            targetGenerationId = StorageGenerationId("foreign-stage"),
        ))
        assertEquals(Observation.DENIED(Reason.JOURNAL_MISMATCH), inspect(active, staged))
    }

    @Test fun replayFenceDeniesPreviouslyActivatedTarget() {
        val (active, staged) = setup()
        FileGenerationReplayFence(root).install(MaintenanceOperationKind.MIGRATION, targetId)
        assertEquals(Observation.DENIED(Reason.REPLAY_FENCE_PRESENT), inspect(active, staged))
    }

    @Test fun quiescenceFailureDoesNotChangeStageJournalOrPointer() {
        val (active, staged) = setup()
        val before = snapshot()
        val failing = object : MaterialWriteQuiescencePort {
            override fun <T> withMaterialWritesQuiesced(
                operationId: MaintenanceOperationId,
                block: () -> T,
            ): T {
                block()
                error("quiescence lost")
            }
        }
        assertEquals(
            Observation.DENIED(Reason.INSPECTION_FAILED),
            FaroV5Finance9DevCutoverPreflight(root, failing).inspect(operationId, staged, active),
        )
        assertEquals(before, snapshot())
    }
}
