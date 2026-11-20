package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
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
import ar.com.faro.v5.store.reality.RealityHousehold10SealedRowsPort
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.LocalDate

/** FBS2v6 backup of a non-active, sealed generation under maintenance ownership. */
internal class FaroV5Household10ArchiveCoordinator(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
) {
    private val layout = FileStorageGenerationLayout(storageRoot)
    private val pointer = FileActiveGenerationPointer(storageRoot)
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

    private fun capture(
        operationId: MaintenanceOperationId,
        backupSetId: BackupSetId,
        stage: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        productVersion: String,
        createdAtEpochMillis: Long,
    ): FaroV5Household10Fbs2Snapshot {
        requirePinnedStage(stage, expectedActive)
        FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(stage)
        val before = fingerprints(stage)
        val (base, auxiliary) = FaroV5GenerationStoreGraph.openValidatedHousehold10ArchiveStage(stage, expectedActive)
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
