package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.NoticeRelevance
import ar.com.faro.v5.delivery.api.NoticeSnapshot
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.owner.reminders.api.ReminderOccurrenceStatus
import ar.com.faro.v5.owner.reminders.api.ReminderSnapshot
import ar.com.faro.v5.store.auxiliary.AuxiliaryDeliveryStore
import ar.com.faro.v5.store.reality.RealityStore

/** Read-only status of a bounded, explicit DEV source-to-Auxiliary reconciliation. */
internal sealed interface S4W03NoticeReconcileResult {
    data object Denied : S4W03NoticeReconcileResult

    data class Saved(
        val created: Int,
        val resolved: Int,
        val linkedSourcesOmitted: Int,
        val sourceRevision: Long,
        val complete: Boolean,
    ) : S4W03NoticeReconcileResult

    data class Partial(
        val created: Int,
        val resolved: Int,
        val reason: String,
    ) : S4W03NoticeReconcileResult
}

/**
 * W03-D2A: explicit LOCAL_ONLY Reminder -> Auxiliary Notice reconciliation.
 *
 * NOT installed in shipping Activity or background scheduler. Neither a
 * Workspace review nor a pointer alone grants owner access. The trusted host
 * must supply independent, revocable source-read and Auxiliary-route policy.
 *
 * Reality and Auxiliary cannot share a SQL transaction. Under ONE material
 * gate, re-read the source before/after each short Auxiliary transaction and
 * fence the full generation/policy epoch within Auxiliary before COMMIT.
 * Never acquire Reality/Workspace locks inside Auxiliary's admission callback.
 * Cross-process atomicity is NOT claimed.
 */
