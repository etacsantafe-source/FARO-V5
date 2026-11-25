package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileRestoreArchiveIdentity
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityFinance9SealedReadPort
import ar.com.faro.v5.store.reality.RealityFinance9SealedSnapshot
import ar.com.faro.v5.store.reality.RealityFinance9SealedTableRows
import ar.com.faro.v5.store.reality.RealityFinance9StagedRowsRestorePort
import java.nio.file.Path

internal sealed interface AuthenticatedHouseholdReceiptBaseArchive {
    val snapshot: FaroV5Household11Fbs2Snapshot
    fun copyEncryptedBytes(): ByteArray
}

/**
 * The exact original authenticated FBS2v8 ciphertext and its fully decoded
 * immutable semantic snapshot. This is not a restore authority or a grant.
 */
internal class AuthenticatedHousehold11RestoreArchive private constructor(
    private val bytes: ByteArray,
    override val snapshot: FaroV5Household11Fbs2Snapshot,
) : AuthenticatedHouseholdReceiptBaseArchive {
    override fun copyEncryptedBytes(): ByteArray = bytes.copyOf()

    companion object {
        fun open(
            archive: ByteArray,
            passphrase: CharArray,
        ): FiveStoreAuthenticatedPayload<AuthenticatedHousehold11RestoreArchive> {
            val crypto = FaroV5FiveStorePortableBackupCrypto()
            require(crypto.admitsArchiveSize(archive.size)) { "FBS2v8 archive size invalid" }
            val pinnedBytes = archive.copyOf()
            return when (val result = crypto.openWithHouseholdReceipts11(pinnedBytes, passphrase)) {
                is FiveStoreAuthenticatedPayload.Opened ->
                    FiveStoreAuthenticatedPayload.Opened(
                        AuthenticatedHousehold11RestoreArchive(pinnedBytes, result.payload),
                    )
                is FiveStoreAuthenticatedPayload.Rejected -> {
                    pinnedBytes.fill(0)
                    result
                }
            }
        }
    }
}

/**
 * A1B3B0 isolated first restore phase. Replays all five semantic base owners
 * and Finance9 into an INACTIVE STAGED9 target, bound to the exact authenticated
 * FBS2v8 ciphertext. No Household10/SQL11 writes and absolutely no cutover.
 */
