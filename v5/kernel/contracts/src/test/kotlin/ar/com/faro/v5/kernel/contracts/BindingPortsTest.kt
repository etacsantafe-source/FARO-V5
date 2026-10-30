package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BindingPortsTest {
    @Test
    fun `operation binding port exposes canonical metadata and executable contract`() {
        val port = object : OperationBindingPort {
            override val descriptor = OperationBindingDescriptor(
                operationId = OperationId("finance.snapshot.read"),
                canonicalExecutor = ExecutorId("finance"),
                executionProfile = ExecutionProfile.READ_ONLY,
            )

            override fun execute(
                preparedOperation: PreparedOperation,
                transactionScope: RealityTransactionScope,
            ): OperationBindingExecutionResult =
                OperationBindingExecutionResult.Completed
        }

        assertEquals(OperationId("finance.snapshot.read"), port.descriptor.operationId)
        assertEquals(ExecutorId("finance"), port.descriptor.canonicalExecutor)
        assertEquals(ExecutionProfile.READ_ONLY, port.descriptor.executionProfile)
    }

    @Test
    fun `provider binding port exposes provider neutral metadata only`() {
        val port = object : ProviderBindingPort {
            override val descriptor = ProviderBindingDescriptor(
                operationId = OperationId("finance.snapshot.read"),
                executionProfile = ExecutionProfile.READ_ONLY,
            )
        }

        assertEquals(OperationId("finance.snapshot.read"), port.descriptor.operationId)
        assertEquals(ExecutionProfile.READ_ONLY, port.descriptor.executionProfile)
    }

    @Test
    fun `active model capability snapshot must satisfy every requirement`() {
        val snapshot = ProviderCapabilitySnapshot(
            bindingId = ModelBindingId("binding-main"),
            lifecycle = ModelLifecycle.ACTIVE,
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.STRUCTURED_OUTPUT,
            ),
            contextLimitTokens = 32768,
        )

        assertTrue(
            snapshot.satisfies(
                ModelRequirements(
                    requiredCapabilities = setOf(
                        ModelCapability.TEXT,
                        ModelCapability.STRUCTURED_OUTPUT,
                    ),
                    minimumContextTokens = 16000,
                )
            )
        )
        assertFalse(
            snapshot.satisfies(
                ModelRequirements(
                    requiredCapabilities = setOf(ModelCapability.TOOL_CALLING),
                )
            )
        )
    }

    @Test
    fun `deprecated model binding never satisfies requirements`() {
        val snapshot = ProviderCapabilitySnapshot(
            bindingId = ModelBindingId("binding-old"),
            lifecycle = ModelLifecycle.DEPRECATED,
            capabilities = setOf(ModelCapability.TEXT),
            contextLimitTokens = 8192,
        )

        assertFalse(
            snapshot.satisfies(
                ModelRequirements(requiredCapabilities = setOf(ModelCapability.TEXT))
            )
        )
    }

    @Test
    fun `retry after must be positive when present`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiFailure(
                kind = AiFailureKind.RATE_LIMIT,
                retryDisposition = RetryDisposition.RETRYABLE,
                retryAfterMillis = 0,
            )
        }
    }
}
