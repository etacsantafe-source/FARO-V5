package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityFinance9SealedReadPort
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Strict admission for a closed, isolated DEV root. This owner only observes existing state. */
internal class FaroV5Household10DevCutoverPreflight(
    root: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
) {
    private val root = root.toAbsolutePath().normalize()

    fun inspect(
        operationId: MaintenanceOperationId,
        staged: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ): Boolean = guarded(operationId) {
        val bootstrap = root.resolve("bootstrap")
        val targetId = staged.descriptor.generationId
        check(targetId != expectedActive.generationId)
        check(listOf(bootstrap.resolve("active"), bootstrap.resolve("journal"), root.resolve("generations"))
            .all { Files.isDirectory(it, NOFOLLOW_LINKS) && it.toRealPath() == it })
        val pointer = FileActiveGenerationPointer(root)
        val journal = FileBootstrapJournal(root)
        val layout = FileStorageGenerationLayout(root)
        val expectedJournal = BootstrapJournalEntry(
            operationId, MaintenanceOperationKind.RESTORE, expectedActive.generationId, targetId,
            BootstrapPhase.VALIDATED,
        )

        fun requirePinnedAdmission() {
            assertExclusiveClosedDevHost()
            check(pointer.read() == expectedActive)
            check(journal.read(operationId) == expectedJournal && journal.pending().singleOrNull() == expectedJournal)
            val fences = bootstrap.resolve("replay-fences")
            if (!Files.notExists(fences, NOFOLLOW_LINKS)) {
                check(Files.isDirectory(fences, NOFOLLOW_LINKS) && fences.toRealPath() == fences)
                val replay = FileGenerationReplayFence(root)
                check(!replay.isInstalled(MaintenanceOperationKind.RESTORE, targetId) &&
                    !replay.isInstalled(MaintenanceOperationKind.MIGRATION, targetId))
            }
            val canonical = layout.requireStaged(targetId)
            check(canonical.descriptor == staged.descriptor &&
                canonical.directory == staged.directory.toAbsolutePath().normalize())
        }

        requirePinnedAdmission()
        FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(staged)
        RealityFinance9SealedReadPort.readTrustedSealedStage10(staged.directory.resolve(GenerationStoreFiles.REALITY))
        requirePinnedAdmission()
        true
    }

    fun verifyAlreadyActive(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ): Boolean = guarded(operationId) {
        check(expectedActive.generationId == stage.descriptor.generationId)
        FaroV5GenerationStoreGraph.verifyReopenedHousehold10Dev(
            root, stage, expectedActive, assertExclusiveClosedDevHost,
        )
    }

    private fun guarded(operationId: MaintenanceOperationId, observe: () -> Boolean): Boolean = try {
        quiescence.withMaterialWritesQuiesced(operationId) {
            assertExclusiveClosedDevHost()
            check(Files.isDirectory(root, NOFOLLOW_LINKS) && root.toRealPath() == root)
            observe()
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    } catch (_: Exception) {
        false
    }
}
