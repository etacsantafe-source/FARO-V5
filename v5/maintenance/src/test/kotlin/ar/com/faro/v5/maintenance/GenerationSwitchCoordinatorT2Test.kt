package ar.com.faro.v5.maintenance

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GenerationSwitchCoordinatorT2Test {
    @Test
    fun `crash after pointer publish recovers journal to switched`(
        @TempDir tempDir: Path,
    ) {
        val previous = StorageGenerationId("generation-old")
        val target = StorageGenerationId("generation-new")
        val operationId = MaintenanceOperationId("restore-1")

        val layout = FileStorageGenerationLayout(tempDir)
        layout.stage(
            StorageGenerationDescriptor(
                generationId = target,
                schemaSetFingerprint = "schema-set-v1",
                createdAtEpochMillis = 20L,
            )
        )

        val pointer = FileActiveGenerationPointer(tempDir)
        assertEquals(
            previous,
            pointer.compareAndSet(null, previous)!!.generationId,
        )

        val journal = FileBootstrapJournal(tempDir)
        val switchPrepared = prepareSwitch(journal, operationId, previous, target)

        val coordinator =
            GenerationSwitchCoordinator(
                journal = journal,
                activePointer = pointer,
                generationLayout = layout,
                faultInjector = { point ->
                    if (
                        point ==
                        GenerationSwitchFaultPoint.AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE
                    ) {
                        error("simulated process death")
                    }
                },
            )

        assertThrows(IllegalStateException::class.java) {
            coordinator.switch(operationId)
        }
        assertEquals(target, pointer.read()!!.generationId)
        assertEquals(
            BootstrapPhase.SWITCH_PREPARED,
            journal.read(operationId)!!.phase,
        )

        val recovered =
            GenerationSwitchCoordinator(
                journal = FileBootstrapJournal(tempDir),
                activePointer = FileActiveGenerationPointer(tempDir),
                generationLayout = FileStorageGenerationLayout(tempDir),
            ).recover(operationId)

        assertEquals(BootstrapPhase.SWITCHED, recovered.phase)

        val fenced =
            GenerationSwitchCoordinator(
                journal = FileBootstrapJournal(tempDir),
                activePointer = FileActiveGenerationPointer(tempDir),
                generationLayout = FileStorageGenerationLayout(tempDir),
            ).markMaterialWritesAccepted(operationId)

        assertTrue(fenced.materialWritesAccepted)
        assertFalse(
            BootstrapTransitionPolicy.previousGenerationMayBeReactivated(fenced)
        )
        assertTrue(
            FileBootstrapJournal(tempDir)
                .read(operationId)!!
                .materialWritesAccepted
        )

        assertEquals(
            switchPrepared.targetGenerationId,
            FileActiveGenerationPointer(tempDir).read()!!.generationId,
        )
    }

    @Test
    fun `unexpected active generation fails closed during recovery`(
        @TempDir tempDir: Path,
    ) {
        val previous = StorageGenerationId("generation-old")
        val target = StorageGenerationId("generation-new")
        val other = StorageGenerationId("generation-other")
        val operationId = MaintenanceOperationId("restore-2")

        val layout = FileStorageGenerationLayout(tempDir)
        layout.stage(
            StorageGenerationDescriptor(
                generationId = target,
                schemaSetFingerprint = "schema-set-v1",
                createdAtEpochMillis = 30L,
            )
        )

        val journal = FileBootstrapJournal(tempDir)
        prepareSwitch(journal, operationId, previous, target)
        FileActiveGenerationPointer(tempDir).compareAndSet(null, other)

        assertThrows(IllegalStateException::class.java) {
            GenerationSwitchCoordinator(
                journal = journal,
                activePointer = FileActiveGenerationPointer(tempDir),
                generationLayout = layout,
            ).recover(operationId)
        }
    }

    @Test
    fun `completed bootstrap fails closed when active generation drifts`(
        @TempDir tempDir: Path,
    ) {
        val previous = StorageGenerationId("generation-old")
        val target = StorageGenerationId("generation-new")
        val other = StorageGenerationId("generation-other")
        val operationId = MaintenanceOperationId("restore-completed")

        val layout = FileStorageGenerationLayout(tempDir)
        layout.stage(
            StorageGenerationDescriptor(
                generationId = target,
                schemaSetFingerprint = "schema-set-v1",
                createdAtEpochMillis = 40L,
            )
        )

        val pointer = FileActiveGenerationPointer(tempDir)
        pointer.compareAndSet(null, previous)
        val journal = FileBootstrapJournal(tempDir)
        var entry = prepareSwitch(journal, operationId, previous, target)
        entry = GenerationSwitchCoordinator(journal, pointer, layout).switch(operationId)
        for (
            next in listOf(
                BootstrapPhase.HEALTH_VERIFIED,
                BootstrapPhase.COMPLETED,
            )
        ) {
            val updated = BootstrapTransitionPolicy.transition(entry, next)
            assertTrue(journal.compareAndSet(entry, updated))
            entry = updated
        }
        pointer.compareAndSet(target, other)

        assertThrows(IllegalStateException::class.java) {
            GenerationSwitchCoordinator(journal, pointer, layout).recover(operationId)
        }
    }

    @Test
    fun `aborted pre-switch bootstrap fails closed when active generation changed`(
        @TempDir tempDir: Path,
    ) {
        val previous = StorageGenerationId("generation-old")
        val target = StorageGenerationId("generation-new")
        val other = StorageGenerationId("generation-other")
        val operationId = MaintenanceOperationId("restore-aborted")

        val pointer = FileActiveGenerationPointer(tempDir)
        pointer.compareAndSet(null, previous)
        val journal = FileBootstrapJournal(tempDir)
        val preparing =
            BootstrapJournalEntry(
                operationId = operationId,
                kind = MaintenanceOperationKind.RESTORE,
                previousGenerationId = previous,
                targetGenerationId = target,
                phase = BootstrapPhase.PREPARING,
            )
        journal.create(preparing)
        val aborted =
            BootstrapTransitionPolicy.transition(
                preparing,
                BootstrapPhase.ABORTED_PRE_SWITCH,
            )
        assertTrue(journal.compareAndSet(preparing, aborted))
        pointer.compareAndSet(previous, other)

        assertThrows(IllegalStateException::class.java) {
            GenerationSwitchCoordinator(
                journal = journal,
                activePointer = pointer,
                generationLayout = FileStorageGenerationLayout(tempDir),
            ).recover(operationId)
        }
    }

    @Test
    fun `corrupt newest pointer never falls back to older generation`(
        @TempDir tempDir: Path,
    ) {
        val pointer = FileActiveGenerationPointer(tempDir)
        val old = StorageGenerationId("generation-old")
        val current = StorageGenerationId("generation-current")

        pointer.compareAndSet(null, old)
        pointer.compareAndSet(old, current)

        val pointerDir = tempDir.resolve("bootstrap").resolve("active")
        val newest =
            Files.list(pointerDir).use { stream ->
                stream
                    .filter { it.fileName.toString().startsWith("active-") }
                    .max(compareBy { it.fileName.toString() })
                    .orElseThrow()
            }
        Files.write(newest, byteArrayOf(1, 2, 3, 4))

        assertThrows(Exception::class.java) {
            FileActiveGenerationPointer(tempDir).read()
        }
    }

    private fun prepareSwitch(
        journal: FileBootstrapJournal,
        operationId: MaintenanceOperationId,
        previous: StorageGenerationId,
        target: StorageGenerationId,
    ): BootstrapJournalEntry {
        var entry =
            BootstrapJournalEntry(
                operationId = operationId,
                kind = MaintenanceOperationKind.RESTORE,
                previousGenerationId = previous,
                targetGenerationId = target,
                phase = BootstrapPhase.PREPARING,
            )
        journal.create(entry)

        for (
            next in listOf(
                BootstrapPhase.STAGED,
                BootstrapPhase.VALIDATED,
                BootstrapPhase.SWITCH_PREPARED,
            )
        ) {
            val updated = BootstrapTransitionPolicy.transition(entry, next)
            assertTrue(journal.compareAndSet(entry, updated))
            entry = updated
        }
        return entry
    }
}
