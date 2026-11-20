package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.store.reality.RealityFinance9SealedReadPort
import ar.com.faro.v5.store.reality.RealityFinance9SealedSnapshot
import ar.com.faro.v5.store.reality.RealityFinance9SealedTableRows
import ar.com.faro.v5.store.reality.RealityFinance9StagedRowsRestorePort
import ar.com.faro.v5.store.reality.RealityHousehold10Row
import ar.com.faro.v5.store.reality.RealityHousehold10Rows
import ar.com.faro.v5.store.reality.RealityHousehold10SealedRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold10StagedRowsRestorePort
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.LocalDate

internal class AuthenticatedHousehold10RestoreArchive private constructor(
    private val ciphertext: ByteArray,
    val snapshot: FaroV5Household10Fbs2Snapshot,
) {
    fun copyEncryptedBytes(): ByteArray = ciphertext.copyOf()

    companion object {
        fun open(
            archive: ByteArray,
            passphrase: CharArray,
        ): FiveStoreAuthenticatedPayload<AuthenticatedHousehold10RestoreArchive> {
            val crypto = FaroV5FiveStorePortableBackupCrypto()
            require(crypto.admitsArchiveSize(archive.size))
            val sealed = archive.copyOf()
            return when (val result = crypto.openWithHousehold(sealed, passphrase)) {
                is FiveStoreAuthenticatedPayload.Rejected -> result
                is FiveStoreAuthenticatedPayload.Opened ->
                    FiveStoreAuthenticatedPayload.Opened(AuthenticatedHousehold10RestoreArchive(sealed, result.payload))
            }
        }
    }
}

internal enum class Household10RestorePhase { MIGRATED_TO_TEN, HOUSEHOLD_RESTORED }

