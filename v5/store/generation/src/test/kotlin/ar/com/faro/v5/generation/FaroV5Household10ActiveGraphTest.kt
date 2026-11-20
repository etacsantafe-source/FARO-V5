package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityHousehold10StagedFilePort
import ar.com.faro.v5.store.reality.RealityHousehold10SealedRowsPort
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5Household10ActiveGraphTest {
    @TempDir lateinit var root: Path
    private val gate = SingleProcessMaterialWriteGate()
    private val pointer get() = FileActiveGenerationPointer(root)

    @Test
    fun activeReadReopensWithSameRowsAndNoDataChanges() {
        val (stage, expected) = selected()
        val files = GenerationStoreFiles.WORKSPACE_V2.map { stage.directory.resolve(it) }
        val before = files.map(Files::readAllBytes)
        val snapshot = RealityHousehold10SealedRowsPort.readTrustedSealedStageTen(reality(stage))
        repeat(2) {
            open(stage, expected).use { assertEquals(snapshot, it.readNeeds { true }) }
        }
        files.zip(before).forEach { (file, bytes) -> assertArrayEquals(bytes, Files.readAllBytes(file)) }
        assertEquals(expected, pointer.read())
    }

    @Test
    fun deniesOwnerOrClosedGateAndObservesPointerAba() {
        val (stage, expected) = selected()
        assertThrows(IllegalStateException::class.java) { open(stage, expected) { false } }
        var admitted = true
        open(stage, expected) { admitted }.use { session ->
            admitted = false
            assertThrows(IllegalStateException::class.java) { session.readNeeds { true } }
            admitted = true
            gate.closeMaterialWriteAdmission()
            try {
                assertThrows(IllegalStateException::class.java) { session.readNeeds { true } }
            } finally {
                gate.openMaterialWriteAdmission()
            }
            val other = StorageGenerationId("other")
            checkNotNull(pointer.compareAndSet(expected.generationId, other))
            checkNotNull(pointer.compareAndSet(other, expected.generationId))
            assertThrows(IllegalStateException::class.java) { session.readNeeds { true } }
        }
        assertThrows(IllegalStateException::class.java) { open(stage, expected) }
    }

    @Test
    fun sharesExclusiveLeaseWithClosedHostAndReleasesOnClose() {
        val (stage, expected) = selected()
        FaroV5Household10DevCutoverRuntime.acquire(root, gate, { true }).use {
            assertThrows(IllegalStateException::class.java) { open(stage, expected) }
        }
        open(stage, expected).use {
            assertThrows(IllegalStateException::class.java) { open(stage, expected) }
        }
        open(stage, expected).close()
    }

    @Test
    fun rejectsWrongRealityAndAuxiliarySchemasAndFakePath() {
        val (stage, expected) = selected(ten = false)
        assertThrows(IllegalStateException::class.java) { open(stage, expected) }
        RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality(stage))
        assertThrows(IllegalStateException::class.java) { open(stage.copy(directory = root), expected) }
        BundledSQLiteDriver().open(stage.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use {
            it.execSQL("PRAGMA user_version=3")
        }
        assertThrows(IllegalStateException::class.java) { open(stage, expected) }
    }

    @Test
    fun rejectsTriggersMissingTablesAndForeignKeyCorruption() {
        val (stage, expected) = selected()
        BundledSQLiteDriver().open(reality(stage).toString()).use {
            it.execSQL("CREATE TRIGGER unexpected AFTER INSERT ON household_need BEGIN SELECT 1; END")
        }
        assertThrows(IllegalStateException::class.java) { open(stage, expected) }
        BundledSQLiteDriver().open(reality(stage).toString()).use {
            it.execSQL("DROP TRIGGER unexpected")
            it.execSQL("PRAGMA foreign_keys=OFF")
            it.execSQL("INSERT INTO finance_account_opening VALUES ('missing','bad-cause',5)")
        }
        assertThrows(IllegalStateException::class.java) { open(stage, expected) }
        BundledSQLiteDriver().open(reality(stage).toString()).use {
            it.execSQL("DELETE FROM finance_account_opening WHERE account_id='missing'")
            it.execSQL("DROP TABLE household_need")
        }
        assertThrows(IllegalStateException::class.java) { open(stage, expected) }
    }

    private fun reality(stage: StagedStorageGeneration) = stage.directory.resolve(GenerationStoreFiles.REALITY)

    private fun open(stage: StagedStorageGeneration, expected: ActiveGenerationPointer, admit: () -> Boolean = { true }) =
        FaroV5GenerationStoreGraph.openActiveHousehold10DevForTrustedHost(root, stage, expected, gate, admit)

    private fun selected(ten: Boolean = true): Pair<StagedStorageGeneration, ActiveGenerationPointer> {
        val origin = checkNotNull(pointer.compareAndSet(null, StorageGenerationId("origin")))
        val stage = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(
            StorageGenerationDescriptor(StorageGenerationId("household"), "dev-test", 100L),
        )
        val quiescence = object : MaterialWriteQuiescencePort {
            override fun <T> withMaterialWritesQuiesced(operationId: MaintenanceOperationId, block: () -> T): T = block()
        }
        val nine = FaroV5Finance9StagedMigration(root, quiescence).migrate(
            MaintenanceOperationId("prepare-nine"), stage, origin, "a".repeat(64), 1000L,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(nine, pointer).close()
        if (ten) {
            RealityHousehold10StagedFilePort.migrateTrustedStagedNine(reality(nine))
            BundledSQLiteDriver().open(reality(nine).toString()).use {
                it.execSQL("UPDATE household_need_state SET revision=5")
                it.execSQL("INSERT INTO household_need VALUES ('yerba','Yerba',500,'GRAM','NORMAL','OPEN',4,NULL)")
            }
        }
        return nine to checkNotNull(pointer.compareAndSet(origin.generationId, nine.descriptor.generationId))
    }
}
