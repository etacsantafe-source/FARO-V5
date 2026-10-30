package ar.com.faro.v5.app

import android.content.Context
import ar.com.faro.v5.control.AiBindingCredentialRef
import ar.com.faro.v5.control.AiControlPreferenceStore
import ar.com.faro.v5.control.AiControlSnapshot
import ar.com.faro.v5.control.AiControlUpdateOutcome
import ar.com.faro.v5.control.AiControlUpdateRequest
import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.control.spi.SecretReader
import ar.com.faro.v5.control.spi.SecretStore
import ar.com.faro.v5.control.spi.SecretValue
import ar.com.faro.v5.platform.android.security.AndroidKeystoreSecretStore
import java.io.File
import java.util.UUID

/**
 * Single process-owned control authority for the F1 remote assistant.
 * Preferences contain ONLY consent/profile/opaque secret references.
 * Secret bytes are stored exclusively through the Android Keystore adapter.
 * Neither a chat message nor a provider reply can invoke this controller.
 */
class FaroV5TrustedAssistantControlOwner internal constructor(
    private val store: AiControlPreferenceStore,
    private val secrets: SecretStore,
) {
    sealed interface Change {
        data class Saved(val snapshot: AiControlSnapshot) : Change
        data object Stale : Change
        data object InvalidCredential : Change
        data object Unavailable : Change
    }

    suspend fun read(): AiControlSnapshot = store.read()

    fun secretReader(): SecretReader = secrets

    /**
     * Explicit user action only. Store a fresh, unique Keystore secret first;
     * CAS the consent reference second. On a stale/failing CAS delete ONLY the
     * newly created secret, never another revision's credential.
     */
    suspend fun enableWithCredential(
        credential: ByteArray,
        expectedRevision: Long,
    ): Change {
        if (credential.size !in 8..256 || credential.any {
                val b = it.toInt() and 0xff
                !(b in 48..57 || b in 65..90 || b in 97..122 ||
                    b == 45 || b == 46 || b == 95)
            }) return Change.InvalidCredential

        val id = SecretId("ai.groq." + UUID.randomUUID().toString().replace("-", ""))
        val stored = try {
            secrets.put(id, SecretValue.copyOf(credential))
        } catch (_: Exception) { return Change.Unavailable }
        if (stored.status() != SecretStore.MutationStatus.STORED)
            return Change.Unavailable

        try {
            val before = store.read()
            if (before.revision != expectedRevision) {
                secrets.delete(id)
                return Change.Stale
            }
            val result = store.update(
                AiControlUpdateRequest(
                    remoteReasoningEnabled = true,
                    activeProfile = FaroV5TrustedModelGatewayFactory.DEFAULT_PROFILE,
                    budget = before.budget,
                    credentialRefs = listOf(
                        AiBindingCredentialRef(
                            FaroV5TrustedModelGatewayFactory.BINDING_ID, id,
                        )
                    ),
                    expectedRevision = expectedRevision,
                )
            )
            return when (result) {
                is AiControlUpdateOutcome.Committed -> {
                    // Deleting a superseded secret cannot revoke the newly
                    // committed credential because its ID is unique.
                    before.credentialRefFor(
                        FaroV5TrustedModelGatewayFactory.BINDING_ID
                    )?.takeIf { it != id }?.let { old ->
                        try { secrets.delete(old) } catch (_: Exception) { }
                    }
                    Change.Saved(result.snapshot)
                }
                is AiControlUpdateOutcome.RejectedStale -> {
                    secrets.delete(id)
                    Change.Stale
                }
            }
        } catch (_: Exception) {
            try { secrets.delete(id) } catch (_: Exception) { }
            return Change.Unavailable
        }
    }

    /** Consent revocation is persisted before attempting secret deletion. */
    suspend fun disable(expectedRevision: Long): Change {
        return try {
            val before = store.read()
            if (before.revision != expectedRevision) return Change.Stale
            when (val result = store.update(
                AiControlUpdateRequest(
                    remoteReasoningEnabled = false,
                    activeProfile = before.activeProfile,
                    budget = before.budget,
                    credentialRefs = emptyList(),
                    expectedRevision = expectedRevision,
                )
            )) {
                is AiControlUpdateOutcome.RejectedStale -> Change.Stale
                is AiControlUpdateOutcome.Committed -> {
                    before.credentialRefs.forEach { ref ->
                        try { secrets.delete(ref.secretId) } catch (_: Exception) { }
                    }
                    Change.Saved(result.snapshot)
                }
            }
        } catch (_: Exception) { Change.Unavailable }
    }

    companion object {
        @Volatile private var instance: FaroV5TrustedAssistantControlOwner? = null

        @JvmStatic
        fun get(context: Context): FaroV5TrustedAssistantControlOwner {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: FaroV5TrustedAssistantControlOwner(
                    AiControlPreferenceStore(
                        File(context.applicationContext.noBackupFilesDir,
                            FaroV5TrustedModelGatewayFactory.CONTROL_FILENAME),
                        FaroV5TrustedModelGatewayFactory.DEFAULT_PROFILE,
                    ),
                    AndroidKeystoreSecretStore(context.applicationContext),
                ).also { instance = it }
            }
        }
    }
}
