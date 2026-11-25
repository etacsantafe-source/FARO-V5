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
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate

internal class AuthenticatedHousehold12RestoreArchive private constructor(
    private val ciphertext: ByteArray,
    val full: FaroV5Household12Fbs2Snapshot,
) : AuthenticatedHouseholdReceiptBaseArchive {
    override val snapshot: FaroV5Household11Fbs2Snapshot get() = full.base
    override fun copyEncryptedBytes(): ByteArray = ciphertext.copyOf()

    companion object {
        fun open(
            archive: ByteArray,
            passphrase: CharArray,
        ): FiveStoreAuthenticatedPayload<AuthenticatedHousehold12RestoreArchive> {
            val crypto = FaroV5FiveStorePortableBackupCrypto()
            require(crypto.admitsArchiveSize(archive.size)) { "FBS2v9 archive size invalid" }
            val ciphertext = archive.copyOf()
            return when (val opened = crypto.openWithHouseholdPurchases12(ciphertext, passphrase)) {
                is FiveStoreAuthenticatedPayload.Opened ->
                    FiveStoreAuthenticatedPayload.Opened(AuthenticatedHousehold12RestoreArchive(ciphertext, opened.payload))
                is FiveStoreAuthenticatedPayload.Rejected -> opened
            }
        }
    }
}

internal enum class Household12StagePhase { BASE_RESTORED, NEEDS_RESTORED, MIGRATED_TO_TWELVE, BEFORE_ROW_COMMIT }

