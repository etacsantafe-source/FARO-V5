package ar.com.faro.v5.app

import ar.com.faro.v5.generation.FaroV5Planning8DevCutoverOwner
import ar.com.faro.v5.generation.FaroV5Planning8DevStageJournalOwner
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.runtime.CanonicalPreparedPlanEnvelopeVerifier
import ar.com.faro.v5.maintenance.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Dedicated V2CoreDev-only Reality8 promotion/restart boundary. No shipping caller.
 * The old Reality7 five-store graph remains immutable and available for rollback.
 *
 * The full origin pointer is recorded DURABLY before creating the Stage8 journal;
 * a journal ID alone does not bind a pointer sequence across process death.
 * No new promotion happens on normal Chat open, only exact recovery of a
 * previously and explicitly requested operation.
 */
internal class FaroV5V2Planning8DevHost(
    private val root: Path,
    private val gate: MaterialWriteGate,
    private val afterStageForTest: () -> Unit = {},
) {
    val targetId = StorageGenerationId("v2-core-dev-planning8-1")
    private val previousId = StorageGenerationId("v2-core-dev-workspace-1")
    private val operationId = MaintenanceOperationId("v2-core-dev-planning8-upgrade-1")
    private val descriptor = StorageGenerationDescriptor(
        targetId, "v2-core-dev-five-store-reality8-v1", 8L,
    )
    private val oldOperation = MaintenanceOperationId("v2-core-dev-first-activation-1")
    private val pointer = FileActiveGenerationPointer(root)
    private val journal = FileBootstrapJournal(root)
    private val fence = FileGenerationReplayFence(root)
    private val quiescence = MaterialWriteGateQuiescence(gate)
    private val originFile = root.resolve("dev-planning8-origin-v1.bin")

    /** Existing normal Chat MUST NOT stage a new generation. */
    fun recoverPreviouslyAuthorizedOrFail() {
        val entry = journal.read(operationId) ?: return
        val origin = requireOrigin()
        check(entry.operationId == operationId &&
            entry.kind == MaintenanceOperationKind.RESTORE &&
            entry.previousGenerationId == origin.generationId &&
            entry.targetGenerationId == targetId) {
            "DEV Reality8 recovery found foreign journal identity"
        }
        check(journal.pending().all { it.operationId == operationId }) {
            "DEV Reality8 cannot coexist with foreign pending operation"
        }
        val observed = checkNotNull(pointer.read()) { "DEV Reality8 pointer missing" }
        val switched = ActiveGenerationPointer(Math.addExact(origin.sequence, 1L), targetId)
        val rolledBack = ActiveGenerationPointer(
            Math.addExact(origin.sequence, 2L), origin.generationId,
        )
        when (entry.phase) {
            BootstrapPhase.COMPLETED -> {
                check(observed == switched) { "DEV Reality8 completed pointer sequence mismatch" }
                requireCompleted(switched)
                return
            }
            BootstrapPhase.ROLLED_BACK_POST_SWITCH,
            BootstrapPhase.ROLLBACK_VERIFIED -> {
                check(observed == rolledBack) {
                    "DEV Reality8 rollback pointer sequence mismatch"
                }
                if (entry.phase == BootstrapPhase.ROLLED_BACK_POST_SWITCH) {
                    check(cutover().recover(operationId, descriptor, origin)
                        is GenerationActivationResult.RolledBack)
                }
                return
            }
            BootstrapPhase.PREPARING -> error(
                "DEV Reality8 PREPARING residue requires manual quarantine review"
            )
            BootstrapPhase.ABORTED_PRE_SWITCH -> error(
                "DEV Reality8 aborted operation cannot be adopted"
            )
            BootstrapPhase.STAGED -> {
                check(observed == origin) { "DEV Reality8 STAGED origin was replaced" }
                FaroV5Planning8DevStageJournalOwner(root, quiescence)
                    .recoverValidatedStage(operationId, descriptor, origin)
            }
            BootstrapPhase.VALIDATED -> {
                check(observed == origin) { "DEV Reality8 VALIDATED origin was replaced" }
            }
            BootstrapPhase.SWITCH_PREPARED -> check(
                observed == origin || observed == switched
            ) { "DEV Reality8 SWITCH_PREPARED pointer is foreign" }
            BootstrapPhase.SWITCHED,
            BootstrapPhase.HEALTH_VERIFIED,
            BootstrapPhase.CLEANUP_PENDING -> check(observed == switched) {
                "DEV Reality8 post-switch sequence is foreign"
            }
            BootstrapPhase.ROLLBACK_PREPARED -> check(
                observed == switched || observed == rolledBack
            ) { "DEV Reality8 rollback-prepared sequence is foreign" }
        }
        when (cutover().recover(operationId, descriptor, origin)) {
            is GenerationActivationResult.Activated ->
                requireCompleted(checkNotNull(pointer.read()))
            is GenerationActivationResult.RolledBack -> check(
                pointer.read() == rolledBack &&
                !fence.isInstalled(MaintenanceOperationKind.RESTORE, targetId)
            ) { "DEV Reality8 rollback did not preserve previous authority" }
            is GenerationActivationResult.Blocked -> error(
                "DEV Reality8 exact journal recovery blocked"
            )
        }
    }

    /**
     * This method is ONLY called by the dedicated foreground upgrade button.
     * A prior completed old7 journal and replay fence are mandatory.
     * The button does not copy or alter old7 stores.
     */
    fun promoteAfterExplicitForegroundRequest(foreground: () -> Boolean) {
        check(foreground()) { "DEV Reality8 upgrade requires foreground user action" }
        val origin = checkNotNull(pointer.read()) { "DEV old pointer missing" }
        check(origin.generationId == previousId) {
            "DEV Reality8 new promotion requires active Reality7"
        }
        val old = checkNotNull(journal.read(oldOperation)) { "DEV old journal missing" }
        check(old.phase == BootstrapPhase.COMPLETED && old.materialWritesAccepted &&
            old.targetGenerationId == previousId &&
            fence.isInstalled(MaintenanceOperationKind.RESTORE, previousId)) {
            "DEV old7 authority is not committed"
        }
        val existing = journal.read(operationId)
        if (existing != null) {
            check(requireOrigin() == origin) {
                "DEV Reality8 existing operation was authorized for another pointer"
            }
            recoverPreviouslyAuthorizedOrFail()
            return
        }
        check(journal.pending().isEmpty()) { "DEV Reality8 cannot interrupt maintenance" }
        publishOrVerifyOrigin(origin)
        check(foreground() && pointer.read() == origin) {
            "DEV Reality8 foreground or original pointer was revoked"
        }
        FaroV5Planning8DevStageJournalOwner(root, quiescence)
            .prepareFresh(operationId, descriptor, origin)
        afterStageForTest()
        // A user can leave the app during staging: keep VALIDATED durable but
        // refuse to START a pointer switch without a fresh foreground check.
        check(foreground() && pointer.read() == origin) {
            "DEV Reality8 foreground revoked before pointer cutover"
        }
        check(cutover().activateValidated(operationId, descriptor, origin)
            is GenerationActivationResult.Activated) {
            "DEV Reality8 canonical cutover did not activate"
        }
        requireCompleted(checkNotNull(pointer.read()))
    }

    fun requireCompleted(active: ActiveGenerationPointer) {
        val origin = requireOrigin()
        check(active == ActiveGenerationPointer(Math.addExact(origin.sequence, 1L), targetId) &&
            pointer.read() == active) { "DEV Reality8 full pointer is not committed" }
        val entry = checkNotNull(journal.read(operationId))
        check(entry.kind == MaintenanceOperationKind.RESTORE &&
            entry.previousGenerationId == origin.generationId &&
            entry.targetGenerationId == targetId &&
            entry.phase == BootstrapPhase.COMPLETED &&
            entry.materialWritesAccepted &&
            fence.isInstalled(MaintenanceOperationKind.RESTORE, targetId)) {
            "DEV Reality8 journal/replay fence not committed"
        }
    }

    fun isCommittedCurrent(active: ActiveGenerationPointer): Boolean = runCatching {
        requireCompleted(active)
        true
    }.getOrDefault(false)

    private fun cutover(): FaroV5Planning8DevCutoverOwner =
        FaroV5Planning8DevCutoverOwner(
            root, gate, quiescence, CanonicalPreparedPlanEnvelopeVerifier,
            certifiedHost = true,
        )

    private fun publishOrVerifyOrigin(origin: ActiveGenerationPointer) {
        check(origin.generationId == previousId && origin.sequence > 0)
        if (Files.exists(originFile, LinkOption.NOFOLLOW_LINKS)) {
            check(requireOrigin() == origin) {
                "DEV Reality8 immutable origin differs from this button request"
            }
            return
        }
        check(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(root))
        // A crash before atomic publish leaves a temp file; never ignore it.
        Files.newDirectoryStream(root, ".dev-planning8-origin-*.tmp").use { stream ->
            check(!stream.iterator().hasNext()) { "DEV Reality8 origin has crash residue" }
        }
        val body = buildString {
            append("FARO_DEV_PLANNING8_ORIGIN_V1\n")
            append(origin.generationId.value).append('\n')
            append(origin.sequence).append('\n')
            append(targetId.value).append('\n')
            append(operationId.value).append('\n')
            append(descriptor.schemaSetFingerprint).append('\n')
            append(descriptor.createdAtEpochMillis).append('\n')
        }
        val bytes = (body + sha256(body) + "\n").toByteArray(Charsets.UTF_8)
        check(bytes.size <= 1024)
        val temp = Files.createTempFile(root, ".dev-planning8-origin-", ".tmp")
        try {
            FileChannel.open(temp, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            check(!Files.exists(originFile, LinkOption.NOFOLLOW_LINKS)) {
                "DEV Reality8 origin appeared during publication"
            }
            Files.move(temp, originFile, StandardCopyOption.ATOMIC_MOVE)
        } catch (failure: Throwable) {
            // Leave crash artifacts for explicit inspection if publication fails.
            throw failure
        }
        check(requireOrigin() == origin)
    }

    private fun requireOrigin(): ActiveGenerationPointer {
        check(!Files.isSymbolicLink(originFile) &&
            Files.isRegularFile(originFile, LinkOption.NOFOLLOW_LINKS) &&
            Files.size(originFile) in 1L..1024L) {
            "DEV Reality8 immutable origin missing, symlinked or oversized"
        }
        val bytes = Files.readAllBytes(originFile)
        val string = bytes.toString(Charsets.UTF_8)
        check(string.toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
            "DEV Reality8 origin is not canonical UTF-8"
        }
        val lines = string.split('\n')
        check(lines.size == 9 && lines.last().isEmpty()) {
            "DEV Reality8 origin record fields are incomplete"
        }
        check(lines[0] == "FARO_DEV_PLANNING8_ORIGIN_V1" &&
            lines[1] == previousId.value && lines[3] == targetId.value &&
            lines[4] == operationId.value &&
            lines[5] == descriptor.schemaSetFingerprint &&
            lines[6] == descriptor.createdAtEpochMillis.toString()) {
            "DEV Reality8 origin identity or descriptor is foreign"
        }
        val sequence = lines[2].toLong()
        check(sequence > 0 && lines[2] == sequence.toString()) {
            "DEV Reality8 origin pointer sequence is not canonical"
        }
        val body = lines.take(7).joinToString("\n", postfix = "\n")
        check(lines[7] == sha256(body)) { "DEV Reality8 origin checksum mismatch" }
        return ActiveGenerationPointer(sequence, previousId)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
