package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** DEV diagnostics for an existing sealed stage in a trusted maintenance root. */
internal class FaroV5Finance9DevCutoverPreflight(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
) {
    sealed interface Observation {
        data object OBSERVED_SEALED_STAGED9_ONLY : Observation
        data class DENIED(val reason: Reason) : Observation
    }

    enum class Reason {
        ACTIVE_POINTER_CHANGED,
        ACTIVE_TARGET,
        JOURNAL_MISMATCH,
        STAGE_MISMATCH,
        SIDECAR_PRESENT,
        REPLAY_FENCE_PRESENT,
        INSPECTION_FAILED,
    }

    private val root = storageRoot.toAbsolutePath().normalize()

    fun inspect(
        operationId: MaintenanceOperationId,
        staged: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ): Observation = try {
        quiescence.withMaterialWritesQuiesced(operationId) {
            observe(operationId, staged, expectedActive)
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        Observation.DENIED(Reason.INSPECTION_FAILED)
    } catch (_: Exception) {
        Observation.DENIED(Reason.INSPECTION_FAILED)
    }

    private fun observe(
        operationId: MaintenanceOperationId,
        staged: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ): Observation {
        val target = staged.descriptor.generationId
        if (target == expectedActive.generationId) return Observation.DENIED(Reason.ACTIVE_TARGET)

        // The existing file adapters create their directories at construction.
        // Require an existing layout before constructing any of them.
        val bootstrap = root.resolve("bootstrap")
        check(listOf(root, bootstrap, bootstrap.resolve("active")).all(::isRealDirectory))
        val pointer = FileActiveGenerationPointer(root)
        if (pointer.read() != expectedActive) return Observation.DENIED(Reason.ACTIVE_POINTER_CHANGED)

        if (!isRealDirectory(bootstrap.resolve("journal"))) {
            return Observation.DENIED(Reason.JOURNAL_MISMATCH)
        }
        val journal = FileBootstrapJournal(root)
        val expectedJournal = BootstrapJournalEntry(
            operationId, MaintenanceOperationKind.RESTORE, expectedActive.generationId, target,
            BootstrapPhase.VALIDATED,
        )
        if (!matchesJournal(journal, expectedJournal)) return Observation.DENIED(Reason.JOURNAL_MISMATCH)

        val fences = bootstrap.resolve("replay-fences")
        if (!Files.notExists(fences, NOFOLLOW_LINKS)) {
            check(isRealDirectory(fences))
            val replayFence = FileGenerationReplayFence(root)
            if (replayFence.isInstalled(MaintenanceOperationKind.RESTORE, target) ||
                replayFence.isInstalled(MaintenanceOperationKind.MIGRATION, target)
            ) return Observation.DENIED(Reason.REPLAY_FENCE_PRESENT)
        }

        check(isRealDirectory(root.resolve("generations")))
        val layout = FileStorageGenerationLayout(root)
        val canonical = layout.requireStaged(target)
        if (staged.directory.toAbsolutePath().normalize() != canonical.directory ||
            staged.descriptor != canonical.descriptor ||
            ExistingGenerationFormatReader.requireAndRead(canonical) != GenerationStoreFormat.WORKSPACE_V2
        ) return Observation.DENIED(Reason.STAGE_MISMATCH)

        if (pointer.read() != expectedActive) return Observation.DENIED(Reason.ACTIVE_POINTER_CHANGED)
        if (!sidecarsAbsent(canonical.directory)) return Observation.DENIED(Reason.SIDECAR_PRESENT)
        StagedSqlitePreflight.requireAll(canonical.directory, finance9Schemas)
        if (!sidecarsAbsent(canonical.directory)) return Observation.DENIED(Reason.SIDECAR_PRESENT)

        if (layout.requireStaged(target) != canonical ||
            ExistingGenerationFormatReader.requireAndRead(canonical) != GenerationStoreFormat.WORKSPACE_V2
        ) return Observation.DENIED(Reason.STAGE_MISMATCH)
        if (!matchesJournal(journal, expectedJournal)) return Observation.DENIED(Reason.JOURNAL_MISMATCH)
        if (pointer.read() != expectedActive) return Observation.DENIED(Reason.ACTIVE_POINTER_CHANGED)

        return Observation.OBSERVED_SEALED_STAGED9_ONLY
    }

    private fun matchesJournal(journal: FileBootstrapJournal, expected: BootstrapJournalEntry): Boolean =
        journal.read(expected.operationId) == expected && journal.pending().singleOrNull() == expected

    private fun isRealDirectory(path: Path): Boolean = Files.isDirectory(path, NOFOLLOW_LINKS)

    private fun sidecarsAbsent(directory: Path): Boolean = finance9Schemas.all { schema ->
        listOf("-wal", "-shm", "-journal").all { suffix ->
            Files.notExists(directory.resolve(schema.name + suffix), NOFOLLOW_LINKS)
        }
    }

    private companion object {
        // The generation graph and migration keep their schema profiles private.
        // Match the sealed Finance9 profile with exact companion-owner versions.
        val finance9Schemas = listOf(
            StagedSqlitePreflight.Schema(GenerationStoreFiles.REALITY, 9, listOf(
                "profile_state", "apply_attempt", "execution_receipt", "reality_outbox",
                "finance_state", "finance_account", "finance_account_opening", "finance_entry",
                "objective_state", "objective_record", "matter_state", "matter_record",
                "reminder_state", "reminder_rule", "reminder_occurrence", "monthly_plan_state",
                "monthly_plan_item", "finance_acl_state", "finance_acl_policy", "finance_apply_grant",
                "finance_opening_confirmation", "finance_transfer", "finance_debt", "finance_schedule",
                "finance_plan_item", "finance_plan_item_payment", "finance_budget_limit",
                "finance_schema_migration_audit",
            )),
            StagedSqlitePreflight.Schema(
                GenerationStoreFiles.COMMERCIAL_EVIDENCE, 1, listOf("commercial_evidence"),
            ),
            StagedSqlitePreflight.Schema(
                GenerationStoreFiles.WEB_EVIDENCE, 2, listOf("web_evidence", "web_search_evidence"),
            ),
            StagedSqlitePreflight.Schema(GenerationStoreFiles.AUXILIARY, 3, listOf(
                "delivery_notice", "delivery_intent", "bookmark_scope",
                "bookmark_folder", "bookmark_item", "bookmark_receipt",
            )),
            StagedSqlitePreflight.Schema(GenerationStoreFiles.WORKSPACE, 4, listOf(
                "workspace_conversation", "workspace_meta", "workspace_receipt",
                "workspace_draft", "workspace_authority", "workspace_message", "workspace_plan_review",
            )),
        )
    }
}
