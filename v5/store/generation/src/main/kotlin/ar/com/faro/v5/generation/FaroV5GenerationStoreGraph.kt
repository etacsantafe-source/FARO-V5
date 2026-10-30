package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import ar.com.faro.v5.store.reality.ActiveSqlitePreflight
import java.nio.file.Files
import java.nio.file.Path

class FaroV5GenerationStoreGraph private constructor(
    val generationDirectory: Path,
    val generationId: String,
    val reality: RealityStore,
    val commercialEvidence: CommercialEvidenceStore,
    val webEvidence: WebEvidenceStore,
    val auxiliary: AuxiliaryDeliveryStore,
    val workspace: ConversationWorkspaceStore?,
) : AutoCloseable {
    fun verifyHealthy(): Boolean {
        val realityHealth = reality.maintenance.inspect()
        val commercialHealth = commercialEvidence.maintenance.inspect()
        val webHealth = webEvidence.maintenance.inspect()
        val auxiliaryHealth = auxiliary.maintenance.inspect()

        return realityHealth.integrityHealthy &&
            realityHealth.foreignKeysHealthy &&
            commercialHealth.integrityHealthy &&
            commercialHealth.foreignKeysHealthy &&
            webHealth.integrityHealthy &&
            webHealth.foreignKeysHealthy &&
            auxiliaryHealth.integrityHealthy &&
            auxiliaryHealth.foreignKeysHealthy &&
            // openExisting already checked SQLite integrity and FKs; also
            // check the current Workspace authority remains readable.
            (workspace == null || workspace.snapshot().revision >= 0L)
    }

    override fun close() {
        runCatching { workspace?.close() }
        runCatching { auxiliary.close() }
        runCatching { webEvidence.close() }
        runCatching { commercialEvidence.close() }
        runCatching { reality.close() }
    }

    companion object {
        @JvmStatic
        fun open(
            generationDirectory: Path,
            generationId: String,
        ): FaroV5GenerationStoreGraph {
            require(generationId.isNotBlank())
            val normalized = generationDirectory.toAbsolutePath().normalize()
            // This is the original four-store runtime. A V2 marker (or
            // orphan workspace.db*) must fail BEFORE ANY SQLite driver opens.
            // A complete V2 graph requires a separate, explicitly certified owner.
            ExistingGenerationFormatReader.requireLegacyUnmarkedCandidate(normalized)
            GenerationStoreFiles.requireMaterialized(normalized)
            return openStores(normalized, generationId, workspace = null)
        }

        /**
         * Explicit, quarantined V2 owner. NOT used by startup/restore/backup
         * until the complete V2 lifecycle is certified. Five stores must
         * already exist; never bootstrap or switch an active generation here.
         */
        @JvmStatic
        fun openValidatedV2(staged: StagedStorageGeneration): FaroV5GenerationStoreGraph =
            openValidatedV2Internal(staged, planVerifier = null)

        /**
         * The only V2 read path permitted to expose immutable prepared plans.
         * The caller must provide the trusted kernel:runtime canonical verifier.
         * This method does NOT activate the staged generation or execute Apply.
         * No dependency from generation to kernel:runtime is introduced.
         */
        @JvmStatic
        fun openValidatedV2WithPlanVerifier(
            staged: StagedStorageGeneration,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
        ): FaroV5GenerationStoreGraph = openValidatedV2Internal(staged, planVerifier)

        /** Staged V2 only. A caller must supply the SAME gate for all five stores. */
        @JvmStatic
        fun openValidatedV2WithSharedGate(
            staged: StagedStorageGeneration,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            materialWriteGate: MaterialWriteGate,
        ): FaroV5GenerationStoreGraph =
            openValidatedV2Internal(staged, planVerifier, materialWriteGate)

        /**
         * Active V2 reopen after durable pointer switch or process restart.
         * Read-only preflight must observe committed WAL rather than treating
         * it as an incomplete STAGED backup. Callers guard pointer+quiescence.
         */
        @JvmStatic
        fun openActiveV2WithSharedGate(
            staged: StagedStorageGeneration,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            materialWriteGate: MaterialWriteGate,
        ): FaroV5GenerationStoreGraph =
            openValidatedV2Internal(
                staged, planVerifier, materialWriteGate, activeGeneration = true,
            )

        private fun openValidatedV2Internal(
            staged: StagedStorageGeneration,
            planVerifier: PreparedPlanEnvelopeVerificationPort?,
            materialWriteGate: MaterialWriteGate? = null,
            activeGeneration: Boolean = false,
        ): FaroV5GenerationStoreGraph {
            check(
                ExistingGenerationFormatReader.requireAndRead(staged) ==
                    GenerationStoreFormat.WORKSPACE_V2
            ) { "V2 generation requires its immutable format marker and five files" }
            val normalized = staged.directory.toAbsolutePath().normalize()
            // V2 must not silently initialize any of the four historical
            // stores from zero-byte placeholders. SQLite will initialize on
            // open, so check every existing database BEFORE opening any one.
            val sqliteHeader = byteArrayOf(
                83, 81, 76, 105, 116, 101, 32, 102,
                111, 114, 109, 97, 116, 32, 51, 0,
            )
            GenerationStoreFiles.WORKSPACE_V2.forEach { fileName ->
                val file = normalized.resolve(fileName)
                check(Files.size(file) >= 100L &&
                    java.io.DataInputStream(Files.newInputStream(file)).use { input ->
                        val actualHeader = ByteArray(sqliteHeader.size)
                        input.readFully(actualHeader)
                        actualHeader.contentEquals(sqliteHeader)
                    }) {
                    "V2 generation store is not an initialized SQLite database: $fileName"
                }
            }
            // A1.3c: fail without writes before ANY existing store's normal
            // initializer can run schema migrations or create WAL sidecars.
            requireStrictV2StoreSchemas(normalized, activeGeneration)
            val workspacePath = normalized.resolve(GenerationStoreFiles.WORKSPACE).toString()
            val workspace = if (planVerifier == null) {
                // The historic one-argument opener stays deny-all for plans.
                ConversationWorkspaceStore.openExisting(workspacePath, materialWriteGate = materialWriteGate)
            } else {
                ConversationWorkspaceStore.openExistingWithPlanVerifier(workspacePath, planVerifier, materialWriteGate = materialWriteGate)
            }
            try {
                // Verify every persisted plan before exposing ANY V2 graph,
                // instead of discovering malformed/unverified plans later in UI.
                // This semantic validation is bounded by WorkspaceBackupSnapshot.
                workspace.backup.snapshot().validateForRestore()
                return openStores(normalized, staged.descriptor.generationId.value, workspace, materialWriteGate)
            } catch (failure: Throwable) {
                runCatching { workspace.close() }
                throw failure
            }
        }

        /**
         * Strict staged V2 admission. No schema migration is allowed on an
         * existing-generation open; versions are pinned to the *current* store
         * schemas and must be deliberately updated with versioned migration.
         * Read-only connections reject malformed / outdated stores BEFORE a
         * read-write store constructor (and its initializeSchema) executes.
         * Staged sets must be quiesced/checkpointed; a hot WAL or leftover SHM
         * cannot be inspected as a self-contained sealed generation.
         */
        private fun requireStrictV2StoreSchemas(directory: Path, activeGeneration: Boolean) {
            val schemas = listOf(
                StagedSqlitePreflight.Schema(GenerationStoreFiles.REALITY, 5, listOf(
                    "profile_state", "apply_attempt", "execution_receipt",
                    "reality_outbox", "finance_state", "finance_account",
                    "finance_account_opening", "finance_entry",
                )),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.COMMERCIAL_EVIDENCE, 1, listOf("commercial_evidence")),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.WEB_EVIDENCE, 2, listOf(
                    "web_evidence", "web_search_evidence",
                )),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.AUXILIARY, 2, listOf(
                    "delivery_notice", "delivery_intent",
                )),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.WORKSPACE, 4, listOf(
                    "workspace_conversation", "workspace_meta", "workspace_receipt",
                    "workspace_draft", "workspace_authority", "workspace_message",
                    "workspace_plan_review",
                )),
            )
            if (activeGeneration) ActiveSqlitePreflight.requireAll(directory, schemas)
            else StagedSqlitePreflight.requireAll(directory, schemas)
        }

        private fun openStores(
            normalized: Path,
            generationId: String,
            workspace: ConversationWorkspaceStore?,
            materialWriteGate: MaterialWriteGate? = null,
        ): FaroV5GenerationStoreGraph {
            var reality: RealityStore? = null
            var commercial: CommercialEvidenceStore? = null
            var web: WebEvidenceStore? = null
            var auxiliary: AuxiliaryDeliveryStore? = null

            try {
                val openedReality =
                    RealityStore(normalized.resolve(GenerationStoreFiles.REALITY).toString(), materialWriteGate = materialWriteGate)
                reality = openedReality
                commercial =
                    CommercialEvidenceStore(
                        normalized.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
                        materialWriteGate = materialWriteGate,
                    )
                web =
                    WebEvidenceStore(
                        normalized.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString(),
                        materialWriteGate = materialWriteGate,
                    )
                auxiliary =
                    AuxiliaryDeliveryStore(
                        normalized.resolve(GenerationStoreFiles.AUXILIARY).toString(),
                        materialWriteGate = materialWriteGate,
                    )
                val graph =
                    FaroV5GenerationStoreGraph(
                        generationDirectory = normalized,
                        generationId = generationId,
                        reality = openedReality,
                        commercialEvidence = commercial,
                        webEvidence = web,
                        auxiliary = auxiliary,
                        workspace = workspace,
                    )
                check(graph.verifyHealthy()) {
                    "active StorageGeneration failed store health verification"
                }
                return graph
            } catch (failure: Throwable) {
                runCatching { auxiliary?.close() }
                runCatching { web?.close() }
                runCatching { commercial?.close() }
                runCatching { reality?.close() }
                runCatching { workspace?.close() }
                throw failure
            }
        }
    }
}
