package ar.com.faro.v5.app

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Pure JVM state tests, NOT two Android PIDs or ContentResolver instrumentation. */
class S4W01FinanceReadPrivateInvalidationTest {
    @Test fun i01BeforeRegistrationNeverInvalidates() {
        var calls = 0
        val gate = S4W01FinanceReadPrivateInvalidationGate { calls++ }
        assertFalse(gate.isOpen())
        assertFalse(gate.onSignal())
        assertEquals(0, calls)
    }

    @Test fun i02RegistrationClearsBeforeAnyRead() {
        var calls = 0
        val gate = S4W01FinanceReadPrivateInvalidationGate { calls++ }
        assertTrue(gate.onRegistered())
        assertTrue(gate.isOpen())
        assertEquals(1, calls)
    }

    @Test fun i03NoticeTriggersAnotherClearNotPermission() {
        var calls = 0
        val gate = S4W01FinanceReadPrivateInvalidationGate { calls++ }
        assertTrue(gate.onRegistered())
        assertTrue(gate.onSignal())
        assertEquals(2, calls)
    }

    @Test fun i04CloseRejectsLateCallback() {
        var calls = 0
        val gate = S4W01FinanceReadPrivateInvalidationGate { calls++ }
        assertTrue(gate.onRegistered())
        gate.close()
        assertFalse(gate.onSignal())
        assertFalse(gate.isOpen())
        assertEquals(1, calls)
    }

    @Test fun i05FailingRegistrationClearPermanentlyPoisons() {
        var calls = 0
        val gate = S4W01FinanceReadPrivateInvalidationGate {
            calls++
            throw IllegalStateException("synthetic failed clear")
        }
        assertFalse(gate.onRegistered())
        assertFalse(gate.onRegistered())
        assertFalse(gate.isOpen())
        assertEquals(1, calls)
    }

    @Test fun i06FailedLaterNoticePermanentlyPoisons() {
        var calls = 0
        val gate = S4W01FinanceReadPrivateInvalidationGate {
            calls++
            if (calls == 2) throw IllegalStateException("synthetic failure")
        }
        assertTrue(gate.onRegistered())
        assertFalse(gate.onSignal())
        assertFalse(gate.onRegistered())
        assertEquals(2, calls)
    }

    @Test fun i07DoubleRegistrationDoesNotCreateNewSession() {
        var calls = 0
        val gate = S4W01FinanceReadPrivateInvalidationGate { calls++ }
        assertTrue(gate.onRegistered())
        assertFalse(gate.onRegistered())
        assertEquals(1, calls)
    }

    @Test fun i08CloseIdempotentAndCannotResurrect() {
        var calls = 0
        val gate = S4W01FinanceReadPrivateInvalidationGate { calls++ }
        assertTrue(gate.onRegistered())
        gate.close()
        gate.close()
        assertFalse(gate.onRegistered())
        assertFalse(gate.onSignal())
        assertFalse(gate.isOpen())
        assertEquals(1, calls)
    }
}
