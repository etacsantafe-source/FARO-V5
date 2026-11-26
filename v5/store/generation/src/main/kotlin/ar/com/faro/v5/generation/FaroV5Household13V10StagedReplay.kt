package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.BackupSetManifest
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.store.reality.RealityFinance9SealedReadPort
import ar.com.faro.v5.store.reality.RealityHousehold10Row
import ar.com.faro.v5.store.reality.RealityHousehold10Rows
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold10StagedRowsRestorePort
import ar.com.faro.v5.store.reality.RealityHousehold11NeedsRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold11StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold12StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold13StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold13CompensationRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold13CompensationRows
import ar.com.faro.v5.store.reality.RealityHousehold13CompensationRow
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold12PurchaseReceiptRows
import ar.com.faro.v5.store.reality.RealityHousehold12PurchaseRow
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRows
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRow
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate

internal class AuthenticatedHousehold13RestoreArchive private constructor(
    private val ciphertext: ByteArray,
    val full: FaroV5Household13Fbs2Snapshot,
) : AuthenticatedHouseholdReceiptBaseArchive {
    override val snapshot: FaroV5Household11Fbs2Snapshot get() = full.base
    override fun copyEncryptedBytes(): ByteArray = ciphertext.copyOf()

    companion object {
        /**
         * W05-A2C0B3: authenticated FBS2v12 contains its complete v11+v10 projection.
         * Preserve the ORIGINAL ciphertext bytes for the durable five-store replay pin.
         * No reseal, SQL15 cutover or Apply is authorized by this factory.
         */
        fun openV12Projection(
            archive: ByteArray,
            passphrase: CharArray,
        ): FiveStoreAuthenticatedPayload<Pair<AuthenticatedHousehold13RestoreArchive, FaroV5Household15Fbs2Snapshot>> {
            val crypto = FaroV5FiveStorePortableBackupCrypto()
            require(crypto.admitsArchiveSize(archive.size)) { "FBS2v12 archive size invalid" }
            val ciphertext = archive.copyOf()
            return when (val result = crypto.openWithHouseholdConsumption15(ciphertext, passphrase)) {
                is FiveStoreAuthenticatedPayload.Opened -> FiveStoreAuthenticatedPayload.Opened(
                    AuthenticatedHousehold13RestoreArchive(ciphertext, result.payload.base.base) to result.payload,
                )
                is FiveStoreAuthenticatedPayload.Rejected -> {
                    ciphertext.fill(0)
                    result
                }
            }
        }

        fun openV11Projection(
            archive: ByteArray,
            passphrase: CharArray,
        ): FiveStoreAuthenticatedPayload<Pair<AuthenticatedHousehold13RestoreArchive, FaroV5Household14Fbs2Snapshot>> {
            val crypto = FaroV5FiveStorePortableBackupCrypto()
            require(crypto.admitsArchiveSize(archive.size)) { "FBS2v11 archive size invalid" }
            val ciphertext = archive.copyOf()
            return when (val result = crypto.openWithHouseholdReservations14(ciphertext, passphrase)) {
                is FiveStoreAuthenticatedPayload.Opened -> FiveStoreAuthenticatedPayload.Opened(
                    AuthenticatedHousehold13RestoreArchive(ciphertext, result.payload.base) to result.payload,
                )
                is FiveStoreAuthenticatedPayload.Rejected -> {
                    ciphertext.fill(0)
                    result
                }
            }
        }

        fun open(
            archive: ByteArray,
            passphrase: CharArray,
        ): FiveStoreAuthenticatedPayload<AuthenticatedHousehold13RestoreArchive> {
            val crypto = FaroV5FiveStorePortableBackupCrypto()
            require(crypto.admitsArchiveSize(archive.size)) { "FBS2v10 archive size invalid" }
            val ciphertext = archive.copyOf()
            return when (val opened = crypto.openWithHouseholdCompensation13(ciphertext, passphrase)) {
                is FiveStoreAuthenticatedPayload.Opened ->
                    FiveStoreAuthenticatedPayload.Opened(AuthenticatedHousehold13RestoreArchive(ciphertext, opened.payload))
                is FiveStoreAuthenticatedPayload.Rejected -> opened
            }
        }
    }
}

