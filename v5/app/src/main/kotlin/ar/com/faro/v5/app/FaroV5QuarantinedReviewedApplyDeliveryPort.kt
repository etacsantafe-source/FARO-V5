package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeRouter
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewSnapshot
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewState
import ar.com.faro.v5.kernel.contracts.ConversationReviewPort
import ar.com.faro.v5.kernel.contracts.ExecutionReceipt
import ar.com.faro.v5.delivery.api.NoticeRouteResult
import ar.com.faro.v5.kernel.contracts.ConversationApplyHostOutcome
import ar.com.faro.v5.kernel.contracts.ConversationApplyRequestPort
import ar.com.faro.v5.kernel.contracts.ConversationPlanReviewContext
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.function.BooleanSupplier

/**
 * Dormant F1 certification bridge: a verified, human-requested Reality receipt
 * may project a generic Notice into the isolated Auxiliary owner.
 *
 * No OS notification is dispatched, no sensitive review text is exported,
 * and this class is not connected to the shipping V1/V2 CompositionRoot.
 * Apply is authoritative even if the post-commit Notice projection fails;
 * a failure must be observed and reconciled rather than replaying Apply.
 */
internal class FaroV5QuarantinedReviewedApplyDeliveryPort(
    private val upstream: ConversationApplyRequestPort,
    private val router: NoticeRouter,
    private val gate: MaterialWriteGate,
    private val admitted: BooleanSupplier,
    private val onProjectionFailure: (Exception) -> Unit,
) : ConversationApplyRequestPort {
    override suspend fun request(
        context: ConversationPlanReviewContext,
    ): ConversationApplyHostOutcome {
        if (!admitted.getAsBoolean()) {
            return ConversationApplyHostOutcome.Denied("quarantine_lost")
        }
        val outcome = upstream.request(context)
        if (outcome is ConversationApplyHostOutcome.Committed) {
            try {
                // The same gate fences maintenance/pointer switching and all
                // five-owner writes. Recheck stage admission UNDER this gate.
                gate.withMaterialWrite {
                    check(admitted.getAsBoolean()) { "quarantine_lost" }
                    FaroV5CommittedApplyNoticeProjection.project(
                        router, outcome.receipt, outcome.review,
                    )
                }
            } catch (failure: Exception) {
                // Never turn a committed Reality effect into a false Apply failure.
                runCatching { onProjectionFailure(failure) }
            }
        }
        return outcome
    }
}

/**
 * A committed Workspace review and its canonical Reality receipt are a
 * durable source for re-projecting a missed Auxiliary Notice after a crash.
 * No Apply is executed here. Projection is deterministic and idempotent.
 */
internal object FaroV5CommittedApplyNoticeProjection {
    fun project(
        router: NoticeRouter,
        receipt: ExecutionReceipt,
        review: ConversationPlanReviewSnapshot,
    ): NoticeRouteResult {
        check(review.state == ConversationPlanReviewState.COMMITTED &&
            review.receipt != null &&
            review.receipt.transactionId == receipt.transactionId &&
            review.planFingerprint == receipt.planFingerprint) {
            "Delivery projection requires a committed, matching Reality receipt"
        }
        val identity = UUID.nameUUIDFromBytes(
            ("f1-apply:" + receipt.transactionId.toString())
                .toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val destination = UUID.nameUUIDFromBytes(
            ("f1-chat:" + review.conversationId.toString())
                .toByteArray(StandardCharsets.UTF_8),
        ).toString()
        return router.route(
            NoticeCandidate(
                noticeKey = NoticeKey.of("f1-apply-$identity"),
                sourceRef = SourceRef.of("f1-reviewed-apply"),
                occurrenceRef = OccurrenceRef.of("committed"),
                materiality = NoticeMateriality.NOTICE,
                sensitivity = NoticeSensitivity.NORMAL,
                summary = NoticeSummary.of("Cambios aplicados y registrados"),
                destinationRef = DestinationRef.of("chat-$destination"),
            ),
        )
    }
}

/**
 * Recovery-only projection. Caller supplies the canonical staged Workspace
 * review port; never trust a UI/model-supplied receipt. Replaying the same
 * committed review creates no duplicate Notice and cannot replay Reality.
 */
internal class FaroV5QuarantinedCommittedDeliveryReconciler(
    private val reviews: ConversationReviewPort,
    private val router: NoticeRouter,
    private val gate: MaterialWriteGate,
    private val admitted: BooleanSupplier,
) {
    suspend fun reproject(conversationId: ConversationId): Int {
        check(admitted.getAsBoolean()) { "quarantine_lost" }
        val committed = reviews.reviews(conversationId)
            .filter { it.state == ConversationPlanReviewState.COMMITTED }
        return gate.withMaterialWrite {
            check(admitted.getAsBoolean()) { "quarantine_lost" }
            committed.count { review ->
                val receipt = checkNotNull(review.receipt) {
                    "committed Workspace review missing Reality receipt"
                }
                when (val result = FaroV5CommittedApplyNoticeProjection.project(
                    router, receipt, review,
                )) {
                    is NoticeRouteResult.Materialized -> result.created
                    else -> error("committed delivery projection was not materialized")
                }
            }
        }
    }
}