internal class FaroV5Household11V8BaseReplay(
    storageRoot: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val afterStore: (FiveStoreStagedRestoreSection) -> Unit = {},
    private val afterFinance: () -> Unit = {},
    private val afterAdmission: () -> Unit = {},
    private val planVerifier: PreparedPlanEnvelopeVerificationPort? = null,
) {
    private val root = storageRoot.toAbsolutePath().normalize()
    private val pointer = FileActiveGenerationPointer(root)
    private val layout = FileStorageGenerationLayout(root)
    private val archiveIdentity = FileRestoreArchiveIdentity(root)
    private val alreadyQuiesced = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }
    private val admission = FaroV5Household11V8RestoreAdmission(
        root, alreadyQuiesced, ::requireHost,
    )

    fun restoreAuthenticatedBaseToNine(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult = try {
        quiescence.withMaterialWritesQuiesced(operationId) {
            restoreUnderLease(operationId, target, expectedActive, encryptedArchive, passphrase)
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        FiveStoreStagedRestoreResult.Blocked(FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE)
    } catch (_: Exception) {
        FiveStoreStagedRestoreResult.Blocked(FiveStoreStagedRestoreBlock.WRITE_PAUSE_UNAVAILABLE)
    }

    private fun restoreUnderLease(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
    ): FiveStoreStagedRestoreResult {
        fun blocked(reason: FiveStoreStagedRestoreBlock) =
            FiveStoreStagedRestoreResult.Blocked(reason)

        try {
            requireOneSealedFinance9Profile(target, expectedActive)
        } catch (_: Exception) {
            return blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
        }
        val originalBytes = encryptedArchive.copyOf()
        return try {
            val authenticated = when (
                val opened = try {
                    AuthenticatedHousehold11RestoreArchive.open(originalBytes, passphrase)
                } catch (_: IllegalArgumentException) {
                    return blocked(FiveStoreStagedRestoreBlock.INVALID_ARCHIVE)
                }
            ) {
                is FiveStoreAuthenticatedPayload.Opened -> opened.payload
                is FiveStoreAuthenticatedPayload.Rejected ->
                    return FiveStoreStagedRestoreResult.Blocked(
                        FiveStoreStagedRestoreBlock.INVALID_ARCHIVE, opened.reason,
                    )
            }
            restoreVerifiedUnderLease(operationId, target, expectedActive, authenticated, originalBytes, passphrase)
        } finally {
            originalBytes.fill(0)
        }
    }

    fun restoreVerifiedV9BaseToNine(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        archive: AuthenticatedHousehold12RestoreArchive,
    ): FiveStoreStagedRestoreResult = quiescence.withMaterialWritesQuiesced(operationId) {
        val bytes = archive.copyEncryptedBytes()
        try {
            restoreVerifiedUnderLease(operationId, target, expectedActive, archive, bytes, null)
        } finally {
            bytes.fill(0)
        }
    }

    /** Reuse the sealed v8 five-owner base while pinning the original v10 ciphertext. */
    fun restoreVerifiedV10BaseToNine(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        archive: AuthenticatedHousehold13RestoreArchive,
    ): FiveStoreStagedRestoreResult = quiescence.withMaterialWritesQuiesced(operationId) {
        val bytes = archive.copyEncryptedBytes()
        try {
            restoreVerifiedUnderLease(operationId, target, expectedActive, archive, bytes, null)
        } finally {
            bytes.fill(0)
        }
    }

    private fun restoreVerifiedUnderLease(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        authenticated: AuthenticatedHouseholdReceiptBaseArchive,
        originalBytes: ByteArray,
        legacyPassphrase: CharArray?,
    ): FiveStoreStagedRestoreResult {
        val targetId = target.descriptor.generationId
        fun blocked(reason: FiveStoreStagedRestoreBlock) = FiveStoreStagedRestoreResult.Blocked(reason)
        val snapshot = authenticated.snapshot
        val expected = snapshot.base.base
        if (expected.base.fourStore.manifest.sourceGenerationId == targetId) {
            return blocked(FiveStoreStagedRestoreBlock.SOURCE_TARGET_IDENTITY_CONFLICT)
        }
        val evidence = expected.base.fourStore.commercialEvidence.schemaVersion
        try {
            requireTargetProfile(target, expectedActive, evidence)
            if (legacyPassphrase != null) {
                val observed = admission.inspect(
                    operationId, target, expectedActive, originalBytes, legacyPassphrase,
                )
                check(observed.sourceGenerationId == expected.base.fourStore.manifest.sourceGenerationId &&
                    observed.targetGenerationId == targetId &&
                    observed.evidenceSchema == evidence &&
                    observed.receiptRevision == snapshot.receipts.revision &&
                    observed.receiptCount == snapshot.receipts.receipts.size) {
                    "FBS2v8 independently admitted archive metadata differs"
                }
            }
            afterAdmission() // test seam: pointer ABA / host revocation
            requirePinned(target, expectedActive)
            requireTargetProfile(target, expectedActive, evidence)
        } catch (_: Exception) {
            return blocked(FiveStoreStagedRestoreBlock.TARGET_NOT_QUARANTINED)
        }

        // The first durable write binds the ORIGINAL v8 ciphertext, not a
        // freshly encrypted v6/v7 payload. Retries cannot change nonce,
        // passphrase, operation ID or target identity.
        try {
            archiveIdentity.pinOrVerify(operationId, targetId, originalBytes)
        } catch (_: Exception) {
            return blocked(FiveStoreStagedRestoreBlock.ARCHIVE_IDENTITY_CONFLICT)
        }

        return try {
            val graph = if (evidence == 2)
                FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Evidence2Stage(
                    target, expectedActive, planVerifier,
                )
            else
                FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Stage(
                    target, expectedActive, planVerifier,
                )
            val restored = graph.use { opened ->
                FaroV5FiveStoreStagedRestore(
                    root, target, opened, alreadyQuiesced,
                    afterSection = { section ->
                        requirePinned(target, expectedActive)
                        afterStore(section)
                    },
                    archiveIdentity = archiveIdentity,
                ).restoreVerifiedHousehold11BaseStageOnly(
                    operationId, authenticated, expectedActive,
                )
            }
            if (restored !is FiveStoreStagedRestoreResult.StagedVerified) return restored
            requirePinned(target, expectedActive)
            requireTargetProfile(target, expectedActive, evidence)
            val finance = checkNotNull(expected.base.finance9) {
                "FBS2v8 base missing Finance9"
            }
            RealityFinance9StagedRowsRestorePort.restoreOrVerifyTrustedStagedNine(
                target.directory.resolve(GenerationStoreFiles.REALITY),
                RealityFinance9SealedSnapshot(
                    finance.schemaVersion,
                    finance.tables.map { RealityFinance9SealedTableRows(it.table, it.rows) },
                ),
            )
            afterFinance()
            requirePinned(target, expectedActive)
            requireTargetProfile(target, expectedActive, evidence)
            verifyBaseAfterClose(operationId, target, expectedActive, expected, evidence)
            archiveIdentity.requireExistingPinnedArchive(operationId, targetId, originalBytes)
            requirePinned(target, expectedActive)
            FiveStoreStagedRestoreResult.StagedVerified(targetId.value)
        } catch (_: Exception) {
            blocked(FiveStoreStagedRestoreBlock.STAGED_RESTORE_FAILED)
        }
    }

    private fun verifyBaseAfterClose(
        operationId: MaintenanceOperationId,
        target: StagedStorageGeneration,
        origin: ActiveGenerationPointer,
        expected: FaroV5Auxiliary4Fbs2Snapshot,
        evidence: Int,
    ) {
        val manifest = expected.base.fourStore.manifest
        val graph = if (evidence == 2)
            FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Evidence2Stage(
                target, origin, planVerifier,
            )
        else FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4Stage(
            target, origin, planVerifier,
        )
        val (captured, auxiliary) = graph.use { opened ->
            FaroV5FiveStoreBackupCapture(opened, alreadyQuiesced).captureReceiptProjection(
                operationId, manifest.backupSetId, manifest.productVersion,
                manifest.createdAtEpochMillis,
            )
        }
        val observedFinance = RealityFinance9SealedReadPort.readTrustedSealedStage9(
            target.directory.resolve(GenerationStoreFiles.REALITY),
        )
        val actual = FaroV5Auxiliary4Fbs2Snapshot(
            captured.copy(
                fourStore = captured.fourStore.copy(manifest = manifest),
                finance9 = Finance9SectionSnapshot(
                    observedFinance.schemaVersion,
                    observedFinance.tables.map { Finance9TableRows(it.table, it.rows) },
                ),
            ),
            auxiliary.receipts,
        )
        check(actual == expected) {
            "FBS2v8 staged five-store / Finance9 semantic readback differs from archive"
        }
    }

    /**
     * Before archive authentication, only inspect the sealed inactive target.
     * Neither profile is inferred from unauthenticated ciphertext. Exactly one
     * strict, read-only schema profile must match; post-auth verification still
     * binds the target to the authenticated archive evidence version.
     */
    private fun requireOneSealedFinance9Profile(
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
    ) {
        requirePinned(target, expectedActive)
        val evidence1 = runCatching {
            FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(
                target, allowAuxiliary3 = false,
            )
        }.isSuccess
        requirePinned(target, expectedActive)
        val evidence2 = runCatching {
            FaroV5GenerationStoreGraph.requireFinance9ReceiptEvidence2ArchiveStage(
                target, allowAuxiliary3 = false,
            )
        }.isSuccess
        requirePinned(target, expectedActive)
        check(evidence1 != evidence2) {
            "FBS2v8 base replay requires exactly one sealed Evidence1/Evidence2 target profile"
        }
    }

    private fun requireTargetProfile(
        target: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        evidence: Int?,
    ) {
        requirePinned(target, expectedActive)
        if (evidence == 2)
            FaroV5GenerationStoreGraph.requireFinance9ReceiptEvidence2ArchiveStage(
                target, allowAuxiliary3 = false,
            )
        else FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(
            target, allowAuxiliary3 = false,
        )
        requirePinned(target, expectedActive)
    }

    private fun requirePinned(target: StagedStorageGeneration, origin: ActiveGenerationPointer) {
        requireHost()
        check(pointer.read() == origin && target.descriptor.generationId != origin.generationId) {
            "FBS2v8 base replay requires exact inactive pointer ID and sequence"
        }
        val canonical = layout.requireStaged(target.descriptor.generationId)
        check(canonical.descriptor == target.descriptor &&
            canonical.directory == target.directory.toAbsolutePath().normalize()) {
            "FBS2v8 base replay target has different descriptor or path"
        }
        requireHost()
        check(pointer.read() == origin) {
            "FBS2v8 base replay detected altered origin pointer"
        }
    }

    private fun requireHost() = assertExclusiveClosedDevHost()
}
