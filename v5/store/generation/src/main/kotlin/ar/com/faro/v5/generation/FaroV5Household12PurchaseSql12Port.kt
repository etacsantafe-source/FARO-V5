package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRow
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRows
import ar.com.faro.v5.store.reality.RealityHousehold12PurchaseReceiptRows
import ar.com.faro.v5.store.reality.RealityHousehold12PurchaseRow
import ar.com.faro.v5.store.reality.RealityHousehold12PurchaseRowsPort
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE

/** SQL12 file access behind the caller's exclusive, quiesced DEV host lease. */
internal class FaroV5Household12PurchaseSql12Port(
    root: Path,
    private val assertExclusiveClosedDevHost: () -> Unit,
) {
    private val pointer = FileActiveGenerationPointer(root)
    private val layout = FileStorageGenerationLayout(root)

    fun requireLogicalStage(stage: StagedStorageGeneration, expected: ActiveGenerationPointer) {
        assertExclusiveClosedDevHost()
        val canonical = layout.requireStaged(stage.descriptor.generationId)
        check(canonical.descriptor == stage.descriptor &&
            canonical.directory == stage.directory.toAbsolutePath().normalize() &&
            pointer.read() == expected && stage.descriptor.generationId != expected.generationId) {
            "Reality12 requires the pinned inactive stage and original pointer sequence"
        }
    }

    fun requireSealedStage(stage: StagedStorageGeneration, expected: ActiveGenerationPointer): Path {
        requireLogicalStage(stage, expected)
        val directory = stage.directory.toAbsolutePath().normalize()
        check(directory.toRealPath() == directory)
        for (name in GenerationStoreFiles.WORKSPACE_V2) {
            val file = directory.resolve(name)
            check(Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L) {
                "Reality12 requires canonical store files"
            }
            val header = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(20) }
            check(header.size == 20 && header[18] == 2.toByte() && header[19] == 2.toByte()) {
                "Reality12 requires WAL-format stores"
            }
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(directory.resolve(name + suffix), NOFOLLOW_LINKS)) {
                    "Reality12 stage has an open SQLite sidecar"
                }
            }
        }
        requireLogicalStage(stage, expected)
        return directory.resolve(GenerationStoreFiles.REALITY)
    }

    fun captureRows(stage: StagedStorageGeneration, expected: ActiveGenerationPointer): RealityHousehold12PurchaseReceiptRows {
        val rows = RealityHousehold12PurchaseRowsPort.readTrustedSealedStageTwelve(requireSealedStage(stage, expected))
        requireSealedStage(stage, expected)
        return rows
    }

    fun restoreRows(
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        snapshot: FaroV5Household12Fbs2Snapshot,
        beforeCommit: () -> Unit,
    ) {
        val file = requireSealedStage(stage, origin)
        val rows = RealityHousehold12PurchaseReceiptRows(
            snapshot.purchases.map {
                RealityHousehold12PurchaseRow(it.purchaseRef, it.stockItemRef, it.quantityThousandths, it.unit,
                    it.confirmedBy, it.confirmedAtMs, it.provenance, it.revision)
            },
            RealityHousehold11ReceiptRows(snapshot.base.receipts.revision, snapshot.base.receipts.receipts.map {
                RealityHousehold11ReceiptRow(it.receiptId, it.intentKey, it.purchaseRef, it.stockItemRef,
                    it.quantityThousandths, it.unit, it.receivedEpochDay, it.recordedAtMs, it.revision)
            }),
        )
        RealityHousehold12PurchaseRowsPort.restoreOrVerifyTrustedStagedTwelve(file, rows) {
            requireLogicalStage(stage, origin)
            beforeCommit()
        }
        force(file)
        check(captureRows(stage, origin) == rows) { "Reality12 readback changed after close" }
    }

    fun force(file: Path) {
        FileChannel.open(file, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
        FileChannel.open(file.parent, READ, NOFOLLOW_LINKS).use { it.force(true) }
    }
}