/** Authenticated capture and replay for sealed, inactive five-store generations. */
internal class FaroV5Household12V9StagedReplay(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val afterPhase: (Household12StagePhase) -> Unit = {},
    private val planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val rows = FaroV5Household12PurchaseSql12Port(root, assertExclusiveClosedDevHost)
    private val pin = FileRestoreArchiveIdentity(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val underLease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(operationId: MaintenanceOperationId, block: () -> T): T = block()
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
        val sealed = crypto.sealWithHouseholdPurchases12(snapshot, passphrase)
        rows.requireSealedStage(stage, origin)
        sealed
    }

    fun restoreAuthenticatedV9(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult = restoreUnderLease(operationId) {
        val authenticated = when (val opened = AuthenticatedHousehold12RestoreArchive.open(archive, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> return@restoreUnderLease FiveStoreStagedRestoreResult.Blocked(
                FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, opened.reason,
            )
        }
        val snapshot = authenticated.full
        if (manifest(snapshot).sourceGenerationId == stage.descriptor.generationId) {
            return@restoreUnderLease blocked(FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT)
        }
        val bytes = authenticated.copyEncryptedBytes()
        val reality = rows.requireSealedStage(stage, origin)
        val version = RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(reality)
        val id = stage.descriptor.generationId
        if (version !in 9L..12L) return@restoreUnderLease blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
        if (version == 9L) {
            val replay = FaroV5Household11V8BaseReplay(
                root, underLease, assertExclusiveClosedDevHost, planVerifier = planVerifier,
            ).restoreVerifiedV9BaseToNine(operationId, stage, origin, authenticated)
            if (replay !is FiveStoreStagedRestoreResult.StagedVerified) return@restoreUnderLease replay
            afterPhase(Household12StagePhase.BASE_RESTORED)
        }
        if (!hasPin(operationId, stage, bytes)) return@restoreUnderLease blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT)

        if (version == 9L) {
            RealityHousehold10StagedFilePort.migrateTrustedStagedNine(rows.requireSealedStage(stage, origin))
            rows.force(reality)
        }
        if (version <= 10L) {
            RealityHousehold10StagedRowsRestorePort.restoreOrVerifyTrustedStagedTen(
                rows.requireSealedStage(stage, origin), snapshot.base.base.household.asRows(),
            )
            rows.force(reality)
            afterPhase(Household12StagePhase.NEEDS_RESTORED)
            RealityHousehold11StagedFilePort.migrateTrustedStagedTen(rows.requireSealedStage(stage, origin))
            rows.force(reality)
        }
        if (version <= 11L) {
            migrate(operationId, stage, origin, bytes)
        }

        // Validate the other semantic sections before altering purchases or receipts.
        val base = observeForVerification(operationId, stage, origin, snapshot)
        check(base.base.base == snapshot.base.base) { "Reality12 target base differs from authenticated v9" }
        rows.restoreRows(stage, origin, snapshot) {
            requirePin(operationId, stage, bytes)
            afterPhase(Household12StagePhase.BEFORE_ROW_COMMIT)
            rows.requireLogicalStage(stage, origin)
            requirePin(operationId, stage, bytes)
        }
        check(observeForVerification(operationId, stage, origin, snapshot) == snapshot) {
            "Reality12 completed stage differs from authenticated v9"
        }
        requirePin(operationId, stage, bytes)
        rows.requireSealedStage(stage, origin)
        FiveStoreStagedRestoreResult.StagedVerified(id.value)
    }

    fun restoreAuthenticatedV8AsTwelve(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult = restoreUnderLease(operationId) {
        val authenticated = when (val opened = AuthenticatedHousehold11RestoreArchive.open(archive, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> return@restoreUnderLease FiveStoreStagedRestoreResult.Blocked(
                FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, opened.reason,
            )
        }
        val snapshot = FaroV5Household12Fbs2Snapshot(authenticated.snapshot, emptyList())
        if (manifest(snapshot).sourceGenerationId == stage.descriptor.generationId) {
            return@restoreUnderLease blocked(FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT)
        }
        val bytes = authenticated.copyEncryptedBytes()
        val version = RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(rows.requireSealedStage(stage, origin))
        if (version != 12L) {
            val replay = FaroV5Household11V8FullStageReplay(root, underLease, assertExclusiveClosedDevHost)
                .restoreAuthenticatedStagedEleven(operationId, stage, origin, bytes, passphrase)
            if (replay !is FiveStoreStagedRestoreResult.StagedVerified) return@restoreUnderLease replay
            migrate(operationId, stage, origin, bytes)
        }
        if (!hasPin(operationId, stage, bytes)) return@restoreUnderLease blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT)
        check(observeForVerification(operationId, stage, origin, snapshot) == snapshot) {
            "Reality12 legacy import differs from authenticated v8 with empty purchases"
        }
        requirePin(operationId, stage, bytes)
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
        afterPhase(Household12StagePhase.MIGRATED_TO_TWELVE)
        rows.requireSealedStage(stage, origin)
        requirePin(operationId, stage, bytes)
    }

    private fun observeForVerification(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        expected: FaroV5Household12Fbs2Snapshot,
    ): FaroV5Household12Fbs2Snapshot {
        val manifest = manifest(expected)
        val evidenceSchema = expected.base.base.base.base.fourStore.commercialEvidence.schemaVersion
        return capture(operationId, stage, origin, evidenceSchema == 2, manifest.backupSetId, manifest.productVersion,
            manifest.createdAtEpochMillis, manifest)
    }

    private fun capture(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        evidence2: Boolean,
        backupSetId: BackupSetId,
        productVersion: String,
        createdAtMs: Long,
        sourceManifest: BackupSetManifest? = null,
    ): FaroV5Household12Fbs2Snapshot {
        val reality = rows.requireSealedStage(stage, origin)
        val before = fingerprints(stage)
        val (base, auxiliary) = FaroV5GenerationStoreGraph.openValidatedHousehold12ArchiveStage(
            stage, origin, evidence2, planVerifier,
        ).use { graph ->
            FaroV5FiveStoreBackupCapture(graph, underLease).captureReceiptProjection(
                operationId, backupSetId, productVersion, createdAtMs,
            )
        }
        val finance = RealityFinance9SealedReadPort.readTrustedSealedStage12(reality)
        val needs = RealityHousehold11NeedsRowsPort.readTrustedSealedStageTwelve(reality)
        val observed = rows.captureRows(stage, origin)
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
        val receipts = Household11ReceiptBackupSection(observed.receipts.revision, observed.receipts.receipts.map {
            Household11ReceiptBackupRow(it.receiptId, it.intentKey, it.purchaseRef, it.stockItemRef,
                it.quantityThousandths, it.unit, it.receivedEpochDay, it.recordedAtMs, it.revision)
        })
        val purchases = observed.purchases.map {
            Household12PurchaseBackupRow(it.purchaseRef, it.stockItemRef, it.quantityThousandths, it.unit,
                it.confirmedBy, it.confirmedAtMs, it.provenance, it.revision)
        }
        return FaroV5Household12Fbs2Snapshot(FaroV5Household11Fbs2Snapshot(h10, receipts), purchases)
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

    private fun manifest(snapshot: FaroV5Household12Fbs2Snapshot): BackupSetManifest =
        snapshot.base.base.base.base.fourStore.manifest

    private fun blocked(reason: FiveStoreStagedRestoreBlock) = FiveStoreStagedRestoreResult.Blocked(reason)

    private fun requirePin(operationId: MaintenanceOperationId, stage: StagedStorageGeneration, bytes: ByteArray) =
        pin.requireExistingPinnedArchive(operationId, stage.descriptor.generationId, bytes)

    private fun hasPin(operationId: MaintenanceOperationId, stage: StagedStorageGeneration, bytes: ByteArray): Boolean =
        runCatching { requirePin(operationId, stage, bytes) }.isSuccess
}
