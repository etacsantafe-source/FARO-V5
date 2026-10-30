package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OperationBindingDescriptorTest {
    @Test
    fun `binding metadata keeps operation executor and profile explicit`() {
        val binding = OperationBindingDescriptor(
            operationId = OperationId("finance.snapshot.read"),
            canonicalExecutor = ExecutorId("finance"),
            executionProfile = ExecutionProfile.READ_ONLY,
        )

        assertEquals(OperationId("finance.snapshot.read"), binding.operationId)
        assertEquals(ExecutorId("finance"), binding.canonicalExecutor)
        assertEquals(ExecutionProfile.READ_ONLY, binding.executionProfile)
    }
}
