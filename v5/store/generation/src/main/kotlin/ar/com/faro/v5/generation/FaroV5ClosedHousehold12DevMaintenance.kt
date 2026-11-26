package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteGateQuiescence
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationDescriptor
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest

/** Closed-host DEV maintenance under the same lease as the isolated Apply hosts. */
class FaroV5ClosedHousehold12DevMaintenance private constructor(
    private val root: Path,
    gate: SingleProcessMaterialWriteGate,
    private val verifier: PreparedPlanEnvelopeVerificationPort,
    private val cutover: FaroV5Household13Evidence2DevJournaledCutover,
) : AutoCloseable {
    data class Stages(
        val sealedCopy: StorageGenerationDescriptor,
        val v9Bridge: StorageGenerationDescriptor,
        val v10Destination: StorageGenerationDescriptor,
    )

    class Prepared internal constructor(
        internal val operation: MaintenanceOperationId,
        internal val origin: ActiveGenerationPointer,
        internal val destination: StagedStorageGeneration,
        internal val archive: ByteArray,
    )

    private val quiescence = MaterialWriteGateQuiescence(gate)
    private val pointer = FileActiveGenerationPointer(root)
    private val replay9 = FaroV5Household12V9StagedReplay(root, quiescence, cutover::assertClosedLease, planVerifier = verifier)
    private val replay10 = FaroV5Household13V10StagedReplay(root, quiescence, cutover::assertClosedLease, planVerifier = verifier)

    /** Capture and authenticate the backups, then create a VALIDATED ticket. */
    fun prepare(
        operation: MaintenanceOperationId,
        origin: ActiveGenerationPointer,
        stages: Stages,
        passphrase: CharArray,
        confirmForeground: () -> Boolean,
    ): Prepared = cutover.withClosedLease {
        check(confirmForeground() && pointer.read() == origin)
        val stageIds = listOf(origin.generationId, stages.sealedCopy.generationId,
            stages.v9Bridge.generationId, stages.v10Destination.generationId)
        require(stageIds.distinct().size == stageIds.size)
        val copy = cutover.copyClosedSql12(origin, stages.sealedCopy)
        requireSettledApply(copy, origin)

        val bridgeOperation = MaintenanceOperationId(operation.value + "-v9")
        val v9 = replay9.captureAuthenticated(
            bridgeOperation, copy, origin, true, BackupSetId(bridgeOperation.value),
            "5.0-dev", stages.sealedCopy.createdAtEpochMillis, passphrase,
        )
        val bridge = stageForRestore(stages.v9Bridge, origin, bridgeOperation)
        try {
            check(replay10.restoreAuthenticatedV9AsThirteen(bridgeOperation, bridge, origin, v9, passphrase)
                == FiveStoreStagedRestoreResult.StagedVerified(bridge.descriptor.generationId.value))
        } finally {
            v9.fill(0)
        }

        check(confirmForeground() && pointer.read() == origin)
        val v10 = replay10.captureAuthenticated(
            operation, bridge, origin, true, BackupSetId(operation.value),
            "5.0-dev", stages.v9Bridge.createdAtEpochMillis, passphrase,
        )
        val destination = stageForRestore(stages.v10Destination, origin, operation)
        check(replay10.restoreAuthenticatedV10(operation, destination, origin, v10, passphrase)
            == FiveStoreStagedRestoreResult.StagedVerified(destination.descriptor.generationId.value))
        check(cutover.authorize(operation, destination, origin, v10, passphrase, confirmForeground).status
            == FaroV5Household13Evidence2DevJournaledCutover.Status.AWAITING_FOREGROUND)
        Prepared(operation, origin, destination, v10)
    }

    fun start(
        prepared: Prepared,
        passphrase: CharArray,
        confirmForeground: () -> Boolean,
    ): ActiveGenerationPointer? {
        val outcome = cutover.start(
            prepared.operation, prepared.destination, prepared.origin, prepared.archive, passphrase, confirmForeground,
        )
        return outcome.pointer.takeIf { outcome.status == FaroV5Household13Evidence2DevJournaledCutover.Status.ACTIVE_THIRTEEN }
    }

    fun recover(): ActiveGenerationPointer? {
        val outcome = cutover.recover()
        return outcome.pointer.takeIf { outcome.status == FaroV5Household13Evidence2DevJournaledCutover.Status.ACTIVE_THIRTEEN }
    }

    private fun requireSettledApply(copy: StagedStorageGeneration, origin: ActiveGenerationPointer) {
        FaroV5GenerationStoreGraph.openValidatedHousehold12ArchiveStage(copy, origin, true, verifier).use { graph ->
            val reality = graph.reality.maintenance.inspect()
            check(reality.applyAttemptCount == reality.executionReceiptCount) { "Recover pending Reality Apply before maintenance" }
            val reviews = checkNotNull(graph.workspace).backup.snapshot().planReviews
            check(reviews.none {
                it.state == ConversationPlanReviewState.APPLYING || it.state == ConversationPlanReviewState.RECOVERING_UNKNOWN
            }) { "Recover pending Workspace Apply before maintenance" }
        }
    }

    private fun stageForRestore(
        descriptor: StorageGenerationDescriptor,
        origin: ActiveGenerationPointer,
        operation: MaintenanceOperationId,
    ): StagedStorageGeneration {
        val fresh = FaroV5WorkspaceV2StageOwner(root).stageFreshPlanning8AndVerify(descriptor)
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(Files.newInputStream(fresh.directory.resolve(GenerationStoreFiles.REALITY)), digest).use {
            val buffer = ByteArray(8192)
            while (it.read(buffer) != -1) {
                // All bytes feed the stage migration's source fingerprint.
            }
        }
        val sourceFingerprint = digest.digest().joinToString("") { "%02x".format(it) }
        val finance = FaroV5Finance9StagedMigration(root, quiescence).migrate(
            MaintenanceOperationId(operation.value + "-finance9"), fresh, origin,
            sourceFingerprint, descriptor.createdAtEpochMillis,
        )
        FaroV5GenerationStoreGraph.openQuarantinedAuxiliaryReceiptV4Stage(finance, pointer).close()
        CommercialEvidenceStore(
            fileName = finance.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE).toString(),
            stagedTypedTermsV2 = true,
            stagedSemanticBackupV2 = true,
        ).close()
        return finance
    }

    override fun close() = cutover.close()

    companion object {
        fun acquire(
            root: Path,
            gate: SingleProcessMaterialWriteGate,
            verifier: PreparedPlanEnvelopeVerificationPort,
            closedDevHost: () -> Boolean,
        ): FaroV5ClosedHousehold12DevMaintenance {
            val canonicalRoot = root.toRealPath()
            val cutover = FaroV5Household13Evidence2DevJournaledCutover.acquire(
                canonicalRoot, gate, closedDevHost, planVerifier = verifier,
            )
            return FaroV5ClosedHousehold12DevMaintenance(canonicalRoot, gate, verifier, cutover)
        }
    }
}
