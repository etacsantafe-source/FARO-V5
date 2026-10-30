package ar.com.faro.v5.delivery.spi

import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeMaterializationIds
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeResolutionReason
import ar.com.faro.v5.delivery.api.NoticeResolveResult
import ar.com.faro.v5.delivery.api.NoticeRouteResult
import ar.com.faro.v5.delivery.api.NoticeSnapshot

interface DeliveryPersistencePort {
    fun readNotices(): List<NoticeSnapshot>

    /**
     * Atomically materializes/coalesces one logical Notice and its desired delivery intent.
     *
     * Implementations must dedupe by semantic occurrence identity and must never rewrite
     * source Reality/Evidence/Watch state.
     */
    fun route(
        candidate: NoticeCandidate,
        newIds: NoticeMaterializationIds,
    ): NoticeRouteResult

    /**
     * Resolves one durable Notice from source reconciliation without mutating source Truth.
     *
     * Implementations must preserve attention history and converge the desired sink toward CANCEL.
     */
    fun resolve(
        noticeId: NoticeId,
        reason: NoticeResolutionReason,
    ): NoticeResolveResult
}
