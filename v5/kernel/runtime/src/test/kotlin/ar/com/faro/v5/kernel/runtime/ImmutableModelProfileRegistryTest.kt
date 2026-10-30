package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.DataEgressTarget
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelProfile
import ar.com.faro.v5.kernel.contracts.ModelProfileBinding
import ar.com.faro.v5.kernel.contracts.ModelProfileId
import ar.com.faro.v5.kernel.contracts.ModelProfileRef
import ar.com.faro.v5.kernel.contracts.ModelProfileVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ImmutableModelProfileRegistryTest {
    @Test
    fun `registry resolves only exact profile version`() {
        val v1 = profile(1, "remote-a")
        val v2 = profile(2, "remote-b")
        val registry = ImmutableModelProfileRegistry.of(listOf(v2, v1))

        assertEquals(v1, registry.resolve(v1.ref))
        assertEquals(v2, registry.resolve(v2.ref))
        assertNull(
            registry.resolve(
                ModelProfileRef(
                    profileId = ModelProfileId("conversation"),
                    version = ModelProfileVersion(3),
                )
            )
        )
        assertEquals(
            listOf(ModelProfileVersion(1), ModelProfileVersion(2)),
            registry.versions(ModelProfileId("conversation")),
        )
    }

    @Test
    fun `registry rejects duplicate exact profile refs`() {
        val first = profile(1, "remote-a")
        val conflicting = profile(1, "remote-b")

        assertThrows(IllegalArgumentException::class.java) {
            ImmutableModelProfileRegistry.of(listOf(first, conflicting))
        }
    }

    @Test
    fun `profile preserves explicit binding order and target`() {
        val profile = ModelProfile(
            ref = ModelProfileRef(
                profileId = ModelProfileId("conversation"),
                version = ModelProfileVersion(1),
            ),
            orderedBindings = listOf(
                ModelProfileBinding(
                    bindingId = ModelBindingId("preferred"),
                    target = DataEgressTarget.REMOTE_MODEL,
                ),
                ModelProfileBinding(
                    bindingId = ModelBindingId("local"),
                    target = DataEgressTarget.ON_DEVICE_MODEL,
                ),
            ),
        )

        assertEquals(
            listOf("preferred", "local"),
            profile.orderedBindings.map { it.bindingId.value },
        )
        assertEquals(
            DataEgressTarget.ON_DEVICE_MODEL,
            profile.orderedBindings.last().target,
        )
    }

    private fun profile(version: Int, binding: String): ModelProfile =
        ModelProfile(
            ref = ModelProfileRef(
                profileId = ModelProfileId("conversation"),
                version = ModelProfileVersion(version),
            ),
            orderedBindings = listOf(
                ModelProfileBinding(
                    bindingId = ModelBindingId(binding),
                    target = DataEgressTarget.REMOTE_MODEL,
                )
            ),
        )
}
