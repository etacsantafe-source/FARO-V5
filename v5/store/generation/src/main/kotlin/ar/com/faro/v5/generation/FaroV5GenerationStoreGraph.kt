package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
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
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import ar.com.faro.v5.store.reality.RealityFinance9SealedReadPort
import ar.com.faro.v5.store.reality.RealityHousehold10SealedRowsPort
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

    internal class Household10DevReadSession(
        internal val graph: FaroV5GenerationStoreGraph,
        private val gate: MaterialWriteGate,
        private val lease: FileLock,
    ) : AutoCloseable {
        fun readNeeds(ownerAdmission: () -> Boolean): RealityHousehold10Rows =
            graph.reality.household10ActiveDevReadForTrustedHost(ownerAdmission)

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
        ): FaroV5GenerationStoreGraph =
            openFinance9ArchiveStage(staged, expectedActive, auxiliary4 = true)

        internal fun openValidatedHousehold10ArchiveStage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
        ): FaroV5GenerationStoreGraph =
            openFinance9ArchiveStage(staged, expectedActive, auxiliary4 = true, householdStage10 = true)

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
                )
                try {
                    check(graph.verifyHealthy() && fence()) { "Household10 DEV graph health or admission changed" }
                    Household10DevReadSession(graph, materialWriteGate, lease)
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

        internal fun requireSealedHousehold10ArchiveStage(staged: StagedStorageGeneration) {
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
                    check(Files.notExists(directory.resolve(name + suffix), NOFOLLOW_LINKS))
                }
            }
            requireStrictV2StoreSchemas(
                directory, activeGeneration = false, planningStage8 = true,
                financeStage9 = true, auxiliary4 = true, householdStage10 = true,
            )
            RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(directory.resolve(GenerationStoreFiles.REALITY))
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

        private fun openFinance9ArchiveStage(
            staged: StagedStorageGeneration,
            expectedActive: ActiveGenerationPointer,
            auxiliary4: Boolean,
            householdStage10: Boolean = false,
        ): FaroV5GenerationStoreGraph {
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
            if (householdStage10) requireSealedHousehold10ArchiveStage(staged)
            val receiptFence: (() -> Boolean)? =
                if (auxiliary4) ({ pointer.read() == expectedActive }) else null
            val graph = openValidatedV2Internal(
                staged, planVerifier = null,
                planningStage8 = true, financeStage9 = true, auxiliaryReceiptFence = receiptFence,
                householdStage10 = householdStage10,
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
        ): FaroV5GenerationStoreGraph {
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
            )
            if (householdActive10Dev) {
                val fence = checkNotNull(auxiliaryReceiptFence)
                check(fence())
                for (name in GenerationStoreFiles.WORKSPACE_V2) {
                    val file = normalized.resolve(name)
                    check((Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                }
                RealityStore.requireHealthyHousehold10ActiveDev(normalized.resolve(GenerationStoreFiles.REALITY))
                check(fence())
            }
            if (financeStage9 && !activeGeneration) {
                val reality = normalized.resolve(GenerationStoreFiles.REALITY)
                if (householdStage10) RealityFinance9SealedReadPort.readTrustedSealedStage10(reality)
                else RealityFinance9SealedReadPort.readTrustedSealedStage9(reality)
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
                    financeStage9 = financeStage9 && !activeGeneration && !householdStage10,
                    financeActive9 = financeStage9 && activeGeneration && !householdActive10Dev,
                    auxiliaryReceiptFence = auxiliaryReceiptFence,
                    householdStage10 = householdStage10,
                    householdActive10Dev = householdActive10Dev,
                    householdNeedsWritableDev = householdNeedsWritableDev,
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
        ) {
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
                    GenerationStoreFiles.REALITY, if (householdStage10) 10 else 9,
                    realityCurrentTables + listOf(
                        "reminder_state", "reminder_rule", "reminder_occurrence",
                        "monthly_plan_state", "monthly_plan_item",
                        "finance_acl_state", "finance_acl_policy", "finance_apply_grant",
                        "finance_opening_confirmation", "finance_transfer",
                        "finance_debt", "finance_schedule", "finance_plan_item",
                        "finance_plan_item_payment", "finance_budget_limit",
                        "finance_schema_migration_audit",
                    ) + if (householdStage10) listOf("household_need_state", "household_need") else emptyList(),
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
                StagedSqlitePreflight.Schema(GenerationStoreFiles.COMMERCIAL_EVIDENCE, 1, listOf("commercial_evidence")),
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
                    )
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
                val auxiliaryPath = normalized.resolve(GenerationStoreFiles.AUXILIARY).toString()
                auxiliary = when {
                    auxiliaryReceiptFence == null ->
                        AuxiliaryDeliveryStore(auxiliaryPath, materialWriteGate = materialWriteGate)
                    financeActive9 || householdActive10Dev -> AuxiliaryDeliveryStore.openActiveReceiptV4ForTrustedGeneration(
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
