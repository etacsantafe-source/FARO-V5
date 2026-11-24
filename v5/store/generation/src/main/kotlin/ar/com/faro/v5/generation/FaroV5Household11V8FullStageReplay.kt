package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityHousehold10Row
import ar.com.faro.v5.store.reality.RealityHousehold10Rows
import ar.com.faro.v5.store.reality.RealityHousehold10SealedRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold10StagedRowsRestorePort
import ar.com.faro.v5.store.reality.RealityHousehold11NeedsRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold11StagedFilePort
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

internal enum class Household11FullStagePhase {
    BASE_READY, MIGRATED_TO_TEN, HOUSEHOLD10_RESTORED,
    MIGRATED_TO_ELEVEN, RECEIPTS11_RESTORED,
}

/**
 * W05-A1B3B1 isolated Evidence1 FBS2v8 replay. Reenters at SQL9/10/11
 * with the SAME original authenticated ciphertext pinned to operation+target.
 * All three store schemas remain inactive STAGED; never writes a pointer,
 * adopts a recovery journal or creates stock/Apply authorization.
 */
internal class FaroV5Household11V8FullStageReplay(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val afterPhase: (Household11FullStagePhase) -> Unit = {},
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val pointer = FileActiveGenerationPointer(root)
    private val layout = FileStorageGenerationLayout(root)
    private val identity = FileRestoreArchiveIdentity(root)
    private val alreadyQuiesced = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }

    fun restoreAuthenticatedStagedEleven(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult = try {
        quiescence.withMaterialWritesQuiesced(operationId) {
            replayUnderLease(operationId, stage, expectedActive, archive, passphrase)
        }
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        FiveStoreStagedRestoreResult.Blocked(FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE)
    } catch (_: Exception) {
        FiveStoreStagedRestoreResult.Blocked(FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE)
    }

    private fun replayUnderLease(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        input: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult {
        fun blocked(reason: FiveStoreStagedRestoreBlock) = FiveStoreStagedRestoreResult.Blocked(reason)
        val bytes = input.copyOf()
        return try {
            try {
                requirePinnedStage(stage, origin)
            } catch (_: Exception) {
                return blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
            }
            val authenticated = when (
                val opened = try {
                    AuthenticatedHousehold11RestoreArchive.open(bytes, passphrase)
                } catch (_: IllegalArgumentException) {
                    return blocked(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE)
                }
            ) {
                is FiveStoreAuthenticatedPayload.Opened -> opened.payload
                is FiveStoreAuthenticatedPayload.Rejected ->
                    return FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, opened.reason,
                    )
            }
            val payload = authenticated.snapshot
            if (payload.base.base.base.fourStore.commercialEvidence.schemaVersion != 1) {
                return blocked(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE) // Evidence2 unverified here.
            }
            val id = stage.descriptor.generationId
            if (payload.base.base.base.fourStore.manifest.sourceGenerationId == id) {
                return blocked(FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT)
            }
            val reality = stage.directory.resolve(GenerationStoreFiles.REALITY)
            val expectedNeeds = payload.base.household.asRows()
            val startVersion = schemaVersion(reality)
            if (startVersion !in 9..11) {
                return blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
            }
            if (startVersion == 9L) {
                try {
                    FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(
                        stage, allowAuxiliary3 = false,
                    )
                    requirePinnedStage(stage, origin)
                } catch (_: Exception) {
                    return blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
                }
                val baseResult = FaroV5Household11V8BaseReplay(
                    root, alreadyQuiesced, assertExclusiveClosedDevHost,
                ).restoreAuthenticatedBaseToNine(
                    operationId, stage, origin, bytes, passphrase,
                )
                if (baseResult !is FiveStoreStagedRestoreResult.StagedVerified) return baseResult
                // All five base owners have already been verified against FBS2v8.
                requirePin(operationId, stage, bytes)
                requirePinnedStage(stage, origin)
                afterPhase(Household11FullStagePhase.BASE_READY)
            } else {
                // A crash cannot substitute a new authenticated archive after SQL10/11 exists.
                try {
                    requirePin(operationId, stage, bytes)
                } catch (_: Exception) {
                    return blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT)
                }
            }
            // The baseline must be captured AFTER the certified B0 base replay;
            // before that, four other stores still legitimately contain fresh data.
            val beforeOthers = digestOtherStores(stage)
            try {
                if (startVersion == 9L) {
                    requirePinnedStage(stage, origin)
                    RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
                    forceReality(reality)
                    requirePinnedStage(stage, origin)
                    FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(stage)
                    afterPhase(Household11FullStagePhase.MIGRATED_TO_TEN)
                }
                if (startVersion <= 10L) {
                    requirePin(operationId, stage, bytes)
                    requirePinnedStage(stage, origin)
                    FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(stage)
                    RealityHousehold10StagedRowsRestorePort.restoreOrVerifyTrustedStagedTen(
                        reality, expectedNeeds,
                    )
                    forceReality(reality)
                    requirePinnedStage(stage, origin)
                    check(RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(reality) ==
                        expectedNeeds) { "Household10 rows mismatch after restored stage10" }
                    afterPhase(Household11FullStagePhase.HOUSEHOLD10_RESTORED)

                    requirePin(operationId, stage, bytes)
                    requirePinnedStage(stage, origin)
                    FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(stage)
                    RealityHousehold11StagedFilePort.migrateTrustedStagedTen(reality)
                    forceReality(reality)
                    requirePinnedStage(stage, origin)
                    requireEleven(stage)
                    afterPhase(Household11FullStagePhase.MIGRATED_TO_ELEVEN)
                }
                // When restarting in SQL11, verify Household10 BEFORE writing H11R.
                requirePin(operationId, stage, bytes)
                requirePinnedStage(stage, origin)
                requireEleven(stage)
                check(RealityHousehold11NeedsRowsPort.readTrustedSealedStageEleven(reality) ==
                    expectedNeeds) { "Household10 rows differ inside SQL11" }
                val sql11 = FaroV5Household11ReceiptSql11Port(
                    root, alreadyQuiesced, assertExclusiveClosedDevHost,
                )
                sql11.restoreOrVerifySealed(
                    operationId, stage, origin, payload.receipts,
                )
                afterPhase(Household11FullStagePhase.RECEIPTS11_RESTORED)
                requirePinnedStage(stage, origin)
                requireEleven(stage)
                requirePin(operationId, stage, bytes)
                check(RealityHousehold11NeedsRowsPort.readTrustedSealedStageEleven(reality) ==
                    expectedNeeds) { "Household10 SQL11 final rows mismatch" }
                check(sql11.captureSealed(operationId, stage, origin) == payload.receipts) {
                    "H11R receipt SQL11 final rows mismatch"
                }
                check(digestOtherStores(stage) == beforeOthers) {
                    "A1B3B1 unexpectedly modified a non-Reality owner"
                }
                requirePin(operationId, stage, bytes)
                requirePinnedStage(stage, origin)
                FiveStoreStagedRestoreResult.StagedVerified(id.value)
            } catch (_: Exception) {
                blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED)
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun Household10BackupSection.asRows() = RealityHousehold10Rows(
        revision, needs.map {
            RealityHousehold10Row(
                it.id.value, it.name.value, it.quantity.thousandths.toInt(),
                it.quantity.unit.name, it.priority.name, it.status.name,
                it.revision, it.dueDate?.toEpochDay(),
            )
        },
    )

    private fun requireEleven(stage: StagedStorageGeneration) {
        StagedSqlitePreflight.requireAll(stage.directory, listOf(
            StagedSqlitePreflight.Schema(
                GenerationStoreFiles.REALITY, 11,
                listOf("household_need_state", "household_need",
                    "household_stock_receipt_state", "household_stock_receipt"),
            ),
        ))
        RealityHousehold11NeedsRowsPort.readTrustedSealedStageEleven(
            stage.directory.resolve(GenerationStoreFiles.REALITY),
        )
    }

    private fun requirePin(op: MaintenanceOperationId, stage: StagedStorageGeneration, bytes: ByteArray) {
        identity.requireExistingPinnedArchive(op, stage.descriptor.generationId, bytes)
    }

    private fun schemaVersion(file: Path): Long =
        RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(file)

    private fun requirePinnedStage(stage: StagedStorageGeneration, origin: ActiveGenerationPointer) {
        assertExclusiveClosedDevHost()
        check(pointer.read() == origin && origin.generationId != stage.descriptor.generationId) {
            "FBS2v8 restore must use the exact inactive pinned generation"
        }
        val actual = layout.requireStaged(stage.descriptor.generationId)
        val dir = stage.directory.toAbsolutePath().normalize()
        check(actual.descriptor == stage.descriptor &&
            actual.directory == dir && dir.toRealPath() == dir) {
            "FBS2v8 restore stage descriptor or path mismatch"
        }
        for (name in GenerationStoreFiles.WORKSPACE_V2) {
            val file = dir.resolve(name)
            check(Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(file) &&
                (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L) {
                "FBS2v8 restore rejects noncanonical linked SQLite file"
            }
            for (ext in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(dir.resolve(name + ext), NOFOLLOW_LINKS)) {
                    "FBS2v8 restore refuses SQLite sidecar"
                }
            }
            val header = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(20) }
            check(header.size == 20 && header[18] == 2.toByte() && header[19] == 2.toByte()) {
                "FBS2v8 restore needs WAL-format sealed SQLite files"
            }
        }
        assertExclusiveClosedDevHost()
        check(pointer.read() == origin) { "FBS2v8 restore pointer ABA or host revoked" }
    }

    private fun digestOtherStores(stage: StagedStorageGeneration): Map<String, List<Byte>> =
        GenerationStoreFiles.WORKSPACE_V2
            .filterNot { it == GenerationStoreFiles.REALITY }
            .associateWith { name ->
                val digest = MessageDigest.getInstance("SHA-256")
                Files.newInputStream(stage.directory.resolve(name), NOFOLLOW_LINKS).use { input ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                    }
                }
                digest.digest().toList()
            }

    private fun forceReality(file: Path) {
        FileChannel.open(file, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
        FileChannel.open(file.parent, READ, NOFOLLOW_LINKS).use { it.force(true) }
    }
}