internal enum class Household13StagePhase { BASE_RESTORED, NEEDS_RESTORED, MIGRATED_TO_TWELVE, MIGRATED_TO_THIRTEEN, BEFORE_ROW_COMMIT }

/** Authenticated FBS2v10 replay and explicit v9 import in isolated SQL13 five-store stages. */
internal class FaroV5Household13V10StagedReplay(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val afterPhase: (Household13StagePhase) -> Unit = {},
    private val planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val rows = FaroV5Household12PurchaseSql12Port(root, assertExclusiveClosedDevHost)
    private val pin = FileRestoreArchiveIdentity(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val underLease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(operationId: MaintenanceOperationId, block: () -> T): T = block()
    }

    fun verifyForEvidence2Cutover(
        operation: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): StorageGenerationId = quiescence.withMaterialWritesQuiesced(operation) {
        val opened = AuthenticatedHousehold13RestoreArchive.open(archive, passphrase)
        check(opened is FiveStoreAuthenticatedPayload.Opened) { "Cutover requires an authentic FBS2v10" }
        val snapshot = opened.payload.full
        val evidence = snapshot.base.base.base.base.fourStore.commercialEvidence
        check(evidence.schemaVersion == 2 && evidence.stagedTyped != null) { "Cutover requires Evidence2" }
        val source = manifest(snapshot).sourceGenerationId
        check(source != stage.descriptor.generationId)
        val frozen = opened.payload.copyEncryptedBytes()
        requirePin(operation, stage, frozen)
        check(observeForVerification(operation, stage, origin, snapshot) == snapshot) {
            "Cutover stage differs from its authenticated FBS2v10"
        }
        requirePin(operation, stage, frozen)
        rows.requireSealedStage(stage, origin)
        source
    }

    fun captureAuthenticated(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        evidence2: Boolean,
        backupSetId: BackupSetId,
        productVersion: String,
        createdAtMs: Long,
        passphrase: CharArray,
    ): ByteArray = quiescence.withMaterialWritesQuiesced(operationId) {
        val snapshot = capture(operationId, stage, origin, evidence2, backupSetId, productVersion, createdAtMs)
        val sealed = crypto.sealWithHouseholdCompensation13(snapshot, passphrase)
        rows.requireSealedStage(stage, origin)
        sealed
    }

    fun restoreAuthenticatedV10(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult = restoreUnderLease(operationId) {
        val authenticated = when (val opened = AuthenticatedHousehold13RestoreArchive.open(archive, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> return@restoreUnderLease FiveStoreStagedRestoreResult.Blocked(
                FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, opened.reason,
            )
        }
        restoreVerifiedBaseToThirteen(operationId, stage, origin, authenticated)
    }

    internal fun restoreVerifiedArchiveBaseToThirteen(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        authenticated: AuthenticatedHousehold13RestoreArchive,
    ): FiveStoreStagedRestoreResult = restoreUnderLease(operationId) {
        restoreVerifiedBaseToThirteen(operationId, stage, origin, authenticated)
    }

    private fun restoreVerifiedBaseToThirteen(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        authenticated: AuthenticatedHousehold13RestoreArchive,
    ): FiveStoreStagedRestoreResult {
        val snapshot = authenticated.full
        if (manifest(snapshot).sourceGenerationId == stage.descriptor.generationId) {
            return blocked(FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT)
        }
        val bytes = authenticated.copyEncryptedBytes()
        val reality = rows.requireSealedStage(stage, origin)
        val version = RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(reality)
        val id = stage.descriptor.generationId
        if (version !in 9L..13L) return blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
        if (version == 9L) {
            val replay = FaroV5Household11V8BaseReplay(
                root, underLease, assertExclusiveClosedDevHost, planVerifier = planVerifier,
            ).restoreVerifiedV10BaseToNine(operationId, stage, origin, authenticated)
            if (replay !is FiveStoreStagedRestoreResult.StagedVerified) return replay
            afterPhase(Household13StagePhase.BASE_RESTORED)
        }
        if (!hasPin(operationId, stage, bytes)) return blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT)

        if (version == 9L) {
            RealityHousehold10StagedFilePort.migrateTrustedStagedNine(rows.requireSealedStage(stage, origin))
            rows.force(reality)
        }
        if (version <= 10L) {
            RealityHousehold10StagedRowsRestorePort.restoreOrVerifyTrustedStagedTen(
                rows.requireSealedStage(stage, origin), snapshot.base.base.household.asRows(),
            )
            rows.force(reality)
            afterPhase(Household13StagePhase.NEEDS_RESTORED)
            RealityHousehold11StagedFilePort.migrateTrustedStagedTen(rows.requireSealedStage(stage, origin))
            rows.force(reality)
        }
        if (version <= 11L) {
            migrate(operationId, stage, origin, bytes)
        }
        if (version <= 12L) {
            migrateThirteen(operationId, stage, origin, bytes)
        }

        // Validate the other semantic sections before altering purchases or receipts.
        val base = observeForVerification(operationId, stage, origin, snapshot)
        check(base.base.base == snapshot.base.base) { "Reality12 target base differs from authenticated v9" }
        val historical = RealityHousehold12PurchaseReceiptRows(
            snapshot.purchases.map { RealityHousehold12PurchaseRow(
                it.purchaseRef, it.stockItemRef, it.quantityThousandths, it.unit,
                it.confirmedBy, it.confirmedAtMs, it.provenance, it.revision,
            ) },
            RealityHousehold11ReceiptRows(snapshot.base.receipts.revision,
                snapshot.base.receipts.receipts.map { RealityHousehold11ReceiptRow(
                    it.receiptId, it.intentKey, it.purchaseRef, it.stockItemRef,
                    it.quantityThousandths, it.unit, it.receivedEpochDay, it.recordedAtMs, it.revision,
                ) }),
        )
        val incoming = RealityHousehold13CompensationRows(historical, snapshot.compensations.map {
            RealityHousehold13CompensationRow(it.compensationId, it.intentKey, it.receiptId,
                it.purchaseRef, it.quantityThousandths, it.unit, it.principal, it.recordedAtMs,
                it.revision)
        })
        val file = rows.requireSealedStage(stage, origin)
        RealityHousehold13CompensationRowsPort.restoreOrVerifyTrustedSealedStageThirteen(file, incoming) {
            requirePin(operationId, stage, bytes)
            afterPhase(Household13StagePhase.BEFORE_ROW_COMMIT)
            rows.requireLogicalStage(stage, origin)
            requirePin(operationId, stage, bytes)
        }
        rows.force(file)
        check(observeForVerification(operationId, stage, origin, snapshot) == snapshot) {
            "Reality12 completed stage differs from authenticated v9"
        }
        requirePin(operationId, stage, bytes)
        rows.requireSealedStage(stage, origin)
        return FiveStoreStagedRestoreResult.StagedVerified(id.value)
    }

    /** Explicit historical v9 import: preserves purchases and receipts, adds no Undo rows. */
    fun restoreAuthenticatedV9AsThirteen(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult = restoreUnderLease(operationId) {
        val authenticated = when (val opened = AuthenticatedHousehold12RestoreArchive.open(archive, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> return@restoreUnderLease FiveStoreStagedRestoreResult.Blocked(
                FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, opened.reason)
        }
        val snapshot = authenticated.full
        if (snapshot.base.base.base.base.fourStore.manifest.sourceGenerationId == stage.descriptor.generationId) {
            return@restoreUnderLease blocked(FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT)
        }
        val frozen = authenticated.copyEncryptedBytes()
        val path = rows.requireSealedStage(stage, origin)
        val version = RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(path)
        if (version !in 9L..13L) return@restoreUnderLease blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
        if (version <= 12L) {
            val result = FaroV5Household12V9StagedReplay(root, underLease,
                assertExclusiveClosedDevHost, planVerifier = planVerifier)
                .restoreAuthenticatedV9(operationId, stage, origin, frozen, passphrase)
            if (result !is FiveStoreStagedRestoreResult.StagedVerified) return@restoreUnderLease result
            requirePin(operationId, stage, frozen)
            migrateThirteen(operationId, stage, origin, frozen)
        } else if (!hasPin(operationId, stage, frozen)) {
            return@restoreUnderLease blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT)
        }
        val expected = FaroV5Household13Fbs2Snapshot(snapshot.base, snapshot.purchases, emptyList())
        check(observeForVerification(operationId, stage, origin, expected) == expected) {
            "Explicit v9 import must retain five stores and have no compensation rows"
        }
        requirePin(operationId, stage, frozen)
        rows.requireSealedStage(stage, origin)
        FiveStoreStagedRestoreResult.StagedVerified(stage.descriptor.generationId.value)
    }

    private fun restoreUnderLease(
        operationId: MaintenanceOperationId,
        body: () -> FiveStoreStagedRestoreResult,
    ): FiveStoreStagedRestoreResult = try {
        quiescence.withMaterialWritesQuiesced(operationId) {
            try {
                body()
            } catch (interrupted: InterruptedException) {
                throw interrupted
            } catch (_: IllegalArgumentException) {
                blocked(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE)
            } catch (_: Exception) {
                blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED)
            }
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        blocked(FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE)
    } catch (_: Exception) {
        blocked(FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE)
    }

    private fun migrate(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        bytes: ByteArray,
    ) {
        val path = rows.requireSealedStage(stage, origin)
        requirePin(operationId, stage, bytes)
        RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(path) {
            rows.requireLogicalStage(stage, origin)
            requirePin(operationId, stage, bytes)
        }
        rows.force(path)
        afterPhase(Household13StagePhase.MIGRATED_TO_TWELVE)
        rows.requireSealedStage(stage, origin)
        requirePin(operationId, stage, bytes)
    }

    private fun migrateThirteen(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        bytes: ByteArray,
    ) {
        val file = rows.requireSealedStage(stage, origin)
        requirePin(operationId, stage, bytes)
        check(RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(file) == 12L)
        RealityHousehold13StagedFilePort.migrateTrustedStagedTwelve(file) {
            rows.requireLogicalStage(stage, origin)
            requirePin(operationId, stage, bytes)
        }
        rows.force(file)
        afterPhase(Household13StagePhase.MIGRATED_TO_THIRTEEN)
        rows.requireSealedStage(stage, origin)
        requirePin(operationId, stage, bytes)
    }

    internal fun observeForVerification(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        expected: FaroV5Household13Fbs2Snapshot,
        stageFourteen: Boolean = false,
    ): FaroV5Household13Fbs2Snapshot {
        val manifest = manifest(expected)
        val evidenceSchema = expected.base.base.base.base.fourStore.commercialEvidence.schemaVersion
        return capture(operationId, stage, origin, evidenceSchema == 2, manifest.backupSetId, manifest.productVersion,
            manifest.createdAtEpochMillis, manifest, stageFourteen)
    }

    internal fun capture(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        evidence2: Boolean,
        backupSetId: BackupSetId,
        productVersion: String,
        createdAtMs: Long,
        sourceManifest: BackupSetManifest? = null,
        stageFourteen: Boolean = false,
    ): FaroV5Household13Fbs2Snapshot {
        val reality = rows.requireSealedStage(stage, origin)
        val before = fingerprints(stage)
        val archiveGraph = if (stageFourteen) {
            FaroV5GenerationStoreGraph.openValidatedHousehold14ArchiveStage(stage, origin, evidence2, planVerifier)
        } else {
            FaroV5GenerationStoreGraph.openValidatedHousehold13ArchiveStage(stage, origin, evidence2, planVerifier)
        }
        val (base, auxiliary) = archiveGraph.use { graph ->
            FaroV5FiveStoreBackupCapture(graph, underLease).captureReceiptProjection(
                operationId, backupSetId, productVersion, createdAtMs,
            )
        }
        val finance = if (stageFourteen) RealityFinance9SealedReadPort.readTrustedSealedStage14(reality)
            else RealityFinance9SealedReadPort.readTrustedSealedStage13(reality)
        val needs = if (stageFourteen) RealityHousehold11NeedsRowsPort.readTrustedSealedStageFourteen(reality)
            else RealityHousehold11NeedsRowsPort.readTrustedSealedStageThirteen(reality)
        val observed = if (stageFourteen) {
            RealityHousehold14ReservationRowsPort.captureTrustedSealedStageFourteen(reality) {
                rows.requireLogicalStage(stage, origin)
            }.historical
        } else {
            RealityHousehold13CompensationRowsPort.captureTrustedSealedStageThirteen(reality)
        }
        check(fingerprints(stage) == before) { "Reality12 capture changed sealed stores" }
        rows.requireSealedStage(stage, origin)
        val fourStore = sourceManifest?.let { base.fourStore.copy(manifest = it) } ?: base.fourStore
        val stores = base.copy(fourStore = fourStore, finance9 = Finance9SectionSnapshot(
            finance.schemaVersion, finance.tables.map { Finance9TableRows(it.table, it.rows) },
        ))
        val household = Household10BackupSection(needs.revision, needs.needs.map { row ->
            HouseholdNeed(HouseholdNeedId(row.id), HouseholdNeedName(row.name),
                HouseholdNeedQuantity(row.quantityThousandths.toLong(), HouseholdNeedUnit.valueOf(row.unit)),
                HouseholdNeedPriority.valueOf(row.priority), HouseholdNeedStatus.valueOf(row.status),
                row.revision, row.dueEpochDay?.let(LocalDate::ofEpochDay))
        })
        val h10 = FaroV5Household10Fbs2Snapshot(FaroV5Auxiliary4Fbs2Snapshot(stores, auxiliary.receipts), household)
        val receipts = Household11ReceiptBackupSection(observed.historical.receipts.revision, observed.historical.receipts.receipts.map {
            Household11ReceiptBackupRow(it.receiptId, it.intentKey, it.purchaseRef, it.stockItemRef,
                it.quantityThousandths, it.unit, it.receivedEpochDay, it.recordedAtMs, it.revision)
        })
        val purchases = observed.historical.purchases.map {
            Household12PurchaseBackupRow(it.purchaseRef, it.stockItemRef, it.quantityThousandths, it.unit,
                it.confirmedBy, it.confirmedAtMs, it.provenance, it.revision)
        }
        val compensations = observed.compensations.map {
            Household13CompensationBackupRow(it.compensationId, it.intentKey, it.receiptId,
                it.purchaseRef, it.quantityThousandths, it.unit, it.principal, it.recordedAtMs,
                it.revision)
        }
        return FaroV5Household13Fbs2Snapshot(FaroV5Household11Fbs2Snapshot(h10, receipts),
            purchases, compensations)
    }

    private fun Household10BackupSection.asRows() = RealityHousehold10Rows(
        revision, needs.map {
            RealityHousehold10Row(it.id.value, it.name.value, it.quantity.thousandths.toInt(),
                it.quantity.unit.name, it.priority.name, it.status.name,
                it.revision, it.dueDate?.toEpochDay())
        },
    )

    private fun fingerprints(stage: StagedStorageGeneration): Map<String, List<Byte>> =
        GenerationStoreFiles.WORKSPACE_V2.associateWith { name ->
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(stage.directory.resolve(name), NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(8_192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().toList()
        }

    private fun manifest(snapshot: FaroV5Household13Fbs2Snapshot): BackupSetManifest =
        snapshot.base.base.base.base.fourStore.manifest

    private fun blocked(reason: FiveStoreStagedRestoreBlock) = FiveStoreStagedRestoreResult.Blocked(reason)

    private fun requirePin(operationId: MaintenanceOperationId, stage: StagedStorageGeneration, bytes: ByteArray) =
        pin.requireExistingPinnedArchive(operationId, stage.descriptor.generationId, bytes)

    private fun hasPin(operationId: MaintenanceOperationId, stage: StagedStorageGeneration, bytes: ByteArray): Boolean =
        runCatching { requirePin(operationId, stage, bytes) }.isSuccess
}
