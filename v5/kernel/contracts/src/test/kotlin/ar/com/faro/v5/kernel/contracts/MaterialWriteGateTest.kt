package ar.com.faro.v5.kernel.contracts

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MaterialWriteGateTest {
    @Test
    fun `quiescence waits for admitted write and blocks following write`() {
        val gate = SingleProcessMaterialWriteGate()
        val executor = Executors.newFixedThreadPool(3)
        val firstWriteEntered = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val quiescenceEntered = CountDownLatch(1)
        val releaseQuiescence = CountDownLatch(1)
        val secondWriteEntered = CountDownLatch(1)

        try {
            val first =
                executor.submit {
                    gate.withMaterialWrite {
                        firstWriteEntered.countDown()
                        check(releaseFirstWrite.await(5, TimeUnit.SECONDS))
                    }
                }
            assertTrue(firstWriteEntered.await(5, TimeUnit.SECONDS))

            val maintenance =
                executor.submit {
                    gate.withQuiescedMaterialWrites {
                        quiescenceEntered.countDown()
                        check(releaseQuiescence.await(5, TimeUnit.SECONDS))
                    }
                }

            assertFalse(quiescenceEntered.await(100, TimeUnit.MILLISECONDS))
            releaseFirstWrite.countDown()
            assertTrue(quiescenceEntered.await(5, TimeUnit.SECONDS))
            first.get(5, TimeUnit.SECONDS)

            val second =
                executor.submit {
                    gate.withMaterialWrite {
                        secondWriteEntered.countDown()
                    }
                }
            assertFalse(secondWriteEntered.await(100, TimeUnit.MILLISECONDS))

            releaseQuiescence.countDown()
            maintenance.get(5, TimeUnit.SECONDS)
            assertTrue(secondWriteEntered.await(5, TimeUnit.SECONDS))
            second.get(5, TimeUnit.SECONDS)
        } finally {
            releaseFirstWrite.countDown()
            releaseQuiescence.countDown()
            executor.shutdownNow()
        }
    }
    @Test
    fun `closed admission stays fail closed after quiescence until explicitly reopened`() {
        val gate = SingleProcessMaterialWriteGate()

        gate.closeMaterialWriteAdmission()

        assertFalse(gate.isMaterialWriteAdmissionOpen())
        assertThrows(IllegalStateException::class.java) {
            gate.withMaterialWrite { error("closed admission must not execute material write") }
        }

        gate.withQuiescedMaterialWrites {
            assertFalse(gate.isMaterialWriteAdmissionOpen())
        }

        assertThrows(IllegalStateException::class.java) {
            gate.withMaterialWrite { error("quiescence release must not reopen material writes") }
        }

        gate.openMaterialWriteAdmission()

        assertTrue(gate.isMaterialWriteAdmissionOpen())
        var executed = false
        gate.withMaterialWrite {
            executed = true
        }
        assertTrue(executed)
    }

    @Test
    fun `closed admission allows only exclusive recovery lease owner`() {
        val gate = SingleProcessMaterialWriteGate()
        gate.closeMaterialWriteAdmission()
        var count = 0
        gate.withQuiescedMaterialWrites {
            gate.withMaterialWrite { count++ }
            assertFalse(gate.isMaterialWriteAdmissionOpen())
        }
        assertTrue(count == 1)
        assertThrows(IllegalStateException::class.java) {
            gate.withMaterialWrite { count++ }
        }
        assertTrue(count == 1)
        assertFalse(gate.isMaterialWriteAdmissionOpen())
    }

    @Test
    fun `outsider cannot mutate across closed maintenance lease`() {
        val gate = SingleProcessMaterialWriteGate()
        gate.closeMaterialWriteAdmission()
        val workers = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val attempted = CountDownLatch(1)
        val outsiderExecuted = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            val maintenance = workers.submit(java.util.concurrent.Callable<Unit> {
                gate.withQuiescedMaterialWrites {
                    gate.withMaterialWrite { entered.countDown() }
                    check(release.await(5, TimeUnit.SECONDS))
                }
            })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val outsider = workers.submit(java.util.concurrent.Callable<Boolean> {
                attempted.countDown()
                try {
                    gate.withMaterialWrite { outsiderExecuted.set(true) }
                    false
                } catch (_: IllegalStateException) {
                    true
                }
            })
            assertTrue(attempted.await(5, TimeUnit.SECONDS))
            assertFalse(outsiderExecuted.get())
            release.countDown()
            maintenance.get(5, TimeUnit.SECONDS)
            assertTrue(outsider.get(5, TimeUnit.SECONDS))
            assertFalse(outsiderExecuted.get())
            assertFalse(gate.isMaterialWriteAdmissionOpen())
        } finally {
            release.countDown()
            workers.shutdownNow()
        }
    }

}
