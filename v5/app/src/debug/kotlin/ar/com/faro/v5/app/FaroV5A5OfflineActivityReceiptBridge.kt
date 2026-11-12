package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.GenerationStoreFiles
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityStore
import ar.com.faro.v5.store.reality.RealityTimedReceiptQuery
import ar.com.faro.v5.surface.*
import ar.com.faro.v5.workspace.ConversationWorkspaceStore
import ar.com.faro.v5.workspace.WorkspaceHistoryQuery
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * DEBUG ONLY: a technical staged-session reader, never an ActiveGenerationPointer
 * or human authentication. Reads the very same owners used by reviewed Apply.
 * No events/receipts/authority are reconstructed or copied from another root.
 */
internal class FaroV5A5OfflineActivityReceiptBridge(
    staged: StagedStorageGeneration,
    private val reality: RealityStore,
    private val workspace: ConversationWorkspaceStore,
    private val stageStillCurrent: () -> Boolean,
) : FaroV5ActivityReadPort, AutoCloseable {
    private data class Grant(val realityEpoch: String, val workspaceEpoch: String)
    private val closed = AtomicBoolean(false)
    private val grant = AtomicReference<Grant?>(null)
    private val changes = MutableStateFlow(0L)
    private val files = (listOf(staged.directory) + GenerationStoreFiles.WORKSPACE_V2.map {
        staged.directory.resolve(it)
    }).associateWith(::identity)

    private fun identity(path: Path): Any {
        check(!Files.isSymbolicLink(path))
        return checkNotNull(Files.readAttributes(path, BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS).fileKey()) { "physical stage identity unavailable" }
    }
    private fun current(expected: Grant): Boolean {
        if (grant.get() != expected) return false
        val valid = runCatching {
            !closed.get() && stageStillCurrent() && files.all { (path, key) -> identity(path) == key }
        }.getOrDefault(false)
        // A detected physical/descriptor/host change consumes this grant. An
        // ABA restoration of identical bytes cannot silently resurrect it.
        if (!valid && grant.compareAndSet(expected, null)) changes.update { it + 1L }
        return valid && grant.get() == expected
    }

    /** Called only by the owning Activity after a distinct foreground button. */
    fun authorizeAfterForegroundRequest(foreground: () -> Boolean) {
        if (closed.get() || !foreground() || !stageStillCurrent()) { revoke(); return }
        val fresh = Grant(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        grant.set(fresh)
        if (!foreground() || !current(fresh)) { revoke(); return }
        changes.update { it + 1L }
    }
    fun revoke() { grant.set(null); changes.update { it + 1L } }
    fun ownerChanged() { changes.update { it + 1L } }

    // No owner polling until separately granted; source changes invalidate stale
    // pages and summaries even if the Activity destination stays mounted.
    override val invalidations: Flow<Unit> = merge(changes.map { Unit }, flow {
        var previous: Pair<Long, Long>? = null
        while (currentCoroutineContext().isActive) {
            delay(250)
            val initial = grant.get()
            val now = if (initial != null && current(initial)) runCatching {
                val stamp = reality.read().revision to workspace.snapshot().revision
                if (current(initial)) stamp else null
            }.getOrNull() else null
            if (now != previous) { previous = now; emit(Unit) }
        }
    }.flowOn(Dispatchers.IO))

    override suspend fun readActivity(): FaroV5ActivityPage? {
        val initial = grant.get() ?: return null
        if (!current(initial)) return null
        return try {
            val realityRevision = reality.read().revision
            val receipts = reality.timedReceiptHistory.query(RealityTimedReceiptQuery(limit = 50))
            if (!current(initial) || receipts.canonicalSource != "reality.execution_receipt") return null
            val messages = workspace.readHistoryPage(WorkspaceHistoryQuery(limit = 50))
            if (!current(initial) || messages.canonicalSource != "workspace.message" ||
                !messages.hostAdmissionRequired) return null
            val entries = receipts.receipts.map { timed ->
                FaroV5ActivityEntry(FaroV5ActivitySource.REALITY_COMMITTED_RECEIPT,
                    timed.receipt.transactionId.value, FaroV5ActivityEventKind.APPLY_COMMITTED,
                    timed.recordedAtEpochMillis, FaroV5ActivityTimeProvenance.REALITY_COMMIT_RECORDED)
            } + messages.events.map { event ->
                check(event.timeProvenance == "workspace.message.created_ms")
                val kind = when (event.kind.name) {
                    "USER_ACCEPTED" -> FaroV5ActivityEventKind.WORKSPACE_USER_MESSAGE
                    "FARO_COMPLETED" -> FaroV5ActivityEventKind.WORKSPACE_ASSISTANT_MESSAGE
                    else -> error("unknown Workspace history kind")
                }
                FaroV5ActivityEntry(FaroV5ActivitySource.WORKSPACE_MESSAGE, event.messageId.value,
                    kind, event.createdAtEpochMillis, FaroV5ActivityTimeProvenance.WORKSPACE_PRODUCER_DECLARED)
            }
            if (!current(initial) || reality.read().revision != realityRevision ||
                workspace.snapshot().revision != messages.ownerRevision) return null
            FaroV5ActivityPage(entries.sortedWith(compareByDescending<FaroV5ActivityEntry> {
                it.timestampEpochMillis
            }.thenBy { it.source.ordinal }.thenBy { it.opaqueId }).take(50),
                completeWithinSources = entries.size <= 50 && receipts.nextCursor == null && messages.complete,
                hasUndatedLegacyReceipts = receipts.legacyUndated)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { null }
    }
    override fun close() { closed.set(true); revoke() }
}
