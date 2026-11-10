package ar.com.faro.v5.generation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5AuxiliaryV3PreflightTest {
    @TempDir lateinit var root: Path
    private fun fresh(name: String): StagedStorageGeneration =
        FaroV5WorkspaceV2StageOwner(root.resolve(name)).stageFreshAndVerify(
            StorageGenerationDescriptor(StorageGenerationId(name), "guardados-v3", 1L))

    private fun alter(staged: StagedStorageGeneration, legacy: Boolean = false, future: Boolean = false) {
        BundledSQLiteDriver().open(staged.directory.resolve(GenerationStoreFiles.AUXILIARY).toString()).use {
            if (!future) {
                it.execSQL("DROP TABLE bookmark_receipt")
                if (legacy) {
                    it.execSQL("DROP TABLE bookmark_item")
                    it.execSQL("DROP TABLE bookmark_folder")
                    it.execSQL("DROP TABLE bookmark_scope")
                }
            }
            if (legacy) it.execSQL("PRAGMA user_version=2")
            if (future) it.execSQL("PRAGMA user_version=4")
        }
    }

    private fun fingerprints(staged: StagedStorageGeneration) =
        GenerationStoreFiles.WORKSPACE_V2.associateWith {
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(staged.directory.resolve(it))).toList()
        }

    @Test fun currentSchemaThreeOpensStagedAndActiveWithoutGrantingBookmarks() {
        val staged = fresh("current")
        FaroV5GenerationStoreGraph.openValidatedV2(staged).use {
            assertTrue(it.verifyHealthy())
            assertEquals(3, it.auxiliary.backup.snapshot().schemaVersion)
        }
        FaroV5GenerationStoreGraph.openActiveV2WithSharedGate(staged,
            PreparedPlanEnvelopeVerificationPort { _, _ -> false }, SingleProcessMaterialWriteGate()).use {
            assertTrue(it.verifyHealthy())
        }
    }

    @Test fun exactLegacyTwoMayMigrateOnlyInsideSealedStage() {
        val staged = fresh("legacy-stage")
        alter(staged, legacy = true)
        FaroV5GenerationStoreGraph.openValidatedV2(staged).use {
            assertEquals(3, it.auxiliary.backup.snapshot().schemaVersion)
            assertTrue(it.verifyHealthy())
            assertNotNull(it.auxiliary.backup.snapshot().bookmarks)
        }
    }

    @Test fun activeLegacyTwoIsRejectedBeforeAnyStoreMigration() {
        val staged = fresh("legacy-active")
        alter(staged, legacy = true)
        val before = fingerprints(staged)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openActiveV2WithSharedGate(staged,
                PreparedPlanEnvelopeVerificationPort { _, _ -> false }, SingleProcessMaterialWriteGate()).close()
        }
        assertEquals(before, fingerprints(staged))
    }

    @Test fun versionThreeWithMissingBookmarkTableCannotOpenOrInitialize() {
        val staged = fresh("incomplete")
        alter(staged)
        val before = fingerprints(staged)
        assertThrows(IllegalStateException::class.java) { FaroV5GenerationStoreGraph.openValidatedV2(staged).close() }
        assertEquals(before, fingerprints(staged))
    }

    @Test fun futureAuxiliaryVersionIsRejectedWithoutTouchingOtherStores() {
        val staged = fresh("future")
        alter(staged, future = true)
        val before = fingerprints(staged)
        assertThrows(IllegalStateException::class.java) { FaroV5GenerationStoreGraph.openValidatedV2(staged).close() }
        assertEquals(before, fingerprints(staged))
    }
}
