package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRestoreDisposition
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRow
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRows
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRowsPort
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE

/**
 * W05-A1B2B: SQL11 receipts only. The caller must own a closed DEV host and
 * pinned, inactive, sealed generation. No FAROBAK2 full-store restore/cutover.
 */
internal class FaroV5Household11ReceiptSql11Port(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val beforeReceiptCommit: () -> Unit = {},
    private val afterCapture: () -> Unit = {},
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val pointer = FileActiveGenerationPointer(root)
    private val layout = FileStorageGenerationLayout(root)

    fun captureSealed(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ): Household11ReceiptBackupSection = quiescence.withMaterialWritesQuiesced(operationId) {
        val reality = requirePinnedStage(stage, expectedActive)
        val rows = RealityHousehold11ReceiptRowsPort.readTrustedSealedStageEleven(reality)
        val section = rows.toSection()
        check(FaroV5Household11ReceiptBackupSectionCodec.decode(
            FaroV5Household11ReceiptBackupSectionCodec.encode(section),
        ) == section) { "Reality11 SQL capture is not canonically encodable" }
        afterCapture()
        requirePinnedStage(stage, expectedActive)
        section
    }

    fun restoreOrVerifySealed(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        section: Household11ReceiptBackupSection,
    ): RealityHousehold11ReceiptRestoreDisposition =
        quiescence.withMaterialWritesQuiesced(operationId) {
            // Validate section BEFORE a writable SQLite connection is opened.
            val frozen = FaroV5Household11ReceiptBackupSectionCodec.decode(
                FaroV5Household11ReceiptBackupSectionCodec.encode(section),
            )
            val reality = requirePinnedStage(stage, expectedActive)
            val result = RealityHousehold11ReceiptRowsPort.restoreOrVerifyTrustedStagedEleven(
                reality, frozen.toRows(), beforeReceiptCommit,
            )
            FileChannel.open(reality, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
            FileChannel.open(reality.parent, READ, NOFOLLOW_LINKS).use { it.force(true) }
            requirePinnedStage(stage, expectedActive)
            check(RealityHousehold11ReceiptRowsPort.readTrustedSealedStageEleven(reality) ==
                frozen.toRows()) { "Reality11 staged SQL receipt verification changed" }
            result
        }

    private fun requirePinnedStage(
        stage: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ): Path {
        assertExclusiveClosedDevHost()
        check(pointer.read() == expectedActive &&
            stage.descriptor.generationId != expectedActive.generationId) {
            "Reality11 SQL access requires exact inactive pinned generation"
        }
        val canonical = layout.requireStaged(stage.descriptor.generationId)
        val dir = stage.directory.toAbsolutePath().normalize()
        check(canonical.descriptor == stage.descriptor &&
            canonical.directory == dir && dir.toRealPath() == dir) {
            "Reality11 SQL target directory/descriptor not canonical"
        }
        for (name in GenerationStoreFiles.WORKSPACE_V2) {
            val file = dir.resolve(name)
            check(Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(file) &&
                (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L) {
                "Reality11 SQL access rejects alias store file"
            }
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(dir.resolve(name + suffix), NOFOLLOW_LINKS)) {
                    "Reality11 SQL stage requires a sealed SQLite file set"
                }
            }
            val header = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(20) }
            check(header.size == 20 && header[18] == 2.toByte() && header[19] == 2.toByte()) {
                "Reality11 SQL staged stores must preserve WAL format"
            }
        }
        StagedSqlitePreflight.requireAll(dir, listOf(
            StagedSqlitePreflight.Schema(GenerationStoreFiles.REALITY, 11,
                listOf("household_need_state", "household_need",
                    "household_stock_receipt_state", "household_stock_receipt")),
        ))
        assertExclusiveClosedDevHost()
        check(pointer.read() == expectedActive) { "Reality11 SQL active pointer changed" }
        return dir.resolve(GenerationStoreFiles.REALITY)
    }

    private fun RealityHousehold11ReceiptRows.toSection() = Household11ReceiptBackupSection(
        revision, receipts.map {
            Household11ReceiptBackupRow(
                it.receiptId, it.intentKey, it.purchaseRef, it.stockItemRef,
                it.quantityThousandths, it.unit, it.receivedEpochDay,
                it.recordedAtMs, it.revision,
            )
        },
    )

    private fun Household11ReceiptBackupSection.toRows() = RealityHousehold11ReceiptRows(
        revision, receipts.map {
            RealityHousehold11ReceiptRow(
                it.receiptId, it.intentKey, it.purchaseRef, it.stockItemRef,
                it.quantityThousandths, it.unit, it.receivedEpochDay,
                it.recordedAtMs, it.revision,
            )
        },
    )
}
