package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityFinance9StagedFilePort
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.file.Path

/**
 * M0B: upgrades only an already SEALED Reality8 member of a complete V2
 * stage. Does not switch an active pointer, activate, restore, or expose
 * an active-schema9 graph. M0C/M1 must independently certify later gates.
 */
class FaroV5Finance9StagedMigration internal constructor(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val afterInstall: () -> Unit,
) {
    constructor(storageRoot: Path, quiescence: MaterialWriteQuiescencePort) :
        this(storageRoot, quiescence, {})

    private val root = storageRoot.toAbsolutePath().normalize()
    private val pointer = FileActiveGenerationPointer(root)
    private val layout = FileStorageGenerationLayout(root)

    fun migrate(
        operationId: MaintenanceOperationId,
        staged: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        sourceFingerprint: String,
        finishedAtEpochMillis: Long,
    ): StagedStorageGeneration =
        quiescence.withMaterialWritesQuiesced(operationId) {
            check(pointer.read() == expectedActive) {
                "Finance9 stage requires an exact pinned active pointer"
            }
            check(staged.descriptor.generationId != expectedActive.generationId) {
                "Finance9 cannot migrate an active generation"
            }
            val canonical = layout.requireStaged(staged.descriptor.generationId)
            check(canonical.descriptor == staged.descriptor &&
                canonical.directory.toAbsolutePath().normalize() ==
                    staged.directory.toAbsolutePath().normalize()) {
                "Finance9 stage descriptor/path is not canonical"
            }
            check(ExistingGenerationFormatReader.requireAndRead(canonical) ==
                GenerationStoreFormat.WORKSPACE_V2) {
                "Finance9 requires V2 marker with five materialized stores"
            }
            StagedSqlitePreflight.requireAll(canonical.directory, schemas(8))
            check(pointer.read() == expectedActive) {
                "Finance9 pointer changed during preflight"
            }
            RealityFinance9StagedFilePort.migrateTrustedStagedEight(
                file = canonical.directory.resolve(GenerationStoreFiles.REALITY),
                migrationId = operationId.value,
                sourceFingerprint = sourceFingerprint,
                finishedAtEpochMillis = finishedAtEpochMillis,
            )
            afterInstall() // Injected only by in-module tests; does not change production.
            check(pointer.read() == expectedActive) {
                "Finance9 pointer changed while staged SQL was being installed"
            }
            StagedSqlitePreflight.requireAll(canonical.directory, schemas(9))
            check(pointer.read() == expectedActive) {
                "Finance9 pointer changed during final sealed-stage validation"
            }
            canonical
        }

    private fun schemas(realityVersion: Int): List<StagedSqlitePreflight.Schema> =
        listOf(
            StagedSqlitePreflight.Schema(
                GenerationStoreFiles.REALITY,
                realityVersion,
                listOf(
                    "profile_state", "apply_attempt", "execution_receipt",
                    "reality_outbox", "finance_state", "finance_account",
                    "finance_account_opening", "finance_entry",
                    "objective_state", "objective_record",
                    "matter_state", "matter_record", "reminder_state",
                    "reminder_rule", "reminder_occurrence",
                    "monthly_plan_state", "monthly_plan_item",
                ) + if (realityVersion == 9) listOf(
                    "finance_acl_state", "finance_acl_policy", "finance_apply_grant",
                    "finance_opening_confirmation", "finance_transfer", "finance_debt",
                    "finance_schedule", "finance_plan_item",
                    "finance_plan_item_payment", "finance_budget_limit",
                    "finance_schema_migration_audit",
                ) else emptyList(),
            ),
            StagedSqlitePreflight.Schema(
                GenerationStoreFiles.COMMERCIAL_EVIDENCE, 1,
                listOf("commercial_evidence"),
            ),
            StagedSqlitePreflight.Schema(
                GenerationStoreFiles.WEB_EVIDENCE, 2,
                listOf("web_evidence", "web_search_evidence"),
            ),
            StagedSqlitePreflight.Schema(
                GenerationStoreFiles.AUXILIARY, 3,
                listOf("delivery_notice", "delivery_intent", "bookmark_scope",
                    "bookmark_folder", "bookmark_item", "bookmark_receipt"),
            ),
            StagedSqlitePreflight.Schema(
                GenerationStoreFiles.WORKSPACE, 4,
                listOf("workspace_conversation", "workspace_meta",
                    "workspace_receipt", "workspace_draft",
                    "workspace_authority", "workspace_message",
                    "workspace_plan_review"),
            ),
        )
}
