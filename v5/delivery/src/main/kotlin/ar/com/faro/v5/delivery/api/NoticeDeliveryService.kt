package ar.com.faro.v5.delivery.api

import ar.com.faro.v5.delivery.spi.DeliveryPersistencePort
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.kernel.contracts.OperationExecutionPort
import java.util.UUID

class NoticeReader(
    private val persistence: DeliveryPersistencePort,
) : OperationExecutionPort {
    override val descriptor = DeliveryOperations.READ_DESCRIPTOR

    fun read(): List<NoticeSnapshot> = persistence.readNotices()
}

class NoticeResolver(
    private val persistence: DeliveryPersistencePort,
) : OperationExecutionPort {
    override val descriptor = DeliveryOperations.RESOLVE_DESCRIPTOR

    fun resolve(
        noticeId: NoticeId,
        reason: NoticeResolutionReason,
    ): NoticeResolveResult = persistence.resolve(noticeId, reason)
}

class NoticeRouter(
    private val persistence: DeliveryPersistencePort,
    private val idFactory: () -> NoticeMaterializationIds = {
        NoticeMaterializationIds(
            noticeId = NoticeId.of("notice-" + UUID.randomUUID()),
            deliveryIntentId = DeliveryIntentId.of("delivery-" + UUID.randomUUID()),
            effectId = EffectId("effect-" + UUID.randomUUID()),
        )
    },
) : OperationExecutionPort {
    override val descriptor = DeliveryOperations.ROUTE_DESCRIPTOR

    fun route(candidate: NoticeCandidate): NoticeRouteResult {
        if (candidate.materiality == NoticeMateriality.SILENT) {
            return NoticeRouteResult.SuppressedSilent
        }
        return persistence.route(candidate, idFactory())
    }
}
