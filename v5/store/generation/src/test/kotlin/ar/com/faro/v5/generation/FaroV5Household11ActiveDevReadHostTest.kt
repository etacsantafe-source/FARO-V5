package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_NOFOLLOW
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household11ActiveDevReadHostTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val lease = MaterialWriteGateQuiescence(gate)
    private val pointer get() = FileActiveGenerationPointer(root)
    private val key = "w05-c1c-closed-host-test".toCharArray()
    private val operation = MaintenanceOperationId("w05-c1c-dev-switch")
    private val crypto = FaroV5FiveStorePortableBackupCrypto()

    private data class Scenario(
        val origin: ActiveGenerationPointer,
        val target: StagedStorageGeneration,
        val archive: ByteArray,
    )

    private fun stage(name: String, origin: ActiveGenerationPointer): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(name), "w05-c1c-dev", 100L))
        val finance = FaroV5Finance9StagedMigration(root, lease).migrate(
            MaintenanceOperationId("w05-c1c-finance-" + name),
            fresh, origin, "a".repeat(64), 1000L)
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(finance, pointer).close()
        return finance
    }

    private fun scenario(): Scenario {
        val initial = checkNotNull(pointer.compareAndSet(
            null, StorageGenerationId("w05-c1c-bootstrap")))
        val original = stage("w05-c1c-origin", initial)
        val origin = checkNotNull(pointer.compareAndSet(
            initial.generationId, original.descriptor.generationId))
        val source = stage("w05-c1c-source", origin)
        val target = stage("w05-c1c-target", origin)
        val originalArchive = FaroV5Auxiliary4ArchiveCoordinator(root, lease)
            .captureAuthenticated(MaintenanceOperationId("w05-c1c-capture"),
                BackupSetId("w05-c1c-backup"), source, origin, "5.0-dev", 3000L, key)
        val base = when (val p = crypto.openWithReceipts(originalArchive, key)) {
            is FiveStoreAuthenticatedPayload.Opened -> p.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error(p.reason)
        }
        val needs = Household10BackupSection(3L, listOf(HouseholdNeed(
            HouseholdNeedId("need-yerba"), HouseholdNeedName("Yerba"),
            HouseholdNeedQuantity(500L, HouseholdNeedUnit.GRAM),
            HouseholdNeedPriority.NORMAL, HouseholdNeedStatus.OPEN, 1L,
        )))
        val receipts = Household11ReceiptBackupSection(2L, listOf(
            Household11ReceiptBackupRow(
                "receipt-a", "intent-a", "purchase", "yerba", 7000, "UNIT", 0L, 3000L, 1L),
            Household11ReceiptBackupRow(
                "receipt-b", "intent-b", "purchase", "yerba", 3000, "UNIT", 0L, 3001L, 2L),
        ))
        val encrypted = crypto.sealWithHouseholdReceipts11(
            FaroV5Household11Fbs2Snapshot(
                FaroV5Household10Fbs2Snapshot(base, needs), receipts), key)
        assertEquals(
            FiveStoreStagedRestoreResult.StagedVerified(target.descriptor.generationId.value),
            FaroV5Household11V8FullStageReplay(root, lease, {})
                .restoreAuthenticatedStagedEleven(operation, target, origin, encrypted, key))
        FaroV5Household11DevCutoverAuthorization.acquire(root, gate, { true }).use {
            assertEquals(FaroV5Household11DevCutoverAuthorization.Status.VALIDATED,
                it.authorize(operation, target, origin, encrypted, key, { true }).status)
        }
        return Scenario(origin, target, encrypted)
    }

    private fun activate(s: Scenario) {
        FaroV5Household11DevJournaledCutover.acquire(root, gate, { true }).use {
            assertEquals(FaroV5Household11DevJournaledCutover.Status.ACTIVE_ELEVEN,
                it.start(operation, s.target, s.origin, s.archive, key, { true }).status)
        }
    }

    private fun reopen(closedHost: () -> Boolean = { true }) =
        FaroV5Household11ActiveDevReadHost.reopen(root, gate, closedHost)

    @Test fun absentDirectoryFailsClosed() {
        assertEquals(FaroV5Household11ActiveDevReadHost.Status.BLOCKED,
            FaroV5Household11ActiveDevReadHost.reopen(root.resolve("absent"), gate, { true }).status)
    }

    @Test fun noTicketIsUnavailableWithoutNewGeneration() {
        assertEquals(FaroV5Household11ActiveDevReadHost.Status.NO_TICKET, reopen().status)
        assertEquals(null, pointer.read())
    }

    @Test fun symbolicRootIsRejected() {
        val link = root.resolve("root-link")
        Files.createSymbolicLink(link, root)
        assertEquals(FaroV5Household11ActiveDevReadHost.Status.BLOCKED,
            FaroV5Household11ActiveDevReadHost.reopen(link, gate, { true }).status)
    }

    @Test fun pendingValidatedTicketDoesNotAutoSwitch() {
        val s = scenario()
        assertEquals(FaroV5Household11ActiveDevReadHost.Status.AWAITING_FOREGROUND,
            reopen().status)
        assertEquals(s.origin, pointer.read())
    }

    @Test fun committedSql11ReopensWithExactReadOnlyLeasedReality() {
        val s = scenario()
        activate(s)
        val pinned = checkNotNull(pointer.read())
        val result = reopen()
        assertEquals(FaroV5Household11ActiveDevReadHost.Status.ACTIVE_ELEVEN, result.status)
        val session = checkNotNull(result.session)
        session.use {
            val reality = it.readPinnedReality { file -> file }
            assertEquals(s.target.directory.resolve(GenerationStoreFiles.REALITY), reality)
            assertNotNull(Files.size(reality))
            assertEquals(pinned, pointer.read())
        }
    }

    @Test fun pointerAbaRevokesLeaseEvenIfGenerationIdReturns() {
        val s = scenario()
        activate(s)
        val result = reopen()
        assertEquals(FaroV5Household11ActiveDevReadHost.Status.ACTIVE_ELEVEN, result.status)
        val session = checkNotNull(result.session)
        session.use {
            val before = checkNotNull(pointer.read())
            checkNotNull(pointer.compareAndSet(before.generationId, s.origin.generationId))
            checkNotNull(pointer.compareAndSet(s.origin.generationId, before.generationId))
            assertThrows(IllegalStateException::class.java) {
                it.readPinnedReality { path -> path }
            }
        }
    }

    @Test fun revokedForegroundDeniesFurtherReads() {
        val s = scenario()
        activate(s)
        var live = true
        val result = reopen { live }
        assertEquals(FaroV5Household11ActiveDevReadHost.Status.ACTIVE_ELEVEN, result.status)
        checkNotNull(result.session).use {
            live = false
            assertThrows(IllegalStateException::class.java) {
                it.readPinnedReality { path -> path }
            }
        }
    }

    @Test fun postClosePortCannotReadOrReactivate() {
        activate(scenario())
        val session = checkNotNull(reopen().session)
        session.close()
        session.close()
        assertThrows(IllegalStateException::class.java) {
            session.readPinnedReality { it }
        }
    }

    @Test fun tamperedTicketIsNeverAdopted() {
        activate(scenario())
        val ticket = root.resolve("bootstrap/household11-dev-cutover-v1.bin")
        val bytes = Files.readAllBytes(ticket)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(ticket, bytes)
        assertEquals(FaroV5Household11ActiveDevReadHost.Status.BLOCKED, reopen().status)
    }

    @Test fun rootLockCollisionFailsClosedWithoutLease() {
        activate(scenario())
        val lockFile = root.resolve("finance9-dev-root.lock")
        java.nio.channels.FileChannel.open(lockFile, java.nio.file.StandardOpenOption.WRITE).use { c ->
            c.lock().use {
                assertEquals(FaroV5Household11ActiveDevReadHost.Status.BLOCKED, reopen().status)
            }
        }
    }

    @Test fun completedSecondReopenAfterReadOnlyWalInspectionPreservesFullPointer() {
        val s = scenario()
        activate(s)
        val before = checkNotNull(pointer.read())
        val first = reopen()
        assertEquals(FaroV5Household11ActiveDevReadHost.Status.ACTIVE_ELEVEN, first.status)
        checkNotNull(first.session).use { session ->
            assertEquals(FaroV5Household11ActiveDevReadHost.AuthorizedRows(3L, 1, 2L, 2),
                session.expectedRows())
            session.readPinnedReality { file ->
                BundledSQLiteDriver().open(
                    file.toString(), SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW,
                ).use { db ->
                    val count = db.prepare("SELECT count(*) FROM household_stock_receipt").use {
                        assertTrue(it.step())
                        it.getLong(0)
                    }
                    assertEquals(2L, count)
                }
            }
        }
        // SQLite WAL readonly may leave -wal/-shm; C1C must NOT call C1B's
        // sealed STAGED verifier again when journal already is COMPLETED.
        val second = reopen()
        assertEquals(FaroV5Household11ActiveDevReadHost.Status.ACTIVE_ELEVEN, second.status)
        checkNotNull(second.session).use { session ->
            assertEquals(2L, session.readPinnedReality { file ->
                BundledSQLiteDriver().open(
                    file.toString(), SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW,
                ).use { db ->
                    db.prepare("SELECT count(*) FROM household_stock_receipt").use {
                        assertTrue(it.step()); it.getLong(0)
                    }
                }
            })
        }
        assertEquals(before, pointer.read())
    }

    @Test fun uncommittedWalIsNeverReadAsACommittedRevision() {
        val s = scenario()
        activate(s)
        checkNotNull(reopen().session).use { session ->
            val path = s.target.directory.resolve(GenerationStoreFiles.REALITY)
            BundledSQLiteDriver().open(path.toString()).use { db ->
                db.execSQL("PRAGMA journal_mode=WAL")
                db.execSQL("BEGIN IMMEDIATE TRANSACTION")
                db.execSQL("UPDATE household_stock_receipt_state SET revision=99 WHERE singleton_id=1")
                try {
                    // ACTIVE readonly preflight must not be fooled by immutable=1.
                    val version = session.readPinnedReality { Files.size(it) }
                    assertTrue(version > 0L)
                } finally {
                    db.execSQL("ROLLBACK TRANSACTION")
                }
            }
        }
    }
}
