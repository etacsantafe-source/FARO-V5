package ar.com.faro.v5.kernel.contracts

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ModelProfileContractTest {
    @Test
    fun `profile version must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelProfileVersion(0)
        }
    }

    @Test
    fun `profile requires non-empty unique binding ids`() {
        val binding = ModelProfileBinding(
            bindingId = ModelBindingId("binding-a"),
            target = DataEgressTarget.REMOTE_MODEL,
        )

        assertThrows(IllegalArgumentException::class.java) {
            ModelProfile(
                ref = ModelProfileRef(
                    profileId = ModelProfileId("chat"),
                    version = ModelProfileVersion(1),
                ),
                orderedBindings = emptyList(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelProfile(
                ref = ModelProfileRef(
                    profileId = ModelProfileId("chat"),
                    version = ModelProfileVersion(1),
                ),
                orderedBindings = listOf(binding, binding),
            )
        }
    }
}
