package ar.com.faro.v5.app

import ar.com.faro.v5.control.AiControlPreferenceStore
import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.control.spi.SecretStore
import ar.com.faro.v5.control.spi.SecretValue
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** F1 consent is a real local Keystore/reference transaction, never a chat grant. */
class FaroV5TrustedAssistantControlOwnerTest {
    @Test
    fun `explicit consent saves opaque key and revocation removes it`() = runBlocking {
        withOwner { owner, secrets ->
            assertFalse(owner.read().remoteReasoningEnabled)
            val key = "gsk_local_test_123456".encodeToByteArray()
            val saved = assertInstanceOf(
                FaroV5TrustedAssistantControlOwner.Change.Saved::class.java,
                owner.enableWithCredential(key, expectedRevision = 0),
            )
            assertTrue(saved.snapshot.remoteReasoningEnabled)
            assertEquals(1L, saved.snapshot.revision)
            val id = requireNotNull(saved.snapshot.credentialRefFor(
                FaroV5TrustedModelGatewayFactory.BINDING_ID
            ))
            assertArrayEquals(key, secrets.read(id).valueOrNull()!!.copyBytes())
            val disabled = assertInstanceOf(
                FaroV5TrustedAssistantControlOwner.Change.Saved::class.java,
                owner.disable(expectedRevision = 1),
            )
            assertFalse(disabled.snapshot.remoteReasoningEnabled)
            assertTrue(disabled.snapshot.credentialRefs.isEmpty())
            assertEquals(0, secrets.size())
        }
    }

    @Test
    fun `stale consent CAS never replaces committed secret`() = runBlocking {
        withOwner { owner, secrets ->
            val first = assertInstanceOf(
                FaroV5TrustedAssistantControlOwner.Change.Saved::class.java,
                owner.enableWithCredential("gsk_first_test_123".encodeToByteArray(), 0),
            )
            val previous = first.snapshot.credentialRefFor(
                FaroV5TrustedModelGatewayFactory.BINDING_ID
            )
            assertEquals(
                FaroV5TrustedAssistantControlOwner.Change.Stale,
                owner.enableWithCredential("gsk_second_test_123".encodeToByteArray(), 0),
            )
            assertEquals(previous, owner.read().credentialRefFor(
                FaroV5TrustedModelGatewayFactory.BINDING_ID
            ))
            assertEquals(1, secrets.size())
        }
    }

    @Test
    fun `invalid key does not create secret or consent`() = runBlocking {
        withOwner { owner, secrets ->
            assertEquals(
                FaroV5TrustedAssistantControlOwner.Change.InvalidCredential,
                owner.enableWithCredential("bad".encodeToByteArray(), 0),
            )
            assertFalse(owner.read().remoteReasoningEnabled)
            assertEquals(0, secrets.size())
        }
    }

    private suspend fun withOwner(
        block: suspend (FaroV5TrustedAssistantControlOwner, FakeSecrets) -> Unit,
    ) {
        val file = Files.createTempDirectory("faro-f1-control")
            .resolve("ai.preferences_pb").toFile()
        AiControlPreferenceStore(
            file, FaroV5TrustedModelGatewayFactory.DEFAULT_PROFILE
        ).use { store ->
            val secrets = FakeSecrets()
            block(FaroV5TrustedAssistantControlOwner(store, secrets), secrets)
        }
    }

    private class FakeSecrets : SecretStore {
        private val keys = mutableMapOf<SecretId, SecretValue>()
        fun size() = keys.size

        override fun read(id: SecretId): SecretStore.ReadResult =
            keys[id]?.let(SecretStore.ReadResult::available)
                ?: SecretStore.ReadResult.missing()

        override fun put(id: SecretId, value: SecretValue): SecretStore.MutationResult {
            keys[id] = value
            return SecretStore.MutationResult.stored()
        }

        override fun delete(id: SecretId): SecretStore.MutationResult =
            if (keys.remove(id) != null) SecretStore.MutationResult.deleted()
            else SecretStore.MutationResult.missing()
    }
}
