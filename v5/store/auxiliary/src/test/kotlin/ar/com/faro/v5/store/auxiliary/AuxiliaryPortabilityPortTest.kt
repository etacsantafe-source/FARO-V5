package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.DeliveryIntentId
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeMaterializationIds
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.kernel.contracts.EffectId
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AuxiliaryPortabilityPortTest {
    @Test
    fun `notice portability projection excludes delivery and routing internals`(
        @TempDir tempDir: Path,
    ) {
        AuxiliaryDeliveryStore(tempDir.resolve("aux.db").toString()).use { store ->
            store.route(
                candidate =
                    NoticeCandidate(
                        noticeKey = NoticeKey.of("price-drop"),
                        sourceRef = SourceRef.of("internal-source-watch"),
                        occurrenceRef = OccurrenceRef.of("occurrence-1"),
                        materiality = NoticeMateriality.NEEDS_ATTENTION,
                        sensitivity = NoticeSensitivity.SENSITIVE,
                        summary = NoticeSummary.of("A sensitive notice"),
                        destinationRef = DestinationRef.of("internal-destination"),
                    ),
                newIds =
                    NoticeMaterializationIds(
                        noticeId = NoticeId.of("internal-notice-id"),
                        deliveryIntentId = DeliveryIntentId.of("internal-delivery-intent"),
                        effectId = EffectId("internal-effect-id"),
                    ),
            )

            val snapshot = store.portability.snapshot()
            assertEquals(1, snapshot.notices.size)
            val notice = snapshot.notices.single()
            assertEquals("price-drop", notice.noticeKey)
            assertEquals("occurrence-1", notice.occurrenceRef)
            assertEquals("SENSITIVE", notice.sensitivity)
            assertEquals("A sensitive notice", notice.summary)
            assertTrue(snapshot.sourceWatermark.matches(Regex("[0-9a-f]{64}")))

            val rendered = snapshot.toString()
            assertFalse(rendered.contains("internal-source-watch"))
            assertFalse(rendered.contains("internal-destination"))
            assertFalse(rendered.contains("internal-notice-id"))
            assertFalse(rendered.contains("internal-delivery-intent"))
            assertFalse(rendered.contains("internal-effect-id"))
        }
    }
}
