package ar.com.faro.v5.app

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.reality.Finance9ReadPolicyIssueRecord
import ar.com.faro.v5.store.reality.RealityFinance9TrustedReadPolicyIssuerPort
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * W01-B3B1B1B1B0: DORMANT DEV-only READ host. Not connected to an Activity,
 * shipping CompositionRoot, Compose, Chat or FinanceReadPort.
 *
 * Only a real BiometricPrompt.CryptoObject with the EXACT prepared Signature
 * can issue short-lived STAGED9 READ policy. No Apply grant, money movement,
 * cached balances or cross-process revoke warranty. Android hardware NOT_RUN.
 */
internal class S4W01FinanceReadAuthHost(
    private val activity: FragmentActivity,
    private val pinned: ActiveGenerationPointer,
    private val pointer: ActiveGenerationPointerPort,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val issuer: RealityFinance9TrustedReadPolicyIssuerPort,
) : AutoCloseable {
    private val mutex = Any()
    private var requested = false
    private var finished = false
    private var callback: ((Finance9ReadPolicyIssueRecord?) -> Unit)? = null
    private var prompt: BiometricPrompt? = null
    private val key = S4W01FinanceReadKeystoreSignature()
    private val uid = Process.myUid()
    private val appId = activity.application.packageName
    private val sandbox = FaroV5AndroidSandboxPrincipal(
        expectedApplicationId = appId,
        pinned = pinned,
        activePointer = pointer,
        expectedHostEpoch = expectedHostEpoch,
        hostEpochOrNull = hostEpochOrNull,
        facts = AndroidPlatformSandboxFactsReader(activity.application),
    )
    private val gate = S4W01FinanceReadConsentGate(
        pinned = pinned,
        pointer = pointer,
        expectedApplicationId = appId,
        expectedApplicationUid = uid,
        expectedHostEpoch = expectedHostEpoch,
        hostEpochOrNull = hostEpochOrNull,
        hostReady = {
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        },
        sandboxEvidence = sandbox::currentEvidenceOrNull,
        principalHashOrNull = { key.technicalPrincipalHash(appId, uid) },
        verifyTrustedSignature = key::verify,
        issuer = issuer,
    )

    /** A terminal foreground loss aborts the pending READ biometric challenge. */
    private val lifecycleFence = S4W01FinanceReadSessionLifecycle { close() }
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> lifecycleFence.onStop()
            Lifecycle.Event.ON_DESTROY -> lifecycleFence.onDestroy()
            else -> Unit
        }
    }

    init {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "DEV READ auth host lifecycle must be installed on Android main thread"
        }
        activity.lifecycle.addObserver(lifecycleObserver)
        if (activity.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            lifecycleFence.onDestroy()
        }
    }

    /** Explicit foreground user action only; never an automatic background read. */
    fun requestAfterExplicitForegroundReadRequest(
        onOutcome: (Finance9ReadPolicyIssueRecord?) -> Unit,
    ) {
        val accepted = synchronized(mutex) {
            if (requested || finished || !lifecycleFence.isOpen()) false else {
                requested = true
                callback = onOutcome
                true
            }
        }
        if (!accepted) {
            onOutcome(null)
            return
        }
        if (Looper.myLooper() != Looper.getMainLooper() ||
            !lifecycleFence.isOpen() ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            BiometricManager.from(activity).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            ) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            finish(null)
            return
        }
        val challenge = gate.beginAfterExplicitReadRequest()
        val signature = runCatching { key.preparePerUseSignature() }.getOrNull()
        if (challenge == null || signature == null) {
            finish(null)
            return
        }
        try {
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Consultar mis cuentas y saldos")
                .setSubtitle("Autorizar una lectura local de Finanzas")
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
                        val outcome = runCatching {
                            if (!lifecycleFence.isOpen() || !activity.lifecycle.currentState
                                    .isAtLeast(Lifecycle.State.RESUMED)
                            ) return@runCatching null
                            val unlocked = result.cryptoObject?.signature
                            if (unlocked !== signature) return@runCatching null
                            unlocked.update(
                                challenge.digestHex.toByteArray(StandardCharsets.US_ASCII),
                            )
                            val signed = unlocked.sign()
                            gate.completeFromAuthenticatedCrypto(challenge, signed)
                        }.getOrNull()
                        finish(outcome)
                    }
                },
            )
            synchronized(mutex) { if (!finished) prompt = localPrompt }
            if (!lifecycleFence.isOpen() ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                finish(null)
                return
            }
            localPrompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
        } catch (_: Exception) {
            finish(null)
        }
    }

    private fun finish(outcome: Finance9ReadPolicyIssueRecord?) {
        // A late biometric result cannot survive a terminal lifecycle event.
        val safeOutcome = if (outcome != null && lifecycleFence.isOpen() &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) outcome else null
        val cb = synchronized(mutex) {
            if (finished) null else {
                finished = true
                val previous = callback
                callback = null
                prompt = null
                previous
            }
        }
        lifecycleFence.close() // irreversible, without recursive terminator
        if (Looper.myLooper() == Looper.getMainLooper()) {
            activity.lifecycle.removeObserver(lifecycleObserver)
        } else {
            Handler(Looper.getMainLooper()).post {
                activity.lifecycle.removeObserver(lifecycleObserver)
            }
        }
        gate.close()
        sandbox.close()
        cb?.invoke(safeOutcome)
    }

    override fun close() {
        lifecycleFence.close() // synchronous fail-closed even from worker thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { close() }
            return
        }
        val old = synchronized(mutex) { prompt }
        runCatching { old?.cancelAuthentication() }
        finish(null)
    }
}

/** Separate READ alias and domain, never reuse M1B Apply signature authority. */
private class S4W01FinanceReadKeystoreSignature {
    private val alias = "faro.v5.finance.w01.read.biometric.per_use.v1"

    private fun store(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): java.security.PrivateKey {
        val keyStore = store()
        // Never recreate a once-existing but invalidated key to satisfy a
        // pending read; only generate when there is no alias at all.
        if (!keyStore.containsAlias(alias)) {
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
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }
            KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore",
            ).apply { initialize(builder.build()) }.generateKeyPair()
        }
        return (store().getEntry(alias, null) as? KeyStore.PrivateKeyEntry)
            ?.privateKey ?: error("READ biometric key unavailable")
    }

    fun preparePerUseSignature(): Signature =
        Signature.getInstance("SHA256withECDSA").apply {
            initSign(key())
        }

    /** Only derived from the fixed app UID + certificate alias AFTER prompt. */
    fun technicalPrincipalHash(appId: String, uid: Int): String? = runCatching {
        if (uid <= 0 || appId.isBlank()) return@runCatching null
        val encoded = store().getCertificate(alias)?.publicKey?.encoded
            ?: return@runCatching null
        if (encoded.isEmpty()) return@runCatching null
        val sha = MessageDigest.getInstance("SHA-256")
        sha.update("FARO_W01_LOCAL_READ_KEY_PRINCIPAL_V1".toByteArray(StandardCharsets.US_ASCII))
        sha.update(0.toByte())
        sha.update(appId.toByteArray(StandardCharsets.UTF_8))
        sha.update(0.toByte())
        sha.update(uid.toString().toByteArray(StandardCharsets.US_ASCII))
        sha.update(0.toByte())
        sha.update(encoded)
        sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }.getOrNull()

    fun verify(digestHex: String, signature: ByteArray): Boolean = runCatching {
        val publicKey = store().getCertificate(alias)?.publicKey
            ?: return@runCatching false
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(digestHex.toByteArray(StandardCharsets.US_ASCII))
            verify(signature)
        }
    }.getOrDefault(false)
}
