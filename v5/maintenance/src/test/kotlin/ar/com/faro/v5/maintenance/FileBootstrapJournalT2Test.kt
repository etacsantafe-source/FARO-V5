package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FileBootstrapJournalT2Test {
    @Test
    fun `journal lifecycle persists across real filesystem reopen`(
        @TempDir tempDir: Path,
    ) {
        val operationId = MaintenanceOperationId("maintenance/op-one")
        val initial =
            BootstrapJournalEntry(
                operationId = operationId,
                kind = MaintenanceOperationKind.MIGRATION,
                previousGenerationId = StorageGenerationId("generation-old"),
                targetGenerationId = StorageGenerationId("generation-new"),
                phase = BootstrapPhase.PREPARING,
            )

        FileBootstrapJournal(tempDir).let { journal ->
            journal.create(initial)
            val staged =
                BootstrapTransitionPolicy.transition(
                    initial,
                    BootstrapPhase.STAGED,
                )
            assertTrue(journal.compareAndSet(initial, staged))
        }

        val reopened = FileBootstrapJournal(tempDir)
        assertEquals(
            BootstrapPhase.STAGED,
            reopened.read(operationId)!!.phase,
        )

        val names =
            Files.list(tempDir.resolve("bootstrap").resolve("journal")).use { stream ->
                stream.map { it.fileName.toString() }.toList()
            }
        assertTrue(names.all { it.startsWith("journal-") })
        assertTrue(names.none { it.contains("op-one") || it.contains("..") })
    }

    @Test
    fun `staged generation uses hashed directory and immutable descriptor`(
        @TempDir tempDir: Path,
    ) {
        val generationId = StorageGenerationId("../logical-generation")
        val descriptor =
            StorageGenerationDescriptor(
                generationId = generationId,
                schemaSetFingerprint = "schema-v1",
                createdAtEpochMillis = 10L,
            )
        val layout = FileStorageGenerationLayout(tempDir)

        val staged = layout.stage(descriptor)
        assertEquals(
            tempDir.toAbsolutePath().normalize().resolve("generations"),
            staged.directory.parent,
        )
        assertTrue(staged.directory.fileName.toString().startsWith("generation-"))
        assertTrue(!staged.directory.fileName.toString().contains("logical-generation"))
        assertEquals(descriptor, layout.requireStaged(generationId).descriptor)
    }

    @Test
    fun `pending scan discovers non-terminal operations and excludes terminal records`(
        @TempDir tempDir: Path,
    ) {
        val journal = FileBootstrapJournal(tempDir)
        val pendingId = MaintenanceOperationId("restore-pending")
        val completedId = MaintenanceOperationId("migration-completed")

        var pending =
            BootstrapJournalEntry(
                operationId = pendingId,
                kind = MaintenanceOperationKind.RESTORE,
                previousGenerationId = StorageGenerationId("old-a"),
                targetGenerationId = StorageGenerationId("new-a"),
                phase = BootstrapPhase.PREPARING,
            )
        journal.create(pending)
        for (next in listOf(BootstrapPhase.STAGED, BootstrapPhase.VALIDATED)) {
            val updated = BootstrapTransitionPolicy.transition(pending, next)
            assertTrue(journal.compareAndSet(pending, updated))
            pending = updated
        }

        var completed =
            BootstrapJournalEntry(
                operationId = completedId,
                kind = MaintenanceOperationKind.MIGRATION,
                previousGenerationId = StorageGenerationId("old-b"),
                targetGenerationId = StorageGenerationId("new-b"),
                phase = BootstrapPhase.PREPARING,
            )
        journal.create(completed)
        for (
            next in listOf(
                BootstrapPhase.STAGED,
                BootstrapPhase.VALIDATED,
                BootstrapPhase.SWITCH_PREPARED,
                BootstrapPhase.SWITCHED,
                BootstrapPhase.HEALTH_VERIFIED,
            )
        ) {
            val updated = BootstrapTransitionPolicy.transition(completed, next)
            assertTrue(journal.compareAndSet(completed, updated))
            completed = updated
        }
        val fenced = BootstrapTransitionPolicy.acceptMaterialWrites(completed)
        assertTrue(journal.compareAndSet(completed, fenced))
        completed = fenced
        val terminal =
            BootstrapTransitionPolicy.transition(
                completed,
                BootstrapPhase.COMPLETED,
            )
        assertTrue(journal.compareAndSet(completed, terminal))

        val unfencedId = MaintenanceOperationId("restore-completed-unfenced")
        var unfenced =
            BootstrapJournalEntry(
                operationId = unfencedId,
                kind = MaintenanceOperationKind.RESTORE,
                previousGenerationId = StorageGenerationId("old-c"),
                targetGenerationId = StorageGenerationId("new-c"),
                phase = BootstrapPhase.PREPARING,
            )
        journal.create(unfenced)
        for (
            next in listOf(
                BootstrapPhase.STAGED,
                BootstrapPhase.VALIDATED,
                BootstrapPhase.SWITCH_PREPARED,
                BootstrapPhase.SWITCHED,
                BootstrapPhase.HEALTH_VERIFIED,
                BootstrapPhase.COMPLETED,
            )
        ) {
            val updated = BootstrapTransitionPolicy.transition(unfenced, next)
            assertTrue(journal.compareAndSet(unfenced, updated))
            unfenced = updated
        }

        val reopenedPending = FileBootstrapJournal(tempDir).pending()

        assertEquals(
            listOf(unfencedId, pendingId),
            reopenedPending.map { it.operationId },
        )
        assertEquals(
            listOf(BootstrapPhase.COMPLETED, BootstrapPhase.VALIDATED),
            reopenedPending.map { it.phase },
        )
    }

    @Test
    fun `rolled back operation remains pending until rollback verification is durable`(
        @TempDir tempDir: Path,
    ) {
        val journal = FileBootstrapJournal(tempDir)
        val operationId = MaintenanceOperationId("migration-rollback")
        var entry =
            BootstrapJournalEntry(
                operationId = operationId,
                kind = MaintenanceOperationKind.MIGRATION,
                previousGenerationId = StorageGenerationId("old-r"),
                targetGenerationId = StorageGenerationId("new-r"),
                phase = BootstrapPhase.PREPARING,
            )
        journal.create(entry)

        for (
            next in listOf(
                BootstrapPhase.STAGED,
                BootstrapPhase.VALIDATED,
                BootstrapPhase.SWITCH_PREPARED,
                BootstrapPhase.SWITCHED,
                BootstrapPhase.ROLLBACK_PREPARED,
                BootstrapPhase.ROLLED_BACK_POST_SWITCH,
            )
        ) {
            val updated = BootstrapTransitionPolicy.transition(entry, next)
            assertTrue(journal.compareAndSet(entry, updated))
            entry = updated
        }

        assertEquals(
            listOf(operationId),
            FileBootstrapJournal(tempDir).pending().map { it.operationId },
        )

        val verified =
            BootstrapTransitionPolicy.transition(
                entry,
                BootstrapPhase.ROLLBACK_VERIFIED,
            )
        assertTrue(journal.compareAndSet(entry, verified))
        assertTrue(FileBootstrapJournal(tempDir).pending().isEmpty())
    }

}
