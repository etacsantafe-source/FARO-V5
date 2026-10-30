package ar.com.faro.v5.control

import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.kernel.contracts.AiMonetaryBudget
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelProfileId
import ar.com.faro.v5.kernel.contracts.ModelProfileRef
import ar.com.faro.v5.kernel.contracts.ModelProfileVersion
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiControlPreferenceStoreTest {
    @Test
    fun `empty store reads safe defaults without secret material`() = runBlocking {
        withStore { store ->
            val snapshot = store.read()

            assertFalse(snapshot.remoteReasoningEnabled)
            assertEquals(profile(1), snapshot.activeProfile)
            assertEquals(AiMonetaryBudget.Disabled, snapshot.budget)
            assertEquals(emptyList<Any>(), snapshot.credentialRefs)
            assertEquals(0L, snapshot.revision)
        }
    }

    @Test
    fun `update persists only non-secret settings and opaque secret ids`() = runBlocking {
        val file = Files.createTempDirectory("faro-ai-control").resolve("ai.preferences_pb").toFile()
        val refs = listOf(
            AiBindingCredentialRef(
                bindingId = ModelBindingId("remote:weird\nidentifier"),
                secretId = SecretId("ai.remote.primary"),
            )
        )

        AiControlPreferenceStore(file, profile(1)).use { store ->
            val outcome = store.update(
                AiControlUpdateRequest(
                    remoteReasoningEnabled = true,
                    activeProfile = profile(2),
                    budget = AiMonetaryBudget.Capped(
                        currencyCode = "USD",
                        remainingMinor = 250,
                    ),
                    credentialRefs = refs,
                    expectedRevision = 0,
                )
            )

            val committed = assertInstanceOf(
                AiControlUpdateOutcome.Committed::class.java,
                outcome,
            )
            assertTrue(committed.changed)
            assertEquals(1L, committed.snapshot.revision)
        }

        AiControlPreferenceStore(file, profile(1)).use { reopened ->
            val persisted = reopened.read()
            assertTrue(persisted.remoteReasoningEnabled)
            assertEquals(profile(2), persisted.activeProfile)
            assertEquals(
                AiMonetaryBudget.Capped(
                    currencyCode = "USD",
                    remainingMinor = 250,
                ),
                persisted.budget,
            )
            assertEquals(refs, persisted.credentialRefs)
            assertEquals(1L, persisted.revision)
        }
    }

    @Test
    fun `stale revision is rejected without mutation`() = runBlocking {
        withStore { store ->
            store.update(
                AiControlUpdateRequest(
                    remoteReasoningEnabled = true,
                    activeProfile = profile(1),
                    budget = AiMonetaryBudget.Disabled,
                    credentialRefs = emptyList(),
                    expectedRevision = 0,
                )
            )

            val stale = store.update(
                AiControlUpdateRequest(
                    remoteReasoningEnabled = false,
                    activeProfile = profile(1),
                    budget = AiMonetaryBudget.Disabled,
                    credentialRefs = emptyList(),
                    expectedRevision = 0,
                )
            )

            assertInstanceOf(
                AiControlUpdateOutcome.RejectedStale::class.java,
                stale,
            )
            assertTrue(store.read().remoteReasoningEnabled)
            assertEquals(1L, store.read().revision)
        }
    }

    @Test
    fun `no-op update does not advance revision`() = runBlocking {
        withStore { store ->
            val first = store.update(
                AiControlUpdateRequest(
                    remoteReasoningEnabled = false,
                    activeProfile = profile(1),
                    budget = AiMonetaryBudget.Disabled,
                    credentialRefs = emptyList(),
                    expectedRevision = 0,
                )
            )

            val committed = assertInstanceOf(
                AiControlUpdateOutcome.Committed::class.java,
                first,
            )
            assertFalse(committed.changed)
            assertEquals(0L, committed.snapshot.revision)
        }
    }

    @Test
    fun `credential reference codec is deterministic for opaque binding ids`() {
        val original = AiBindingCredentialRef(
            bindingId = ModelBindingId("binding:with:anything"),
            secretId = SecretId("ai.remote.primary"),
        )

        val encoded = AiControlPreferenceStore.encodeCredentialRef(original)
        assertEquals(
            original,
            AiControlPreferenceStore.decodeCredentialRef(encoded),
        )
    }

    private suspend fun withStore(
        block: suspend (AiControlPreferenceStore) -> Unit,
    ) {
        val file = Files.createTempDirectory("faro-ai-control").resolve("ai.preferences_pb").toFile()
        AiControlPreferenceStore(file, profile(1)).use { block(it) }
    }

    private fun profile(version: Int): ModelProfileRef =
        ModelProfileRef(
            profileId = ModelProfileId("conversation"),
            version = ModelProfileVersion(version),
        )
}
