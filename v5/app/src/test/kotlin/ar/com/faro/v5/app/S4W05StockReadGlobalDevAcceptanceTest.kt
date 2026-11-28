@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.generation.*
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.surface.FaroV5StockUiMovement
import ar.com.faro.v5.surface.FaroV5StockUiPosition
import ar.com.faro.v5.surface.FaroV5StockUiState
import ar.com.faro.v5.surface.FaroV5StockUiUnit
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.Executor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W05StockReadGlobalDevAcceptanceTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val quiescence = MaterialWriteGateQuiescence(gate)
    private val pointer get() = FileActiveGenerationPointer(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val grant = FaroV5StockReadGrant("local-human", 10)
    private val item = "Yerba"

    private data class Frame(
        val id: String,
        val position: FaroV5StockUiPosition,
        val history: List<FaroV5StockUiMovement>,
    )

    @Test
    fun authenticatedFiveStoreReplayReadsTheExactStockScenarioAndFailsClosed() {
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("d2-origin")))
        val passphrase = "a2d2-isolated-fixture".toCharArray()
        val frames = frames()
        val staged = try {
            val archivedBase = captureBase(origin, passphrase)
            val openedBase = crypto.openWithHouseholdEvidence2(archivedBase, passphrase)
            val base = when (val result = openedBase) {
                is FiveStoreAuthenticatedPayload.Opened -> result.payload
                is FiveStoreAuthenticatedPayload.Rejected -> error(result.reason)
            }
            val noReceipt = Household11ReceiptBackupSection(0, emptyList())
            val receipt = Household11ReceiptBackupSection(1, listOf(
                Household11ReceiptBackupRow("receipt", "i-receipt", "purchase", item, 7_000, "UNIT", 0, 20, 1),
            ))
            val reserve = Household14ReservationBackupRow(
                "reserve", "i-reserve", Household14ReservationEventType.RESERVE,
                "reserve", item, "UNIT", 2_000, grant.principal, 21, 2,
            )
            val consume = Household15ConsumptionBackupRow(
                "consume", "i-consume", Household15ConsumptionEventType.CONSUME, "consume",
                item, "UNIT", 1_000, grant.principal, "manual-use", null, 30, 31, 3,
            )
            val undo = consume.copy(eventId = "undo", intentKey = "i-undo",
                type = Household15ConsumptionEventType.CONSUME_UNDO, occurredAtMs = 40, recordedAtMs = 41, revision = 4)
            val release = reserve.copy(eventId = "release", intentKey = "i-release",
                type = Household14ReservationEventType.RELEASE, recordedAtMs = 51, revision = 5)
            val replay = FaroV5Household15V12StagedReplay(root, quiescence, {})
            frames.map { frame ->
                val receiptSection = if (frame.id == "purchase-only") noReceipt else receipt
                val reservations = when (frame.id) {
                    "purchase-only" -> emptyList()
                    "released" -> listOf(reserve, release)
                    else -> listOf(reserve)
                }
                val consumptions = when (frame.id) {
                    "consumed" -> listOf(consume)
                    "undone", "released" -> listOf(consume, undo)
                    else -> emptyList()
                }
                val history = FaroV5Household13Fbs2Snapshot(
                    FaroV5Household11Fbs2Snapshot(base, receiptSection),
                    listOf(Household12PurchaseBackupRow(
                        "purchase", item, 10_000, "UNIT", grant.principal, 1, "MANUAL_EXPLICIT", 1,
                    )),
                    emptyList(),
                )
                val archive = crypto.sealWithHouseholdConsumption15(
                    FaroV5Household15Fbs2Snapshot(
                        FaroV5Household14Fbs2Snapshot(history, reservations), consumptions,
                    ),
                    passphrase,
                )
                val target = stage(frame.id, origin)
                val op = MaintenanceOperationId("d2-replay-" + frame.id)
                val expected = FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value)
                assertEquals(expected, replay.restoreAuthenticatedV12(op, target, origin, archive, passphrase))
                if (frame === frames.last()) {
                    assertEquals(expected, replay.restoreAuthenticatedV12(op, target, origin, archive, passphrase))
                }
                assertEquals(origin, pointer.read())
                target
            }
        } finally {
            passphrase.fill('\u0000')
        }
        var previous = origin
        for ((frame, stage) in frames.zip(staged)) {
            val selected = checkNotNull(pointer.compareAndSet(previous.generationId, stage.descriptor.generationId))
            installCompletedFixture(previous, selected, frame.id)
            S4W05StockReadDevBinding.afterForegroundClick(root, grant, gate, Executor { it.run() }, { true }).use { view ->
                val ready = assertInstanceOf(FaroV5StockUiState.Ready::class.java, view.state.value)
                assertEquals(listOf(frame.position), ready.positions)
                assertEquals(frame.position.physical - frame.position.reserved, ready.positions.single().available)
                assertEquals(frame.history, ready.history.map { it.movement })
            }
            previous = selected
        }

        val selected = previous
        var live: FaroV5StockReadGrant? = grant
        val reopened = FaroV5Household15DevStockReadHost(root, selected, gate, grant, { live }, { 100L })
        val result = assertInstanceOf(FaroV5StockReadResult.Verified::class.java, reopened.read())
        assertEquals(7_000L, result.positions.single().availableThousandths)
        assertEquals(listOf("receipt", "reserve", "consume", "undo", "release"), result.history.map { it.eventId })
        live = null
        assertSame(FaroV5StockReadResult.Hidden.DENIED, reopened.read())
        live = grant
        val other = checkNotNull(pointer.compareAndSet(selected.generationId, origin.generationId))
        checkNotNull(pointer.compareAndSet(other.generationId, selected.generationId))
        assertSame(FaroV5StockReadResult.Hidden.STALE, reopened.read())
    }

    private fun frames(): List<Frame> {
        fun position(physical: Long, reserved: Long, consumed: Long, pending: Long = 3_000) =
            FaroV5StockUiPosition(item, FaroV5StockUiUnit.UNIT, physical, reserved, consumed, pending)
        val reserved = listOf(FaroV5StockUiMovement.RESERVE, FaroV5StockUiMovement.RECEIPT)
        val consumed = listOf(FaroV5StockUiMovement.CONSUME) + reserved
        val undone = listOf(FaroV5StockUiMovement.CONSUME_UNDO) + consumed
        return listOf(
            Frame("purchase-only", position(0, 0, 0, 10_000), emptyList()),
            Frame("reserved", position(7_000, 2_000, 0), reserved),
            Frame("consumed", position(6_000, 2_000, 1_000), consumed),
            Frame("undone", position(7_000, 2_000, 0), undone),
            Frame("released", position(7_000, 0, 0),
                listOf(FaroV5StockUiMovement.RELEASE) + undone),
        )
    }

    private fun stage(id: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("d2-" + id), "w05-a2d2-dev", 100),
        )
        val stage = FaroV5Finance9StagedMigration(root, quiescence).migrate(
            MaintenanceOperationId("d2-finance-" + id), fresh, origin, "a".repeat(64), 1_000,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        CommercialEvidenceStore(
            fileName = stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
            stagedTypedTermsV2 = true, stagedSemanticBackupV2 = true,
        ).close()
        return stage
    }

    private fun captureBase(origin: ActiveGenerationPointer, passphrase: CharArray): ByteArray {
        val source = stage("archive-source", origin)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(
            source.directory.resolve(GenerationStoreFiles.REALITY),
        )
        return FaroV5Household10ArchiveCoordinator(root, quiescence, {}).captureAuthenticatedEvidence2(
            MaintenanceOperationId("d2-capture"), BackupSetId("d2-stock-history"), source, origin,
            "5.0-dev", 3_000, passphrase,
        )
    }

    // Replay creates the verified staged database. These records model completed selection for
    // each historical frame; CI also executes the real journaled cutover suite on the same SHA.
    private fun installCompletedFixture(origin: ActiveGenerationPointer, selected: ActiveGenerationPointer, tag: String) {
        val operation = MaintenanceOperationId("d2-select-" + tag)
        FileBootstrapJournal(root).create(BootstrapJournalEntry(operation, MaintenanceOperationKind.RESTORE,
            origin.generationId, selected.generationId, BootstrapPhase.COMPLETED, materialWritesAccepted = true))
        FileGenerationReplayFence(root).install(MaintenanceOperationKind.RESTORE, selected.generationId)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use {
            it.writeUTF("FARO_SQL15_EVIDENCE2_CUTOVER_V1")
            it.writeUTF(operation.value)
            it.writeLong(origin.sequence)
            it.writeUTF(origin.generationId.value)
            it.writeUTF(selected.generationId.value)
            it.writeUTF(origin.generationId.value)
            repeat(4) { _ -> it.writeUTF("b".repeat(64)) }
            it.writeUTF("d2-precursor")
        }
        val payload = buffer.toByteArray()
        Files.write(root.resolve("bootstrap/sql15-evidence2-cutover.ticket"),
            payload + MessageDigest.getInstance("SHA-256").digest(payload))
    }
}
