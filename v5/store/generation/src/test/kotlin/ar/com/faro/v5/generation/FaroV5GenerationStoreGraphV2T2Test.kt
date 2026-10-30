package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.WebEvidenceStore
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import java.nio.file.Files
import java.nio.file.Path
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import java.security.MessageDigest
import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import androidx.sqlite.execSQL
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Linux/glibc native SQLite T2 only. Android Bionic Termux JVM cannot load
 * the bundled Linux driver: never misclassify that host failure as product.
 */
class FaroV5GenerationStoreGraphV2T2Test {
    @TempDir lateinit var root: Path

    private fun stage(id: String): StagedStorageGeneration =
        FileStorageGenerationLayout(root).stageWorkspaceV2(
            StorageGenerationDescriptor(
                StorageGenerationId(id), "schema-five-store-1", 4L
            )
        )

    private fun materializeFive(staged: StagedStorageGeneration) {
        val dir = staged.directory
        RealityStore(dir.resolve(GenerationStoreFiles.REALITY).toString()).use { }
        CommercialEvidenceStore(dir.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString()).use { }
        WebEvidenceStore(dir.resolve(GenerationStoreFiles.WEB_EVIDENCE).toString()).use { }
        AuxiliaryDeliveryStore(dir.resolve(GenerationStoreFiles.AUXILIARY).toString()).use { }
        ConversationWorkspaceStore(dir.resolve(GenerationStoreFiles.WORKSPACE).toString()).use { }
    }

    /** Inserts a sealed, digest-correct but synthetic prepared plan. The test
     * verifier is not production authorization: only kernel:runtime is trusted.
     */
    private fun materializePendingPlan(staged: StagedStorageGeneration): ByteArray {
        materializeFive(staged)
        val envelope = "synthetic-plan-envelope-only-for-verifier-wiring".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(envelope)
            .joinToString("") { "%02x".format(it) }
        val path = staged.directory.resolve(GenerationStoreFiles.WORKSPACE).toString()
        BundledSQLiteDriver().open(path, SQLITE_OPEN_READWRITE).use { connection ->
            connection.execSQL(
                """INSERT INTO workspace_conversation
                   (id, title, activity_ms, pinned, archived, pending_review)
                   VALUES ('chat-review', 'Chat review', 1, 0, 0, 1)"""
            )
            connection.prepare(
                """INSERT INTO workspace_plan_review
                   (review_id, conversation_id, fingerprint, review_text, review_revision,
                    state, envelope, envelope_sha256, transaction_id, receipt_id)
                   VALUES ('review-one', 'chat-review', ?, 'Review only', 0,
                           'PENDING', ?, ?, NULL, NULL)"""
            ).use { statement ->
                statement.bindText(1, "b".repeat(64))
                statement.bindBlob(2, envelope)
                statement.bindText(3, sha)
                statement.step()
            }
        }
        return envelope
    }

    @Test fun unverifiedPreparedPlanCannotBeExposedByDefaultFiveStoreOpener() {
        val staged = stage("deny-all-prepared")
        materializePendingPlan(staged)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2(staged).use { }
        }
        // No automatic activation or erasure of the quarantined authority.
        assertTrue(Files.exists(staged.directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }

    @Test fun explicitVerifierIsRequiredAndConsultedBeforeExposingPreparedV2Graph() {
        val staged = stage("verified-prepared")
        val envelope = materializePendingPlan(staged)
        val calls = AtomicInteger(0)
        val testOnlyVerifier = PreparedPlanEnvelopeVerificationPort { bytes, fingerprint ->
            calls.incrementAndGet()
            bytes.contentEquals(envelope) && fingerprint == PlanFingerprint("b".repeat(64))
        }
        FaroV5GenerationStoreGraph.openValidatedV2WithPlanVerifier(
            staged, testOnlyVerifier,
        ).use { graph ->
            assertTrue(graph.verifyHealthy())
            assertEquals(1, graph.workspace!!.backup.snapshot().planReviews.size)
            assertEquals("review-one", graph.workspace!!.backup.snapshot().planReviews.single().reviewId)
        }
        assertTrue(calls.get() >= 2, "sealed V2 must verify on open and later read")
    }

    @Test fun falseVerifierRejectsPreparedV2GraphWithoutFallback() {
        val staged = stage("reject-prepared")
        materializePendingPlan(staged)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2WithPlanVerifier(
                staged, PreparedPlanEnvelopeVerificationPort { _, _ -> false },
            ).use { }
        }
    }

    @Test fun fiveStoreV2CanOpenOnlyThroughExplicitGenerationOwner() {
        val staged = stage("valid-five")
        materializeFive(staged)
        FaroV5GenerationStoreGraph.openValidatedV2(staged).use { graph ->
            assertEquals(staged.descriptor.generationId.value, graph.generationId)
            assertNotNull(graph.workspace)
            assertEquals(0L, graph.workspace!!.snapshot().revision)
            assertTrue(graph.verifyHealthy())
        }
        assertTrue(Files.isRegularFile(staged.directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }

    @Test fun validFiveStoreV2IsRejectedByHistoricalFourStoreAppOpener() {
        val staged = stage("v2-fenced")
        materializeFive(staged)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.open(
                staged.directory, staged.descriptor.generationId.value
            )
        }
        FaroV5GenerationStoreGraph.openValidatedV2(staged).use { graph ->
            assertTrue(graph.verifyHealthy())
        }
    }

    @Test fun incompleteV2NeverCreatesWorkspaceDatabaseOnOwnerOpen() {
        val staged = stage("no-fifth")
        GenerationStoreFiles.LEGACY_V1.forEach {
            Files.createFile(staged.directory.resolve(it))
        }
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2(staged)
        }
        assertFalse(Files.exists(staged.directory.resolve(GenerationStoreFiles.WORKSPACE)))
    }

