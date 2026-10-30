package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ModelRoutePlanContractTest {
    @Test
    fun `egress rejection must identify blocked fragments`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelRouteRejection(
                bindingId = ModelBindingId("binding"),
                reason = ModelRouteRejectionReason.EGRESS_BLOCKED,
            )
        }
    }

    @Test
    fun `binding cannot be both eligible and rejected`() {
        val binding = ModelProfileBinding(
            bindingId = ModelBindingId("binding"),
            target = DataEgressTarget.REMOTE_MODEL,
        )

        assertThrows(IllegalArgumentException::class.java) {
            ModelRoutePlan(
                eligibleBindings = listOf(binding),
                rejections = listOf(
                    ModelRouteRejection(
                        bindingId = binding.bindingId,
                        reason = ModelRouteRejectionReason.CAPABILITY_MISMATCH,
                    )
                ),
            )
        }
    }
}
