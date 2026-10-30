package ar.com.faro.v5.store.auxiliary

import ar.com.faro.v5.delivery.api.DestinationRef
import ar.com.faro.v5.delivery.api.DeliveryIntentId
import ar.com.faro.v5.delivery.api.NoticeCandidate
import ar.com.faro.v5.delivery.api.NoticeId
import ar.com.faro.v5.delivery.api.NoticeKey
import ar.com.faro.v5.delivery.api.NoticeMateriality
import ar.com.faro.v5.delivery.api.NoticeMaterializationIds
import ar.com.faro.v5.delivery.api.NoticeRouteResult
import ar.com.faro.v5.delivery.api.NoticeRouter
import ar.com.faro.v5.delivery.api.NoticeSensitivity
import ar.com.faro.v5.delivery.api.NoticeSummary
import ar.com.faro.v5.delivery.api.OccurrenceRef
import ar.com.faro.v5.delivery.api.SourceRef
import ar.com.faro.v5.kernel.contracts.EffectId
import ar.com.faro.v5.kernel.contracts.SingleProcessMaterialWriteGate
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** C116 pilot task2: staged Auxiliary owner admission, not full five-store cutover. */
class AuxiliaryMaterialAdmissionT2Test {
    @TempDir lateinit var directory: Path
    private fun file() = directory.resolve("aux-gated.db").toString()

    private fun route(store: AuxiliaryDeliveryStore): NoticeRouteResult =
        NoticeRouter(store) {
            NoticeMaterializationIds(
                noticeId = NoticeId.of("notice-gated"),
                deliveryIntentId = DeliveryIntentId.of("delivery-gated"),
                effectId = EffectId("effect-gated"),
            )
        }.route(
            NoticeCandidate(
                noticeKey = NoticeKey.of("radar.material-gate"),
                sourceRef = SourceRef.of("radar:gate:1"),
                occurrenceRef = OccurrenceRef.of("occurrence:2026-10-29T10:00"),
                materiality = NoticeMateriality.NOTICE,
                sensitivity = NoticeSensitivity.NORMAL,
                summary = NoticeSummary.of("Prueba controlada."),
                destinationRef = DestinationRef.of("asuntos:gate"),
            )
        )

    @Test fun closedAdmissionRejectsMaterializationWithoutAuxiliaryRows() {
        val gate = SingleProcessMaterialWriteGate()
        AuxiliaryDeliveryStore(file(), materialWriteGate = gate).use { store ->
            gate.closeMaterialWriteAdmission()
            assertThrows(IllegalStateException::class.java) { route(store) }
            assertTrue(store.readNotices().isEmpty())
            gate.openMaterialWriteAdmission()
            assertTrue(route(store) is NoticeRouteResult.Materialized)
            assertEquals(1, store.readNotices().size)
        }
    }

    @Test fun exclusiveMaintenanceLeaseBlocksExternalAuxiliaryWriteUntilReleased() {
        val gate = SingleProcessMaterialWriteGate()
        AuxiliaryDeliveryStore(file(), materialWriteGate = gate).use { store ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val attempted = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val maintenance = pool.submit(Callable<String> {
                    gate.withQuiescedMaterialWrites {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        "released"
                    }
                })
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val pending = pool.submit(Callable<NoticeRouteResult> {
                    attempted.countDown()
                    route(store)
                })
                assertTrue(attempted.await(5, TimeUnit.SECONDS))
                assertThrows(TimeoutException::class.java) {
                    pending.get(150, TimeUnit.MILLISECONDS)
                }
                assertTrue(store.readNotices().isEmpty())
                release.countDown()
                assertEquals("released", maintenance.get(5, TimeUnit.SECONDS))
                assertTrue(pending.get(5, TimeUnit.SECONDS) is NoticeRouteResult.Materialized)
                assertEquals(1, store.readNotices().size)
            } finally {
                release.countDown()
                pool.shutdownNow()
            }
        }
    }

    @Test fun maintenanceThreadCanUseItsOwnExclusiveLeaseForAuxiliaryWrite() {
        val gate = SingleProcessMaterialWriteGate()
        AuxiliaryDeliveryStore(file(), materialWriteGate = gate).use { store ->
            val outcome = gate.withQuiescedMaterialWrites { route(store) }
            assertTrue(outcome is NoticeRouteResult.Materialized)
            assertEquals(1, store.readNotices().size)
        }
    }
}