    @Test fun uninitializedFifthDatabaseCannotBeImplicitlyUpgraded() {
        val staged = stage("workspace-empty")
        GenerationStoreFiles.WORKSPACE_V2.forEach {
            Files.createFile(staged.directory.resolve(it))
        }
        val file = staged.directory.resolve(GenerationStoreFiles.WORKSPACE)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2(staged)
        }
        assertEquals(0L, Files.size(file))
    }

    @Test fun malformedFifthDatabaseDoesNotCreateAnyLegacyAuthority() {
        val staged = stage("workspace-invalid")
        GenerationStoreFiles.WORKSPACE_V2.forEach {
            Files.createFile(staged.directory.resolve(it))
        }
        Files.writeString(staged.directory.resolve(GenerationStoreFiles.WORKSPACE), "corrupt")
        assertThrows(Exception::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2(staged)
        }
        GenerationStoreFiles.LEGACY_V1.forEach {
            assertEquals(0L, Files.size(staged.directory.resolve(it)))
        }
    }
    /** Byte fingerprints, including sidecars, prove rejection did not mutate the staged set. */
    private fun directoryFingerprint(dir: Path): Map<String, String> =
        Files.list(dir).use { paths ->
            paths.filter { Files.isRegularFile(it) }.toList().associate { file ->
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file))
                    .joinToString("") { "%02x".format(it) }
                file.fileName.toString() to digest
            }
        }

    @Test fun incompleteLegacySchemasFailWithoutWritingOrCreatingSidecars() {
        val storeNames = GenerationStoreFiles.LEGACY_V1
        for ((index, name) in storeNames.withIndex()) {
            val staged = stage("legacy-version-$index")
            materializeFive(staged)
            // Keep SQLite magic header but convert a legacy store into an
            // outdated schema. This used to invoke normal CREATE/MIGRATE.
            val path = staged.directory.resolve(name)
            BundledSQLiteDriver().open(path.toString(), SQLITE_OPEN_READWRITE).use {
                it.prepare("PRAGMA user_version = 0").use { statement -> statement.step() }
            }
            val before = directoryFingerprint(staged.directory)
            assertThrows(IllegalStateException::class.java) {
                FaroV5GenerationStoreGraph.openValidatedV2(staged)
            }
            assertEquals(before, directoryFingerprint(staged.directory), name)
        }
    }

    @Test fun matchingSchemaVersionWithMissingTableFailsBeforeWritableOpen() {
        val staged = stage("missing-table")
        materializeFive(staged)
        val path = staged.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE)
        BundledSQLiteDriver().open(path.toString(), SQLITE_OPEN_READWRITE).use {
            it.prepare("DROP TABLE commercial_evidence").use { statement -> statement.step() }
        }
        val before = directoryFingerprint(staged.directory)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2(staged)
        }
        assertEquals(before, directoryFingerprint(staged.directory))
    }

    @Test fun schema4MissingPlanReviewTableFailsBeforeAnyWritableOpen() {
        val staged = stage("missing-plan-review")
        materializeFive(staged)
        val path = staged.directory.resolve(GenerationStoreFiles.WORKSPACE)
        BundledSQLiteDriver().open(path.toString(), SQLITE_OPEN_READWRITE).use {
            it.prepare("DROP TABLE workspace_plan_review").use { statement -> statement.step() }
        }
        val before = directoryFingerprint(staged.directory)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2(staged)
        }
        assertEquals(before, directoryFingerprint(staged.directory))
    }

    @Test fun schema3WorkspaceIsNotSilentlyMigratedByV2Open() {
        val staged = stage("outdated-workspace")
        materializeFive(staged)
        val path = staged.directory.resolve(GenerationStoreFiles.WORKSPACE)
        BundledSQLiteDriver().open(path.toString(), SQLITE_OPEN_READWRITE).use {
            it.prepare("PRAGMA user_version = 3").use { statement -> statement.step() }
        }
        val before = directoryFingerprint(staged.directory)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2(staged)
        }
        assertEquals(before, directoryFingerprint(staged.directory))
    }

    @Test fun uncheckpointedWalSidecarBlocksBeforeAnyStoreOpen() {
        val staged = stage("hot-wal")
        materializeFive(staged)
        val wal = staged.directory.resolve(GenerationStoreFiles.REALITY + "-wal")
        Files.write(wal, byteArrayOf(1, 2, 3))
        val before = directoryFingerprint(staged.directory)
        assertThrows(IllegalStateException::class.java) {
            FaroV5GenerationStoreGraph.openValidatedV2(staged)
        }
        assertEquals(before, directoryFingerprint(staged.directory))
    }
}
