package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household11V8RestoreAdmissionTest {
    @TempDir lateinit var root: Path

    private val pointer get() = FileActiveGenerationPointer(root)
    private val gate = MaterialWriteGateQuiescence(SingleProcessMaterialWriteGate())
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val passphrase = "w05-a1b3a-isolated-development-passphrase".toCharArray()
    private val op = MaintenanceOperationId("w05-a1b3a-restore-admission")

    private data class Fixture(
        val active: ActiveGenerationPointer,
        val source: StagedStorageGeneration,
        val target: StagedStorageGeneration,
        val snapshot: FaroV5Household11Fbs2Snapshot,
        val archive: ByteArray,
    )

    private fun stage(id: String, active: ActiveGenerationPointer, auxiliary4: Boolean = true):
        StagedStorageGeneration {
        val created = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId(id), "w05-v8-admission", 100L),
        )
        FaroV5Finance9StagedMigration(root, gate).migrate(
            MaintenanceOperationId("finance9-" + id), created, active, "a".repeat(64), 1000L,
        )
        if (auxiliary4) {
            FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(
                created, pointer,
            ).close()
        }
        return created
    }

    private fun fixture(
        receipts: Household11ReceiptBackupSection = Household11ReceiptBackupSection(0L, emptyList()),
    ): Fixture {
        val active = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("w05-v8-active")))
        val source = stage("w05-v8-source", active)
        val target = stage("w05-v8-target", active)
        val legacy = FaroV5Auxiliary4ArchiveCoordinator(root, gate).captureAuthenticated(
            MaintenanceOperationId("capture-v8-source"),
            BackupSetId("w05-v8-source-backup"),
            source, active, "5.0-dev", 1100L, passphrase,
        )
        val base = when (val read = crypto.openWithReceipts(legacy, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> read.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Fixture source rejected: " + read.reason)
        }
        val snapshot = FaroV5Household11Fbs2Snapshot(
            FaroV5Household10Fbs2Snapshot(base, Household10BackupSection(0L, emptyList())),
            receipts,
        )
        return Fixture(active, source, target, snapshot,
            crypto.sealWithHouseholdReceipts11(snapshot, passphrase))
    }

    private fun port(
        host: () -> Unit = {},
        afterAuth: () -> Unit = {},
    ) = FaroV5Household11V8RestoreAdmission(root, gate, host, afterAuth)

    private fun inspect(f: Fixture, archive: ByteArray = f.archive, key: CharArray = passphrase) =
        port().inspect(op, f.target, f.active, archive, key)

    private fun bytesOf(stage: StagedStorageGeneration) =
        GenerationStoreFiles.WORKSPACE_V2.map { name ->
            Files.readAllBytes(stage.directory.resolve(name))
        }

    @Test fun acceptsOnlyAuthenticatedV8AndLeavesEveryStoreAndPointerUnmodified() {
        val f = fixture()
        val before = bytesOf(f.target)
        val result = inspect(f)
        assertEquals(f.snapshot.base.base.base.fourStore.manifest.sourceGenerationId,
            result.sourceGenerationId)
        assertEquals(f.target.descriptor.generationId, result.targetGenerationId)
        assertEquals(f.active, result.activePointer)
        assertEquals(1, result.evidenceSchema)
        assertEquals(0L, result.receiptRevision)
        assertEquals(0, result.receiptCount)
        assertEquals(MessageDigest.getInstance("SHA-256")
            .digest(f.archive).joinToString("") { "%02x".format(it) }, result.archiveSha256)
        bytesOf(f.target).zip(before).forEach { (actual, old) -> assertArrayEquals(old, actual) }
        assertEquals(f.active, pointer.read())
    }

    @Test fun preservesPartialReceiptsAndRevisedEmptyWithoutInventingStock() {
        val f = fixture(Household11ReceiptBackupSection(
            2L, listOf(
                Household11ReceiptBackupRow("a", "intent-a", "purchase", "yerba",
                    7_000, "UNIT", 0L, 1000L, 1L),
                Household11ReceiptBackupRow("b", "intent-b", "purchase", "yerba",
                    3_000, "UNIT", 0L, 1001L, 2L),
            ),
        ))
        assertEquals(2, inspect(f).receiptCount)
        assertEquals(2L, inspect(f).receiptRevision)
        val revised = f.snapshot.copy(receipts = Household11ReceiptBackupSection(9L, emptyList()))
        val revisedArchive = crypto.sealWithHouseholdReceipts11(revised, passphrase)
        assertEquals(9L, inspect(f, revisedArchive).receiptRevision)
        assertEquals(0, inspect(f, revisedArchive).receiptCount)
    }

    @Test fun wrongPasswordCipherTagAndHeaderFailBeforeTargetMutation() {
        val f = fixture()
        val before = bytesOf(f.target)
        val modifiedTag = f.archive.clone().also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        val modifiedHeader = f.archive.clone().also { it[0] = 0 }
        val attempts = listOf(
            f.archive to "w05-wrong-secret-passphrase".toCharArray(),
            modifiedTag to passphrase,
            modifiedHeader to passphrase,
        )
        attempts.forEach { (archive, key) ->
            assertThrows(IllegalArgumentException::class.java) { inspect(f, archive, key) }
        }
        bytesOf(f.target).zip(before).forEach { (actual, old) -> assertArrayEquals(old, actual) }
    }

    @Test fun previouslyCertifiedV5V6AreNotV8RestoreGrants() {
        val f = fixture()
        val oldV5 = crypto.sealWithReceipts(f.snapshot.base.base, passphrase)
        val oldV6 = crypto.sealWithHousehold(f.snapshot.base, passphrase)
        listOf(oldV5, oldV6).forEach {
            assertThrows(IllegalArgumentException::class.java) { inspect(f, it) }
        }
        assertEquals(f.active, pointer.read())
    }

    @Test fun sourceTargetIdentityConflictAndActiveTargetAreDenied() {
        val f = fixture()
        // Valid archive, but the staged "source" must not be its own restore target.
        assertThrows(IllegalArgumentException::class.java) {
            port().inspect(op, f.source, f.active, f.archive, passphrase)
        }
        val switched = checkNotNull(
            pointer.compareAndSet(f.active.generationId, f.target.descriptor.generationId),
        )
        assertThrows(IllegalStateException::class.java) {
            port().inspect(op, f.target, switched, f.archive, passphrase)
        }
    }

    @Test fun targetSchemaElevenAndLegacyAuxiliaryThreeCannotPassAdmission() {
        val f = fixture()
        BundledSQLiteDriver().open(
            f.target.directory.resolve(GenerationStoreFiles.REALITY).toString(),
        ).use { it.execSQL("PRAGMA user_version = 11") }
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        val oldTarget = stage("w05-v8-auxiliary3", f.active, auxiliary4 = false)
        assertThrows(IllegalStateException::class.java) {
            port().inspect(op, oldTarget, f.active, f.archive, passphrase)
        }
    }

    @Test fun hardlinkAndSqliteSidecarAreRejectedBeforeAuthentication() {
        val f = fixture()
        val file = f.target.directory.resolve(GenerationStoreFiles.REALITY)
        val alias = Files.createLink(root.resolve("w05-v8-hardlink.db"), file)
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        Files.delete(alias)
        val sidecar = file.resolveSibling(file.fileName.toString() + "-wal")
        Files.writeString(sidecar, "not an immutable stage")
        assertThrows(IllegalStateException::class.java) { inspect(f) }
        Files.delete(sidecar)
        assertEquals(f.active, inspect(f).activePointer)
    }

    @Test fun revokedHostAndPointerAbaCannotAuthorizeRestore() {
        val f = fixture()
        assertThrows(IllegalStateException::class.java) {
            port(host = { error("exclusive closed DEV host revoked") })
                .inspect(op, f.target, f.active, f.archive, passphrase)
        }
        assertThrows(IllegalStateException::class.java) {
            port(afterAuth = {
                val intermediate = StorageGenerationId("w05-v8-aba")
                checkNotNull(pointer.compareAndSet(f.active.generationId, intermediate))
                checkNotNull(pointer.compareAndSet(intermediate, f.active.generationId))
            }).inspect(op, f.target, f.active, f.archive, passphrase)
        }
        assertEquals(f.active.generationId, checkNotNull(pointer.read()).generationId)
        assertEquals(0, crypto.openWithHouseholdReceipts11(f.archive, passphrase).let {
            when (it) {
                is FiveStoreAuthenticatedPayload.Opened -> it.payload.receipts.receipts.size
                is FiveStoreAuthenticatedPayload.Rejected -> error("auth failed")
            }
        })
    }
}
