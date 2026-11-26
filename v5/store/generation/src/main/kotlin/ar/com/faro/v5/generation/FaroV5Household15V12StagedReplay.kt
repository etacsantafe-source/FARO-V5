package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityHousehold11NeedsRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRow
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRows
import ar.com.faro.v5.store.reality.RealityHousehold12PurchaseReceiptRows
import ar.com.faro.v5.store.reality.RealityHousehold12PurchaseRow
import ar.com.faro.v5.store.reality.RealityHousehold13CompensationRow
import ar.com.faro.v5.store.reality.RealityHousehold13CompensationRows
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationRow
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationRows
import ar.com.faro.v5.store.reality.RealityHousehold14ReservationType
import ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionRow
import ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionRows
import ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionType
import ar.com.faro.v5.store.reality.RealityHousehold15StagedFilePort
import java.nio.file.Path

internal enum class Household15StagePhase {
    BASE_FIVE_STORES_VERIFIED, SQL15_STAGED_MIGRATED, BEFORE_CONSUMPTION_COMMIT,
}

/**
 * FBS2v12 authenticated SQL15 STAGED replay only.
 *
 * Reuses the v11 five-store baseline replay, keeping the ORIGINAL v12 ciphertext
 * identity for the durable pin. Replays SQL15 rows only after SQL14 full baseline
 * verification and never changes the ACTIVE generation pointer.
 *
 * Full five-store SQL15 cutover validation, stock.consume Apply and release to
 * production require separate A2C0C/A2C1 grants; this class exposes neither.
 */
