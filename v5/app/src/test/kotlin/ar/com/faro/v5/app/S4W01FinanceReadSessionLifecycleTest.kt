package ar.com.faro.v5.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Pure-JVM adversarial contract, not Android lifecycle instrumentation. */
class S4W01FinanceReadSessionLifecycleTest {
    @Test fun r01StopClosesAndCallsTerminalOnce() {
        val calls = AtomicInteger()
        val gate = S4W01FinanceReadSessionLifecycle { calls.incrementAndGet() }
        assertTrue(gate.isOpen())
        gate.onStop()
        assertFalse(gate.isOpen())
        assertEquals(1, calls.get())
    }

    @Test fun r02DestroyClosesAndCallsTerminalOnce() {
        val calls = AtomicInteger()
        val gate = S4W01FinanceReadSessionLifecycle { calls.incrementAndGet() }
        gate.onDestroy()
        assertFalse(gate.isOpen())
        assertEquals(1, calls.get())
    }

    @Test fun r03ExplicitCloseDoesNotCallTerminator() {
        val calls = AtomicInteger()
        val gate = S4W01FinanceReadSessionLifecycle { calls.incrementAndGet() }
        gate.close()
        assertFalse(gate.isOpen())
        assertEquals(0, calls.get())
    }

    @Test fun r04StopDestroyAndRepeatCannotDuplicateCallback() {
        val calls = AtomicInteger()
        val gate = S4W01FinanceReadSessionLifecycle { calls.incrementAndGet() }
        gate.onStop()
        gate.onDestroy()
        gate.onStop()
        assertEquals(1, calls.get())
        assertFalse(gate.isOpen())
    }

    @Test fun r05ResumeDoesNotReviveTerminatedSession() {
        val calls = AtomicInteger()
        val gate = S4W01FinanceReadSessionLifecycle { calls.incrementAndGet() }
        gate.onStop()
        assertFalse(gate.onResume())
        assertFalse(gate.isOpen())
        assertEquals(1, calls.get())
    }

    @Test fun r06ConcurrentStopDestroyOnlyOneTerminalCallback() {
        val calls = AtomicInteger()
        val gate = S4W01FinanceReadSessionLifecycle { calls.incrementAndGet() }
        val start = CountDownLatch(1)
        val workers = (0 until 16).map { index ->
            Thread {
                start.await()
                if (index % 2 == 0) gate.onStop() else gate.onDestroy()
            }.apply { start() }
        }
        start.countDown()
        workers.forEach { thread ->
            thread.join(3000L)
            assertFalse(thread.isAlive, "lifecycle worker blocked")
        }
        assertEquals(1, calls.get())
        assertFalse(gate.isOpen())
    }

    @Test fun r07TerminatorExceptionStillPoisoned() {
        val gate = S4W01FinanceReadSessionLifecycle {
            throw IllegalStateException("synthetic cancellation failure")
        }
        assertThrows(IllegalStateException::class.java) { gate.onStop() }
        assertFalse(gate.isOpen())
        assertFalse(gate.onResume())
        gate.onDestroy() // no retry/callback after an exception
    }

    @Test fun r08ClosedBeforeLateStopCannotNotifyOrReopen() {
        val calls = AtomicInteger()
        val gate = S4W01FinanceReadSessionLifecycle { calls.incrementAndGet() }
        gate.close()
        gate.onStop()
        gate.onDestroy()
        assertFalse(gate.onResume())
        assertEquals(0, calls.get())
    }
}