/** FBS2v6 capture and replay for sealed non-active generations under maintenance ownership. */
internal class FaroV5Household10ArchiveCoordinator(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val afterStore: (FiveStoreStagedRestoreSection) -> Unit = {},
    private val afterPhase: (Household10RestorePhase) -> Unit = {},
    private val planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
) {
    private val layout = FileStorageGenerationLayout(storageRoot)
    private val pointer = FileActiveGenerationPointer(storageRoot)
    private val archiveIdentity by lazy { FileRestoreArchiveIdentity(storageRoot) }
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val alreadyQuiesced = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(operationId: MaintenanceOperationId, block: () -> T): T =
            block()
    }

    fun captureAuthenticated(
        operationId: MaintenanceOperationId,
        backupSetId: BackupSetId,
        source: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        productVersion: String,
        createdAtEpochMillis: Long,
        passphrase: CharArray,
    ): ByteArray = quiescence.withMaterialWritesQuiesced(operationId) {
        val snapshot = capture(operationId, backupSetId, source, expectedActive, productVersion, createdAtEpochMillis)
        val archive = crypto.sealWithHousehold(snapshot, passphrase)
        requirePinnedStage(source, expectedActive)
        archive
    }

    /**
     * An exclusive, closed DEV host may archive a sealed copy of its selected generation.
     * An active database with sidecars is rejected before any copy is allocated.
     */
    fun captureClosedActiveAuthenticated(
        operationId: MaintenanceOperationId,
        backupSetId: BackupSetId,
        expectedActive: ActiveGenerationPointer,
        copyDescriptor: StorageGenerationDescriptor,
        productVersion: String,
        createdAtEpochMillis: Long,
        passphrase: CharArray,
    ): ByteArray = quiescence.withMaterialWritesQuiesced(operationId) {
        assertExclusiveClosedDevHost()
        check(pointer.read() == expectedActive && copyDescriptor.generationId != expectedActive.generationId)
        val source = layout.requireStaged(expectedActive.generationId)
        FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(source)
        val before = fingerprints(source)
        assertExclusiveClosedDevHost()
        check(pointer.read() == expectedActive)

        val copy = layout.stageWorkspaceV2(copyDescriptor)
        for (name in GenerationStoreFiles.WORKSPACE_V2) {
            assertExclusiveClosedDevHost()
            check(pointer.read() == expectedActive)
            val destination = copy.directory.resolve(name)
            Files.copy(source.directory.resolve(name), destination, NOFOLLOW_LINKS)
            FileChannel.open(destination, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
        }
        FileChannel.open(copy.directory, READ, NOFOLLOW_LINKS).use { it.force(true) }
        check(fingerprints(copy) == before) { "Household10 archive copy changed during capture" }

        val snapshot = capture(operationId, backupSetId, copy, expectedActive, productVersion, createdAtEpochMillis)
        val archive = crypto.sealWithHousehold(snapshot, passphrase)
        assertExclusiveClosedDevHost()
        check(pointer.read() == expectedActive)
        FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(source)
        check(fingerprints(source) == before) { "Household10 active source changed during capture" }
        archive
    }

    fun restoreAuthenticated(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult = try {
        quiescence.withMaterialWritesQuiesced(operationId) {
            restoreWithinLease(operationId, target, expectedActive, encryptedArchive, passphrase)
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        blocked(FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE)
    } catch (_: Exception) {
        blocked(FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE)
    }

    private fun restoreWithinLease(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult {
        val isNine = try {
            requirePinnedStage(target, origin)
            requireSealedStoreFiles(target)
            isStagedNine(target)
        } catch (_: Exception) {
            return blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
        }
        val archive = try {
            when (val open = AuthenticatedHousehold10RestoreArchive.open(encryptedArchive, passphrase)) {
                is FiveStoreAuthenticatedPayload.Opened -> open.payload
                is FiveStoreAuthenticatedPayload.Rejected -> return FiveStoreStagedRestoreResult.Blocked(
                    FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, open.reason,
                )
            }
        } catch (_: IllegalArgumentException) {
            return blocked(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE)
        }
        val expected = archive.snapshot
        val manifest = expected.base.base.fourStore.manifest
        val generationId = target.descriptor.generationId
        if (manifest.sourceGenerationId == generationId) return blocked(
            FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT,
        )

        val sealed = archive.copyEncryptedBytes()
        try {
            requirePinnedStage(target, origin)
            if (isNine) archiveIdentity.pinOrVerify(operationId, generationId, sealed)
            else archiveIdentity.requireExistingPinnedArchive(operationId, generationId, sealed)
        } catch (_: Exception) {
            return blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT)
        }

        return try {
            val reality = target.directory.resolve(GenerationStoreFiles.REALITY)
            if (isNine) {
                val base = restoreNine(operationId, target, origin, archive)
                if (base !is FiveStoreStagedRestoreResult.StagedVerified) return base
                requirePinnedStage(target, origin)
                requireSealedStoreFiles(target)
                RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
                forceRealityAndDirectory(reality)
                afterPhase(Household10RestorePhase.MIGRATED_TO_TEN)
            }

            fun observe() = capture(
                operationId, manifest.backupSetId, target, origin, manifest.productVersion,
                manifest.createdAtEpochMillis,
            )
            val baseAtTen = observe().base
            requireMatchingBase(baseAtTen, expected.base)
            requirePinnedStage(target, origin)
            RealityHousehold10StagedRowsRestorePort.restoreOrVerifyTrustedStagedTen(reality, expected.household.toRows())
            forceRealityAndDirectory(reality)
            afterPhase(Household10RestorePhase.HOUSEHOLD_RESTORED)
            val verified = observe()
            requireMatchingBase(verified.base, expected.base)
            check(verified.household == expected.household) { "Household10 stage does not equal the archive" }
            archiveIdentity.requireExistingPinnedArchive(operationId, generationId, sealed)
            requirePinnedStage(target, origin)
            FiveStoreStagedRestoreResult.StagedVerified(generationId.value)
        } catch (_: Exception) {
            blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED)
        }
    }

    private fun restoreNine(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: AuthenticatedHousehold10RestoreArchive,
    ): FiveStoreStagedRestoreResult {
        requirePinnedStage(target, origin)
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(target, pointer).close()
        requirePinnedStage(target, origin)
        val replay = FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Stage(target, origin, planVerifier).use { graph ->
            FaroV5FiveStoreStagedRestore(
                target.directory.parent.parent, target, graph, alreadyQuiesced,
                afterSection = { section ->
                    requirePinnedStage(target, origin)
                    afterStore(section)
                },
                archiveIdentity = archiveIdentity,
            ).restoreVerifiedHousehold10BaseStageOnly(operationId, archive, origin)
        }
        if (replay is FiveStoreStagedRestoreResult.StagedVerified) {
            requirePinnedStage(target, origin)
            FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(target, allowAuxiliary3 = false)
            val finance = checkNotNull(archive.snapshot.base.base.finance9)
            RealityFinance9StagedRowsRestorePort.restoreOrVerifyTrustedStagedNine(
                target.directory.resolve(GenerationStoreFiles.REALITY),
                RealityFinance9SealedSnapshot(
                    finance.schemaVersion, finance.tables.map { RealityFinance9SealedTableRows(it.table, it.rows) },
                ),
            )
        }
        return replay
    }

    private fun isStagedNine(stage: StagedStorageGeneration): Boolean {
        try {
            FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(stage, allowAuxiliary3 = true)
            return true
        } catch (_: IllegalStateException) {
            FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(stage)
            return false
        }
    }

    private fun requireMatchingBase(observed: FaroV5Auxiliary4Fbs2Snapshot, expected: FaroV5Auxiliary4Fbs2Snapshot) {
        val manifest = expected.base.fourStore.manifest
        val normalized = observed.copy(base = observed.base.copy(fourStore = observed.base.fourStore.copy(manifest = manifest)))
        check(normalized == expected) { "Household10 five-store base does not equal the archive" }
    }

    private fun Household10BackupSection.toRows() = RealityHousehold10Rows(revision, needs.map { need ->
        RealityHousehold10Row(
            need.id.value, need.name.value, need.quantity.thousandths.toInt(),
            need.quantity.unit.name, need.priority.name, need.status.name, need.revision, need.dueDate?.toEpochDay(),
        )
    })

    private fun requireSealedStoreFiles(stage: StagedStorageGeneration) {
        val directory = stage.directory.toAbsolutePath().normalize()
        check(directory.toRealPath() == directory)
        for (name in GenerationStoreFiles.WORKSPACE_V2) {
            val file = directory.resolve(name)
            check(Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(directory.resolve(name + suffix), NOFOLLOW_LINKS))
            }
            val header = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(20) }
            check(header.size == 20 && header[18] == 2.toByte() && header[19] == 2.toByte()) {
                "Household10 archive requires sealed WAL-mode stores"
            }
        }
    }

    private fun forceRealityAndDirectory(file: Path) {
        FileChannel.open(file, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
        FileChannel.open(file.parent, READ, NOFOLLOW_LINKS).use { it.force(true) }
    }

    private fun blocked(reason: FiveStoreStagedRestoreBlock) = FiveStoreStagedRestoreResult.Blocked(reason)

    private fun capture(
        operationId: MaintenanceOperationId,
        backupSetId: BackupSetId,
        stage: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        productVersion: String,
        createdAtEpochMillis: Long,
    ): FaroV5Household10Fbs2Snapshot {
        requirePinnedStage(stage, expectedActive)
        requireSealedStoreFiles(stage)
        FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(stage)
        val before = fingerprints(stage)
        val (base, auxiliary) = FaroV5GenerationStoreGraph.openValidatedHousehold10ArchiveStage(stage, expectedActive, planVerifier)
            .use { graph ->
                FaroV5FiveStoreBackupCapture(graph, alreadyQuiesced).captureReceiptProjection(
                    operationId, backupSetId, productVersion, createdAtEpochMillis,
                )
            }
        requirePinnedStage(stage, expectedActive)
        val file = stage.directory.resolve(GenerationStoreFiles.REALITY)
        val finance = RealityFinance9SealedReadPort.readTrustedSealedStage10(file)
        val rows = RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(file)
        val household = Household10BackupSection(rows.revision, rows.needs.map { row ->
            HouseholdNeed(
                HouseholdNeedId(row.id), HouseholdNeedName(row.name),
                HouseholdNeedQuantity(row.quantityThousandths.toLong(), HouseholdNeedUnit.valueOf(row.unit)),
                HouseholdNeedPriority.valueOf(row.priority), HouseholdNeedStatus.valueOf(row.status),
                row.revision, row.dueEpochDay?.let(LocalDate::ofEpochDay),
            )
        })
        FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(stage)
        check(fingerprints(stage) == before) { "Household10 capture changed a sealed source store" }
        requirePinnedStage(stage, expectedActive)
        return FaroV5Household10Fbs2Snapshot(
            FaroV5Auxiliary4Fbs2Snapshot(
                base.copy(finance9 = Finance9SectionSnapshot(
                    finance.schemaVersion, finance.tables.map { Finance9TableRows(it.table, it.rows) },
                )),
                auxiliary.receipts,
            ),
            household,
        )
    }

    private fun requirePinnedStage(stage: StagedStorageGeneration, expectedActive: ActiveGenerationPointer) {
        assertExclusiveClosedDevHost()
        check(pointer.read() == expectedActive && stage.descriptor.generationId != expectedActive.generationId)
        val canonical = layout.requireStaged(stage.descriptor.generationId)
        check(canonical.descriptor == stage.descriptor &&
            canonical.directory == stage.directory.toAbsolutePath().normalize())
        check(pointer.read() == expectedActive)
    }

    private fun fingerprints(stage: StagedStorageGeneration): List<List<Byte>> =
        GenerationStoreFiles.WORKSPACE_V2.map { file ->
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(stage.directory.resolve(file), NOFOLLOW_LINKS).use { input ->
                DigestInputStream(input, digest).use { it.transferTo(java.io.OutputStream.nullOutputStream()) }
            }
            digest.digest().toList()
        }
}
