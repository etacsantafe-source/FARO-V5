package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationId
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Non-capability facts about one authenticated archive and one pinned DEV target.
 * Does NOT authorize, pin or perform a database restore, journal switch or Apply.
 */
internal data class FaroV5Household11V8RestoreInspection(
    val sourceGenerationId: StorageGenerationId,
    val targetGenerationId: StorageGenerationId,
    val activePointer: ActiveGenerationPointer,
    val archiveSha256: String,
    val evidenceSchema: Int,
    val receiptRevision: Long,
    val receiptCount: Int,
)

/**
 * W05-A1B3A: bounded, read-only FBS2v8 admission for a sealed STAGED9 target.
 * The caller must already control the exclusive, closed development host.
 * A1B3B separately owns authenticated replay/identity; A1B3C owns cutover.
 */
internal class FaroV5Household11V8RestoreAdmission(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val afterAuthentication: () -> Unit = {},
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val pointer = FileActiveGenerationPointer(root)
    private val layout = FileStorageGenerationLayout(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()

    fun inspect(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
    ): FaroV5Household11V8RestoreInspection =
        quiescence.withMaterialWritesQuiesced(operationId) {
            requirePinnedAndSealed(target, expectedActive)
            require(crypto.admitsArchiveSize(encryptedArchive.size)) {
                "FBS2v8 restore admission archive size invalid"
            }
            // Copy exactly once so the authenticated bytes also determine the
            // visible ciphertext digest, even if the input is mutable elsewhere.
            val immutableArchive = encryptedArchive.copyOf()
            try {
                val result = crypto.openWithHouseholdReceipts11(immutableArchive, passphrase)
                val opened = when (result) {
                    is FiveStoreAuthenticatedPayload.Opened -> result.payload
                    is FiveStoreAuthenticatedPayload.Rejected -> throw IllegalArgumentException(
                        "FBS2v8 restore admission rejected authenticated archive: " + result.reason
                    )
                }
                val manifest = opened.base.base.base.fourStore.manifest
                require(manifest.sourceGenerationId != target.descriptor.generationId) {
                    "FBS2v8 restore source equals target generation"
                }
                require(opened.base.base.base.finance9 != null) {
                    "FBS2v8 archive missing complete Finance9 section"
                }
                val evidence = opened.base.base.base.fourStore.commercialEvidence.schemaVersion
                check(evidence == 1 || evidence == 2) { "FBS2v8 Evidence profile unsupported" }

                requireTargetEvidenceProfile(target, evidence)
                afterAuthentication() // Test-only pointer/host revocation seam.
                requirePinnedAndSealed(target, expectedActive)
                requireTargetEvidenceProfile(target, evidence)
                FaroV5Household11V8RestoreInspection(
                    sourceGenerationId = manifest.sourceGenerationId,
                    targetGenerationId = target.descriptor.generationId,
                    activePointer = expectedActive,
                    archiveSha256 = MessageDigest.getInstance("SHA-256")
                        .digest(immutableArchive).joinToString("") { "%02x".format(it) },
                    evidenceSchema = evidence,
                    receiptRevision = opened.receipts.revision,
                    receiptCount = opened.receipts.receipts.size,
                )
            } finally {
                immutableArchive.fill(0)
            }
        }

    private fun requireTargetEvidenceProfile(target: StagedStorageGeneration, evidence: Int) {
        if (evidence == 2) {
            FaroV5GenerationStoreGraph.requireFinance9ReceiptEvidence2ArchiveStage(
                target, allowAuxiliary3 = false,
            )
        } else {
            FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(
                target, allowAuxiliary3 = false,
            )
        }
    }

    private fun requirePinnedAndSealed(
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ) {
        assertExclusiveClosedDevHost()
        check(pointer.read() == expectedActive &&
            target.descriptor.generationId != expectedActive.generationId) {
            "FBS2v8 restore target must be inactive with exact pointer sequence"
        }
        val canonical = layout.requireStaged(target.descriptor.generationId)
        val dir = target.directory.toAbsolutePath().normalize()
        check(canonical.descriptor == target.descriptor &&
            canonical.directory == dir &&
            Files.isDirectory(dir, NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(dir) && dir.toRealPath() == dir) {
            "FBS2v8 restore target is not canonical"
        }
        for (name in GenerationStoreFiles.WORKSPACE_V2) {
            val file = dir.resolve(name)
            check(Files.isRegularFile(file, NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) &&
                (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L) {
                "FBS2v8 restore requires one regular single-linked SQLite file: " + name
            }
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                check(Files.notExists(dir.resolve(name + suffix), NOFOLLOW_LINKS)) {
                    "FBS2v8 restore target contains an unsafe SQLite sidecar"
                }
            }
            val header = Files.newInputStream(file, NOFOLLOW_LINKS).use { it.readNBytes(20) }
            check(header.size == 20 && header[18] == 2.toByte() && header[19] == 2.toByte()) {
                "FBS2v8 restore target requires sealed WAL-mode SQLite"
            }
        }
        assertExclusiveClosedDevHost()
        check(pointer.read() == expectedActive) {
            "FBS2v8 restore pointer changed during sealed stage admission"
        }
    }
}
