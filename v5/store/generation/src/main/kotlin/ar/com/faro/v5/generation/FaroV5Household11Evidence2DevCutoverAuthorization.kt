package ar.com.faro.v5.generation

import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import ar.com.faro.v5.evidence.SealedCommercialEvidence2SnapshotReader
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.BootstrapJournalEntry
import ar.com.faro.v5.maintenance.BootstrapPhase
import ar.com.faro.v5.maintenance.BootstrapTransitionPolicy
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
import ar.com.faro.v5.store.reality.RealityHousehold11NeedsRowsPort
import ar.com.faro.v5.store.reality.RealityHousehold11ReceiptRowsPort
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

/**
 * W05-A1B3C2B1B: durable Evidence2 SQL11 PRE-CUTOVER DEV authorization only. No pointer switch,
 * replay fence, app graph, material writer, stock Apply or automatic cutover.
 */
internal class FaroV5Household11Evidence2DevCutoverAuthorization private constructor(
    private val root: Path,
    private val gate: SingleProcessMaterialWriteGate,
    private val channel: FileChannel,
    private val rootLock: FileLock,
    private val hostClosed: () -> Boolean,
    private val afterAuthorization: () -> Unit,
    private val afterTicket: () -> Unit,
    private val afterJournal: () -> Unit,
    private val afterStaged: () -> Unit,
) : AutoCloseable {
    enum class Status { VALIDATED, STAGED, PREPARING, TICKET_ONLY, NO_TICKET, BLOCKED }
    data class Selection(val status: Status, val pointer: ActiveGenerationPointer?)

    private data class Ticket(
        val op: MaintenanceOperationId,
        val origin: ActiveGenerationPointer,
        val sourceId: StorageGenerationId,
        val targetId: StorageGenerationId,
        val archiveSha256: String,
        val originFingerprint: String,
        val targetFingerprint: String,
        val needsRevision: Long,
        val needsCount: Int,
        val receiptsRevision: Long,
        val receiptsCount: Int,
    ) {
        init {
            require(origin.generationId != targetId && sourceId != targetId)
            require(listOf(archiveSha256, originFingerprint, targetFingerprint)
                .all { it.matches(Regex("[0-9a-f]{64}")) })
            require(needsRevision >= 0L && receiptsRevision >= 0L)
            require(needsCount in 0..8192 && receiptsCount in 0..8192)
        }
    }

    private val ticketFile = root.resolve("bootstrap").resolve("household11-evidence2-dev-cutover-v2.bin")
    private val legacyTicket = root.resolve("bootstrap").resolve("household11-dev-cutover-v1.bin")
    private val journal = FileBootstrapJournal(root)
    private val pointer = FileActiveGenerationPointer(root)
    private val layout = FileStorageGenerationLayout(root)
    private val identity = FileRestoreArchiveIdentity(root)
    private val fence = FileGenerationReplayFence(root)
    private val crypto = FaroV5FiveStorePortableBackupCrypto()
    private val noNestedQuiescence = object : MaterialWriteQuiescencePort {
        override fun <T> withMaterialWritesQuiesced(
            operationId: MaintenanceOperationId,
            block: () -> T,
        ): T = block()
    }
    private var closed = false

    /**
     * Foreground authorization is required both before admission and
     * immediately before publishing the durable ticket. A retry of the same
     * operation must present the identical original encrypted v8 bytes.
     */
    fun authorize(
        operationId: MaintenanceOperationId,
        stage: StagedStorageGeneration,
        expectedActive: ActiveGenerationPointer,
        encryptedArchive: ByteArray,
        passphrase: CharArray,
        confirmInForeground: () -> Boolean,
    ): Selection = guarded {
        check(confirmInForeground()) { "SQL11 cutover requires foreground confirmation" }
        val frozen = encryptedArchive.copyOf()
        try {
            requireLive()
            val existing = readTicket()
            check(existing == null || existing.op == operationId) {
                "Evidence2 durable ticket belongs to another operation"
            }
            if (existing == null) {
                val inspection = FaroV5Household11Evidence2SemanticPreflight(
                    root, noNestedQuiescence, ::requireLive,
                ).inspect(operationId, stage, expectedActive, frozen, passphrase)
                check(confirmInForeground()) { "SQL11 authorization was not reconfirmed" }
                afterAuthorization()
                // The preflight MUST be valid again after foreground actions,
                // immediately before publication (including full pointer ABA).
                val repeated = FaroV5Household11Evidence2SemanticPreflight(
                    root, noNestedQuiescence, ::requireLive,
                ).inspect(operationId, stage, expectedActive, frozen, passphrase)
                check(repeated == inspection) { "SQL11 cutover preflight changed before authorization" }
                val ticket = inspection.toTicket(operationId)
                publishTicket(ticket)
                afterTicket() // A process crash here leaves ticket only; no journal or pointer.
                verifyTicket(ticket, stage, frozen, passphrase)
                advanceJournal(ticket)
            } else {
                check(confirmInForeground()) { "SQL11 resumed authorization not reconfirmed" }
                verifyTicket(existing, stage, frozen, passphrase)
                advanceJournal(existing)
            }
            Selection(Status.VALIDATED, pointer.read())
        } finally {
            frozen.fill(0)
        }
    }

    /**
     * Startup inspection only; NEVER advances a journal, recreates a missing
     * ticket, changes the pointer, installs a replay fence or grants Apply.
     * An interrupted TICKET_ONLY state needs an explicit encrypted archive.
     */
    fun recoverReadOnly(): Selection = guarded {
        val ticket = readTicket() ?: run {
            check(journal.pending().isEmpty()) { "SQL11 missing cutover ticket with pending journal" }
            return@guarded Selection(Status.NO_TICKET, pointer.read())
        }
        verifyTicketWithoutArchive(ticket)
        val current = journal.read(ticket.op)
        check(journal.pending().all { it == current }) { "SQL11 foreign pending journal" }
        val status = when (current?.phase) {
            null -> Status.TICKET_ONLY
            BootstrapPhase.PREPARING -> Status.PREPARING
            BootstrapPhase.STAGED -> Status.STAGED
            BootstrapPhase.VALIDATED -> Status.VALIDATED
            else -> error("SQL11 authorization recovery requires pre-switch journal")
        }
        Selection(status, pointer.read())
    }

    private fun FaroV5Household11CutoverInspection.toTicket(op: MaintenanceOperationId) = Ticket(
        op, origin, sourceGenerationId, targetGenerationId, archiveSha256,
        originFingerprint, targetFingerprint,
        needsRevision, needsCount, receiptsRevision, receiptsCount,
    )

    private fun verifyTicket(
        ticket: Ticket,
        stage: StagedStorageGeneration,
        archive: ByteArray,
        passphrase: CharArray,
    ) {
        requireLive()
        check(stage.descriptor.generationId == ticket.targetId &&
            sha256Hex(archive) == ticket.archiveSha256) {
            "SQL11 authorization archive, operation or target substituted"
        }
        identity.requireExistingPinnedArchive(ticket.op, ticket.targetId, archive)
        val payload = when (val opened = crypto.openWithHouseholdReceipts11(archive, passphrase)) {
            is FiveStoreAuthenticatedPayload.Opened -> opened.payload
            is FiveStoreAuthenticatedPayload.Rejected -> error(
                "SQL11 authorization requires authenticated FBS2v8: " + opened.reason,
            )
        }
        val commercial = payload.base.base.base.fourStore.commercialEvidence
        check(payload.base.base.base.fourStore.manifest.sourceGenerationId == ticket.sourceId &&
            commercial.schemaVersion == 2 &&
            commercial.stagedTyped != null &&
            payload.base.base.base.finance9 != null &&
            payload.base.household.revision == ticket.needsRevision &&
            payload.base.household.needs.size == ticket.needsCount &&
            payload.receipts.revision == ticket.receiptsRevision &&
            payload.receipts.receipts.size == ticket.receiptsCount) {
            "SQL11 authorization changed authenticated FBS2v8 payload"
        }
        val authenticatedTyped = checkNotNull(commercial.stagedTyped)
        check(SealedCommercialEvidence2SnapshotReader.read(
            stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE)
        ) == authenticatedTyped) {
            "Evidence2 durable authorization commercial semantics differ from sealed FBS2v8"
        }
        verifyTicketWithoutArchive(ticket)
    }

    private fun verifyTicketWithoutArchive(ticket: Ticket) {
        requireLive()
        check(pointer.read() == ticket.origin) { "SQL11 pre-cutover origin full pointer changed" }
        val stage = layout.requireStaged(ticket.targetId)
        val original = layout.requireStaged(ticket.origin.generationId)
        check(FaroV5Household10DevCutoverOwner.fingerprint(original) ==
            ticket.originFingerprint &&
            FaroV5Household10DevCutoverOwner.fingerprint(stage) ==
            ticket.targetFingerprint) { "SQL11 pre-cutover five-store fingerprint drift" }
        FaroV5GenerationStoreGraph.requireFinance9ReceiptArchiveStage(
            original, allowAuxiliary3 = false,
        )
        check(SealedCommercialEvidence2SnapshotReader.read(
            stage.directory.resolve(GenerationStoreFiles.COMMERCIAL_EVIDENCE)
        ).schemaVersion == 2)
        val reality = stage.directory.resolve(GenerationStoreFiles.REALITY)
        val needs = RealityHousehold11NeedsRowsPort.readTrustedSealedStageEleven(reality)
        val receipts = RealityHousehold11ReceiptRowsPort.readTrustedSealedStageEleven(reality)
        check(needs.revision == ticket.needsRevision &&
            needs.needs.size == ticket.needsCount &&
            receipts.revision == ticket.receiptsRevision &&
            receipts.receipts.size == ticket.receiptsCount) {
            "SQL11 pre-cutover H10/H11R revisions or counts changed"
        }
        check(!fence.isInstalled(MaintenanceOperationKind.RESTORE, ticket.targetId) &&
            !fence.isInstalled(MaintenanceOperationKind.MIGRATION, ticket.targetId)) {
            "SQL11 pre-cutover target is already replay-fenced"
        }
        val recorded = journal.read(ticket.op)
        check(recorded == null || (recorded.operationId == ticket.op &&
            recorded.kind == MaintenanceOperationKind.RESTORE &&
            recorded.previousGenerationId == ticket.origin.generationId &&
            recorded.targetGenerationId == ticket.targetId &&
            recorded.phase in setOf(
                BootstrapPhase.PREPARING, BootstrapPhase.STAGED, BootstrapPhase.VALIDATED,
            ) && !recorded.materialWritesAccepted)) {
            "SQL11 pre-cutover journal is not a valid pending authorization"
        }
        check(journal.pending().all { it == recorded }) {
            "SQL11 pre-cutover blocked by a foreign unresolved operation"
        }
        requireLive()
        check(pointer.read() == ticket.origin) { "SQL11 pre-cutover pointer ABA after verification" }
    }

    private fun advanceJournal(ticket: Ticket) {
        verifyTicketWithoutArchive(ticket)
        var current = journal.read(ticket.op)
        if (current == null) {
            val preparing = BootstrapJournalEntry(
                ticket.op, MaintenanceOperationKind.RESTORE,
                ticket.origin.generationId, ticket.targetId, BootstrapPhase.PREPARING,
            )
            check(journal.pending().isEmpty() && pointer.read() == ticket.origin)
            journal.create(preparing)
            current = preparing
            afterJournal() // crash here: durable PREPARING, still no switch
        }
        if (current.phase == BootstrapPhase.PREPARING) {
            verifyTicketWithoutArchive(ticket)
            val staged = BootstrapTransitionPolicy.transition(current, BootstrapPhase.STAGED)
            check(journal.compareAndSet(current, staged)) {
                "SQL11 cutover journal CAS failed PREPARING to STAGED"
            }
            current = staged
            afterStaged() // crash here: durable STAGED, pointer still origin
        }
        if (current.phase == BootstrapPhase.STAGED) {
            verifyTicketWithoutArchive(ticket)
            val validated = BootstrapTransitionPolicy.transition(current, BootstrapPhase.VALIDATED)
            check(journal.compareAndSet(current, validated)) {
                "SQL11 cutover journal CAS failed STAGED to VALIDATED"
            }
            current = validated
        }
        verifyTicketWithoutArchive(ticket)
        check(current.phase == BootstrapPhase.VALIDATED &&
            journal.read(ticket.op) == current &&
            pointer.read() == ticket.origin) {
            "SQL11 cutover was not left journaled and pre-switch validated"
        }
    }

    private fun publishTicket(ticket: Ticket) {
        requireLive()
        val bootstrap = ticketFile.parent
        check(bootstrap.toRealPath() == bootstrap &&
            Files.isDirectory(bootstrap, NOFOLLOW_LINKS) &&
            Files.notExists(ticketFile, NOFOLLOW_LINKS)) {
            "SQL11 authorization refuses existing ticket or unsafe bootstrap path"
        }
        check(journal.read(ticket.op) == null && journal.pending().isEmpty()) {
            "SQL11 authorization refuses conflicting journal before ticket"
        }
        val payload = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeUTF(TICKET_VERSION)
                out.writeUTF(ticket.op.value)
                out.writeLong(ticket.origin.sequence)
                out.writeUTF(ticket.origin.generationId.value)
                out.writeUTF(ticket.sourceId.value)
                out.writeUTF(ticket.targetId.value)
                out.writeUTF(ticket.archiveSha256)
                out.writeUTF(ticket.originFingerprint)
                out.writeUTF(ticket.targetFingerprint)
                out.writeLong(ticket.needsRevision)
                out.writeInt(ticket.needsCount)
                out.writeLong(ticket.receiptsRevision)
                out.writeInt(ticket.receiptsCount)
            }
        }.toByteArray()
        val tmp = Files.createTempFile(bootstrap, ".h11-evidence2-cutover-auth-", ".tmp")
        try {
            Files.write(tmp, payload + sha256(payload), WRITE, NOFOLLOW_LINKS)
            FileChannel.open(tmp, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
            Files.move(tmp, ticketFile, ATOMIC_MOVE)
            FileChannel.open(bootstrap, READ, NOFOLLOW_LINKS).use { it.force(true) }
            check(readTicket() == ticket) { "SQL11 authorization ticket failed durable readback" }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun readTicket(): Ticket? {
        if (Files.notExists(ticketFile, NOFOLLOW_LINKS)) return null
        check(ticketFile.parent.toRealPath() == ticketFile.parent &&
            Files.isRegularFile(ticketFile, NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(ticketFile) &&
            (Files.getAttribute(ticketFile, "unix:nlink", NOFOLLOW_LINKS) as Number).toLong() == 1L &&
            Files.size(ticketFile) in 65..4096) {
            "SQL11 authorization ticket is not a canonical immutable file"
        }
        val bytes = Files.readAllBytes(ticketFile)
        val payload = bytes.copyOfRange(0, bytes.size - 32)
        check(MessageDigest.isEqual(
            sha256(payload), bytes.copyOfRange(payload.size, bytes.size),
        )) { "SQL11 authorization ticket checksum mismatch" }
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            check(input.readUTF() == TICKET_VERSION) { "SQL11 authorization ticket version mismatch" }
            val operationId = MaintenanceOperationId(input.readUTF())
            val origin = ActiveGenerationPointer(
                input.readLong(), StorageGenerationId(input.readUTF()),
            )
            val record = Ticket(
                operationId, origin,
                StorageGenerationId(input.readUTF()), StorageGenerationId(input.readUTF()),
                input.readUTF(), input.readUTF(), input.readUTF(),
                input.readLong(), input.readInt(), input.readLong(), input.readInt(),
            )
            check(input.available() == 0) { "SQL11 authorization ticket trailing bytes" }
            record
        }
    }

    private fun guarded(block: () -> Selection): Selection = try {
        gate.withQuiescedMaterialWrites {
            requireLive()
            block()
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        Selection(Status.BLOCKED, pointer.read())
    } catch (_: Exception) {
        Selection(Status.BLOCKED, pointer.read())
    }

    private fun requireLive() {
        check(!closed && channel.isOpen && rootLock.isValid && hostClosed() &&
            Files.notExists(legacyTicket, NOFOLLOW_LINKS)) {
            "SQL11 authorization requires the exclusive closed DEV root"
        }
    }

    override fun close() = gate.withQuiescedMaterialWrites {
        if (!closed) {
            closed = true
            try {
                rootLock.release()
            } finally {
                channel.close()
            }
        }
    }

    companion object {
        private const val TICKET_VERSION = "FARO_H11_SQL11_EVIDENCE2_PRE_CUTOVER_AUTHORIZATION_V2"
        private fun sha256(data: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(data)
        private fun sha256Hex(data: ByteArray): String =
            sha256(data).joinToString("") { "%02x".format(it) }

        fun acquire(
            storageRoot: Path,
            gate: SingleProcessMaterialWriteGate,
            hostClosed: () -> Boolean,
            afterAuthorization: () -> Unit = {},
            afterTicket: () -> Unit = {},
            afterJournal: () -> Unit = {},
            afterStaged: () -> Unit = {},
        ): FaroV5Household11Evidence2DevCutoverAuthorization {
            check(Files.isDirectory(storageRoot, NOFOLLOW_LINKS) && hostClosed()) {
                "SQL11 authorization requires existing closed DEV root"
            }
            val root = storageRoot.toRealPath()
            val channel = FileChannel.open(
                root.resolve("finance9-dev-root.lock"), CREATE, WRITE, NOFOLLOW_LINKS,
            )
            return try {
                val exclusive = checkNotNull(channel.tryLock()) {
                    "SQL11 cutover root lease already held"
                }
                FaroV5Household11Evidence2DevCutoverAuthorization(
                    root, gate, channel, exclusive, hostClosed,
                    afterAuthorization, afterTicket, afterJournal, afterStaged,
                )
            } catch (failure: Exception) {
                channel.close()
                throw IllegalStateException("SQL11 cutover root lease unavailable", failure)
            }
        }
    }
}
