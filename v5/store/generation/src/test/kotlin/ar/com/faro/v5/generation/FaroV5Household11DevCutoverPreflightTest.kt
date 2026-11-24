package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household11DevCutoverPreflightTest {
    @TempDir lateinit var root: Path
    private val pointer get() = FileActiveGenerationPointer(root)
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val passphrase = "w05-a1b3c0-sql11-dev-only".toCharArray()
    private val op = MaintenanceOperationId("w05-a1b3c0-sql11-cutover-preflight")

    private data class Fixture(
        val origin: ActiveGenerationPointer,
        val original: StagedStorageGeneration,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household11Fbs2Snapshot,
        val archive: ByteArray,
    )

    private fun stage(id: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val prepared = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "w05-c0-stage", 100L),
        )
        val finance = FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("w05-c0-finance-" + id),
            prepared, origin, "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(
            finance, pointer,
        ).close()
        return finance
    }

    private fun need() = HouseholdNeed(
        HouseholdNeedId("need-yerba"), HouseholdNeedName("Yerba"),
        HouseholdNeedQuantity(500L, HouseholdNeedUnit.GRAM),
        HouseholdNeedPriority.HIGH, HouseholdNeedStatus.OPEN, 1L,
    )

    private fun household() = Household10BackupSection(3L, listOf(need()))

    private fun receipts() = Household11ReceiptBackupSection(2L, listOf(
        Household11ReceiptBackupRow(
            "receipt-1","intent-1","purchase","yerba",7000,"UNIT",0L,3000L,1L,
        ),
        Household11ReceiptBackupRow(
            "receipt-2","intent-2","purchase","yerba",3000,"UNIT",0L,3001L,2L,
        ),
    ))

    private fun fixture(
        needs: Household10BackupSection = household(),
        receiptRows: Household11ReceiptBackupSection = receipts(),
    ): Fixture {
        val initial = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("w05-c0-bootstrap"),
        ))
        val original = stage("w05-c0-origin", initial)
        val origin = checkNotNull(pointer.compareAndSet(
            initial.generationId, original.descriptor.generationId,
        ))
        val source = stage("w05-c0-source", origin)
        val target = stage("w05-c0-target", origin)
        BundledSQLiteDriver().open(
            source.directory.resolve(GenerationStoreFiles.REALITY).toString(),
        ).use { db ->
            db.execSQL("INSERT INTO finance_budget_limit VALUES " +
                "('2026-11','Comida','ARS',180000,1,1000,'HUMAN')")
        }
        val baseCipher = FaroV5Auxiliary4ArchiveCoordinator(root, gate).captureAuthenticated(
            MaintenanceOperationId("w05-c0-fixture"), BackupSetId("w05-c0-backup"),
            source, origin, "5.0-dev", 3000L, passphrase,
        )
        val base = when (val opened = crypto.openWithReceipts(baseCipher, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error(opened.reason.toString())
        }
        val snapshot = FaroV5Household11Fbs2Snapshot(
            FaroV5Household10Fbs2Snapshot(base, needs), receiptRows,
        )
        val archive = crypto.sealWithHouseholdReceipts11(snapshot, passphrase)
        val restored = FaroV5Household11V8FullStageReplay(root, gate, {})
            .restoreAuthenticatedStagedEleven(op, target, origin, archive, passphrase)
        assertEquals(FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value),
            restored)
        return Fixture(origin, original, source, target, snapshot, archive)
    }

    private fun inspect(
        f: Fixture,
        bytes: ByteArray = f.archive,
        secret: CharArray = passphrase,
        host: () -> Unit = {},
        after: () -> Unit = {},
    ) = FaroV5Household11DevCutoverPreflight(root, gate, host, after)
        .inspect(op, f.target, f.origin, bytes, secret)

    private fun file(f: Fixture) = f.target.directory.resolve(GenerationStoreFiles.REALITY)

    @Test fun admitsExactlyRestoredSql11AndDoesNotWritePointerJournalOrStoreBytes() {
        val f = fixture()
        val before = GenerationStoreFiles.WORKSPACE_V2.associateWith {
            Files.readAllBytes(f.target.directory.resolve(it))
        }
        val result = inspect(f)
        assertEquals(f.origin, pointer.read())
        assertEquals(f.origin, result.origin)
        assertEquals(f.target.descriptor.generationId, result.targetGenerationId)
        assertEquals(f.snapshot.base.base.base.fourStore.manifest.sourceGenerationId,
            result.sourceGenerationId)
        assertEquals(3L, result.needsRevision)
        assertEquals(1, result.needsCount)
        assertEquals(2L, result.receiptsRevision)
        assertEquals(2, result.receiptsCount)
        assertEquals(FaroV5Household10DevCutoverOwner.fingerprint(f.target),
            result.targetFingerprint)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(f.archive)
            .joinToString("") { "%02x".format(it) }, result.archiveSha256)
        for ((name, bytes) in before) {
            org.junit.jupiter.api.Assertions.assertArrayEquals(bytes,
                Files.readAllBytes(f.target.directory.resolve(name)))
        }
        assertEquals(null, FileBootstrapJournal(root).read(op))
        assertEquals(emptyList<BootstrapJournalEntry>(), FileBootstrapJournal(root).pending())
    }

    @Test fun revisedEmptyNeedsAndReceiptsDoNotSynthesizeStock() {
        val f = fixture(Household10BackupSection(7L, emptyList()),
            Household11ReceiptBackupSection(9L, emptyList()))
        val observed = inspect(f)
        assertEquals(7L, observed.needsRevision)
        assertEquals(0, observed.needsCount)
        assertEquals(9L, observed.receiptsRevision)
        assertEquals(0, observed.receiptsCount)
        assertEquals(f.origin, pointer.read())
    }

    @Test fun wrongPasswordCipherTagOlderVersionAndChangedNonceAreDenied() {
        val f = fixture()
        val wrongNonce = crypto.sealWithHouseholdReceipts11(f.snapshot, passphrase)
        val tampered = f.archive.clone().also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        val legacy = crypto.sealWithHousehold(f.snapshot.base, passphrase)
        for ((bytes, key) in listOf(
            f.archive to "incorrect-passphrase".toCharArray(),
            wrongNonce to passphrase,
            tampered to passphrase,
            legacy to passphrase,
        )) {
            assertThrows(Exception::class.java) { inspect(f, bytes, key) }
        }
        assertEquals(f.origin, pointer.read())
        assertEquals(f.target.descriptor.generationId, inspect(f).targetGenerationId)
    }

    @Test fun refusesHousehold10DriftAfterAuthenticatedRestore() {
        val f = fixture()
        BundledSQLiteDriver().open(file(f).toString()).use { db ->
            db.execSQL("UPDATE household_need_state SET revision=8 WHERE singleton_id=1")
        }
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        assertEquals(f.origin, pointer.read())
    }

    @Test fun refusesH11ReceiptDriftAfterAuthenticatedRestore() {
        val f = fixture()
        BundledSQLiteDriver().open(file(f).toString()).use { db ->
            db.execSQL("UPDATE household_stock_receipt_state SET revision=8 WHERE singleton_id=1")
        }
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        assertEquals(f.origin, pointer.read())
    }

    @Test fun revokedHostAndPointerAbaFailAfterObservation() {
        val f = fixture()
        assertThrows(IllegalStateException::class.java) {
            inspect(f, host = { error("DEV host revoked") })
        }
        assertThrows(IllegalStateException::class.java) {
            inspect(f, after = {
                val intermediate = StorageGenerationId("w05-c0-aba")
                checkNotNull(pointer.compareAndSet(f.origin.generationId, intermediate))
                checkNotNull(pointer.compareAndSet(intermediate, f.origin.generationId))
            })
        }
        assertEquals(f.origin.generationId, checkNotNull(pointer.read()).generationId)
        assertEquals(null, FileBootstrapJournal(root).read(op))
    }

    @Test fun unfinishedBootstrapJournalBlocksWithoutWritingAnything() {
        val f = fixture()
        val unfinished = BootstrapJournalEntry(
            op, MaintenanceOperationKind.RESTORE, f.origin.generationId,
            f.target.descriptor.generationId, BootstrapPhase.PREPARING,
        )
        FileBootstrapJournal(root).create(unfinished)
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        assertEquals(unfinished, FileBootstrapJournal(root).read(op))
        assertEquals(f.origin, pointer.read())
    }

    @Test fun hardlinkSidecarAndReplayFenceRejectPreCutoverAdmission() {
        val f = fixture()
        val alias = Files.createLink(root.resolve("w05-c0-hardlink.db"), file(f))
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        Files.delete(alias)
        val sidecar = file(f).resolveSibling(GenerationStoreFiles.REALITY + "-wal")
        Files.writeString(sidecar, "unsafe")
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        Files.delete(sidecar)
        assertEquals(f.target.descriptor.generationId, inspect(f).targetGenerationId)
        FileGenerationReplayFence(root).install(
            MaintenanceOperationKind.RESTORE, f.target.descriptor.generationId,
        )
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        assertEquals(f.origin, pointer.read())
    }
}
