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
import ar.com.faro.v5.store.reality.RealityFinance9SealedReadPort
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.file.Path

/**
 * M0C2A isolated DEV Finance9 data-only capture; never exports a full backup,
 * restores, switches the pointer, or grants Finance authority. M0C2B must
 * separately certify paired five-store snapshot + authenticated replay.
 *
 * The quiescence injected here is in-process only until the M1 cross-process
 * material-write/activation boundary is certified.
 */
class FaroV5Finance9SealedSnapshotCapture internal constructor(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val afterRead: () -> Unit,
) {
    constructor(storageRoot: Path, quiescence: MaterialWriteQuiescencePort) :
        this(storageRoot, quiescence, {})

    private val pointer = FileActiveGenerationPointer(storageRoot)
    private val layout = FileStorageGenerationLayout(storageRoot)

    fun capture(
        operationId: MaintenanceOperationId,
        staged: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ): Finance9SectionSnapshot =
        quiescence.withMaterialWritesQuiesced(operationId) {
            check(pointer.read() == expectedActive) {
                "Finance9 data capture requires exact pinned active pointer"
            }
            check(staged.descriptor.generationId != expectedActive.generationId) {
                "Finance9 cannot capture an ACTIVE target"
            }
            val canonical = layout.requireStaged(staged.descriptor.generationId)
            check(canonical.descriptor == staged.descriptor &&
                canonical.directory.toAbsolutePath().normalize() ==
                    staged.directory.toAbsolutePath().normalize()
            ) { "Finance9 capture requires canonical immutable descriptor/path" }
            check(ExistingGenerationFormatReader.requireAndRead(canonical) ==
                GenerationStoreFormat.WORKSPACE_V2) {
                "Finance9 data capture requires V2 format and five stores"
            }
            StagedSqlitePreflight.requireAll(canonical.directory, schemas)
            check(pointer.read() == expectedActive) {
                "Finance9 pointer changed during sealed preflight"
            }
            val fromReality = RealityFinance9SealedReadPort.readTrustedSealedStage9(
                canonical.directory.resolve(GenerationStoreFiles.REALITY),
            )
            val snapshot = Finance9SectionSnapshot(
                schemaVersion = fromReality.schemaVersion,
                tables = fromReality.tables.map { Finance9TableRows(it.table, it.rows) },
            )
            // Enforce final canonical size and section identity before return.
            check(FaroV5Finance9BackupCodec.decode(
                FaroV5Finance9BackupCodec.encode(snapshot),
            ) == snapshot) { "Finance9 sealed read failed canonical roundtrip" }
            afterRead() // Fault seam in module tests only.
            check(pointer.read() == expectedActive) {
                "Finance9 active generation pointer changed during capture"
            }
            StagedSqlitePreflight.requireAll(canonical.directory, schemas)
            check(pointer.read() == expectedActive) {
                "Finance9 pointer changed during final sealed snapshot validation"
            }
            snapshot
        }

    private val schemas = listOf(
        StagedSqlitePreflight.Schema(
            GenerationStoreFiles.REALITY, 9,
            listOf(
                "profile_state", "apply_attempt", "execution_receipt",
                "reality_outbox", "finance_state", "finance_account",
                "finance_account_opening", "finance_entry",
                "objective_state", "objective_record", "matter_state",
                "matter_record", "reminder_state", "reminder_rule",
                "reminder_occurrence", "monthly_plan_state", "monthly_plan_item",
                "finance_acl_state", "finance_acl_policy", "finance_apply_grant",
                "finance_opening_confirmation", "finance_transfer",
                "finance_debt", "finance_schedule", "finance_plan_item",
                "finance_plan_item_payment", "finance_budget_limit",
                "finance_schema_migration_audit",
            ),
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
                "workspace_receipt", "workspace_draft", "workspace_authority",
                "workspace_message", "workspace_plan_review"),
        ),
    )
}
