package ar.com.faro.v5.delivery

import ar.com.faro.v5.delivery.api.DeliveryIntentId
import ar.com.faro.v5.delivery.api.NotificationDeliveryReprojector
import ar.com.faro.v5.delivery.api.NotificationDeliveryScheduledWorkHandler
import ar.com.faro.v5.delivery.spi.DeliveryProjectionPersistencePort
import ar.com.faro.v5.delivery.spi.ScheduledDeliveryWorkProjectionPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NotificationDeliveryReprojectorTest {
    @Test
    fun reprojectsBoundedCandidatesUsingStableSchedulerIdentity() {
        val requestedLimits = mutableListOf<Int>()
        val persistence =
            DeliveryProjectionPersistencePort { limit ->
                requestedLimits += limit
                listOf(
                    DeliveryIntentId.of("delivery-a"),
                    DeliveryIntentId.of("delivery-b"),
                )
            }
        val requests = mutableListOf<Pair<String, String>>()
        val projection =
            ScheduledDeliveryWorkProjectionPort { workKind, semanticId ->
                requests += workKind to semanticId
            }

        val count =
            NotificationDeliveryReprojector(
                persistence = persistence,
                workProjection = projection,
            ).reproject(2)

        assertEquals(2, count)
        assertEquals(listOf(2), requestedLimits)
        assertEquals(
            listOf(
                NotificationDeliveryScheduledWorkHandler.WORK_KIND to "delivery-a",
                NotificationDeliveryScheduledWorkHandler.WORK_KIND to "delivery-b",
            ),
            requests,
        )
    }

    @Test
    fun rejectsUnboundedRequestsBeforeReadingPersistence() {
        var reads = 0
        val reprojector =
            NotificationDeliveryReprojector(
                persistence =
                    DeliveryProjectionPersistencePort {
                        reads += 1
                        emptyList()
                    },
                workProjection = ScheduledDeliveryWorkProjectionPort { _, _ -> },
            )

        assertThrows(IllegalArgumentException::class.java) {
            reprojector.reproject(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            reprojector.reproject(NotificationDeliveryReprojector.MAX_SCAN_LIMIT + 1)
        }
        assertEquals(0, reads)
    }

    @Test
    fun projectionFailurePropagatesWithoutChangingDurableCandidateSource() {
        val candidate = DeliveryIntentId.of("delivery-repeatable")
        var reads = 0
        val persistence =
            DeliveryProjectionPersistencePort {
                reads += 1
                listOf(candidate)
            }
        val projection =
            ScheduledDeliveryWorkProjectionPort { _, _ ->
                error("simulated scheduler submission failure")
            }
        val reprojector = NotificationDeliveryReprojector(persistence, projection)

        assertThrows(IllegalStateException::class.java) {
            reprojector.reproject()
        }
        assertThrows(IllegalStateException::class.java) {
            reprojector.reproject()
        }
        assertEquals(2, reads)
    }
}
