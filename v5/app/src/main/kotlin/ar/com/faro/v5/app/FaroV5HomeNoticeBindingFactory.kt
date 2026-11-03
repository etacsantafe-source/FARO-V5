package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeRelevance
import ar.com.faro.v5.delivery.api.NoticeSnapshot
import ar.com.faro.v5.surface.FaroV5HomeAuthorityStamp
import ar.com.faro.v5.surface.FaroV5HomeCount
import ar.com.faro.v5.surface.FaroV5HomeIndicator
import ar.com.faro.v5.surface.FaroV5HomeIndicatorOwner
import ar.com.faro.v5.surface.FaroV5HomeOwnerBinding
import ar.com.faro.v5.surface.FaroV5HomeReadCoordinator
import ar.com.faro.v5.surface.FaroV5HomeReadPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.function.BooleanSupplier
import java.util.function.LongSupplier
import java.util.function.Supplier

/**
 * Read-only N-032 notice projection from the *active* AuxiliaryDeliveryStore.
 * Counts OPEN notices that have not been dismissed (SEEN remains visible).
 * Delivery to Android, notice relevance, and attention are distinct; reading
 * must not mutate any of them. No fabricated data for other Home indicators.
 */
internal fun faroV5ActiveNoticeCount(notices: List<NoticeSnapshot>): Int =
    notices.asSequence()
        .filter { it.relevance == NoticeRelevance.OPEN && it.attention != NoticeAttention.DISMISSED }
        .distinctBy { it.noticeId }
        .count()

object FaroV5HomeNoticeBindingFactory {
    /**
     * The composition root is the only trusted caller. No store or generation
     * is created here. The authority stamp is checked before AND after reading,
     * so recovery, revocation or active-generation changes fail closed.
     */
    @JvmStatic
    fun create(
        readNotices: Supplier<List<NoticeSnapshot>>,
        expectedGeneration: String,
        activeGeneration: Supplier<String>,
        startupReady: BooleanSupplier,
        clock: LongSupplier,
        invalidations: Flow<Unit> = emptyFlow(),
    ): FaroV5HomeReadPort {
        require(expectedGeneration.isNotBlank())
        return FaroV5HomeReadCoordinator(
            invalidations = invalidations,
            mapOf(
                FaroV5HomeIndicator.AVISOS to FaroV5HomeOwnerBinding(
                    expectedCanonicalSource = "notice.read",
                    readPort = FaroV5HomeIndicatorOwner {
                        FaroV5HomeCount(
                            count = faroV5ActiveNoticeCount(readNotices.get()),
                            asOfEpochMillis = clock.asLong,
                            canonicalSource = "notice.read",
                        )
                    },
                    authorityStamp = FaroV5HomeAuthorityStamp {
                        if (startupReady.asBoolean && activeGeneration.get() == expectedGeneration) {
                            expectedGeneration
                        } else {
                            null
                        }
                    },
                ),
            ),
        )
    }
}
