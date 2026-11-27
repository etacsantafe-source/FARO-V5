package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.evidence.CommercialEvidenceRecord
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.CommercialPriceHistoryReadExecutor
import ar.com.faro.v5.evidence.CommercialEvidenceRecordExecutor
import ar.com.faro.v5.evidence.CommercialEvidenceExecution
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialEvidencePayload
import ar.com.faro.v5.kernel.contracts.PreparedOperation
import ar.com.faro.v5.kernel.contracts.OperationContractDescriptor
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.ApplyCommitPort
import ar.com.faro.v5.kernel.contracts.AtomicApplyTransactionPort
import ar.com.faro.v5.kernel.contracts.DependencyStampLookupPort
import ar.com.faro.v5.owner.household.api.HouseholdReviewGeneration
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import ar.com.faro.v5.store.reality.RealityFinance9SealedReadPort
import ar.com.faro.v5.store.reality.RealityHousehold10SealedRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold12PurchaseRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold13CompensationRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionRows
import ar.com.faro.v5.store.reality.ActiveSqlitePreflight
import ar.com.faro.v5.store.reality.RealityHousehold10Rows
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

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

    class Household12PurchaseDevSession internal constructor(
        private val graph: FaroV5GenerationStoreGraph,
        private val gate: MaterialWriteGate,
        private val channel: FileChannel,
    ) : AutoCloseable {
        val workspace: ConversationWorkspaceStore get() = checkNotNull(graph.workspace)
        val identities = graph.reality.applyAttemptIdentities
        val attempts = graph.reality.applyAttemptLookup
        val receipts = graph.reality.executionReceiptLookup
        val transactions: AtomicApplyTransactionPort = object : AtomicApplyTransactionPort by graph.reality {}
        val commits: ApplyCommitPort = object : ApplyCommitPort by graph.reality {}
        val dependencies: DependencyStampLookupPort = object : DependencyStampLookupPort by graph.reality {}

        fun purchases(principal: String, generation: HouseholdReviewGeneration) =
            graph.reality.household12PurchasePersistenceForTrustedDev(principal, generation)

        internal fun stockReceipts(principal: String, generation: HouseholdReviewGeneration) =
            graph.reality.household12StockReceivePersistenceForTrustedDev(principal, generation)

        fun snapshotPurchaseReceipts() = graph.reality.snapshotActiveHousehold12RowsForTrustedHost()

        override fun close() = gate.withQuiescedMaterialWrites {
            if (channel.isOpen) {
                try {
                    graph.close()
                } finally {
                    channel.close()
                }
            }
        }
    }

    /** Does not mint N-016 authority or expose an Apply binding to the model. */
    class Household13StockUndoDevSession internal constructor(
        private val graph: FaroV5GenerationStoreGraph,
        private val gate: MaterialWriteGate,
        private val channel: FileChannel,
    ) : AutoCloseable {
        val workspace: ConversationWorkspaceStore get() = checkNotNull(graph.workspace)
        val identities get() = graph.reality.applyAttemptIdentities
        val attempts get() = graph.reality.applyAttemptLookup
        val receipts get() = graph.reality.executionReceiptLookup
        val transactions: AtomicApplyTransactionPort = object : AtomicApplyTransactionPort by graph.reality {}
        val commits: ApplyCommitPort = object : ApplyCommitPort by graph.reality {}
        val dependencies: DependencyStampLookupPort = object : DependencyStampLookupPort by graph.reality {}

        fun compensations(principal: String, generation: HouseholdReviewGeneration) =
            graph.reality.household13CompensationForTrustedDev(principal, generation)

        override fun close() = gate.withQuiescedMaterialWrites {
            if (channel.isOpen) {
                try { graph.close() } finally { channel.close() }
            }
        }
    }

    /** Isolated SQL14 DEV: one root lease, one Reality Apply transaction owner. */
    class Household14ReservationDevSession internal constructor(
        private val graph: FaroV5GenerationStoreGraph,
        private val gate: MaterialWriteGate,
        private val channel: FileChannel,
    ) : AutoCloseable {
        val workspace: ConversationWorkspaceStore get() = checkNotNull(graph.workspace)
        val identities get() = graph.reality.applyAttemptIdentities
        val attempts get() = graph.reality.applyAttemptLookup
        val receipts get() = graph.reality.executionReceiptLookup
        val transactions: AtomicApplyTransactionPort = object : AtomicApplyTransactionPort by graph.reality {}
        val commits: ApplyCommitPort = object : ApplyCommitPort by graph.reality {}
        val dependencies: DependencyStampLookupPort = object : DependencyStampLookupPort by graph.reality {}
        fun reservations(principal: String, generation: HouseholdReviewGeneration) =
            graph.reality.household14ReservationForTrustedDev(principal, generation)
        fun snapshotReservations() = graph.reality.snapshotActiveHousehold14RowsForTrustedHost()
        override fun close() = gate.withQuiescedMaterialWrites {
            if (channel.isOpen) {
                try { graph.close() } finally { channel.close() }
            }
        }
    }

    /** SQL15 isolated DEV: one real completed-ticket admission and one Reality transaction owner. */
    class Household15ConsumptionDevSession internal constructor(
        private val graph: FaroV5GenerationStoreGraph,
        private val gate: MaterialWriteGate,
        private val channel: FileChannel,
    ) : AutoCloseable {
        val workspace: ConversationWorkspaceStore get() = checkNotNull(graph.workspace)
        val identities get() = graph.reality.applyAttemptIdentities
        val attempts get() = graph.reality.applyAttemptLookup
        val receipts get() = graph.reality.executionReceiptLookup
        val transactions: AtomicApplyTransactionPort = object : AtomicApplyTransactionPort by graph.reality {}
        val commits: ApplyCommitPort = object : ApplyCommitPort by graph.reality {}
        val dependencies: DependencyStampLookupPort = object : DependencyStampLookupPort by graph.reality {}
        fun consumptions(principal: String, generation: HouseholdReviewGeneration) =
            graph.reality.household15ConsumptionForTrustedDev(principal, generation)
        fun snapshotConsumptions() = graph.reality.snapshotActiveHousehold15RowsForTrustedHost()
        override fun close() = gate.withQuiescedMaterialWrites {
            if (channel.isOpen) {
                try { graph.close() } finally { channel.close() }
            }
        }
    }

    class Household12StockReceiveDevSession internal constructor(
        private val session: Household12PurchaseDevSession,
    ) : AutoCloseable {
        val workspace get() = session.workspace
        val identities get() = session.identities
        val attempts get() = session.attempts
        val receipts get() = session.receipts
        val transactions get() = session.transactions
        val commits get() = session.commits
        val dependencies get() = session.dependencies

        fun stockReceipts(principal: String, generation: HouseholdReviewGeneration) =
            session.stockReceipts(principal, generation)

        override fun close() = session.close()
    }

    internal class Household10DevReadSession(
        internal val graph: FaroV5GenerationStoreGraph,
        private val gate: MaterialWriteGate,
        private val lease: FileLock,
        private val pointerFence: () -> Boolean,
    ) : AutoCloseable {
        fun readNeeds(ownerAdmission: () -> Boolean): RealityHousehold10Rows =
            graph.reality.household10ActiveDevReadForTrustedHost(ownerAdmission)

        /**
         * C0B only: uses the SAME leased five-store graph. No second SQLite
         * opener and no mutable Evidence store exposed to the application.
         * The host has to prove independent Household and Commercial grants.
         */
        fun authorizedCommercialPriceHistory(
            canonicalContract: OperationContractDescriptor,
            productKey: CommercialProductKey,
            limit: Int,
            householdAdmission: () -> Boolean,
            commercialAdmission: () -> Boolean,
        ): List<CommercialEvidenceRecord>? = gate.withMaterialWrite {
            if (!lease.isValid || !householdAdmission() || !commercialAdmission()) {
                return@withMaterialWrite null
            }
            val records = CommercialPriceHistoryReadExecutor(
                graph.commercialEvidence, canonicalContract,
            ).execute(productKey, limit)
            records.takeIf { lease.isValid && householdAdmission() && commercialAdmission() }
        }

        /**
         * F2B1 DEV: read typed Evidence2 from this SAME already-selected
         * Household10 graph/lease. The trusted host must supply both independent
         * grants for the selected need/product. Never a live quote or Apply.
         * Returns null if ACL, pointer, lease or snapshot integrity changes.
         */
        internal fun authorizedCommercialTypedSnapshot(
            householdAdmission: () -> Boolean,
            commercialAdmission: () -> Boolean,
        ): CommercialEvidenceStagedTypedPortabilitySnapshot? = gate.withMaterialWrite {
            fun admits(): Boolean = runCatching {
                lease.isValid && pointerFence() &&
                    householdAdmission() && commercialAdmission()
            }.getOrDefault(false)
            if (!admits()) return@withMaterialWrite null
            val snapshot = runCatching {
                check(graph.commercialEvidence.maintenance.inspect().schemaVersion == 2)
                graph.commercialEvidence.stagedTypedPortability.snapshot()
            }.getOrNull() ?: return@withMaterialWrite null
            snapshot.takeIf { admits() }
        }


        /**
         * F2C1B1B1B0: policy-issuance SOURCE ONLY, on the ALREADY OPEN DEV
         * Household10/Evidence2 graph and exact root lease. This is not an
         * independent commercial grant or an Android human authentication.
         * The trusted host's decision is checked before and after the read.
         */
        internal fun typedOwnerSourceForTrustedDevDecision(
            trustedHostDecision: () -> Boolean,
        ): CommercialEvidenceStagedTypedPortabilitySnapshot? = gate.withMaterialWrite {
            fun current(): Boolean = runCatching {
                lease.isValid && pointerFence() && trustedHostDecision()
            }.getOrDefault(false)
            if (!current()) return@withMaterialWrite null
            val data = runCatching {
                check(graph.commercialEvidence.maintenance.inspect().schemaVersion == 2)
                graph.commercialEvidence.stagedTypedPortability.snapshot()
            }.getOrNull() ?: return@withMaterialWrite null
            data.takeIf { current() }
        }

        /**
         * C1a: only a MANUAL schema1 Evidence observation in this already leased
         * generation. Never exports a mutable store, root opener or Reality Apply.
         * The trusted caller supplies independent Household+commercial permissions
         * and a separate human confirmation. No post-commit rollback is promised.
         */
        fun authorizedManualEvidenceRecord(
            canonicalContract: OperationContractDescriptor,
            preparedOperation: PreparedOperation,
            householdAdmission: () -> Boolean,
            commercialWriteAdmission: () -> Boolean,
            trustedConfirmation: () -> Boolean,
        ): CommercialEvidenceExecution? = gate.withMaterialWrite {
            if (!lease.isValid || !householdAdmission() ||
                !commercialWriteAdmission() || !trustedConfirmation()
            ) return@withMaterialWrite null
            if (preparedOperation.contract != canonicalContract ||
                CommercialEvidencePayload.decode(preparedOperation.payload)?.sourceKind !=
                    CommercialEvidenceSourceKind.MANUAL
            ) return@withMaterialWrite null
            CommercialEvidenceRecordExecutor(
                graph.commercialEvidence, canonicalContract,
            ).execute(preparedOperation)
        }

        override fun close() = gate.withQuiescedMaterialWrites {
            if (lease.isValid) {
                try {
                    graph.close()
                } finally {
                    lease.channel().close()
                }
            }
        }
    }

    /** Runtime graph held by the trusted Household DEV composition root. */
    class Household10DevApplySession internal constructor(
        private val session: Household10DevReadSession,
    ) : AutoCloseable {
        val workspace: ConversationWorkspaceStore get() = checkNotNull(session.graph.workspace)
        val reality: RealityStore get() = session.graph.reality

        fun authorizedCommercialPriceHistory(
            canonicalContract: OperationContractDescriptor,
            productKey: CommercialProductKey,
            limit: Int,
            householdAdmission: () -> Boolean,
            commercialAdmission: () -> Boolean,
        ): List<CommercialEvidenceRecord>? =
            session.authorizedCommercialPriceHistory(
                canonicalContract, productKey, limit, householdAdmission, commercialAdmission,
            )

        fun authorizedManualEvidenceRecord(
            canonicalContract: OperationContractDescriptor,
            preparedOperation: PreparedOperation,
            householdAdmission: () -> Boolean,
            commercialWriteAdmission: () -> Boolean,
            trustedConfirmation: () -> Boolean,
        ): CommercialEvidenceExecution? =
            session.authorizedManualEvidenceRecord(
                canonicalContract, preparedOperation, householdAdmission,
                commercialWriteAdmission, trustedConfirmation,
            )

        override fun close() = session.close()
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
         * W03-D2B2A: ONLY a sealed, quiescent, INACTIVE V2 STAGED generation.
         * This does NOT change the active pointer, regular v3 openers or FBS2.
         * A version4 ledger cannot be promoted until the receipt-inclusive
         * backup/restore codec and active preflight are separately certified.
         */
        @JvmStatic
        fun openQuarantinedAuxiliaryReceiptV4Stage(
            staged: StagedStorageGeneration,
            activePointer: ActiveGenerationPointerPort,
        ): AuxiliaryDeliveryStore {
            val pinned = activePointer.read()
            check(pinned?.generationId != staged.descriptor.generationId) {
                "Receipt stage4 cannot open the current ACTIVE generation"
            }
            check(ExistingGenerationFormatReader.requireAndRead(staged) ==
                GenerationStoreFormat.WORKSPACE_V2) {
                "Notice receipt stage4 requires immutable V2 generation marker"
            }
            val v3Tables = listOf(
                "delivery_notice", "delivery_intent", "bookmark_scope",
                "bookmark_folder", "bookmark_item", "bookmark_receipt",
            )
            StagedSqlitePreflight.requireAll(staged.directory, listOf(
                StagedSqlitePreflight.Schema(
                    GenerationStoreFiles.AUXILIARY, 4,
                    v3Tables + "notice_attention_receipt",
                    legacyVersion = 3, legacyTables = v3Tables,
                ),
            ))
            // Immutable full pointer (sequence AND ID) detects switch-away-back.
            val fence: () -> Boolean = {
                activePointer.read() == pinned &&
                    pinned?.generationId != staged.descriptor.generationId
            }
            check(fence()) { "ACTIVE pointer changed during receipt preflight" }
            val owner = AuxiliaryDeliveryStore
                .openQuarantinedStagedReceiptV4ForTrustedGeneration(
                    staged.directory.resolve(GenerationStoreFiles.AUXILIARY).toString(),
                    fence,
                )
            if (!fence()) {
                owner.close()
                error("ACTIVE pointer changed after receipt migration")
            }
            val health = owner.maintenance.inspect()
            if (!health.integrityHealthy || !health.foreignKeysHealthy ||
                health.schemaVersion != 4
            ) {
                owner.close()
                error("Staged Notice receipt schema4 failed owner health")
            }
            return owner
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
         * C1b2G: one isolated STAGED Evidence2 five-owner graph, not an ACTIVE
         * opener or promotion/cutover API. Requires a sealed schema2 Commercial
         * DB BEFORE graph opening and the SAME material gate for all owners.
         * The immutable descriptor and durable pointer are pinned across open.
         */
        internal fun openValidatedV2Evidence2StagedDev(
            staged: StagedStorageGeneration,
            materialWriteGate: MaterialWriteGate,
        ): FaroV5GenerationStoreGraph {
            val normalized = staged.directory.toAbsolutePath().normalize()
            check(normalized.parent?.fileName?.toString() == "generations") {
                "isolated Evidence2 requires canonical STAGED generation path"
            }
            val root = normalized.parent.parent
            val canonical = FileStorageGenerationLayout(root)
                .requireStaged(staged.descriptor.generationId)
            check(canonical.descriptor == staged.descriptor &&
                canonical.directory.toAbsolutePath().normalize() == normalized) {
                "Evidence2 stage descriptor/path changed"
            }
            val pointer = FileActiveGenerationPointer(root)
            val pinned = pointer.read()
            check(pinned?.generationId != staged.descriptor.generationId) {
                "Evidence2 staged graph cannot open ACTIVE generation"
            }
            val graph = openValidatedV2Internal(
                canonical,
                planVerifier = null,
                materialWriteGate = materialWriteGate,
                commercialEvidence2StagedDev = true,
            )
            if (pointer.read() != pinned) {
                graph.close()
                error("Evidence2 staged pointer changed during graph opening")
            }
            return graph
        }

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

        /**
         * N033C1 INTERNAL ONLY: reopen an ALREADY active, exactly schema8 V2
         * generation. Does NOT switch pointers, migrate schema7, certify host,
         * grant user ACL or bind any Planning operation to Android/Chat.
         *
         * The caller supplies a generation identity plus the monotonic durable
         * pointer sequence; switching away/back to the same ID invalidates it.
         * Read-only WAL-aware exact-schema preflight precedes writable opens.
         */
        internal fun reopenVerifiedActivePlanning8(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            expectedPointerSequence: Long,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            materialWriteGate: MaterialWriteGate,
        ): FaroV5GenerationStoreGraph = reopenVerifiedActivePlanning(
            storageRoot, staged, expectedPointerSequence, planVerifier, materialWriteGate,
            finance9 = false,
        )

        internal fun reopenVerifiedActiveFinance9Dev(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            expectedPointerSequence: Long,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            materialWriteGate: MaterialWriteGate,
        ): FaroV5GenerationStoreGraph = reopenVerifiedActivePlanning(
            storageRoot, staged, expectedPointerSequence, planVerifier, materialWriteGate,
            finance9 = true,
        )

        internal fun reopenVerifiedActiveFinance9Auxiliary4Dev(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            expectedPointerSequence: Long,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            materialWriteGate: MaterialWriteGate,
        ): FaroV5GenerationStoreGraph = reopenVerifiedActivePlanning(
            storageRoot, staged, expectedPointerSequence, planVerifier, materialWriteGate,
            finance9 = true, auxiliary4 = true,
        )

        private fun reopenVerifiedActivePlanning(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            expectedPointerSequence: Long,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            materialWriteGate: MaterialWriteGate,
            finance9: Boolean,
            auxiliary4: Boolean = false,
        ): FaroV5GenerationStoreGraph {
            require(expectedPointerSequence > 0L) {
                "Active Planning generation requires a positive durable pointer sequence"
            }
            val pointer = FileActiveGenerationPointer(storageRoot)
            val observed = checkNotNull(pointer.read()) {
                "Active Planning generation requires an existing durable generation pointer"
            }
            check(observed.generationId == staged.descriptor.generationId &&
                observed.sequence == expectedPointerSequence
            ) { "Active Planning generation generation pointer identity/sequence mismatch" }
            val canonical = FileStorageGenerationLayout(storageRoot)
                .requireStaged(staged.descriptor.generationId)
            check(canonical.descriptor == staged.descriptor &&
                canonical.directory.toAbsolutePath().normalize() ==
                    staged.directory.toAbsolutePath().normalize()
            ) { "Active Planning generation requires an exact canonical immutable descriptor" }
            // Header, marker, exact schema and all five read-only WAL checks are
            // inside openValidatedV2Internal, before any writable SQLite open.
            val graph = openValidatedV2Internal(
                canonical, planVerifier, materialWriteGate,
                activeGeneration = true, planningStage8 = true, financeStage9 = finance9,
                checkedPlanning8Active = true,
                auxiliaryReceiptFence = if (auxiliary4) ({ pointer.read() == observed }) else null,
            )
            try {
                check(pointer.read() == observed) {
                    "Active Planning generation generation pointer changed during store open"
                }
                check(graph.generationId == observed.generationId.value &&
                    graph.verifyHealthy()
                ) { "Active Planning generation reopened graph health/identity mismatch" }
                return graph
            } catch (failure: Throwable) {
                graph.close()
                throw failure
            }
        }


        /**
         * A5: active Reality8 host handoff. This never stages, migrates, activates,
         * authenticates a human or admits any UI/Apply operation. Callers MUST
         * provide an independently certified host; false denies before SQLite.
         * The proven N033C1 opener pins the full durable generation sequence.
         */
        @JvmStatic
        fun openActivePlanning8ForCertifiedHost(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            expectedPointerSequence: Long,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            materialWriteGate: MaterialWriteGate,
            v2HostCertified: Boolean,
        ): FaroV5GenerationStoreGraph {
            check(v2HostCertified) {
                "Active Reality8 is forbidden before explicit host certification"
            }
            return reopenVerifiedActivePlanning8(
                storageRoot, staged, expectedPointerSequence, planVerifier,
                materialWriteGate,
            )
        }

        /**
         * N033C2B2: host-accessible *readiness only*. Never expose the active
         * mutable graph, migrate an active schema, grant ACL or switch pointers.
         * The fenced internal opener performs exact schema8/WAL checks and is
         * always closed before this method returns a Boolean.
         */
        @JvmStatic
        fun isActivePlanning8HealthyForHost(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            expectedPointerSequence: Long,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            materialWriteGate: MaterialWriteGate,
        ): Boolean = runCatching {
            reopenVerifiedActivePlanning8(
                storageRoot, staged, expectedPointerSequence, planVerifier,
                materialWriteGate,
            ).use { graph ->
                graph.workspace != null &&
                    graph.reality.maintenance.inspect().schemaVersion == 8 &&
                    graph.verifyHealthy()
            }
        }.getOrDefault(false)

        /**
         * ISOLATED, INTERNAL staged Planning8 upgrade. No Android host, no active
         * generation, no public export/restore. Validates the durable descriptor
         * and non-active pointer BEFORE any SQLite schema migration is possible.
         */
        internal fun openValidatedV2PlanningStage8(
            staged: StagedStorageGeneration,
        ): FaroV5GenerationStoreGraph {
            val generationsDir = staged.directory.toAbsolutePath().normalize().parent
            check(generationsDir.fileName.toString() == "generations") {
                "Planning8 requires an isolated canonical staged generation path"
            }
            val root = generationsDir.parent
            val canonical = FileStorageGenerationLayout(root)
                .requireStaged(staged.descriptor.generationId)
            check(canonical.descriptor == staged.descriptor &&
                canonical.directory.toAbsolutePath().normalize() ==
                    staged.directory.toAbsolutePath().normalize()
            ) { "Planning8 stage descriptor identity mismatch" }
            check(FileActiveGenerationPointer(root).read()?.generationId !=
                staged.descriptor.generationId
            ) { "Planning8 cannot migrate an active generation" }
            return openValidatedV2Internal(
                staged, planVerifier = null, planningStage8 = true,
            )
        }

        /**
         * INTERNAL Finance9 STAGED-only graph. Caller supplies the exact active
         * pointer ID+sequence; a changed pointer before/after opening fails
         * and closes all opened stores. No Android, product or Apply mounting.
         */
        internal fun openValidatedV2Finance9Stage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
        ): FaroV5GenerationStoreGraph =
            openFinance9ArchiveStage(staged, expectedActive, auxiliary4 = false)

        internal fun openValidatedV2Finance9Auxiliary4Stage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
        ): FaroV5GenerationStoreGraph =
            openFinance9ArchiveStage(staged, expectedActive, auxiliary4 = true, planVerifier = planVerifier)

        internal fun openValidatedHousehold10ArchiveStage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
        ): FaroV5GenerationStoreGraph = openFinance9ArchiveStage(
            staged, expectedActive, auxiliary4 = true, householdStage10 = true, planVerifier = planVerifier,
        )

        /** F2B2B02: strictly STAGED typed Evidence2 owner; no ACTIVE pointer switch. */
        internal fun openValidatedHousehold10Evidence2ArchiveStage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
        ): FaroV5GenerationStoreGraph = openFinance9ArchiveStage(
            staged, expectedActive, auxiliary4 = true, householdStage10 = true,
            planVerifier = planVerifier, commercialEvidence2StagedDev = true,
        )

        /** Finance9/Auxiliary4/Evidence2 is the sealed pre-Household restore target. */
        internal fun openValidatedV2Finance9Auxiliary4Evidence2Stage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
        ): FaroV5GenerationStoreGraph = openFinance9ArchiveStage(
            staged, expectedActive, auxiliary4 = true, planVerifier = planVerifier,
            commercialEvidence2StagedDev = true,
        )

        /** The runtime host keeps the same DEV root lease as the cutover owner. */
        internal fun openActiveHousehold10DevForTrustedHost(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            expected: ActiveGenerationPointer,
            materialWriteGate: MaterialWriteGate,
            ownerAdmission: () -> Boolean,
        ): Household10DevReadSession = openActiveHousehold10Dev(
            storageRoot, staged, expected, materialWriteGate, ownerAdmission,
        )

        /**
         * F2B1: opens ONLY a generation whose pointer has already been selected,
         * with Commercial Evidence schema2 exact. This function cannot switch
         * generations, restore archives, or open a second SQLite connection.
         */
        internal fun openActiveHousehold10Evidence2DevForTrustedHost(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            expected: ActiveGenerationPointer,
            materialWriteGate: MaterialWriteGate,
            ownerAdmission: () -> Boolean,
        ): Household10DevReadSession = openActiveHousehold10Dev(
            storageRoot, staged, expected, materialWriteGate, ownerAdmission,
            commercialEvidence2ActiveDev = true,
        )

        fun openActiveHousehold10DevForApplyHost(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            expected: ActiveGenerationPointer,
            materialWriteGate: MaterialWriteGate,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            ownerAdmission: () -> Boolean,
        ): Household10DevApplySession = Household10DevApplySession(openActiveHousehold10Dev(
            storageRoot, staged, expected, materialWriteGate, ownerAdmission, planVerifier, writable = true,
        ))

        private fun openActiveHousehold10Dev(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            expected: ActiveGenerationPointer,
            materialWriteGate: MaterialWriteGate,
            ownerAdmission: () -> Boolean,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
            writable: Boolean = false,
            commercialEvidence2ActiveDev: Boolean = false,
        ): Household10DevReadSession = materialWriteGate.withMaterialWrite {
            check(expected.sequence > 0L && ownerAdmission()) { "Household10 DEV admission denied" }
            check(Files.isDirectory(storageRoot, NOFOLLOW_LINKS))
            val root = storageRoot.toRealPath()
            val channel = FileChannel.open(root.resolve("finance9-dev-root.lock"), CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                val lease = try {
                    checkNotNull(channel.tryLock()) { "DEV root lease is occupied" }
                } catch (occupied: OverlappingFileLockException) {
                    throw IllegalStateException("DEV root lease is occupied", occupied)
                }
                val pointer = FileActiveGenerationPointer(root)
                val fence = { lease.isValid && ownerAdmission() && pointer.read() == expected }
                check(fence()) { "Household10 DEV pointer changed" }
                val canonical = FileStorageGenerationLayout(root).requireStaged(expected.generationId)
                check(canonical.descriptor == staged.descriptor &&
                    canonical.directory == staged.directory.toAbsolutePath().normalize() &&
                    canonical.directory.toRealPath() == canonical.directory) {
                    "Household10 DEV requires the canonical generation"
                }
                val graph = openValidatedV2Internal(
                    canonical, planVerifier = planVerifier, materialWriteGate = materialWriteGate,
                    activeGeneration = true, planningStage8 = true, financeStage9 = true,
                    checkedPlanning8Active = true, auxiliaryReceiptFence = fence,
                    householdActive10Dev = true, householdNeedsWritableDev = writable,
                    commercialEvidence2ActiveDev = commercialEvidence2ActiveDev,
                )
                try {
                    check(graph.verifyHealthy() && fence()) { "Household10 DEV graph health or admission changed" }
                    Household10DevReadSession(graph, materialWriteGate, lease, fence)
                } catch (failure: Throwable) {
                    graph.close()
                    throw failure
                }
            } catch (failure: Throwable) {
                runCatching { channel.close() }
                throw failure
            }
        }

        /** Health projection of an already-selected DEV root while its owner holds the closed-host lease. */
        internal fun verifyReopenedHousehold10Dev(
            root: Path,
            staged: StagedStorageGeneration,
            expected: ActiveGenerationPointer,
            assertExclusiveClosedDevHost: () -> Unit,
        ): Boolean {
            assertExclusiveClosedDevHost()
            val pointer = FileActiveGenerationPointer(root)
            val canonical = FileStorageGenerationLayout(root).requireStaged(expected.generationId)
            check(canonical.descriptor == staged.descriptor &&
                canonical.directory == staged.directory.toAbsolutePath().normalize() &&
                pointer.read() == expected) { "Household10 DEV reopen pointer or descriptor changed" }
            requireSealedHousehold10ArchiveStage(canonical)
            val healthy = openValidatedV2Internal(
                canonical, planVerifier = null,
                planningStage8 = true, financeStage9 = true,
                auxiliaryReceiptFence = {
                    assertExclusiveClosedDevHost()
                    pointer.read() == expected
                },
                householdStage10 = true,
            ).use { graph ->
                graph.workspace != null && graph.verifyHealthy() &&
                    graph.reality.maintenance.inspect().schemaVersion == 10
            }
            assertExclusiveClosedDevHost()
            requireSealedHousehold10ArchiveStage(canonical)
            return healthy && pointer.read() == expected
        }

        /**
         * F2B2C: verifies an ALREADY selected Household10/Evidence2 DEV generation,
         * under the CALLER's existing root lease; no second file-lock or pointer CAS.
         * Isolated from the legacy Evidence1 archive health opener.
         */
        internal fun verifyReopenedHousehold10Evidence2Dev(
            root: Path,
            staged: StagedStorageGeneration,
            expected: ActiveGenerationPointer,
            materialWriteGate: MaterialWriteGate,
            assertExclusiveClosedDevHost: () -> Unit,
        ): Boolean {
            assertExclusiveClosedDevHost()
            val pointer = FileActiveGenerationPointer(root)
            val canonical = FileStorageGenerationLayout(root).requireStaged(expected.generationId)
            check(canonical.descriptor == staged.descriptor &&
                canonical.directory == staged.directory.toAbsolutePath().normalize() &&
                pointer.read() == expected) { "Typed Household10 DEV reopen pointer or descriptor changed" }
            requireSealedHousehold10Evidence2ArchiveStage(canonical)
            val fence = {
                assertExclusiveClosedDevHost()
                pointer.read() == expected
            }
            // F2B2C: maintenance must inspect a sealed, quiesced snapshot.
            // Reopening a live ACTIVE read-only Reality10 SQLite connection here
            // left a 0-byte reality.db-wal on close. Do NOT weaken the strict
            // NOFOLLOW sidecar guard, or reuse this opener for normal host reads.
            // The same immutable Household10 + typed FBS2v7 projection already
            // certified for STAGED archive capture is safe while the selected
            // generation is pinned by the exclusive closed-host lease.
            val healthy = materialWriteGate.withQuiescedMaterialWrites {
                check(fence())
                openValidatedV2Internal(
                    canonical, planVerifier = null,
                    planningStage8 = true, financeStage9 = true,
                    auxiliaryReceiptFence = fence, householdStage10 = true,
                    commercialEvidence2StagedDev = true,
                    commercialEvidence2AuthenticatedArchiveDev = true,
                ).use { graph ->
                    graph.workspace != null && graph.verifyHealthy() && fence() &&
                        graph.reality.maintenance.inspect().schemaVersion == 10 &&
                        graph.commercialEvidence.maintenance.inspect().schemaVersion == 2
                }
            }
            assertExclusiveClosedDevHost()
            requireSealedHousehold10Evidence2ArchiveStage(canonical)
            return healthy && pointer.read() == expected
        }

        fun openActiveHousehold12DevForPurchaseHost(
            storageRoot: Path,
            selected: ActiveGenerationPointer,
            gate: MaterialWriteGate,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            ownerAdmission: () -> Boolean,
        ): Household12PurchaseDevSession = openHousehold12DevForApplyHost(
            storageRoot, selected, gate, planVerifier, ownerAdmission, forStockReceive = false,
        )

        fun openActiveHousehold12DevForStockReceiveHost(
            storageRoot: Path,
            selected: ActiveGenerationPointer,
            gate: MaterialWriteGate,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            ownerAdmission: () -> Boolean,
        ): Household12StockReceiveDevSession = Household12StockReceiveDevSession(
            openHousehold12DevForApplyHost(storageRoot, selected, gate, planVerifier, ownerAdmission,
                forStockReceive = true),
        )

        private fun openHousehold12DevForApplyHost(
            storageRoot: Path,
            selected: ActiveGenerationPointer,
            gate: MaterialWriteGate,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            ownerAdmission: () -> Boolean,
            forStockReceive: Boolean,
        ): Household12PurchaseDevSession = gate.withMaterialWrite {
            check(ownerAdmission() && Files.isDirectory(storageRoot, NOFOLLOW_LINKS))
            val root = storageRoot.toRealPath()
            val lockPath = root.resolve("finance9-dev-root.lock")
            val channel = FileChannel.open(lockPath, CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                check((Files.getAttribute(lockPath, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                val lease = checkNotNull(channel.tryLock()) { "Household12 DEV root is already leased" }
                val fence = {
                    lease.isValid && ownerAdmission() &&
                        FaroV5Household12Evidence2DevJournaledCutover.hasCompletedSelection(root, selected)
                }
                check(fence()) { "Household12 DEV Apply needs completed SQL12 cutover" }
                val graph = openActiveHousehold12ForHeldDevHost(
                    FileStorageGenerationLayout(root).requireStaged(selected.generationId), gate, fence, planVerifier,
                    purchaseWritable = !forStockReceive, stockReceiveWritable = forStockReceive,
                )
                Household12PurchaseDevSession(graph, gate, channel)
            } catch (failure: Throwable) {
                channel.close()
                throw failure
            }
        }

        internal fun openActiveHousehold12ForHeldDevHost(
            selected: StagedStorageGeneration,
            materialWriteGate: MaterialWriteGate,
            liveFence: () -> Boolean,
            planVerifier: PreparedPlanEnvelopeVerificationPort?,
            purchaseWritable: Boolean = false,
            stockReceiveWritable: Boolean = false,
        ): FaroV5GenerationStoreGraph {
            check(liveFence()) { "Reality12 ACTIVE DEV fence is unavailable" }
            val graph = openValidatedV2Internal(
                selected, planVerifier = planVerifier, materialWriteGate = materialWriteGate,
                activeGeneration = true, planningStage8 = true, financeStage9 = true,
                checkedPlanning8Active = true, auxiliaryReceiptFence = liveFence,
                commercialEvidence2ActiveDev = true, householdActive12Dev = true,
                householdPurchaseWritableDev = purchaseWritable,
                householdStockReceiveWritableDev = stockReceiveWritable,
            )
            try {
                check(graph.verifyHealthy() && liveFence()) { "Reality12 ACTIVE DEV graph lost its admission" }
                return graph
            } catch (failure: Throwable) {
                graph.close()
                throw failure
            }
        }

        /** B6 G0: isolated SQL13 ACTIVE DEV only, root lock and completed Evidence2 journal. */
        fun openActiveHousehold13DevForUndoHost(
            storageRoot: Path,
            selected: ActiveGenerationPointer,
            gate: MaterialWriteGate,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            ownerAdmission: () -> Boolean,
        ): Household13StockUndoDevSession = gate.withMaterialWrite {
            check(ownerAdmission() && Files.isDirectory(storageRoot, NOFOLLOW_LINKS))
            val root = storageRoot.toRealPath()
            val lockFile = root.resolve("finance9-dev-root.lock")
            val channel = FileChannel.open(lockFile, CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                check((Files.getAttribute(lockFile, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                val lease = checkNotNull(channel.tryLock()) { "Household13 DEV root already leased" }
                val fence = {
                    lease.isValid && ownerAdmission() &&
                        FaroV5Household13Evidence2DevJournaledCutover.hasCompletedSelection(root, selected)
                }
                check(fence()) { "Household13 DEV requires COMPLETED Evidence2 cutover" }
                val graph = openActiveHousehold13ForHeldDevHost(
                    FileStorageGenerationLayout(root).requireStaged(selected.generationId),
                    gate, fence, planVerifier, compensationWritable = true,
                )
                Household13StockUndoDevSession(graph, gate, channel)
            } catch (failure: Throwable) {
                channel.close()
                throw failure
            }
        }

        /** SQL13 DEV defaults to read-only; writable flag only from isolated G0 factory. */
        internal fun openActiveHousehold13ForHeldDevHost(
            selected: StagedStorageGeneration,
            materialWriteGate: MaterialWriteGate,
            liveFence: () -> Boolean,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
            compensationWritable: Boolean = false,
        ): FaroV5GenerationStoreGraph {
            check(liveFence()) { "Reality13 ACTIVE DEV admission revoked" }
            val graph = openValidatedV2Internal(
                selected, planVerifier = planVerifier, materialWriteGate = materialWriteGate,
                activeGeneration = true, planningStage8 = true, financeStage9 = true,
                checkedPlanning8Active = true, auxiliaryReceiptFence = liveFence,
                commercialEvidence2ActiveDev = true, householdActive13Dev = true,
                householdStockCompensationWritableDev = compensationWritable,
            )
            try {
                check(graph.verifyHealthy() && liveFence()) { "Reality13 ACTIVE DEV graph lost admission" }
                return graph
            } catch (failure: Throwable) {
                graph.close()
                throw failure
            }
        }

        /** SQL14 ACTIVE DEV only. Deny without exact completed cutover and exclusive root lease. */
        fun openActiveHousehold14DevForReservationHost(
            storageRoot: Path,
            selected: ActiveGenerationPointer,
            gate: MaterialWriteGate,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            ownerAdmission: () -> Boolean,
        ): Household14ReservationDevSession = gate.withMaterialWrite {
            check(ownerAdmission() && Files.isDirectory(storageRoot, NOFOLLOW_LINKS))
            val root = storageRoot.toRealPath()
            val lockFile = root.resolve("finance9-dev-root.lock")
            val channel = FileChannel.open(lockFile, CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                check((Files.getAttribute(lockFile, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                val lease = try {
                    checkNotNull(channel.tryLock()) { "SQL14 DEV root already leased" }
                } catch (occupied: OverlappingFileLockException) {
                    throw IllegalStateException("SQL14 DEV root already leased", occupied)
                }
                val fence = {
                    lease.isValid && ownerAdmission() &&
                        FaroV5Household14Evidence2DevJournaledCutover.hasCompletedSelection(root, selected)
                }
                check(fence()) { "SQL14 reservation DEV requires COMPLETED cutover" }
                val staged = FileStorageGenerationLayout(root).requireStaged(selected.generationId)
                requireActiveHousehold14ReadProfile(staged)
                val graph = openValidatedV2Internal(
                    staged, planVerifier = planVerifier, materialWriteGate = gate,
                    activeGeneration = true, planningStage8 = true, financeStage9 = true,
                    checkedPlanning8Active = true, auxiliaryReceiptFence = fence,
                    commercialEvidence2ActiveDev = true, householdActive14Dev = true,
                    householdStockReservationWritableDev = true,
                )
                try {
                    check(graph.verifyHealthy() && fence())
                    Household14ReservationDevSession(graph, gate, channel)
                } catch (failure: Throwable) {
                    graph.close()
                    throw failure
                }
            } catch (failure: Throwable) {
                channel.close()
                throw failure
            }
        }

        internal fun requireActiveHousehold14ReadProfile(staged: StagedStorageGeneration) {
            check(ExistingGenerationFormatReader.requireAndRead(staged) == GenerationStoreFormat.WORKSPACE_V2)
            val directory = staged.directory.toAbsolutePath().normalize()
            check(directory.toRealPath() == directory)
            for (name in GenerationStoreFiles.WORKSPACE_V2) {
                val file = directory.resolve(name)
                check(Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                    (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
            }
            requireStrictV2StoreSchemas(
                directory, activeGeneration = true, planningStage8 = true, financeStage9 = true,
                auxiliary4 = true, commercialEvidence2ActiveDev = true, householdStage14 = true,
            )
        }

        /**
         * A2C1C: only a genuine SQL15 journal COMPLETED selection can open Apply.
         * The root lease, five-store schema, owner fence and DEV flag are mandatory.
         */
        fun openActiveHousehold15DevForConsumptionHost(
            storageRoot: Path,
            selected: ActiveGenerationPointer,
            gate: MaterialWriteGate,
            planVerifier: PreparedPlanEnvelopeVerificationPort,
            ownerAdmission: () -> Boolean,
        ): Household15ConsumptionDevSession = gate.withMaterialWrite {
            check(selected.sequence > 0L && ownerAdmission() &&
                Files.isDirectory(storageRoot, NOFOLLOW_LINKS))
            val root = storageRoot.toRealPath()
            val lockFile = root.resolve("finance9-dev-root.lock")
            val channel = FileChannel.open(lockFile, CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                check((Files.getAttribute(lockFile, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                val lease = try {
                    checkNotNull(channel.tryLock()) { "SQL15 DEV root already leased" }
                } catch (occupied: OverlappingFileLockException) {
                    throw IllegalStateException("SQL15 DEV root already leased", occupied)
                }
                val pointer = FileActiveGenerationPointer(root)
                val fence: () -> Boolean = {
                    lease.isValid && ownerAdmission() && pointer.read() == selected &&
                        FaroV5Household15Evidence2DevJournaledCutover.hasCompletedSelection(root, selected)
                }
                check(fence()) { "SQL15 consumption DEV requires real COMPLETED ticket" }
                val staged = FileStorageGenerationLayout(root).requireStaged(selected.generationId)
                check(ExistingGenerationFormatReader.requireAndRead(staged) ==
                    GenerationStoreFormat.WORKSPACE_V2)
                val dir = staged.directory.toAbsolutePath().normalize()
                check(dir.toRealPath() == dir && dir.parent == root.resolve("generations"))
                for (name in GenerationStoreFiles.WORKSPACE_V2) {
                    val file = dir.resolve(name)
                    check(Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                        (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                }
                requireStrictV2StoreSchemas(dir, activeGeneration = true, planningStage8 = true,
                    financeStage9 = true, auxiliary4 = true, commercialEvidence2ActiveDev = true,
                    householdActive15Dev = true)
                val graph = openValidatedV2Internal(staged, planVerifier = planVerifier,
                    materialWriteGate = gate, activeGeneration = true, planningStage8 = true,
                    financeStage9 = true, checkedPlanning8Active = true,
                    auxiliaryReceiptFence = fence, commercialEvidence2ActiveDev = true,
                    householdActive15Dev = true, householdStockConsumptionWritableDev = true)
                try {
                    check(graph.verifyHealthy() && fence())
                    Household15ConsumptionDevSession(graph, gate, channel)
                } catch (failure: Throwable) {
                    graph.close()
                    throw failure
                }
            } catch (failure: Throwable) {
                channel.close()
                throw failure
            }
        }

        /** SQL15 SEALED five-store semantic capture only: no ACTIVE/Apply admission. */
        internal fun openValidatedHousehold15ArchiveStage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
            evidence2: Boolean,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
        ): FaroV5GenerationStoreGraph = openFinance9ArchiveStage(
            staged, expectedActive, auxiliary4 = true, planVerifier = planVerifier,
            commercialEvidence2StagedDev = evidence2, householdStage15 = true,
        )


        /**
         * C1B isolated DEV fixtures: WAL-aware, one-shot five-store preflight.
         * A2C0C2 must independently certify real journaled completed selection.
         * No graph, SQLite connection or Apply/stock port escapes this scope.
         */
        internal fun inspectActiveHousehold15DevReadOnly(
            storageRoot: Path,
            selected: ActiveGenerationPointer,
            materialWriteGate: MaterialWriteGate,
            ownerAdmission: () -> Boolean,
            completedDevSelection: () -> Boolean,
        ): RealityHousehold15ConsumptionRows = materialWriteGate.withMaterialWrite {
            check(selected.sequence > 0L && Files.isDirectory(storageRoot, NOFOLLOW_LINKS)) {
                "SQL15 ACTIVE DEV requires a canonical root and full pointer"
            }
            val root = storageRoot.toRealPath()
            val lockPath = root.resolve("finance9-dev-root.lock")
            val channel = FileChannel.open(lockPath, CREATE, WRITE, NOFOLLOW_LINKS)
            try {
                check((Files.getAttribute(lockPath, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                val lease = try {
                    checkNotNull(channel.tryLock()) { "SQL15 ACTIVE DEV root is already leased" }
                } catch (occupied: OverlappingFileLockException) {
                    throw IllegalStateException("SQL15 ACTIVE DEV root is already leased", occupied)
                }
                val pointer = FileActiveGenerationPointer(root)
                val fence: () -> Boolean = {
                    lease.isValid && ownerAdmission() && completedDevSelection() &&
                        pointer.read() == selected
                }
                check(fence()) { "SQL15 ACTIVE DEV selection/owner admission absent" }
                val canonical = FileStorageGenerationLayout(root).requireStaged(selected.generationId)
                val directory = canonical.directory.toAbsolutePath().normalize()
                check(directory.parent == root.resolve("generations") &&
                    directory.toRealPath() == directory &&
                    ExistingGenerationFormatReader.requireAndRead(canonical) ==
                        GenerationStoreFormat.WORKSPACE_V2) {
                    "SQL15 ACTIVE DEV generation format/path is not canonical"
                }
                for (name in GenerationStoreFiles.WORKSPACE_V2) {
                    val file = directory.resolve(name)
                    check(Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                        (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L) {
                        "SQL15 ACTIVE DEV cannot follow store links"
                    }
                }
                // NEVER immutable=1 on ACTIVE: committed frames may be in WAL.
                requireStrictV2StoreSchemas(
                    directory, activeGeneration = true, planningStage8 = true,
                    financeStage9 = true, auxiliary4 = true,
                    commercialEvidence2ActiveDev = true, householdActive15Dev = true,
                )
                check(fence()) { "SQL15 ACTIVE DEV selection changed during preflight" }
                RealityStore(
                    directory.resolve(GenerationStoreFiles.REALITY).toString(),
                    planningStage8 = true, materialWriteGate = materialWriteGate,
                    householdActive15DevReadOnly = fence,
                ).use { reality ->
                    val snapshot = reality.snapshotActiveHousehold15RowsForTrustedHost()
                    check(fence() && reality.maintenance.inspect().schemaVersion == 15) {
                        "SQL15 ACTIVE DEV read or selection changed"
                    }
                    snapshot
                }
            } finally {
                channel.close()
            }
        }

        internal fun openValidatedHousehold14ArchiveStage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
            evidence2: Boolean,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
        ): FaroV5GenerationStoreGraph = openFinance9ArchiveStage(
            staged, expectedActive, auxiliary4 = true, planVerifier = planVerifier,
            commercialEvidence2StagedDev = evidence2, householdStage14 = true,
        )

        internal fun openValidatedHousehold13ArchiveStage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
            evidence2: Boolean,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
        ): FaroV5GenerationStoreGraph = openFinance9ArchiveStage(
            staged, expectedActive, auxiliary4 = true, planVerifier = planVerifier,
            commercialEvidence2StagedDev = evidence2, householdStage13 = true,
        )

        internal fun openValidatedHousehold12ArchiveStage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
            evidence2: Boolean,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
        ): FaroV5GenerationStoreGraph = openFinance9ArchiveStage(
            staged, expectedActive, auxiliary4 = true, planVerifier = planVerifier,
            commercialEvidence2StagedDev = evidence2, householdStage12 = true,
        )

        internal fun requireSealedHousehold15ArchiveStage(staged: StagedStorageGeneration, evidence2: Boolean) =
            requireSealedHouseholdArchiveStage(staged, evidence2, householdStage15 = true)

        internal fun requireSealedHousehold13ArchiveStage(staged: StagedStorageGeneration, evidence2: Boolean) =
            requireSealedHouseholdArchiveStage(staged, evidence2, householdStage13 = true)

        internal fun requireSealedHousehold12ArchiveStage(staged: StagedStorageGeneration, evidence2: Boolean) =
            requireSealedHouseholdArchiveStage(staged, evidence2, householdStage12 = true)

        internal fun requireSealedHousehold10ArchiveStage(staged: StagedStorageGeneration) =
            requireSealedHouseholdArchiveStage(staged, typedEvidence2 = false)

        /**
         * F2B2A: independently sealed Household10/Evidence2 STAGED format check.
         * Never changes a pointer, admits an ACTIVE session or reuses FBS2v6.
         */
        internal fun requireSealedHousehold10Evidence2ArchiveStage(staged: StagedStorageGeneration) =
            requireSealedHouseholdArchiveStage(staged, typedEvidence2 = true)

        private fun requireSealedHouseholdArchiveStage(
            staged: StagedStorageGeneration,
            typedEvidence2: Boolean,
            householdStage12: Boolean = false,
            householdStage13: Boolean = false,
            householdStage14: Boolean = false,
            householdStage15: Boolean = false,
        ) {
            check(listOf(householdStage12, householdStage13, householdStage14, householdStage15).count { it } <= 1)
            check(ExistingGenerationFormatReader.requireAndRead(staged) == GenerationStoreFormat.WORKSPACE_V2)
            val directory = staged.directory.toAbsolutePath().normalize()
            check(directory.toRealPath() == directory)
            for (name in GenerationStoreFiles.WORKSPACE_V2) {
                val file = directory.resolve(name)
                check(Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                    (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                val header = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(20) }
                check(header.size == 20 && header[18] == 2.toByte() && header[19] == 2.toByte()) {
                    "Household10 DEV requires sealed WAL-mode stores"
                }
                for (suffix in listOf("-wal", "-shm", "-journal")) {
                    val companion = directory.resolve(name + suffix)
                    // F2B2C RED_FIX3: observation ONLY. The original fail-closed
                    // condition remains byte-for-byte identical in semantics.
                    check(Files.notExists(companion, NOFOLLOW_LINKS)) {
                        "Household10 sealed sidecar rejected: " + name + suffix +
                            " existsNoFollow=" + Files.exists(companion, NOFOLLOW_LINKS) +
                            " symbolicLink=" + Files.isSymbolicLink(companion) +
                            " regularNoFollow=" + Files.isRegularFile(companion, NOFOLLOW_LINKS) +
                            " size=" + (if (Files.isRegularFile(companion, NOFOLLOW_LINKS))
                                runCatching { Files.size(companion) }.getOrDefault(-1L) else -1L)
                    }
                }
            }
            requireStrictV2StoreSchemas(
                directory, activeGeneration = false, planningStage8 = true,
                financeStage9 = true, auxiliary4 = true, householdStage10 = !householdStage12 && !householdStage13 && !householdStage14 && !householdStage15,
                commercialEvidence2StagedDev = typedEvidence2,
                householdStage12 = householdStage12, householdStage13 = householdStage13,
                householdStage14 = householdStage14, householdStage15 = householdStage15,
            )
            val reality = directory.resolve(GenerationStoreFiles.REALITY)
            if (householdStage15) {
                RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(reality) {}
                return
            }
            if (householdStage14) return // The read-only Reality14 opener validates its ledger.
            if (householdStage13) RealityHousehold13CompensationRowsPort.captureTrustedSealedStageThirteen(reality)
            else if (householdStage12) RealityHousehold12PurchaseRowsPort.readTrustedSealedStageTwelve(reality)
            else RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(reality)
        }

        internal fun requireFinance9ReceiptArchiveStage(
            staged: StagedStorageGeneration,
            allowAuxiliary3: Boolean,
        ) {
            check(ExistingGenerationFormatReader.requireAndRead(staged) == GenerationStoreFormat.WORKSPACE_V2)
            requireStrictV2StoreSchemas(
                staged.directory, activeGeneration = false, planningStage8 = true,
                financeStage9 = true, auxiliary4 = true, allowAuxiliary3 = allowAuxiliary3,
            )
        }

        internal fun requireFinance9ReceiptEvidence2ArchiveStage(
            staged: StagedStorageGeneration,
            allowAuxiliary3: Boolean,
        ) {
            check(ExistingGenerationFormatReader.requireAndRead(staged) == GenerationStoreFormat.WORKSPACE_V2)
            requireStrictV2StoreSchemas(
                staged.directory, activeGeneration = false, planningStage8 = true,
                financeStage9 = true, auxiliary4 = true, allowAuxiliary3 = allowAuxiliary3,
                commercialEvidence2StagedDev = true,
            )
        }

        private fun openFinance9ArchiveStage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
            auxiliary4: Boolean,
            householdStage10: Boolean = false,
            planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
            commercialEvidence2StagedDev: Boolean = false,
            householdStage12: Boolean = false,
            householdStage13: Boolean = false,
            householdStage14: Boolean = false,
            householdStage15: Boolean = false,
        ): FaroV5GenerationStoreGraph {
            check(listOf(householdStage12, householdStage13, householdStage14, householdStage15).count { it } <= 1)
            val generationsDir = staged.directory.toAbsolutePath().normalize().parent
            check(generationsDir.fileName.toString() == "generations") {
                "Finance9 requires canonical staged generation directory"
            }
            val root = generationsDir.parent
            val canonical = FileStorageGenerationLayout(root)
                .requireStaged(staged.descriptor.generationId)
            check(canonical.descriptor == staged.descriptor &&
                canonical.directory.toAbsolutePath().normalize() ==
                    staged.directory.toAbsolutePath().normalize()
            ) { "Finance9 staged graph descriptor/path mismatch" }
            val pointer = FileActiveGenerationPointer(root)
            check(pointer.read() == expectedActive &&
                expectedActive.generationId != staged.descriptor.generationId) {
                "Finance9 staged graph requires nonactive target and pinned pointer"
            }
            if (householdStage10) {
                if (commercialEvidence2StagedDev) requireSealedHousehold10Evidence2ArchiveStage(staged)
                else requireSealedHousehold10ArchiveStage(staged)
            }
            if (householdStage12) requireSealedHousehold12ArchiveStage(staged, commercialEvidence2StagedDev)
            if (householdStage13) requireSealedHousehold13ArchiveStage(staged, commercialEvidence2StagedDev)
            if (householdStage14) {
                requireSealedHouseholdArchiveStage(staged, commercialEvidence2StagedDev, householdStage14 = true)
            }
            if (householdStage15) {
                requireSealedHousehold15ArchiveStage(staged, commercialEvidence2StagedDev)
            }
            val receiptFence: (() -> Boolean)? =
                if (auxiliary4) ({ pointer.read() == expectedActive }) else null
            val graph = openValidatedV2Internal(
                staged, planVerifier = planVerifier,
                planningStage8 = true, financeStage9 = true, auxiliaryReceiptFence = receiptFence,
                householdStage10 = householdStage10,
                commercialEvidence2StagedDev = commercialEvidence2StagedDev,
                commercialEvidence2AuthenticatedArchiveDev = commercialEvidence2StagedDev,
                householdStage12 = householdStage12,
                householdStage13 = householdStage13,
                householdStage14 = householdStage14, householdStage15 = householdStage15,
            )
            try {
                check(pointer.read() == expectedActive) {
                    "Finance9 pointer changed during graph opening"
                }
                return graph
            } catch (failure: Throwable) {
                runCatching { graph.close() }
                throw failure
            }
        }

        private fun openValidatedV2Internal(
            staged: StagedStorageGeneration,
            planVerifier: PreparedPlanEnvelopeVerificationPort?,
            materialWriteGate: MaterialWriteGate? = null,
            activeGeneration: Boolean = false,
            planningStage8: Boolean = false,
            financeStage9: Boolean = false,
            checkedPlanning8Active: Boolean = false,
            auxiliaryReceiptFence: (() -> Boolean)? = null,
            householdStage10: Boolean = false,
            householdActive10Dev: Boolean = false,
            householdNeedsWritableDev: Boolean = false,
            commercialEvidence2StagedDev: Boolean = false,
            commercialEvidence2ActiveDev: Boolean = false,
            commercialEvidence2AuthenticatedArchiveDev: Boolean = false,
            householdStage12: Boolean = false,
            householdStage13: Boolean = false,
            householdStage14: Boolean = false,
            householdStage15: Boolean = false,
            householdActive13Dev: Boolean = false,
            householdActive12Dev: Boolean = false,
            householdPurchaseWritableDev: Boolean = false,
            householdStockReceiveWritableDev: Boolean = false,
            householdStockCompensationWritableDev: Boolean = false,
            householdActive14Dev: Boolean = false,
            householdStockReservationWritableDev: Boolean = false,
            householdActive15Dev: Boolean = false,
            householdStockConsumptionWritableDev: Boolean = false,
        ): FaroV5GenerationStoreGraph {
            check(!(householdPurchaseWritableDev && householdStockReceiveWritableDev))
            check(!householdStockConsumptionWritableDev ||
                (householdActive15Dev && planVerifier != null && !householdActive14Dev &&
                    !householdActive13Dev && !householdActive12Dev && !householdPurchaseWritableDev &&
                    !householdStockReceiveWritableDev && !householdStockCompensationWritableDev &&
                    !householdStockReservationWritableDev))
            check(!householdStockReservationWritableDev ||
                (householdActive14Dev && planVerifier != null && !householdActive13Dev &&
                    !householdStockCompensationWritableDev && !householdActive12Dev &&
                    !householdPurchaseWritableDev && !householdStockReceiveWritableDev))
            check(!householdStockCompensationWritableDev ||
                (householdActive13Dev && planVerifier != null && !householdActive12Dev &&
                    !householdPurchaseWritableDev && !householdStockReceiveWritableDev))
            check(!(householdPurchaseWritableDev || householdStockReceiveWritableDev) ||
                (householdActive12Dev && planVerifier != null))
            check(!householdActive12Dev || (activeGeneration && !householdStage12 && !householdStage10 &&
                !householdActive10Dev && !householdNeedsWritableDev && financeStage9 && checkedPlanning8Active &&
                auxiliaryReceiptFence != null && materialWriteGate != null && commercialEvidence2ActiveDev))
            check(!householdActive13Dev || (
                activeGeneration && !householdStage13 && !householdStage12 && !householdStage10 &&
                !householdActive12Dev && !householdActive10Dev && !householdNeedsWritableDev &&
                financeStage9 && checkedPlanning8Active && auxiliaryReceiptFence != null &&
                materialWriteGate != null && commercialEvidence2ActiveDev
            )) { "Reality13 ACTIVE DEV requires isolated read-only authority" }
            check(!householdActive14Dev || (activeGeneration && !householdStage14 && !householdStage13 &&
                !householdStage12 && !householdStage10 && !householdActive13Dev &&
                !householdActive12Dev && !householdActive10Dev && !householdNeedsWritableDev &&
                financeStage9 && checkedPlanning8Active && auxiliaryReceiptFence != null &&
                materialWriteGate != null && commercialEvidence2ActiveDev)) {
                "Reality14 ACTIVE DEV requires distinct trusted reservation admission"
            }
            check(!householdActive15Dev || (activeGeneration && !householdStage15 && !householdStage14 &&
                !householdStage13 && !householdStage12 && !householdStage10 && !householdActive14Dev &&
                !householdActive13Dev && !householdActive12Dev && !householdActive10Dev &&
                !householdNeedsWritableDev && !householdStockReservationWritableDev &&
                financeStage9 && checkedPlanning8Active && auxiliaryReceiptFence != null &&
                materialWriteGate != null && commercialEvidence2ActiveDev)) {
                "SQL15 ACTIVE DEV requires distinct authenticated consumption admission"
            }
            check(!householdStage14 || (!activeGeneration && !householdActive14Dev && !householdActive13Dev && !householdActive12Dev &&
                !householdStage13 && !householdStage12 && !householdStage10 && !householdActive10Dev &&
                !householdStage15 && financeStage9 && auxiliaryReceiptFence != null && materialWriteGate == null))
            check(!householdStage15 || (!activeGeneration && !householdActive14Dev && !householdActive13Dev &&
                !householdActive12Dev && !householdStage14 && !householdStage13 &&
                !householdStage12 && !householdStage10 && !householdActive10Dev &&
                !householdNeedsWritableDev && !householdPurchaseWritableDev &&
                !householdStockReceiveWritableDev && !householdStockCompensationWritableDev &&
                !householdStockReservationWritableDev && financeStage9 && auxiliaryReceiptFence != null &&
                materialWriteGate == null)) { "SQL15 staged semantic graph must be isolated and read-only" }
            check(!householdStage13 || (!activeGeneration && !householdActive13Dev &&
                !householdStage12 && !householdStage10 &&
                !householdActive10Dev && financeStage9 && auxiliaryReceiptFence != null && materialWriteGate == null))
            check(!householdStage12 || (!activeGeneration && !householdStage13 && !householdStage10 && !householdActive10Dev &&
                financeStage9 && auxiliaryReceiptFence != null && materialWriteGate == null))
            // Historical C1b2G isolated STAGED owner remains unchanged.
            val isolatedTypedStage = !activeGeneration && !planningStage8 && !financeStage9 &&
                !checkedPlanning8Active && auxiliaryReceiptFence == null &&
                !householdStage10 && !householdActive10Dev &&
                !householdNeedsWritableDev && materialWriteGate != null
            // F2B2B02 is an independently sealed, exact-pointer, closed-host
            // STAGED archive replay. This is not an ACTIVE or general owner grant.
            val authenticatedArchiveStage = commercialEvidence2AuthenticatedArchiveDev &&
                commercialEvidence2StagedDev && !activeGeneration && planningStage8 &&
                financeStage9 && !checkedPlanning8Active && auxiliaryReceiptFence != null &&
                !householdActive10Dev && !householdNeedsWritableDev && materialWriteGate == null
            check(!commercialEvidence2StagedDev ||
                (if (commercialEvidence2AuthenticatedArchiveDev) authenticatedArchiveStage
                 else isolatedTypedStage)
            ) { "Evidence2 staged graph cannot be active or share privileged owner stage" }
            check(!commercialEvidence2AuthenticatedArchiveDev || authenticatedArchiveStage) {
                "Typed Evidence2 archive admission requires a sealed nonactive Finance9 DEV stage"
            }
            check(!commercialEvidence2ActiveDev || (
                activeGeneration && (householdActive10Dev || householdActive12Dev || householdActive13Dev || householdActive14Dev || householdActive15Dev) && financeStage9 &&
                    checkedPlanning8Active && auxiliaryReceiptFence != null &&
                    materialWriteGate != null && !commercialEvidence2StagedDev &&
                    !householdNeedsWritableDev
            )) { "Evidence2 ACTIVE requires isolated Household10 DEV trusted read-only session" }
            check(!householdNeedsWritableDev || (householdActive10Dev && planVerifier != null))
            check(!householdActive10Dev || (activeGeneration && !householdStage10 &&
                financeStage9 && auxiliaryReceiptFence != null && materialWriteGate != null))
            check(!householdStage10 || (financeStage9 && !activeGeneration && auxiliaryReceiptFence != null))
            check(auxiliaryReceiptFence == null || (financeStage9 && auxiliaryReceiptFence()))
            check(!planningStage8 || !activeGeneration || checkedPlanning8Active) {
                "Planning8 active open requires exact internal pointer/reopen admission"
            }
            check(!financeStage9 || (planningStage8 && activeGeneration == checkedPlanning8Active)) {
                "Finance9 ACTIVE requires explicit exact-pointer admission"
            }
            check(!checkedPlanning8Active || (planningStage8 && activeGeneration)) {
                "Planning8 active admission cannot be used in another generation path"
            }
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
            requireStrictV2StoreSchemas(
                normalized, activeGeneration, planningStage8, financeStage9,
                auxiliary4 = auxiliaryReceiptFence != null,
                householdStage10 = householdStage10 || householdActive10Dev,
                commercialEvidence2StagedDev = commercialEvidence2StagedDev,
                commercialEvidence2ActiveDev = commercialEvidence2ActiveDev,
                householdStage12 = householdStage12 || householdActive12Dev,
                householdStage13 = householdStage13 || householdActive13Dev,
                householdStage14 = householdStage14 || householdActive14Dev,
                householdStage15 = householdStage15, householdActive15Dev = householdActive15Dev,
            )
            if (householdActive10Dev || householdActive12Dev || householdActive13Dev || householdActive14Dev || householdActive15Dev) {
                val fence = checkNotNull(auxiliaryReceiptFence)
                check(fence())
                for (name in GenerationStoreFiles.WORKSPACE_V2) {
                    val file = normalized.resolve(name)
                    check((Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                }
                val reality = normalized.resolve(GenerationStoreFiles.REALITY)
                if (householdActive15Dev) {
                    RealityStore(reality.toString(), planningStage8 = true,
                        materialWriteGate = materialWriteGate,
                        householdActive15DevReadOnly = fence).use {
                        it.snapshotActiveHousehold15RowsForTrustedHost()
                    }
                } else if (householdActive14Dev) {
                    RealityStore(reality.toString(), planningStage8 = true,
                        materialWriteGate = materialWriteGate,
                        householdActive14DevReadOnly = fence).use {
                        it.snapshotActiveHousehold14RowsForTrustedHost()
                    }
                } else if (householdActive13Dev) RealityStore.requireHealthyHousehold13ActiveDev(reality)
                else if (householdActive12Dev) RealityStore.requireHealthyHousehold12ActiveDev(reality)
                else RealityStore.requireHealthyHousehold10ActiveDev(reality)
                check(fence())
            }
            if (financeStage9 && !activeGeneration) {
                val reality = normalized.resolve(GenerationStoreFiles.REALITY)
                when {
                    householdStage15 -> RealityFinance9SealedReadPort.readTrustedSealedStage15(reality)
                    householdStage14 -> RealityFinance9SealedReadPort.readTrustedSealedStage14(reality)
                    householdStage13 -> RealityFinance9SealedReadPort.readTrustedSealedStage13(reality)
                    householdStage12 -> RealityFinance9SealedReadPort.readTrustedSealedStage12(reality)
                    householdStage10 -> RealityFinance9SealedReadPort.readTrustedSealedStage10(reality)
                    else -> RealityFinance9SealedReadPort.readTrustedSealedStage9(reality)
                }
            }
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
                return openStores(
                    normalized, staged.descriptor.generationId.value, workspace,
                    materialWriteGate, planningStage8,
                    financeStage9 = financeStage9 && !activeGeneration && !householdStage10 &&
                        !householdStage12 && !householdStage13 && !householdStage14 && !householdStage15,
                    financeActive9 = financeStage9 && activeGeneration && !householdActive10Dev && !householdActive12Dev && !householdActive13Dev && !householdActive14Dev && !householdActive15Dev,
                    auxiliaryReceiptFence = auxiliaryReceiptFence,
                    householdStage10 = householdStage10,
                    householdActive10Dev = householdActive10Dev,
                    householdNeedsWritableDev = householdNeedsWritableDev,
                    commercialEvidence2StagedDev = commercialEvidence2StagedDev,
                    commercialEvidence2ActiveDev = commercialEvidence2ActiveDev,
                    householdStage12 = householdStage12,
                    householdStage13 = householdStage13,
                    householdStage14 = householdStage14, householdStage15 = householdStage15,
                    householdActive13Dev = householdActive13Dev,
                    householdActive12Dev = householdActive12Dev,
                    householdPurchaseWritableDev = householdPurchaseWritableDev,
                    householdStockReceiveWritableDev = householdStockReceiveWritableDev,
                    householdStockCompensationWritableDev = householdStockCompensationWritableDev,
                    householdActive14Dev = householdActive14Dev,
                    householdStockReservationWritableDev = householdStockReservationWritableDev,
                    householdActive15Dev = householdActive15Dev,
                    householdStockConsumptionWritableDev = householdStockConsumptionWritableDev,
                )
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
        private fun requireStrictV2StoreSchemas(
            directory: Path,
            activeGeneration: Boolean,
            planningStage8: Boolean = false,
            financeStage9: Boolean = false,
            exactAuxiliary: Boolean = activeGeneration,
            auxiliary4: Boolean = false,
            allowAuxiliary3: Boolean = false,
            householdStage10: Boolean = false,
            commercialEvidence2StagedDev: Boolean = false,
            commercialEvidence2ActiveDev: Boolean = false,
            householdStage12: Boolean = false,
            householdStage13: Boolean = false,
            householdStage14: Boolean = false,
            householdStage15: Boolean = false,
            householdActive15Dev: Boolean = false,
        ) {
            check(!householdStage15 || !activeGeneration) {
                "SQL15 full semantic stage profile must never be ACTIVE"
            }
            check(!commercialEvidence2ActiveDev ||
                (activeGeneration && (householdStage10 || householdStage12 || householdStage13 || householdStage14 || householdActive15Dev))) {
                "Typed Evidence2 ACTIVE schema requires explicit Household DEV admission"
            }
            check(!commercialEvidence2StagedDev || !activeGeneration) {
                "Commercial Evidence2 must not enter ACTIVE strict preflight"
            }
            // Reality schema7 adds nullable, durable receipt commit time.
            // The only admitted older layouts are exact schema6 (full domains)
            // and schema5 (historical base); RealityStore owns migration after
            // staged/active READ-ONLY integrity and schema admission.
            val realityCurrentTables = listOf(
                "profile_state", "apply_attempt", "execution_receipt",
                "reality_outbox", "finance_state", "finance_account",
                "finance_account_opening", "finance_entry",
                "objective_state", "objective_record",
                "matter_state", "matter_record",
            )
            val historicalBase = listOf(
                "profile_state", "apply_attempt", "execution_receipt",
                "reality_outbox", "finance_state", "finance_account",
                "finance_account_opening", "finance_entry",
            )
            val realityProfile = if (financeStage9) {
                check(planningStage8) { "Finance9 requires Planning owner schema" }
                StagedSqlitePreflight.Schema(
                    GenerationStoreFiles.REALITY, when {
                        householdStage15 || householdActive15Dev -> 15
                        householdStage14 -> 14
                        householdStage13 -> 13
                        householdStage12 -> 12
                        householdStage10 -> 10
                        else -> 9
                    },
                    realityCurrentTables + listOf(
                        "reminder_state", "reminder_rule", "reminder_occurrence",
                        "monthly_plan_state", "monthly_plan_item",
                        "finance_acl_state", "finance_acl_policy", "finance_apply_grant",
                        "finance_opening_confirmation", "finance_transfer",
                        "finance_debt", "finance_schedule", "finance_plan_item",
                        "finance_plan_item_payment", "finance_budget_limit",
                        "finance_schema_migration_audit",
                    ) + when {
                        householdStage15 || householdActive15Dev || householdStage14 || householdStage13 ->
                            listOf("household_need_state", "household_need",
                                "household_stock_receipt_state", "household_stock_receipt",
                                "household_confirmed_purchase", "household_stock_compensation") +
                                (if (householdStage14 || householdStage15 || householdActive15Dev) listOf("household_stock_reservation_event")
                                 else emptyList()) +
                                (if (householdStage15 || householdActive15Dev) listOf("household_stock_consumption_event")
                                 else emptyList())
                        householdStage12 -> listOf("household_need_state", "household_need",
                            "household_stock_receipt_state", "household_stock_receipt", "household_confirmed_purchase")
                        householdStage10 -> listOf("household_need_state", "household_need")
                        else -> emptyList()
                    },
                )
            } else if (planningStage8) {
                val required = realityCurrentTables + listOf(
                    "reminder_state", "reminder_rule", "reminder_occurrence",
                    "monthly_plan_state", "monthly_plan_item",
                )
                if (activeGeneration) {
                    // N033C1: absolutely NO Reality7->8 migration on ACTIVE.
                    // Reject version 5/6/7 before any writable RealityStore opens.
                    StagedSqlitePreflight.Schema(GenerationStoreFiles.REALITY, 8, required)
                } else {
                    // Keep the existing isolated STAGED restore/migration semantics.
                    StagedSqlitePreflight.Schema(
                        GenerationStoreFiles.REALITY, 8, required,
                        legacyVersion = 7, legacyTables = realityCurrentTables,
                        olderLegacyVersion = 6, olderLegacyTables = realityCurrentTables,
                        oldestLegacyVersion = 5, oldestLegacyTables = historicalBase,
                    )
                }
            } else {
                StagedSqlitePreflight.Schema(
                    GenerationStoreFiles.REALITY, 7, realityCurrentTables,
                    legacyVersion = 6, legacyTables = realityCurrentTables,
                    olderLegacyVersion = 5, olderLegacyTables = historicalBase,
                )
            }
            val auxiliaryTables = listOf(
                "delivery_notice", "delivery_intent", "bookmark_scope",
                "bookmark_folder", "bookmark_item", "bookmark_receipt",
            )
            val auxiliarySchema = if (auxiliary4) {
                StagedSqlitePreflight.Schema(
                    GenerationStoreFiles.AUXILIARY, 4, auxiliaryTables + "notice_attention_receipt",
                    legacyVersion = if (allowAuxiliary3) 3 else null,
                    legacyTables = if (allowAuxiliary3) auxiliaryTables else emptyList(),
                )
            } else {
                StagedSqlitePreflight.Schema(
                    GenerationStoreFiles.AUXILIARY, 3, auxiliaryTables,
                    legacyVersion = if (exactAuxiliary) null else 2,
                    legacyTables = if (exactAuxiliary) emptyList() else
                        listOf("delivery_notice", "delivery_intent"),
                )
            }
            val schemas = listOf(
                realityProfile,
                if (commercialEvidence2StagedDev || commercialEvidence2ActiveDev) StagedSqlitePreflight.Schema(
                    GenerationStoreFiles.COMMERCIAL_EVIDENCE, 2,
                    listOf("commercial_evidence", "commercial_evidence_quote_terms"),
                    rejectTriggers = commercialEvidence2StagedDev,
                ) else StagedSqlitePreflight.Schema(
                    GenerationStoreFiles.COMMERCIAL_EVIDENCE, 1, listOf("commercial_evidence"),
                ),
                StagedSqlitePreflight.Schema(GenerationStoreFiles.WEB_EVIDENCE, 2, listOf(
                    "web_evidence", "web_search_evidence",
                )),
                auxiliarySchema,
                StagedSqlitePreflight.Schema(GenerationStoreFiles.WORKSPACE, 4, listOf(
                    "workspace_conversation", "workspace_meta", "workspace_receipt",
                    "workspace_draft", "workspace_authority", "workspace_message",
                    "workspace_plan_review",
                )),
            )
            if (activeGeneration) ActiveSqlitePreflight.requireAll(directory, schemas)
            else StagedSqlitePreflight.requireAll(directory, schemas)
        }

        internal fun requireSealedFinance9ForDevCutover(staged: StagedStorageGeneration) {
            check(ExistingGenerationFormatReader.requireAndRead(staged) == GenerationStoreFormat.WORKSPACE_V2)
            for (file in GenerationStoreFiles.WORKSPACE_V2) {
                for (suffix in listOf("-wal", "-shm", "-journal")) {
                    check(Files.notExists(staged.directory.resolve(file + suffix), NOFOLLOW_LINKS))
                }
            }
            requireStrictV2StoreSchemas(
                staged.directory, activeGeneration = false, planningStage8 = true,
                financeStage9 = true, exactAuxiliary = true,
            )
            RealityFinance9SealedReadPort.readTrustedSealedStage9(staged.directory.resolve(GenerationStoreFiles.REALITY))
        }

        private fun openStores(
            normalized: Path,
            generationId: String,
            workspace: ConversationWorkspaceStore?,
            materialWriteGate: MaterialWriteGate? = null,
            planningStage8: Boolean = false,
            financeStage9: Boolean = false,
            financeActive9: Boolean = false,
            auxiliaryReceiptFence: (() -> Boolean)? = null,
            householdStage10: Boolean = false,
            householdActive10Dev: Boolean = false,
            householdNeedsWritableDev: Boolean = false,
            commercialEvidence2StagedDev: Boolean = false,
            commercialEvidence2ActiveDev: Boolean = false,
            householdStage12: Boolean = false,
            householdStage13: Boolean = false,
            householdStage14: Boolean = false,
            householdStage15: Boolean = false,
            householdActive13Dev: Boolean = false,
            householdActive12Dev: Boolean = false,
            householdPurchaseWritableDev: Boolean = false,
            householdStockReceiveWritableDev: Boolean = false,
            householdStockCompensationWritableDev: Boolean = false,
            householdActive14Dev: Boolean = false,
            householdStockReservationWritableDev: Boolean = false,
            householdActive15Dev: Boolean = false,
            householdStockConsumptionWritableDev: Boolean = false,
        ): FaroV5GenerationStoreGraph {
            var reality: RealityStore? = null
            var commercial: CommercialEvidenceStore? = null
            var web: WebEvidenceStore? = null
            var auxiliary: AuxiliaryDeliveryStore? = null

            try {
                val openedReality =
                    RealityStore(
                        normalized.resolve(GenerationStoreFiles.REALITY).toString(),
                        materialWriteGate = materialWriteGate,
                        planningStage8 = planningStage8,
                        financeStage9 = financeStage9,
                        financeActive9 = financeActive9,
                        householdStage10ReadOnly = householdStage10,
                        householdActive10Dev = if (householdActive10Dev) checkNotNull(auxiliaryReceiptFence) else null,
                        householdNeedsWritableDev = householdNeedsWritableDev,
                        householdStage12ReadOnly = householdStage12,
                        householdStage13ReadOnly = householdStage13,
                        householdStage14ReadOnly = householdStage14,
                        householdStage15ReadOnly = householdStage15,
                        householdActive13DevReadOnly = if (householdActive13Dev) checkNotNull(auxiliaryReceiptFence) else null,
                        householdActive12DevReadOnly = if (householdActive12Dev) checkNotNull(auxiliaryReceiptFence) else null,
                        householdPurchaseWritableDev = householdPurchaseWritableDev,
                        householdStockReceiveWritableDev = householdStockReceiveWritableDev,
                        householdStockCompensationWritableDev = householdStockCompensationWritableDev,
                        householdActive14DevReadOnly = if (householdActive14Dev) checkNotNull(auxiliaryReceiptFence) else null,
                        householdStockReservationWritableDev = householdStockReservationWritableDev,
                        householdActive15DevReadOnly = if (householdActive15Dev) checkNotNull(auxiliaryReceiptFence) else null,
                        householdStockConsumptionWritableDev = householdStockConsumptionWritableDev,
                    )
                reality = openedReality
                commercial =
                    CommercialEvidenceStore(
                        normalized.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
                        materialWriteGate = materialWriteGate,
                        stagedTypedTermsV2 = commercialEvidence2StagedDev || commercialEvidence2ActiveDev,
                        stagedSemanticBackupV2 = commercialEvidence2StagedDev || commercialEvidence2ActiveDev,
                    )
                web =
                    WebEvidenceStore(
                        normalized.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString(),
                        materialWriteGate = materialWriteGate,
                    )
                val auxiliaryPath = normalized.resolve(GenerationStoreFiles.AUXILIARY).toString()
                auxiliary = when {
                    auxiliaryReceiptFence == null ->
                        AuxiliaryDeliveryStore(auxiliaryPath, materialWriteGate = materialWriteGate)
                    financeActive9 || householdActive10Dev || householdActive12Dev || householdActive13Dev || householdActive14Dev || householdActive15Dev ->
                        AuxiliaryDeliveryStore.openActiveReceiptV4ForTrustedGeneration(
                        auxiliaryPath, checkNotNull(materialWriteGate), auxiliaryReceiptFence,
                    )
                    else -> {
                        check(auxiliaryReceiptFence())
                        AuxiliaryDeliveryStore.openQuarantinedStagedReceiptV4ForTrustedGeneration(
                            auxiliaryPath, auxiliaryReceiptFence,
                        )
                    }
                }
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
                check(graph.verifyHealthy() && (!commercialEvidence2ActiveDev ||
                    runCatching { graph.commercialEvidence.stagedTypedPortability.snapshot() }.isSuccess)) {
                    "active StorageGeneration failed store health or typed Evidence2 semantic verification"
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
