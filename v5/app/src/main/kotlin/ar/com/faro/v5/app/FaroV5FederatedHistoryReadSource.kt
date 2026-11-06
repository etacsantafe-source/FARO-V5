package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.ConversationMessageId
import ar.com.faro.v5.store.reality.RealityTimedReceiptCursor
import ar.com.faro.v5.store.reality.RealityTimedReceiptHistoryReadPort
import ar.com.faro.v5.store.reality.RealityTimedReceiptQuery
import ar.com.faro.v5.workspace.WorkspaceHistoryCursor
import ar.com.faro.v5.workspace.WorkspaceHistoryPage
import ar.com.faro.v5.workspace.WorkspaceHistoryQuery
import kotlinx.coroutines.CancellationException

/** Only the canonical Workspace owner can supply the new N015 history projection. */
fun interface FaroV5WorkspaceHistoryReadPort {
    fun read(query: WorkspaceHistoryQuery): WorkspaceHistoryPage
}

enum class FaroV5ActivityDomain { REALITY_RECEIPT, WORKSPACE_MESSAGE }

data class FaroV5ActivityItem(
    val domain: FaroV5ActivityDomain,
    val eventKind: String,
    val opaqueId: String,
    val timestampEpochMillis: Long,
    val timeProvenance: String,
) {
    init {
        require(opaqueId.isNotBlank())
        require(timestampEpochMillis >= 0L)
        require(timeProvenance.isNotBlank())
    }
}

data class FaroV5FederatedHistoryCursor(
    val generationId: String,
    val pointerEpoch: String,
    val realityAclEpoch: String,
    val workspaceAclEpoch: String,
    val workspaceRevision: Long,
    val realityBefore: RealityTimedReceiptCursor?,
    val workspaceBefore: WorkspaceHistoryCursor?,
) {
    init {
        require(generationId.isNotBlank() && pointerEpoch.isNotBlank())
        require(realityAclEpoch.isNotBlank() && workspaceAclEpoch.isNotBlank())
        require(workspaceRevision >= 0)
    }
}

data class FaroV5FederatedHistoryPage(
    val items: List<FaroV5ActivityItem>,
    val nextCursor: FaroV5FederatedHistoryCursor?,
    val completeWithinTwoSources: Boolean,
    val legacyUndatedReality: Boolean,
) {
    val canonicalSources: Set<String>
        get() = setOf("reality.execution_receipt", "workspace.message")
    val globallyComplete: Boolean get() = false
}

/**
 * Two-owner Activity read. No synthetic events, writes, or UI authority.
 * The trusted host must inject real, independent read grants and a durable
 * pointer ABA epoch. Missing or changing authority denies the ENTIRE result.
 *
 * The timestamp of a Workspace message is producer-declared, NOT Apply time.
 * Future host integration must not promote this two-source view to global history.
 */
class FaroV5FederatedHistoryReadSource(
    private val expectedGenerationId: String,
    private val reality: RealityTimedReceiptHistoryReadPort,
    private val workspace: FaroV5WorkspaceHistoryReadPort,
    private val currentGenerationId: () -> String?,
    private val pointerCurrent: () -> Boolean,
    private val pointerEpoch: () -> String?,
    private val realityAclEpoch: () -> String?,
    private val workspaceAclEpoch: () -> String?,
    private val realityReadAdmitted: () -> Boolean,
    private val workspaceReadAdmitted: () -> Boolean,
) {
    init { require(expectedGenerationId.isNotBlank()) }

    private data class Admission(
        val pointer: String,
        val reality: String,
        val workspace: String,
    )

    private fun admission(): Admission? = try {
        if (!pointerCurrent() ||
            currentGenerationId() != expectedGenerationId ||
            !realityReadAdmitted() || !workspaceReadAdmitted()) null
        else {
            val p = pointerEpoch()?.takeIf(String::isNotBlank)
            val r = realityAclEpoch()?.takeIf(String::isNotBlank)
            val w = workspaceAclEpoch()?.takeIf(String::isNotBlank)
            if (p == null || r == null || w == null || !pointerCurrent() ||
                currentGenerationId() != expectedGenerationId ||
                !realityReadAdmitted() || !workspaceReadAdmitted() ||
                pointerEpoch() != p || realityAclEpoch() != r ||
                workspaceAclEpoch() != w
            ) null else Admission(p, r, w)
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }

    fun readAuthorized(
        limit: Int = 50,
        before: FaroV5FederatedHistoryCursor? = null,
    ): FaroV5FederatedHistoryPage? {
        require(limit in 1..100)
        val grant = admission() ?: return null
        if (before != null &&
            (before.generationId != expectedGenerationId ||
                before.pointerEpoch != grant.pointer ||
                before.realityAclEpoch != grant.reality ||
                before.workspaceAclEpoch != grant.workspace)
        ) return null

        val receipts = try {
            reality.query(RealityTimedReceiptQuery(before = before?.realityBefore, limit = limit))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return null
        }
        if (admission() != grant ||
            receipts.canonicalSource != "reality.execution_receipt" ||
            receipts.receipts.size > limit) return null

        val messages = try {
            workspace.read(WorkspaceHistoryQuery(before = before?.workspaceBefore, limit = limit))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return null
        }
        if (admission() != grant ||
            messages.canonicalSource != "workspace.message" ||
            !messages.hostAdmissionRequired || messages.events.size > limit ||
            (before != null && messages.ownerRevision != before.workspaceRevision)
        ) return null

        val all = (
            receipts.receipts.map {
                FaroV5ActivityItem(
                    FaroV5ActivityDomain.REALITY_RECEIPT,
                    "APPLY_COMMITTED",
                    it.receipt.transactionId.value,
                    it.recordedAtEpochMillis,
                    "reality.receipt.commit_recorded_at_epoch_millis",
                )
            } + messages.events.map {
                FaroV5ActivityItem(
                    FaroV5ActivityDomain.WORKSPACE_MESSAGE,
                    it.kind.name,
                    it.messageId.value,
                    it.createdAtEpochMillis,
                    it.timeProvenance,
                )
            }
        ).sortedWith(compareByDescending<FaroV5ActivityItem> { it.timestampEpochMillis }
            .thenBy { it.domain.ordinal }
            .thenBy { it.opaqueId })

        val page = all.take(limit)
        val hasMore = all.size > limit || receipts.nextCursor != null || !messages.complete
        val lastReceipt = page.lastOrNull { it.domain == FaroV5ActivityDomain.REALITY_RECEIPT }
        val lastMessage = page.lastOrNull { it.domain == FaroV5ActivityDomain.WORKSPACE_MESSAGE }
        val next = if (!hasMore) null else FaroV5FederatedHistoryCursor(
            generationId = expectedGenerationId,
            pointerEpoch = grant.pointer,
            realityAclEpoch = grant.reality,
            workspaceAclEpoch = grant.workspace,
            workspaceRevision = messages.ownerRevision,
            realityBefore = if (lastReceipt == null) before?.realityBefore else
                RealityTimedReceiptCursor(lastReceipt.timestampEpochMillis, lastReceipt.opaqueId),
            workspaceBefore = if (lastMessage == null) before?.workspaceBefore else
                WorkspaceHistoryCursor(
                    lastMessage.timestampEpochMillis,
                    ConversationMessageId(lastMessage.opaqueId),
                    messages.ownerRevision,
                ),
        )
        return if (admission() == grant) FaroV5FederatedHistoryPage(
            items = page,
            nextCursor = next,
            completeWithinTwoSources = !hasMore,
            legacyUndatedReality = receipts.legacyUndated,
        ) else null
    }
}
