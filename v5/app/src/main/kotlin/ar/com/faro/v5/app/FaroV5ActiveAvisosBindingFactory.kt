package ar.com.faro.v5.app

import ar.com.faro.v5.delivery.api.NoticeAttention
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeRelevance
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSnapshot
import ar.com.faro.v5.surface.FaroV5AvisoImportance
import ar.com.faro.v5.surface.FaroV5AvisoItem
import ar.com.faro.v5.surface.FaroV5AvisosPage
import ar.com.faro.v5.surface.FaroV5AvisosReadPort
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlinx.coroutines.flow.Flow

/**
 * S3.A4: only real active AuxiliaryDeliveryStore notice snapshots.
 * No mark-seen/dismiss, no OS deliveries, no synthesized notice or timestamp.
 * SENSITIVE summary remains private until a separately approved auth surface.
 */
object FaroV5ActiveAvisosBindingFactory {
    @JvmStatic
    fun create(
        readCanonical: Supplier<List<NoticeSnapshot>>,
        expectedGeneration: String,
        activeGeneration: Supplier<String>,
        startupReady: BooleanSupplier,
        invalidations: Flow<Unit>,
    ): FaroV5AvisosReadPort = createPinned(
        readCanonical, expectedGeneration, activeGeneration, startupReady,
        BooleanSupplier { true }, invalidations,
    )

    /**
     * Active graph route: a generation ID alone is not an authority fence.
     * A->B->A can restore the same ID with a different durable pointer sequence.
     * Recheck the graph's pinned pointer before and after reading the owner.
     */
    @JvmStatic
    fun createPinned(
        readCanonical: Supplier<List<NoticeSnapshot>>,
        expectedGeneration: String,
        activeGeneration: Supplier<String>,
        startupReady: BooleanSupplier,
        pinnedPointerCurrent: BooleanSupplier,
        invalidations: Flow<Unit>,
    ): FaroV5AvisosReadPort {
        require(expectedGeneration.isNotBlank())

        fun admitted(): Boolean = runCatching {
            startupReady.asBoolean &&
                activeGeneration.get() == expectedGeneration &&
                pinnedPointerCurrent.asBoolean
        }.getOrDefault(false)

        return object : FaroV5AvisosReadPort {
            override val invalidations: Flow<Unit> = invalidations

            override suspend fun readNotices(): FaroV5AvisosPage? {
                if (!admitted()) return null
                val owner = try {
                    readCanonical.get()
                } catch (_: Exception) {
                    return null
                }
                if (!admitted()) return null
                val active = owner.asSequence()
                    .filter { it.relevance == NoticeRelevance.OPEN }
                    .filter { it.attention != NoticeAttention.DISMISSED }
                    .distinctBy { it.noticeId.value }
                    // Stable owner-key order; no reliable chronology in N032.
                    .sortedWith(compareBy<NoticeSnapshot> {
                        if (it.materiality == NoticeMateriality.NEEDS_ATTENTION) 0 else 1
                    }.thenBy { it.noticeKey.value }.thenBy { it.occurrenceRef.value })
                    .toList()
                val page = FaroV5AvisosPage(
                    active.take(100).map { notice ->
                        FaroV5AvisoItem(
                            opaqueId = notice.noticeId.value,
                            summary = if (notice.sensitivity == NoticeSensitivity.SENSITIVE)
                                "Aviso privado" else notice.summary.value,
                            importance = if (notice.materiality == NoticeMateriality.NEEDS_ATTENTION)
                                FaroV5AvisoImportance.REQUIERE_ATENCION
                            else FaroV5AvisoImportance.INFORMATIVO,
                        )
                    },
                    complete = active.size <= 100,
                )
                return if (admitted()) page else null
            }
        }
    }
}
