package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class IdentifiersTest {
    @Test
    fun `opaque identities reject blank values`() {
        assertThrows(IllegalArgumentException::class.java) { CapabilityId(" ") }
        assertThrows(IllegalArgumentException::class.java) { OperationId("") }
        assertThrows(IllegalArgumentException::class.java) { ExecutorId("\t") }
        assertThrows(IllegalArgumentException::class.java) { ContractVersion("\n") }
        assertThrows(IllegalArgumentException::class.java) { ContractFingerprint(" ") }
        assertThrows(IllegalArgumentException::class.java) { TransactionId("\t") }
        assertThrows(IllegalArgumentException::class.java) { ReceiptId("\n") }
        assertThrows(IllegalArgumentException::class.java) { EffectId(" ") }
        assertThrows(IllegalArgumentException::class.java) { IdempotencyKey("") }
        assertThrows(IllegalArgumentException::class.java) { PlanFingerprint(" ") }
        assertThrows(IllegalArgumentException::class.java) { PlanNodeId("\t") }
    }
    @Test
    fun triggerIdRejectsBlank() {
        assertThrows(IllegalArgumentException::class.java) { TriggerId("   ") }
        assertEquals("watch:daily", TriggerId("watch:daily").value)
    }
}
