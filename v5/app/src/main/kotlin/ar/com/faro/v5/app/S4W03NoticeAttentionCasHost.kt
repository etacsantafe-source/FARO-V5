package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.auxiliary.S4W03NoticeAttentionCasResult
import ar.com.faro.v5.store.auxiliary.S4W03NoticeAttentionReceiptV4Result

/**
 * W03-D2B1 trusted DEV host only; one explicit foreground click.
 * Mutation is owned by Auxiliary, never Reality Reminder/Matter.
 * This does NOT install shipping UI, autonomous Notice actions or OS delivery.
 */
internal class S4W03NoticeAttentionCasHost(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val auxiliary: AuxiliaryDeliveryStore,
    private val gate: MaterialWriteGate,
    principal: String,
    private val expectedEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val ownerReady: () -> Boolean,
    private val foreground: () -> Boolean,
    policy: Planning8TrustedObjectPolicyPort,
) : AutoCloseable {
    @Volatile private var closed = false
    private val grants = FaroV5Planning8ObjectGrantOwner(
        principal, pinned, pointer, hostEpochOrNull, expectedEpoch, policy,
    )

    private fun current(): Boolean = runCatching {
        !closed && ownerReady() && foreground() &&
            pointer.read() == pinned && hostEpochOrNull() == expectedEpoch
    }.getOrDefault(false)

    /**
     * Caller MUST provide the previously read notice revision from an
     * independent permission-checked Notice read surface. The fresh policy
     * for notice.read and THIS exact mutation/NoticeId is checked before
     * and during the same Auxiliary SQL commit.
     */
    fun explicitClick(
        noticeId: NoticeId,
        expectedRevision: Long,
        target: NoticeAttention,
    ): S4W03NoticeAttentionCasResult = withClick(
        noticeId, expectedRevision, target, S4W03NoticeAttentionCasResult.Denied,
    ) { admission ->
        auxiliary.mutateReminderNoticeAttention(noticeId, expectedRevision, target, admission)
    }

    fun explicitClickWithReceipt(
        requestKey: String,
        noticeId: NoticeId,
        expectedRevision: Long,
        target: NoticeAttention,
    ): S4W03NoticeAttentionReceiptV4Result {
        if (!requestKey.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:/-]{0,191}"))) {
            return S4W03NoticeAttentionReceiptV4Result.Denied
        }
        return withClick(
            noticeId, expectedRevision, target, S4W03NoticeAttentionReceiptV4Result.Denied,
        ) { admission ->
            auxiliary.mutateReminderNoticeAttentionWithReceipt(requestKey, noticeId, expectedRevision, target, admission)
        }
    }

    private fun <T> withClick(
        noticeId: NoticeId,
        expectedRevision: Long,
        target: NoticeAttention,
        denied: T,
        mutation: (() -> Boolean) -> T,
    ): T = gate.withMaterialWrite {
        if (!current() || noticeId.value.length > 128 || expectedRevision <= 0L ||
            expectedRevision == Long.MAX_VALUE ||
            (target != NoticeAttention.SEEN && target != NoticeAttention.DISMISSED)
        ) return@withMaterialWrite denied

        val objectId = noticeId.value
        val readScope = Planning8ObjectScope(OperationId("notice.read"), objectId)
        val operation = if (target == NoticeAttention.SEEN) "notice.seen.set" else "notice.dismiss"
        val mutationScope = Planning8ObjectScope(OperationId(operation), objectId)
        val readNonce = grants.grantAfterTrustedPolicyCheck(readScope)
            ?: return@withMaterialWrite denied
        val mutationNonce = grants.grantAfterTrustedPolicyCheck(mutationScope)
            ?: run {
                grants.revokeAll()
                return@withMaterialWrite denied
            }
        try {
            // This callback must be pure policy/pointer logic, never Reality SQL.
            val admission = {
                current() &&
                    grants.epochOrNull(readScope, pinned) == readNonce &&
                    grants.epochOrNull(mutationScope, pinned) == mutationNonce
            }
            if (!admission()) return@withMaterialWrite denied
            mutation(admission)
        } finally {
            grants.revokeAll()
        }
    }

    override fun close() {
        closed = true
        grants.close()
    }
}
