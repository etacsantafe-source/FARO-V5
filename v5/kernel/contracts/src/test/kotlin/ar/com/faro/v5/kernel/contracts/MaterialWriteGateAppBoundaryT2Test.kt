package ar.com.faro.v5.kernel.contracts

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * C110: exact app-boundary admission primitive used by the Java CompositionRoot
 * for onboarding, alongside Kotlin Delivery. Synthetic owner work: native T2
 * proves the common gate mechanics, NOT all five store mutation entry points.
 */
class MaterialWriteGateAppBoundaryT2Test {
    @Test fun javaSupplierCannotMutateWhenMaintenanceRecoveryHasClosedAdmission() {
        val gate = SingleProcessMaterialWriteGate()
        var mutations = 0
        val onboarding = Supplier { mutations++; mutations }
        gate.closeMaterialWriteAdmission()
        assertThrows(IllegalStateException::class.java) {
            gate.supplyMaterialWrite(onboarding)
        }
        assertEquals(0, mutations)
        gate.openMaterialWriteAdmission()
        assertEquals(1, gate.supplyMaterialWrite(onboarding))
    }

    @Test fun onboardingAndDeliveryAdmissionBothBlockDuringExclusiveMaintenanceLease() {
        val gate = SingleProcessMaterialWriteGate()
        val pool = Executors.newFixedThreadPool(3)
        val quiesced = CountDownLatch(1)
        val release = CountDownLatch(1)
        val onboardingEntered = CountDownLatch(1)
        val deliveryEntered = CountDownLatch(1)
        try {
            val maintenance = pool.submit {
                gate.withQuiescedMaterialWrites {
                    quiesced.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
            }
            assertTrue(quiesced.await(5, TimeUnit.SECONDS))
            val onboarding = pool.submit(java.util.concurrent.Callable<String> {
                gate.supplyMaterialWrite(Supplier { onboardingEntered.countDown(); "ok" })
            })
            val delivery = pool.submit(java.util.concurrent.Callable<String> {
                gate.withMaterialWrite { deliveryEntered.countDown(); "delivered" }
            })
            assertFalse(onboardingEntered.await(150, TimeUnit.MILLISECONDS))
            assertFalse(deliveryEntered.await(150, TimeUnit.MILLISECONDS))
            release.countDown()
            maintenance.get(5, TimeUnit.SECONDS)
            assertEquals("ok", onboarding.get(5, TimeUnit.SECONDS))
            assertEquals("delivered", delivery.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test fun exceptionalUiMutationReleasesSharedAdmissionForNextWriter() {
        val gate = SingleProcessMaterialWriteGate()
        assertThrows(IllegalStateException::class.java) {
            gate.supplyMaterialWrite(Supplier<String> { error("synthetic onboarding failure") })
        }
        assertEquals("next", gate.supplyMaterialWrite(Supplier { "next" }))
        gate.withQuiescedMaterialWrites { assertTrue(gate.isMaterialWriteAdmissionOpen()) }
    }
}
