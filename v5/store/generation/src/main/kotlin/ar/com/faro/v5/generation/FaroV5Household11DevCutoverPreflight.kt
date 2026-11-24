package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.store.reality.RealityHousehold10Row
import ar.com.faro.v5.store.reality.RealityHousehold10Rows
import ar.com.faro.v5.store.reality.RealityHousehold11NeedsRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRow
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRows
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRowsPort
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Observation only: never a cutover/Apply capability. Requires a pre-existing
 * FBS2v8 restore pin and a fully sealed SQL11 destination.
 */
internal data class FaroV5Household11CutoverInspection(
    val origin: ActiveGenerationPointer,
    val sourceGenerationId: StorageGenerationId,
    val targetGenerationId: StorageGenerationId,
    val archiveSha256: String,
    val originFingerprint: String,
    val targetFingerprint: String,
    val needsRevision: Long,
    val needsCount: Int,
    val receiptsRevision: Long,
    val receiptsCount: Int,
)

/**
 * W05-A1B3C0 DEV isolated read-only cutover admission for Evidence1/SQL11.
 * Explicitly NOT the journaled switch, active graph reopen or startup recovery.
 * The caller must hold the exclusive closed DEV host and global quiescence.
 */
internal class FaroV5Household11DevCutoverPreflight(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val afterObservation: () -> Unit = {},
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val pointer = FileActiveGenerationPointer(root)
    private val layout = FileStorageGenerationLayout(root)
    private val identity = FileRestoreArchiveIdentity(root)
    private val journal = FileBootstrapJournal(root)
    private val fence = FileGenerationReplayFence(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()

    fun inspect(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
    ): FaroV5Household11CutoverInspection =
        quiescence.withMaterialWritesQuiesced(operationId) {
            requireClosedAndPinned(operationId, target, expectedActive)
            check(crypto.admitsArchiveSize(encryptedArchive.size)) {
                "SQL11 cutover preflight rejects archive size"
            }
            val immutableCiphertext = encryptedArchive.copyOf()
            try {
                val payload = when (val opened = crypto.openWithHouseholdReceipts11(
                    immutableCiphertext, passphrase,
                )) {
                    is FiveStoreAuthenticatedPayload.Opened -> opened.payload
                    is FiveStoreAuthenticatedPayload.Rejected -> throw IllegalArgumentException(
                        "SQL11 cutover preflight requires authenticated FBS2v8: " + opened.reason,
                    )
                }
                val base = payload.base.base.base
                val source = base.fourStore.manifest.sourceGenerationId
                check(source != target.descriptor.generationId) {
                    "SQL11 cutover source and destination identity must differ"
                }
                check(base.fourStore.commercialEvidence.schemaVersion == 1 &&
                    base.fourStore.commercialEvidence.stagedTyped == null) {
                    "SQL11 cutover Evidence2 not certified"
                }
                check(base.finance9 != null) { "SQL11 archive missing Finance9" }
                // Existing B1 stage replay created this immutable pin before writes.
                // This admission NEVER creates an archive identity or journal.
                identity.requireExistingPinnedArchive(
                    operationId, target.descriptor.generationId, immutableCiphertext,
                )
                val original = layout.requireStaged(expectedActive.generationId)
                requireClosedFive(original)
                FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(
                    original, allowAuxiliary3 = false,
                )
                val originFingerprint = FaroV5Household10DevCutoverOwner.fingerprint(original)
                val targetFingerprint = FaroV5Household10DevCutoverOwner.fingerprint(target)
                val reality = target.directory.resolve(GenerationStoreFiles.REALITY)
                val expectedNeeds = payload.base.household.toRows()
                val expectedReceipts = payload.receipts.toRows()
                check(RealityHousehold11NeedsRowsPort.readTrustedSealedStageEleven(reality) ==
                    expectedNeeds) {
                    "SQL11 cutover Household10 rows do not match original authenticated archive"
                }
                check(RealityHousehold11ReceiptRowsPort.readTrustedSealedStageEleven(reality) ==
                    expectedReceipts) {
                    "SQL11 cutover H11R receipts do not match original authenticated archive"
                }
                afterObservation() // Adversarial pointer ABA/host/journal/content change seam.
                requireClosedAndPinned(operationId, target, expectedActive)
                requireClosedFive(original)
                FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(
                    original, allowAuxiliary3 = false,
                )
                check(FaroV5Household10DevCutoverOwner.fingerprint(original) ==
                    originFingerprint && FaroV5Household10DevCutoverOwner.fingerprint(target) ==
                    targetFingerprint) { "SQL11 cutover stage changed during admission" }
                check(RealityHousehold11NeedsRowsPort.readTrustedSealedStageEleven(reality) ==
                    expectedNeeds &&
                    RealityHousehold11ReceiptRowsPort.readTrustedSealedStageEleven(reality) ==
                    expectedReceipts) {
                    "SQL11 cutover stage rows changed after observation"
                }
                identity.requireExistingPinnedArchive(
                    operationId, target.descriptor.generationId, immutableCiphertext,
                )
                FaroV5Household11CutoverInspection(
                    origin = expectedActive,
                    sourceGenerationId = source,
                    targetGenerationId = target.descriptor.generationId,
                    archiveSha256 = MessageDigest.getInstance("SHA-256")
                        .digest(immutableCiphertext).joinToString("") { "%02x".format(it) },
                    originFingerprint = originFingerprint,
                    targetFingerprint = targetFingerprint,
                    needsRevision = expectedNeeds.revision,
                    needsCount = expectedNeeds.needs.size,
                    receiptsRevision = expectedReceipts.revision,
                    receiptsCount = expectedReceipts.receipts.size,
                )
            } finally {
                immutableCiphertext.fill(0)
            }
        }

    private fun requireClosedAndPinned(
        op: MaintenanceOperationId,
        target: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
    ) {
        assertExclusiveClosedDevHost()
        check(Files.isDirectory(root, NOFOLLOW_LINKS) && root.toRealPath() == root &&
            pointer.read() == origin && origin.generationId != target.descriptor.generationId) {
            "SQL11 cutover requires canonical root, full pinned pointer and inactive target"
        }
        val canonical = layout.requireStaged(target.descriptor.generationId)
        check(canonical.descriptor == target.descriptor &&
            canonical.directory == target.directory.toAbsolutePath().normalize()) {
            "SQL11 cutover requires canonical stage descriptor/directory"
        }
        // Existing pending/another operation, or a pre-existing activation fence,
        // is NOT authority to rerun this read-only admission.
        check(journal.read(op) == null && journal.pending().isEmpty()) {
            "SQL11 cutover requires no existing/unfinished bootstrap operation"
        }
        check(!fence.isInstalled(MaintenanceOperationKind.RESTORE, target.descriptor.generationId) &&
            !fence.isInstalled(MaintenanceOperationKind.MIGRATION, target.descriptor.generationId)) {
            "SQL11 cutover cannot re-admit a replay-fenced destination"
        }
        for (name in listOf(
            "household10-dev-cutover-v1.bin", "household11-dev-cutover-v1.bin",
        )) {
            check(Files.notExists(root.resolve("bootstrap").resolve(name), NOFOLLOW_LINKS)) {
                "SQL11 cutover refuses an unresolved DEV cutover ticket"
            }
        }
        requireClosedFive(target)
        assertExclusiveClosedDevHost()
        check(pointer.read() == origin) { "SQL11 cutover pointer/host changed" }
    }

    private fun requireClosedFive(stage: StagedStorageGeneration) {
        val directory = stage.directory.toAbsolutePath().normalize()
        check(directory.toRealPath() == directory && Files.isDirectory(directory, NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(directory)) { "SQL11 cutover directory is noncanonical" }
        for (name in GenerationStoreFiles.WORKSPACE_V2) {
            val file = directory.resolve(name)
            check(Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
                (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L) {
                "SQL11 cutover SQLite file is not regular and single-linked: " + name
            }
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(directory.resolve(name + suffix), NOFOLLOW_LINKS)) {
                    "SQL11 cutover rejects SQLite sidecar"
                }
            }
            val header = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(20) }
            check(header.size == 20 && header[18] == 2.toByte() && header[19] == 2.toByte()) {
                "SQL11 cutover requires sealed WAL-mode store"
            }
        }
    }

    private fun Household10BackupSection.toRows() = RealityHousehold10Rows(
        revision, needs.map {
            RealityHousehold10Row(
                it.id.value, it.name.value, it.quantity.thousandths.toInt(),
                it.quantity.unit.name, it.priority.name, it.status.name,
                it.revision, it.dueDate?.toEpochDay(),
            )
        },
    )

    private fun Household11ReceiptBackupSection.toRows() = RealityHousehold11ReceiptRows(
        revision, receipts.map {
            RealityHousehold11ReceiptRow(
                it.receiptId, it.intentKey, it.purchaseRef, it.stockItemRef,
                it.quantityThousandths, it.unit, it.receivedEpochDay, it.recordedAtMs,
                it.revision,
            )
        },
    )
}
