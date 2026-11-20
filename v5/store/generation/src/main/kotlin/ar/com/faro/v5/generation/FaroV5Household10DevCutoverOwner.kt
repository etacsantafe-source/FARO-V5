package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.BootstrapTransitionPolicy
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileBootstrapJournal
import ar.com.faro.v5.maintenance.FileGenerationReplayFence
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationActivationBlockReason
import ar.com.faro.v5.maintenance.GenerationActivationResult
import ar.com.faro.v5.maintenance.GenerationPreSwitchAdmissionPort
import ar.com.faro.v5.maintenance.GenerationProjectionReconciliationPort
import ar.com.faro.v5.maintenance.GenerationRuntimeActivationPort
import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.GenerationSwitchCoordinator
import ar.com.faro.v5.maintenance.GenerationSwitchFaultPoint
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationId
import ar.com.faro.v5.maintenance.ValidatedGenerationActivationCoordinator
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest

internal data class Household10CutoverIntent(
    val operationId: MaintenanceOperationId,
    val origin: ActiveGenerationPointer,
    val targetId: StorageGenerationId,
    val archiveDigest: String,
    val originDigest: String,
    val targetDigest: String,
) {
    init {
        require(origin.generationId != targetId)
        require(listOf(archiveDigest, originDigest, targetDigest).all { it.matches(Regex("[0-9a-f]{64}")) })
    }
}