internal class FaroV5Household15V12StagedReplay(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    assertExclusiveClosedDevHost: () -> Unit,
    private val afterPhase: (Household15StagePhase) -> Unit = {},
    planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val files = FaroV5Household12PurchaseSql12Port(root, assertExclusiveClosedDevHost)
    private val identity = FileRestoreArchiveIdentity(root)
    private val heldLease = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(operationId: MaintenanceOperationId, block: () -> T): T = block()
    }
    private val baseReplay = FaroV5Household14V11StagedReplay(
        root, heldLease, assertExclusiveClosedDevHost, planVerifier = planVerifier,
    )

    fun restoreAuthenticatedV12(
        operation: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult = restoreUnderLease(operation) {
        when (val opened = AuthenticatedHousehold13RestoreArchive.openV12Projection(archive, passphrase)) {
            is FiveStoreAuthenticatedPayload.Rejected ->
                FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, opened.reason,
                )
            is FiveStoreAuthenticatedPayload.Opened -> {
                val (projection, snapshot) = opened.payload
                restoreVerified(operation, stage, origin, projection, snapshot)
            }
        }
    }

    private fun restoreVerified(
        operation: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        authenticated: AuthenticatedHousehold13RestoreArchive,
        expected: FaroV5Household15Fbs2Snapshot,
    ): FiveStoreStagedRestoreResult {
        val source = expected.base.base.base.base.base.base.fourStore.manifest.sourceGenerationId
        if (source == stage.descriptor.generationId) {
            return blocked(FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT)
        }
        val originalArchive = authenticated.copyEncryptedBytes()
        try {
            val file = files.requireSealedStage(stage, origin)
            val version = RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(file)
            if (version !in 9L..15L) {
                return blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
            }
            if (version < 15L) {
                // Reconstruct and authenticate all five stores up to SQL14 exactly once.
                val replay = baseReplay.restoreVerifiedV12BaseToFourteen(
                    operation, stage, origin, authenticated, expected.base,
                )
                if (replay !is FiveStoreStagedRestoreResult.StagedVerified) return replay
                afterPhase(Household15StagePhase.BASE_FIVE_STORES_VERIFIED)
            } else {
                // A resumed SQL15 stage MUST already have been replayed using these exact bytes.
                if (runCatching { requirePin(operation, stage, originalArchive) }.isFailure) {
                    return blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT)
                }
            }

            val fence = {
                files.requireLogicalStage(stage, origin)
                requirePin(operation, stage, originalArchive)
            }
            fence()
            if (version < 15L) {
                RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(
                    files.requireSealedStage(stage, origin), fence,
                )
                files.force(file)
                afterPhase(Household15StagePhase.SQL15_STAGED_MIGRATED)
            }

            // The SQL15 history projection MUST equal the authenticated FBS2v12 history.
            // The rows port enforces this even when a prior attempt already committed.
            val rows = expected.asSqlFifteenRows()
            val staged = files.requireSealedStage(stage, origin)
            RealityHousehold15ConsumptionRowsPort.restoreOrVerifyTrustedSealedStageFifteen(
                staged, rows,
            ) {
                fence()
                afterPhase(Household15StagePhase.BEFORE_CONSUMPTION_COMMIT)
                fence()
            }
            files.force(file)
            check(RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(
                files.requireSealedStage(stage, origin), fence,
            ) == rows) { "FBS2v12 SQL15 staged readback is not the authenticated snapshot" }
            fence()
            return FiveStoreStagedRestoreResult.StagedVerified(stage.descriptor.generationId.value)
        } finally {
            originalArchive.fill(0)
        }
    }

    /** Compare the *whole* SQL14 historical projection plus the SQL15 ledger, not a ledger-only suffix. */
    private fun FaroV5Household15Fbs2Snapshot.asSqlFifteenRows(): RealityHousehold15ConsumptionRows {
        val historic = base.base
        val receipts = historic.base.receipts
        return RealityHousehold15ConsumptionRows(
            historical = RealityHousehold14ReservationRows(
                historical = RealityHousehold13CompensationRows(
                    historical = RealityHousehold12PurchaseReceiptRows(
                        purchases = historic.purchases.map {
                            RealityHousehold12PurchaseRow(
                                it.purchaseRef, it.stockItemRef, it.quantityThousandths, it.unit,
                                it.confirmedBy, it.confirmedAtMs, it.provenance, it.revision,
                            )
                        },
                        receipts = RealityHousehold11ReceiptRows(receipts.revision, receipts.receipts.map {
                            RealityHousehold11ReceiptRow(
                                it.receiptId, it.intentKey, it.purchaseRef, it.stockItemRef,
                                it.quantityThousandths, it.unit, it.receivedEpochDay,
                                it.recordedAtMs, it.revision,
                            )
                        }),
                    ),
                    compensations = historic.compensations.map {
                        RealityHousehold13CompensationRow(
                            it.compensationId, it.intentKey, it.receiptId, it.purchaseRef,
                            it.quantityThousandths, it.unit, it.principal, it.recordedAtMs, it.revision,
                        )
                    },
                ),
                reservations = base.reservations.map {
                    RealityHousehold14ReservationRow(
                        it.eventId, it.intentKey, RealityHousehold14ReservationType.valueOf(it.type.name),
                        it.reservationRef, it.stockItemRef, it.unit, it.quantityThousandths,
                        it.principal, it.recordedAtMs, it.revision,
                    )
                },
            ),
            consumptions = consumptions.map {
                RealityHousehold15ConsumptionRow(
                    it.eventId, it.intentKey, RealityHousehold15ConsumptionType.valueOf(it.type.name),
                    it.consumptionRef, it.stockItemRef, it.unit, it.quantityThousandths,
                    it.principal, it.sourceRef, it.reservationRef, it.occurredAtMs,
                    it.recordedAtMs, it.revision,
                )
            },
        )
    }

    private fun requirePin(
        operation: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        archive: ByteArray,
    ) = identity.requireExistingPinnedArchive(operation, stage.descriptor.generationId, archive)

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

    private fun blocked(reason: FiveStoreStagedRestoreBlock) = FiveStoreStagedRestoreResult.Blocked(reason)
}