internal class S4W03ReminderNoticeReconcileHost(
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val reality: RealityStore,
    private val auxiliary: AuxiliaryDeliveryStore,
    private val gate: MaterialWriteGate,
    principal: String,
    private val expectedEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val ownerReady: () -> Boolean,
    private val trustedForeground: () -> Boolean,
    policy: Planning8TrustedObjectPolicyPort,
) : AutoCloseable {
    @Volatile private var closed = false
    private val grants = FaroV5Planning8ObjectGrantOwner(
        principal, pinned, pointer, hostEpochOrNull, expectedEpoch, policy,
    )

    private val sourceScope = Planning8ObjectScope(
        OperationId("reminder.read"), "reminders",
    )
    private val localNoticeScope = Planning8ObjectScope(
        OperationId("notice.route"), "reminder-local",
    )
    private fun matterScope(id: String) =
        Planning8ObjectScope(OperationId("matter.read"), id)

    private fun current(): Boolean = runCatching {
        !closed && ownerReady() && trustedForeground() &&
            pointer.read() == pinned && hostEpochOrNull() == expectedEpoch
    }.getOrDefault(false)

    private fun sourceSnapshotOrNull(): ReminderSnapshot? = runCatching {
        if (!current()) null
        else reality.planningDueReadOwnersForTrustedHost().reminders.read()
    }.getOrNull()

    fun reconcile(
        fromEpochMillis: Long,
        untilEpochMillis: Long,
        maxCandidates: Int = 64,
    ): S4W03NoticeReconcileResult = gate.withMaterialWrite {
        if (!current() || fromEpochMillis < 0L ||
            untilEpochMillis <= fromEpochMillis || maxCandidates !in 1..128
        ) return@withMaterialWrite S4W03NoticeReconcileResult.Denied

        val sourceNonce = grants.grantAfterTrustedPolicyCheck(sourceScope)
            ?: return@withMaterialWrite S4W03NoticeReconcileResult.Denied
        val noticeNonce = grants.grantAfterTrustedPolicyCheck(localNoticeScope)
            ?: run {
                grants.revokeAll()
                return@withMaterialWrite S4W03NoticeReconcileResult.Denied
            }
        try {
            val original = sourceSnapshotOrNull()
                ?: return@withMaterialWrite S4W03NoticeReconcileResult.Denied
            val linked = original.rules.mapNotNull { it.linkedMatterId }.distinct()
            val linkEpochs = linked.associateWith { id ->
                grants.grantAfterTrustedPolicyCheck(matterScope(id))
            }
            val omitted = linkEpochs.count { it.value == null }

            // Only session policy reads in this callback; never Reality or Workspace.
            val admitted: () -> Boolean = {
                current() &&
                    grants.epochOrNull(sourceScope, pinned) == sourceNonce &&
                    grants.epochOrNull(localNoticeScope, pinned) == noticeNonce &&
                    linkEpochs.all { (id, nonce) ->
                        nonce == null || grants.epochOrNull(matterScope(id), pinned) == nonce
                    }
            }
            if (!admitted()) return@withMaterialWrite S4W03NoticeReconcileResult.Denied
            val eligible = runCatching {
                S4W03ReminderLocalNoticeProjection.candidates(
                    original, fromEpochMillis, untilEpochMillis, maxCandidates,
                ) { id ->
                    val nonce = linkEpochs[id]
                    nonce != null && grants.epochOrNull(matterScope(id), pinned) == nonce
                }
            }.getOrNull() ?: return@withMaterialWrite S4W03NoticeReconcileResult.Denied

            var created = 0
            var resolved = 0
            for (candidate in eligible) {
                if (!admitted() || sourceSnapshotOrNull() != original) {
                    return@withMaterialWrite S4W03NoticeReconcileResult.Partial(
                        created, resolved, "source_or_grant_changed",
                    )
                }
                val outcome = runCatching {
                    auxiliary.routeReminderLocalOnly(candidate, admitted)
                }.getOrNull() ?: return@withMaterialWrite S4W03NoticeReconcileResult.Partial(
                    created, resolved, "auxiliary_route_denied_or_failed",
                )
                if (outcome.created) created++
            }

            // Only explicitly acknowledged/cancelled occurrences or disabled
            // source rules permit resolution. Window absence is NOT evidence.
            val stored = runCatching { auxiliary.readNotices() }.getOrNull()
                ?: return@withMaterialWrite S4W03NoticeReconcileResult.Partial(
                    created, resolved, "auxiliary_read_failed",
                )
            for (notice in stored) {
                if (notice.relevance != NoticeRelevance.OPEN ||
                    !notice.sourceRef.value.startsWith("reminder:") ||
                    notice.destinationRef.value != "faro:avisos.local"
                ) continue
                val id = notice.sourceRef.value.removePrefix("reminder:")
                val rule = original.rules.singleOrNull { it.id.value == id } ?: continue
                val link = rule.linkedMatterId
                if (link != null && linkEpochs[link] == null) continue
                val attendance = original.occurrences.singleOrNull {
                    it.id.value == notice.occurrenceRef.value && it.ruleId == rule.id
                }
                val resolvedBySource = !rule.enabled ||
                    attendance?.status == ReminderOccurrenceStatus.ACKNOWLEDGED ||
                    attendance?.status == ReminderOccurrenceStatus.CANCELLED
                if (!resolvedBySource) continue
                if (!admitted() || sourceSnapshotOrNull() != original)
                    return@withMaterialWrite S4W03NoticeReconcileResult.Partial(
                        created, resolved, "source_or_grant_changed",
                    )
                val outcome = runCatching {
                    auxiliary.resolveReminderLocalOnly(notice.noticeId, admitted)
                }.getOrNull() ?: return@withMaterialWrite S4W03NoticeReconcileResult.Partial(
                    created, resolved, "auxiliary_resolve_denied_or_failed",
                )
                if (outcome is ar.com.faro.v5.delivery.api.NoticeResolveResult.Resolved)
                    resolved++
            }
            if (!admitted() || sourceSnapshotOrNull() != original)
                return@withMaterialWrite S4W03NoticeReconcileResult.Partial(
                    created, resolved, "source_changed_after_auxiliary_commit",
                )
            S4W03NoticeReconcileResult.Saved(
                created, resolved, omitted, original.revision, complete = omitted == 0,
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
