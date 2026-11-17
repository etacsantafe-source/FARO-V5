package ar.com.faro.v5.app

import android.os.Build
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Real OS-backed M1B adapter. DORMANT: no Activity, shipping host, Finance SQL,
 * Apply or grant uses this class. Tests on a JVM cannot attest device hardware.
 *
 * Requires AndroidX BIOMETRIC_STRONG, AndroidKeyStore auth-per-use ECDSA and
 * BiometricPrompt.CryptoObject; no weak/credential/AI fallback. The fixed
 * alias is confined to the current Android app UID. An invalidated key is
 * DENIED, not deleted/recreated silently.
 */
internal class S4W01FinanceAuthHost(
    private val activity: FragmentActivity,
    pinned: ActiveGenerationPointer,
    activePointer: ActiveGenerationPointerPort,
    expectedHostEpoch: String,
    hostEpochOrNull: () -> String?,
) : AutoCloseable {
    private val lock = Any()
    private var requested = false
    private var finished = false
    private var callback: ((S4W01FinanceLocalPresenceEvidence?) -> Unit)? = null
    private var prompt: BiometricPrompt? = null
    private val signingKey = S4W01FinanceKeystoreSignature()
    private val sandbox = FaroV5AndroidSandboxPrincipal(
        expectedApplicationId = activity.application.packageName,
        pinned = pinned,
        activePointer = activePointer,
        expectedHostEpoch = expectedHostEpoch,
        hostEpochOrNull = hostEpochOrNull,
        facts = AndroidPlatformSandboxFactsReader(activity.application),
    )
    private val principal = S4W01FinanceLocalPrincipal(
        expectedApplicationId = activity.application.packageName,
        pinned = pinned,
        pointer = activePointer,
        expectedHostEpoch = expectedHostEpoch,
        hostEpoch = hostEpochOrNull,
        sandboxEvidence = sandbox::currentEvidenceOrNull,
        verifyTrustedSignature = signingKey::verify,
    )

    /**
     * May be invoked ONLY on the foreground main thread after a canonical
     * human-reviewed Finance summary exists. The resulting proof is technical
     * presence evidence only; M1C still needs durable ACL/Review/COMMIT.
     */
    fun requestAfterForegroundReview(
        scope: S4W01FinanceLocalConsentScope,
        onOutcome: (S4W01FinanceLocalPresenceEvidence?) -> Unit,
    ) {
        synchronized(lock) {
            if (requested || finished) {
                onOutcome(null)
                return
            }
            requested = true
            callback = onOutcome
        }
        if (Looper.myLooper() != Looper.getMainLooper() ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            BiometricManager.from(activity).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            ) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            finish(null)
            return
        }
        val challenge = principal.beginAfterLocalHumanRequest(scope)
        if (challenge == null) {
            finish(null)
            return
        }

        val signature = runCatching { signingKey.preparePerUseSignature() }.getOrNull()
        if (signature == null) {
            finish(null)
            return
        }
        try {
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirmar presencia en Finanzas")
                .setSubtitle(scope.reviewedSummary)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancelar")
                .build()
            val localPrompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        finish(null)
                    }

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        val evidence = runCatching {
                            if (!activity.lifecycle.currentState
                                    .isAtLeast(Lifecycle.State.RESUMED)) return@runCatching null
                            val unlocked = result.cryptoObject?.signature
                            // The authenticated Signature MUST be exactly the
                            // prepared Keystore operation bound to this prompt.
                            if (unlocked !== signature) return@runCatching null
                            unlocked.update(challenge.digestHex.toByteArray(StandardCharsets.US_ASCII))
                            val signed = unlocked.sign()
                            principal.completeFromAuthenticatedCrypto(challenge, signed)
                        }.getOrNull()
                        finish(evidence)
                    }
                },
            )
            synchronized(lock) { if (!finished) prompt = localPrompt }
            localPrompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
        } catch (_: Exception) {
            finish(null)
        }
    }

    private fun finish(result: S4W01FinanceLocalPresenceEvidence?) {
        val complete = synchronized(lock) {
            if (finished) return
            finished = true
            val cb = callback
            callback = null
            prompt = null
            cb
        }
        principal.close()
        sandbox.close()
        complete?.invoke(result)
    }

    override fun close() {
        val old = synchronized(lock) { prompt }
        runCatching { old?.cancelAuthentication() }
        finish(null)
    }
}

private class S4W01FinanceKeystoreSignature {
    private val alias = "faro.v5.finance.w01.m1b.biometric.per_use.v1"

    private fun store(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private fun existingOrCreateKey(): java.security.PrivateKey {
        val store = store()
        // An alias that once existed but is now invalid must never silently
        // recreate a key merely to pass a stale grant/review.
        if (!store.containsAlias(alias)) {
            val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    0, KeyProperties.AUTH_BIOMETRIC_STRONG,
                )
            } else {
                // Android API29 isolated support: per-use biometric only.
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }
            KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore",
            ).apply { initialize(builder.build()) }.generateKeyPair()
        }
        val entry = store().getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: error("Finance biometric key unavailable or invalidated")
        return entry.privateKey
    }

    fun preparePerUseSignature(): Signature =
        Signature.getInstance("SHA256withECDSA").apply {
            initSign(existingOrCreateKey())
        }

    /** Never accept an untrusted public key or a signature-only boolean. */
    fun verify(digestHex: String, signature: ByteArray): Boolean = runCatching {
        val key = store().getCertificate(alias)?.publicKey
            ?: return@runCatching false
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(digestHex.toByteArray(StandardCharsets.US_ASCII))
            verify(signature)
        }
    }.getOrDefault(false)
}
