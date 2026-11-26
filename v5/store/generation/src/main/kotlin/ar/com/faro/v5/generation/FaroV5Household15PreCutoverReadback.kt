package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityHousehold11NeedsRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionRowsPort
import ar.com.faro.v5.store.reality.StagedSqlitePreflight
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

/**
 * A2C0C0: READ-ONLY proof for a sealed, inactive SQL15 five-store stage.
 * This DOES NOT prove all semantic sections of all five stores, open an ACTIVE
 * SQL15 runtime, mint a cutover ticket, change a pointer or authorize stock.
 * Full semantic proof + active read host must be certified separately before
 * any C2 journaled switch.
 */
internal class FaroV5Household15PreCutoverReadback(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
) {
    internal data class EvidenceOnly(
        val sourceGenerationId: StorageGenerationId,
        val stageGenerationId: StorageGenerationId,
        val authenticatedArchiveSha256: String,
    )

    private val root = storageRoot.toAbsolutePath().normalize()
    private val files = FaroV5Household12PurchaseSql12Port(root, assertExclusiveClosedDevHost)
    private val pin = FileRestoreArchiveIdentity(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val mapper = FaroV5Household15V12StagedReplay(root, quiescence, assertExclusiveClosedDevHost)
    private val semantic = FaroV5Household13V10StagedReplay(
        root, quiescence, assertExclusiveClosedDevHost,
    )

    fun verifyOnly(
        operation: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        archive: ByteArray,
        passphrase: CharArray,
    ): EvidenceOnly = quiescence.withMaterialWritesQuiesced(operation) {
        assertExclusiveClosedDevHost()
        val file = files.requireSealedStage(stage, origin)
        val original = fingerprint(stage.directory)
        check(RealityHousehold11NeedsRowsPort.readTrustedSealedSchemaVersion(file) == 15L) {
            "Precutover readback requires exact sealed SQL15"
        }
        val opened = crypto.openWithHouseholdConsumption15(archive, passphrase)
        val snapshot = when (opened) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error("Precutover requires authenticated FBS2v12")
        }
        val source = snapshot.base.base.base.base.base.base.fourStore.manifest.sourceGenerationId
        check(source != stage.descriptor.generationId) { "Precutover source cannot equal target" }
        pin.requireExistingPinnedArchive(operation, stage.descriptor.generationId, archive)

        // All five DBs are checked without write-enabled connections and without
        // assuming that quick_check alone proves equality of every backup section.
        StagedSqlitePreflight.requireAll(stage.directory, listOf(
            StagedSqlitePreflight.Schema(GenerationStoreFiles.WORKSPACE, 4,
                listOf("workspace_conversation")),
            StagedSqlitePreflight.Schema(GenerationStoreFiles.AUXILIARY, 4,
                listOf("notice_attention_receipt")),
            StagedSqlitePreflight.Schema(GenerationStoreFiles.REALITY, 15,
                listOf("finance_state", "finance_entry", "objective_record", "matter_record",
                    "household_need", "household_stock_receipt", "household_confirmed_purchase",
                    "household_stock_compensation", "household_stock_reservation_event",
                    "household_stock_consumption_event")),
            StagedSqlitePreflight.Schema(GenerationStoreFiles.COMMERCIAL_EVIDENCE, 2,
                listOf("commercial_evidence", "commercial_evidence_quote_terms")),
            StagedSqlitePreflight.Schema(GenerationStoreFiles.WEB_EVIDENCE, 2,
                listOf("web_evidence")),
        ))
        val expected = mapper.expectedSqlFifteenRows(snapshot)
        val observed = RealityHousehold15ConsumptionRowsPort.captureTrustedSealedStageFifteen(file) {
            files.requireLogicalStage(stage, origin)
        }
        check(observed == expected) { "Precutover SQL15 history/ledger differs from authenticated FBS2v12" }

        // A2C0C1A: compare ALL five canonical owners to the authenticated
        // FBS2v12 historical projection. A quick_check/row-ledger match alone
        // cannot certify Workspace, Auxiliary, Web, Evidence2, G4 or Finance9.
        val expectedFive = snapshot.base.base
        val capturedFive = semantic.observeForVerification(
            operation, stage, origin, expectedFive, stageFifteen = true,
        )
        check(capturedFive == expectedFive) {
            "SQL15 five-store semantic content differs from authenticated FBS2v12"
        }
        files.requireLogicalStage(stage, origin)
        pin.requireExistingPinnedArchive(operation, stage.descriptor.generationId, archive)
        check(fingerprint(stage.directory) == original) { "Precutover stage files changed during inspection" }
        files.requireSealedStage(stage, origin)
        EvidenceOnly(source, stage.descriptor.generationId, sha256(archive))
    }

    private fun fingerprint(directory: Path): List<String> =
        GenerationStoreFiles.WORKSPACE_V2.map { name ->
            val path = directory.resolve(name)
            check(Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
                buffer.fill(0)
            }
            hex(digest.digest())
        }

    private fun sha256(bytes: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
