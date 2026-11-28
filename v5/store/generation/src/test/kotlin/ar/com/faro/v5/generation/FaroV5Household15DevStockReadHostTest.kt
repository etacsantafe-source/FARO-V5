package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import ar.com.faro.v5.store.reality.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household15DevStockReadHostTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val pointer get() = FileActiveGenerationPointer(root)
    private val ticket get() = root.resolve("bootstrap/sql15-evidence2-cutover.ticket")
    private val grant = FaroV5StockReadGrant("human", 10)
    private var liveGrant: FaroV5StockReadGrant? = grant

    private data class Fixture(val selected: ActiveGenerationPointer, val reality: Path)

    private fun fixture(schema15: Boolean = true, completed: Boolean = true): Fixture {
        val original = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("d0b-origin")))
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("d0b-target"), "d0b-isolated", 100),
        )
        val stage = FaroV5Finance9StagedMigration(root, MaterialWriteGateQuiescence(gate)).migrate(
            MaintenanceOperationId("d0b-finance"), fresh, original, "a".repeat(64), 1_000,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(stage, pointer).close()
        val reality = stage.directory.resolve(GenerationStoreFiles.REALITY)
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality)
        RealityHousehold11StagedFilePort.migrateTrustedStagedTen(reality)
        RealityHousehold12StagedFilePort.migrateTrustedStagedEleven(reality) {}
        RealityHousehold13StagedFilePort.migrateTrustedStagedTwelve(reality)
        RealityHousehold14StagedFilePort.migrateTrustedStagedThirteen(reality) {}
        if (schema15) RealityHousehold15StagedFilePort.migrateTrustedStagedFourteen(reality) {}
        CommercialEvidenceStore(
            fileName = stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
            stagedTypedTermsV2 = true, stagedSemanticBackupV2 = true,
        ).close()
        val selected = checkNotNull(pointer.compareAndSet(original.generationId, stage.descriptor.generationId))
        if (completed) seedCompletedSelection(original, selected)
        return Fixture(selected, reality)
    }

    /** Fixture records exercise the actual reader format. The restore coordinator is covered separately. */
    private fun seedCompletedSelection(origin: ActiveGenerationPointer, selected: ActiveGenerationPointer) {
        val operation = MaintenanceOperationId("d0b-restore")
        FileBootstrapJournal(root).create(BootstrapJournalEntry(
            operation, MaintenanceOperationKind.RESTORE, origin.generationId, selected.generationId,
            BootstrapPhase.COMPLETED, materialWritesAccepted = true,
        ))
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
            it.writeUTF("d0b-precursor")
        }
        val bytes = buffer.toByteArray()
        Files.write(ticket, bytes + MessageDigest.getInstance("SHA-256").digest(bytes))
    }

    private fun host(selected: ActiveGenerationPointer, clock: () -> Long = { 100 }) =
        FaroV5Household15DevStockReadHost(root, selected, gate, grant, { liveGrant }, clock)

    @Test fun readsCommittedWalAndSeparatesPrincipals() {
        val f = fixture()
        val reader = host(f.selected)
        val base = Files.readAllBytes(f.reality)
        BundledSQLiteDriver().open(f.reality.toString()).use { writer ->
            writer.execSQL("PRAGMA wal_autocheckpoint=0")
            writer.execSQL("INSERT INTO household_confirmed_purchase VALUES " +
                "('p1','item',10000,'UNIT','human',1,'MANUAL_EXPLICIT',1)," +
                "('p2','item',10000,'UNIT','other',1,'MANUAL_EXPLICIT',2)")
            writer.execSQL("INSERT INTO household_stock_receipt VALUES " +
                "('r1','i-r1','p1','item',7000,'UNIT',0,20,1)," +
                "('r2','i-r2','p2','item',9000,'UNIT',0,21,2)")
            writer.execSQL("UPDATE household_stock_receipt_state SET revision=2")
            writer.execSQL("INSERT INTO household_stock_consumption_event VALUES " +
                "('c1','i-c1','CONSUME','c1','item','UNIT',1000,'human','use1',NULL,50,51,3)")
            assertTrue(Files.size(f.reality.resolveSibling("reality.db-wal")) > 32)
            val snapshot = reader.read() as FaroV5StockReadResult.Verified
            assertEquals(FaroV5StockPosition("item", "UNIT", 6_000, 0, 1_000, 6_000, 3_000),
                snapshot.positions.single())
            assertEquals(listOf("r1", "c1"), snapshot.history.map { it.eventId })
            assertEquals(snapshot, reader.read())
            assertArrayEquals(base, Files.readAllBytes(f.reality))
        }
    }

    @Test fun deniesRevocationBeforeAndAfterSnapshotAndOnResume() {
        val f = fixture()
        val reader = host(f.selected)
        assertEquals(FaroV5StockCoverage.EMPTY_CONFIRMED, reader.read().coverage)
        liveGrant = null
        assertEquals(FaroV5StockReadResult.Hidden.DENIED, reader.read())
        liveGrant = grant.copy(epoch = 11)
        assertEquals(FaroV5StockReadResult.Hidden.DENIED, reader.read())
        liveGrant = grant
        assertEquals(FaroV5StockReadResult.Hidden.DENIED, host(f.selected) {
            liveGrant = null
            100
        }.read())
    }

    @Test fun hidesOnFullPointerAbaEvenWhenGenerationIdMatches() {
        val f = fixture()
        val reader = host(f.selected)
        val other = checkNotNull(pointer.compareAndSet(f.selected.generationId, StorageGenerationId("d0b-other")))
        val back = checkNotNull(pointer.compareAndSet(other.generationId, f.selected.generationId))
        assertEquals(f.selected.generationId, back.generationId)
        assertEquals(FaroV5StockReadResult.Hidden.STALE, reader.read())
    }

    @Test fun hidesOnMissingOrCorruptTicketAndIncompleteRecovery() {
        val f = fixture(completed = false)
        val reader = host(f.selected)
        assertEquals(FaroV5StockReadResult.Hidden.UNAVAILABLE, reader.read())
        Files.write(ticket, byteArrayOf(1, 2, 3))
        assertEquals(FaroV5StockReadResult.Hidden.UNAVAILABLE, reader.read())
        Files.delete(ticket)
        val origin = ActiveGenerationPointer(f.selected.sequence - 1, StorageGenerationId("d0b-origin"))
        seedCompletedSelection(origin, f.selected)
        assertEquals(FaroV5StockCoverage.EMPTY_CONFIRMED, reader.read().coverage)
        FileBootstrapJournal(root).create(BootstrapJournalEntry(
            MaintenanceOperationId("d0b-pending"), MaintenanceOperationKind.RESTORE,
            f.selected.generationId, StorageGenerationId("d0b-next"), BootstrapPhase.PREPARING,
        ))
        assertEquals(FaroV5StockReadResult.Hidden.UNAVAILABLE, reader.read())
    }

    @Test fun rejectsSql14EvenWithCompletionRecords() {
        val f = fixture(schema15 = false)
        assertEquals(FaroV5StockReadResult.Hidden.UNAVAILABLE, host(f.selected).read())
    }

    @Test fun hidesWhenRootLeaseIsBusyOrWalIsAliased() {
        val f = fixture()
        val reader = host(f.selected)
        FileChannel.open(root.resolve("finance9-dev-root.lock"), CREATE, WRITE).use { channel ->
            channel.lock().use { assertEquals(FaroV5StockReadResult.Hidden.UNAVAILABLE, reader.read()) }
        }
        val wal = f.reality.resolveSibling("reality.db-wal")
        Files.createSymbolicLink(wal, Files.writeString(root.resolve("fake-wal"), "not SQLite"))
        assertEquals(FaroV5StockReadResult.Hidden.UNAVAILABLE, reader.read())
    }
}
