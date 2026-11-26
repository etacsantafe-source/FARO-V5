package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityHousehold11NeedsRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationRow
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationRows
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationType
import ar.com.faro.v5.store.reality.RealityHousehold14StagedFilePort
import java.nio.file.Path

internal enum class Household14StagePhase { BASE_VERIFIED, MIGRATED_TO_FOURTEEN, BEFORE_RESERVATIONS }

/** Authenticated five-store replay on the inactive generation, bound to the exact original archive. */
internal class FaroV5Household14V11StagedReplay(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    assertExclusiveClosedDevHost: () -> Unit,
    private val afterPhase: (Household14StagePhase) -> Unit = {},
    planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val files = FaroV5Household12PurchaseSql12Port(root, assertExclusiveClosedDevHost)
    private val identity = FileRestoreArchiveIdentity(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val heldLease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(operationId: MaintenanceOperationId, block: () -> T): T = block()
    }
    private val base = FaroV5Household13V10StagedReplay(
        root, heldLease, assertExclusiveClosedDevHost, planVerifier = planVerifier,
    )

    fun restoreAuthenticatedV11(
        operation: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult = restoreUnderLease(operation) {
        when (val opened = AuthenticatedHousehold13RestoreArchive.openV11Projection(archive, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> {
                val (projection, snapshot) = opened.payload
                restoreVerified(operation, stage, origin, projection, snapshot)
            }
            is FiveStoreAuthenticatedPayload.Rejected -> invalidArchive(opened.reason)
        }
    }

    /** Explicit migration path for a v10 backup. Its reservation event section starts empty. */
    fun restoreAuthenticatedV10AsFourteen(
        operation: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult = restoreUnderLease(operation) {
        when (val opened = AuthenticatedHousehold13RestoreArchive.open(archive, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> restoreVerified(operation, stage, origin, opened.payload,
                FaroV5Household14Fbs2Snapshot(opened.payload.full, emptyList()))
            is FiveStoreAuthenticatedPayload.Rejected -> invalidArchive(opened.reason)
        }
    }

    fun captureAuthenticated(
        operation: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        evidence2: Boolean,
        backupSetId: BackupSetId,
        productVersion: String,
        createdAtMs: Long,
        passphrase: CharArray,
    ): ByteArray = quiescence.withMaterialWritesQuiesced(operation) {
        val observed = base.capture(operation, stage, origin, evidence2, backupSetId,
            productVersion, createdAtMs, stageFourteen = true)
        val rows = readRows(stage, origin).reservations.map { it.asBackupRow() }
        crypto.sealWithHouseholdReservations14(FaroV5Household14Fbs2Snapshot(observed, rows), passphrase)
            .also { files.requireSealedStage(stage, origin) }
    }

    fun verifyForEvidence2Cutover(
        operation: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): StorageGenerationId = quiescence.withMaterialWritesQuiesced(operation) {
        val opened = AuthenticatedHousehold13RestoreArchive.openV11Projection(archive, passphrase)
        check(opened is FiveStoreAuthenticatedPayload.Opened) { "SQL14 cutover requires an authentic FBS2v11" }
        val (projection, expected) = opened.payload
        val evidence = expected.base.base.base.base.base.fourStore.commercialEvidence
        check(evidence.schemaVersion == 2 && evidence.stagedTyped != null) { "SQL14 cutover requires Evidence2" }
        val source = sourceId(expected)
        check(source != stage.descriptor.generationId)
        val pinned = projection.copyEncryptedBytes()
        try {
            requirePin(operation, stage, pinned)
            verifyStage(operation, stage, origin, expected)
            requirePin(operation, stage, pinned)
            source
        } finally {
            pinned.fill(0)
        }
    }

    private fun restoreVerified(
        operation: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        authenticated: AuthenticatedHousehold13RestoreArchive,
        expected: FaroV5Household14Fbs2Snapshot,
    ): FiveStoreStagedRestoreResult {
        if (sourceId(expected) == stage.descriptor.generationId) {
            return blocked(FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT)
        }
        val pinned = authenticated.copyEncryptedBytes()
        try {
            val file = files.requireSealedStage(stage, origin)
            val version = RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(file)
            if (version !in 9L..14L) return blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
            if (version < 14L) {
                val result = base.restoreVerifiedArchiveBaseToThirteen(operation, stage, origin, authenticated)
                if (result !is FiveStoreStagedRestoreResult.StagedVerified) return result
                afterPhase(Household14StagePhase.BASE_VERIFIED)
            }
            if (runCatching { requirePin(operation, stage, pinned) }.isFailure) {
                return blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT)
            }
            val fence = {
                files.requireLogicalStage(stage, origin)
                requirePin(operation, stage, pinned)
            }
            RealityHousehold14StagedFilePort.migrateOrVerifyTrustedStagedFourteen(
                files.requireSealedStage(stage, origin), fence,
            )
            files.force(file)
            if (version < 14L) afterPhase(Household14StagePhase.MIGRATED_TO_FOURTEEN)

            val observedBase = base.observeForVerification(operation, stage, origin, expected.base, stageFourteen = true)
            check(observedBase == expected.base) { "SQL14 five-store baseline differs from authenticated FBS2" }
            val history = readRows(stage, origin).historical
            val reservations = expected.reservations.map { it.asSqlRow() }
            afterPhase(Household14StagePhase.BEFORE_RESERVATIONS)
            RealityHousehold14ReservationRowsPort.restoreOrVerifyTrustedSealedStageFourteen(
                files.requireSealedStage(stage, origin),
                RealityHousehold14ReservationRows(history, reservations),
                fence,
            )
            files.force(file)
            verifyStage(operation, stage, origin, expected)
            fence()
            return FiveStoreStagedRestoreResult.StagedVerified(stage.descriptor.generationId.value)
        } finally {
            pinned.fill(0)
        }
    }

    private fun verifyStage(
        operation: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        expected: FaroV5Household14Fbs2Snapshot,
    ) {
        check(base.observeForVerification(operation, stage, origin, expected.base, stageFourteen = true) == expected.base &&
            readRows(stage, origin).reservations.map { it.asBackupRow() } == expected.reservations) {
            "SQL14 five-store readback differs from authenticated FBS2v11"
        }
        files.requireSealedStage(stage, origin)
    }

    private fun readRows(stage: StagedStorageGeneration, origin: ActiveGenerationPointer) =
        RealityHousehold14ReservationRowsPort.captureTrustedSealedStageFourteen(files.requireSealedStage(stage, origin)) {
            files.requireLogicalStage(stage, origin)
        }

    private fun sourceId(snapshot: FaroV5Household14Fbs2Snapshot): StorageGenerationId =
        snapshot.base.base.base.base.base.fourStore.manifest.sourceGenerationId

    private fun Household14ReservationBackupRow.asSqlRow() = RealityHousehold14ReservationRow(
        eventId, intentKey, RealityHousehold14ReservationType.valueOf(type.name), reservationRef,
        stockItemRef, unit, quantityThousandths, principal, recordedAtMs, revision,
    )

    private fun RealityHousehold14ReservationRow.asBackupRow() = Household14ReservationBackupRow(
        eventId, intentKey, Household14ReservationEventType.valueOf(type.name), reservationRef,
        stockItemRef, unit, quantityThousandths, principal, recordedAtMs, revision,
    )

    private fun requirePin(operation: MaintenanceOperationId, stage: StagedStorageGeneration, archive: ByteArray) =
        identity.requireExistingPinnedArchive(operation, stage.descriptor.generationId, archive)

    private fun restoreUnderLease(
        operation: MaintenanceOperationId,
        replay: () -> FiveStoreStagedRestoreResult,
    ): FiveStoreStagedRestoreResult = try {
        quiescence.withMaterialWritesQuiesced(operation) {
            try {
                replay()
            } catch (failure: InterruptedException) {
                throw failure
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

    private fun invalidArchive(reason: FiveStorePortableBackupOpenFailure) =
        FiveStoreStagedRestoreResult.Blocked(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, reason)

    private fun blocked(reason: FiveStoreStagedRestoreBlock) = FiveStoreStagedRestoreResult.Blocked(reason)
}
