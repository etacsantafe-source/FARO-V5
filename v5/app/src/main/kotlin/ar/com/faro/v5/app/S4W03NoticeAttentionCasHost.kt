package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.auxiliary.S4W03NoticeAttentionCasResult

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
    ): S4W03NoticeAttentionCasResult = gate.withMaterialWrite {
        if (!current() || expectedRevision <= 0L ||
            expectedRevision == Long.MAX_VALUE ||
            (target != NoticeAttention.SEEN && target != NoticeAttention.DISMISSED)
        ) return@withMaterialWrite S4W03NoticeAttentionCasResult.Denied

        val objectId = noticeId.value
        val readScope = Planning8ObjectScope(OperationId("notice.read"), objectId)
        val operation = if (target == NoticeAttention.SEEN) "notice.seen.set" else "notice.dismiss"
        val mutationScope = Planning8ObjectScope(OperationId(operation), objectId)
        val readNonce = grants.grantAfterTrustedPolicyCheck(readScope)
            ?: return@withMaterialWrite S4W03NoticeAttentionCasResult.Denied
        val mutationNonce = grants.grantAfterTrustedPolicyCheck(mutationScope)
            ?: run {
                grants.revokeAll()
                return@withMaterialWrite S4W03NoticeAttentionCasResult.Denied
            }
        try {
            // This callback must be pure policy/pointer logic, never Reality SQL.
            val admission = {
                current() &&
                    grants.epochOrNull(readScope, pinned) == readNonce &&
                    grants.epochOrNull(mutationScope, pinned) == mutationNonce
            }
            if (!admission()) return@withMaterialWrite S4W03NoticeAttentionCasResult.Denied
            auxiliary.mutateReminderNoticeAttention(
                noticeId, expectedRevision, target, admission,
            )
        } finally {
            grants.revokeAll()
        }
    }

    override fun close() {
        closed = true
        grants.close()
    }
}
