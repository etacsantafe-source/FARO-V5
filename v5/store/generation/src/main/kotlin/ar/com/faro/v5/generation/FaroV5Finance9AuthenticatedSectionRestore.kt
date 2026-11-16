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
import ar.com.faro.v5.store.reality.RealityFinance9SealedSnapshot
import ar.com.faro.v5.store.reality.RealityFinance9SealedTableRows
import ar.com.faro.v5.store.reality.RealityFinance9SealedReadPort
import ar.com.faro.v5.store.reality.RealityFinance9StagedRowsRestorePort
import ar.com.faro.v5.store.reality.RealityFinance9SectionRestoreDisposition
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.file.Path

/**
 * DEV-ONLY M0C2B1 authenticated FINANCE9-section restorer.
 *
 * This does not replay the four-store Reality ledger, G4, Planning8,
 * Workspace or receipt journal from the archive. It DOES NOT permit cutover
 * or an external backup restore. M0C2B2 will integrate the other owners and
 * durable archive identity; M1 will certify cross-process quiescence/ACL.
 */
class FaroV5Finance9AuthenticatedSectionRestore internal constructor(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val afterFinanceMutation: () -> Unit,
) {
    constructor(storageRoot: Path, quiescence: MaterialWriteQuiescencePort) :
        this(storageRoot, quiescence, {})

    private val pointer = FileActiveGenerationPointer(storageRoot)
    private val layout = FileStorageGenerationLayout(storageRoot)

    fun restoreFinanceSectionOnly(
        operationId: MaintenanceOperationId,
        staged: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): RealityFinance9SectionRestoreDisposition =
        quiescence.withMaterialWritesQuiesced(operationId) {
            check(pointer.read() == expectedActive) {
                "Finance9 archive section restore requires exact pinned pointer"
            }
            check(staged.descriptor.generationId != expectedActive.generationId) {
                "Finance9 archive restore cannot write an active generation"
            }
            val target = layout.requireStaged(staged.descriptor.generationId)
            check(target.descriptor == staged.descriptor &&
                target.directory.toAbsolutePath().normalize() ==
                    staged.directory.toAbsolutePath().normalize()
            ) { "Finance9 stage archive target identity/path is not canonical" }
            check(ExistingGenerationFormatReader.requireAndRead(target) ==
                GenerationStoreFormat.WORKSPACE_V2) {
                "Finance9 archive restore requires sealed V2 five-store marker"
            }
            StagedSqlitePreflight.requireAll(target.directory, schemas)
            check(pointer.read() == expectedActive) {
                "Finance9 pointer changed during target preflight"
            }

            val opened = FaroV5FiveStorePortableBackupCrypto().open(archive, passphrase)
            val backup = (opened as? FiveStorePortableBackupOpenResult.Opened)?.backupSet
                ?: error("Finance9 archive failed FAROBAK2 authentication/decode")
            val originalFinance = checkNotNull(backup.finance9) {
                "Finance9 section cannot be silently omitted by archive"
            }
            check(backup.g4Domains != null && backup.planningDomains != null) {
                "Finance9 FBS2v4 requires complete legacy G4 and Planning sections"
            }
            check(backup.fourStore.manifest.sourceGenerationId.value !=
                staged.descriptor.generationId.value
            ) { "Finance9 archive source and target generation cannot coincide" }
            // Enforce canonical F9S1 validity. The archive's data is never
            // admitted as a principal/ACL, regardless of signed provenance.
            val finance = FaroV5Finance9BackupCodec.decode(
                FaroV5Finance9BackupCodec.encode(originalFinance),
            )
            check(pointer.read() == expectedActive) {
                "Finance9 pointer changed during authenticated archive decode"
            }
            val file = target.directory.resolve(GenerationStoreFiles.REALITY)
            val desired = RealityFinance9SealedSnapshot(
                finance.schemaVersion,
                finance.tables.map { RealityFinance9SealedTableRows(it.table, it.rows) },
            )
            val disposition = RealityFinance9StagedRowsRestorePort
                .restoreOrVerifyTrustedStagedNine(file, desired)
            afterFinanceMutation() // Test-only fault seam; never product authorization.
            check(pointer.read() == expectedActive) {
                "Finance9 pointer changed after staged restore COMMIT"
            }
            StagedSqlitePreflight.requireAll(target.directory, schemas)
            val observed = RealityFinance9SealedReadPort.readTrustedSealedStage9(file)
            check(observed == desired && pointer.read() == expectedActive) {
                "Finance9 staged archive section is not exact or pointer became stale"
            }
            disposition
        }

    /** Exact five-store schema fence, including Finance9 only on Reality. */
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