/** Binds the shared journaled switch to the exact verified Household10 DEV generation. */
internal class FaroV5Household10DevCutoverOwner(
    private val root: Path,
    private val quiescence: MaterialWriteQuiescencePort,
    private val assertExclusiveClosedDevHost: () -> Unit,
    private val fault: (GenerationSwitchFaultPoint) -> Unit,
    private val afterReplayFence: () -> Unit,
    private val targetRuntimeHealthy: () -> Boolean,
) {
    fun activate(intent: Household10CutoverIntent, recovery: Boolean): GenerationActivationResult {
        assertExclusiveClosedDevHost()
        val pointer = FileActiveGenerationPointer(root)
        val journal = FileBootstrapJournal(root)
        val layout = FileStorageGenerationLayout(root)
        val stage = layout.requireStaged(intent.targetId)
        val preflight = FaroV5Household10DevCutoverPreflight(root, quiescence, assertExclusiveClosedDevHost)
        val origin = intent.origin
        val selected = ActiveGenerationPointer(Math.addExact(origin.sequence, 1L), intent.targetId)
        val rolledBack = ActiveGenerationPointer(Math.addExact(origin.sequence, 2L), origin.generationId)
        val operation = intent.operationId

        fun requireOrigin() {
            assertExclusiveClosedDevHost()
            val candidate = layout.requireStaged(origin.generationId)
            FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(candidate, allowAuxiliary3 = false)
            check(fingerprint(candidate) == intent.originDigest) { "Household10 origin changed after authorization" }
        }

        fun requireTarget() {
            assertExclusiveClosedDevHost()
            check(layout.requireStaged(intent.targetId) == stage)
            FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage(stage)
            check(fingerprint(stage) == intent.targetDigest) { "Household10 target changed after archive verification" }
        }

        fun targetIsHealthy(): Boolean {
            requireTarget()
            return pointer.read() == selected && targetRuntimeHealthy() &&
                preflight.verifyAlreadyActive(operation, stage, selected) &&
                fingerprint(stage) == intent.targetDigest
        }

        var record = checkNotNull(journal.read(operation))
        check(record.kind == MaintenanceOperationKind.RESTORE && record.previousGenerationId == origin.generationId &&
            record.targetGenerationId == intent.targetId && journal.pending().all { it == record })
        val observed = pointer.read()
        check(when (record.phase) {
            BootstrapPhase.PREPARING, BootstrapPhase.STAGED, BootstrapPhase.VALIDATED -> observed == origin
            BootstrapPhase.SWITCH_PREPARED -> observed == origin || observed == selected
            BootstrapPhase.SWITCHED, BootstrapPhase.HEALTH_VERIFIED, BootstrapPhase.COMPLETED -> observed == selected
            BootstrapPhase.ROLLBACK_PREPARED -> observed == selected || observed == rolledBack
            BootstrapPhase.ROLLED_BACK_POST_SWITCH, BootstrapPhase.ROLLBACK_VERIFIED -> observed == rolledBack
            else -> false
        }) { "Household10 journal and full pointer disagree" }
        requireOrigin()

        if (record.phase == BootstrapPhase.PREPARING || record.phase == BootstrapPhase.STAGED) {
            requireTarget()
            if (record.phase == BootstrapPhase.PREPARING) {
                val stagedRecord = BootstrapTransitionPolicy.transition(record, BootstrapPhase.STAGED)
                check(journal.compareAndSet(record, stagedRecord))
                record = stagedRecord
            }
            val validated = BootstrapTransitionPolicy.transition(record, BootstrapPhase.VALIDATED)
            check(journal.compareAndSet(record, validated))
            record = validated
        }
        if (record.phase == BootstrapPhase.VALIDATED) {
            check(preflight.inspect(operation, stage, origin))
        }

        val fence = FileGenerationReplayFence(root)
        check(!fence.isInstalled(MaintenanceOperationKind.MIGRATION, intent.targetId))
        val replayFenced = fence.isInstalled(MaintenanceOperationKind.RESTORE, intent.targetId)
        if (replayFenced || record.materialWritesAccepted) {
            check(replayFenced && observed == selected &&
                record.phase in setOf(BootstrapPhase.HEALTH_VERIFIED, BootstrapPhase.COMPLETED))
            if (!targetIsHealthy()) return GenerationActivationResult.Blocked(
                operation, GenerationActivationBlockReason.TARGET_RUNTIME_UNSUPPORTED,
            )
        }

        val coordinator = ValidatedGenerationActivationCoordinator(
            journal, pointer, layout, quiescence, GenerationSwitchCoordinator(journal, pointer, layout, fault),
            runtime = GenerationRuntimeActivationPort { kind, id ->
                kind == MaintenanceOperationKind.RESTORE && when (id) {
                    intent.targetId -> targetIsHealthy()
                    origin.generationId -> {
                        requireOrigin()
                        pointer.read() == rolledBack
                    }
                    else -> false
                }
            },
            replayFence = fence,
            projections = GenerationProjectionReconciliationPort { _, _ -> afterReplayFence() },
            preSwitchAdmission = GenerationPreSwitchAdmissionPort { kind, id ->
                kind == MaintenanceOperationKind.RESTORE && id == intent.targetId && when (pointer.read()) {
                    origin -> {
                        requireOrigin()
                        requireTarget()
                        val pending = checkNotNull(journal.read(operation))
                        check(pending == journal.pending().singleOrNull() &&
                            pending.previousGenerationId == origin.generationId &&
                            pending.targetGenerationId == intent.targetId && !pending.materialWritesAccepted &&
                            pending.phase in setOf(BootstrapPhase.VALIDATED, BootstrapPhase.SWITCH_PREPARED))
                        pointer.read() == origin
                    }
                    selected -> targetIsHealthy()
                    else -> false
                }
            },
        )
        val result = if (recovery) coordinator.recover(operation) else coordinator.activate(operation)
        val completed = checkNotNull(journal.read(operation))
        when (result) {
            is GenerationActivationResult.Activated -> {
                check(pointer.read() == selected && completed.phase == BootstrapPhase.COMPLETED &&
                    completed.materialWritesAccepted && fence.isInstalled(MaintenanceOperationKind.RESTORE, intent.targetId))
                check(targetIsHealthy())
            }
            is GenerationActivationResult.RolledBack -> {
                check(pointer.read() == rolledBack && completed.phase == BootstrapPhase.ROLLBACK_VERIFIED &&
                    !fence.isInstalled(MaintenanceOperationKind.RESTORE, intent.targetId))
                requireOrigin()
            }
            is GenerationActivationResult.Blocked -> Unit
        }
        return result
    }

    companion object {
        internal fun fingerprint(stage: StagedStorageGeneration): String {
            val directory = stage.directory
            check(directory.toRealPath() == directory.toAbsolutePath().normalize())
            val digest = MessageDigest.getInstance("SHA-256")
            val descriptorBytes = ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeUTF(stage.descriptor.generationId.value)
                    output.writeUTF(stage.descriptor.schemaSetFingerprint)
                    output.writeLong(stage.descriptor.createdAtEpochMillis)
                }
            }.toByteArray()
            digest.update(descriptorBytes)
            for (name in GenerationStoreFiles.WORKSPACE_V2) {
                val file = directory.resolve(name)
                check(Files.isRegularFile(file, NOFOLLOW_LINKS) &&
                    (Files.getAttribute(file, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L)
                for (suffix in listOf("-wal", "-shm", "-journal")) {
                    check(Files.notExists(directory.resolve(name + suffix), NOFOLLOW_LINKS))
                }
                val fileDigest = MessageDigest.getInstance("SHA-256")
                DigestInputStream(Files.newInputStream(file, NOFOLLOW_LINKS), fileDigest).use {
                    it.transferTo(OutputStream.nullOutputStream())
                }
                digest.update(fileDigest.digest())
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
