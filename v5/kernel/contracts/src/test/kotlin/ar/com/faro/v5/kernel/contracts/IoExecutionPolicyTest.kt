package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class IoExecutionPolicyTest {
    @Test
    fun timeoutsMustBePositiveAndCoveredByTotal() {
        assertThrows(IllegalArgumentException::class.java) {
            IoExecutionPolicy(0, 1, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IoExecutionPolicy(10, 20, 15)
        }
    }

    @Test
    fun neverCancelledIsStableDefault() {
        assertFalse(NeverCancelled.isCancelled())
    }
}
