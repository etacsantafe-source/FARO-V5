package ar.com.faro.v5.app

import android.app.Application
import android.os.Build
import android.os.Process
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import java.util.UUID

/** Immutable OS process facts; neither a human account nor object ACL. */
internal data class AndroidSandboxProcessFacts(
    val processUid: Int,
    val applicationUid: Int,
    val processIsApplicationUid: Boolean,
    val processIsIsolated: Boolean,
    val processIsSdkSandbox: Boolean,
    val applicationId: String,
)

/** Injectable for JVM tests; production MUST supply the Application-backed reader. */
internal fun interface AndroidSandboxFactsReadPort {
    fun read(): AndroidSandboxProcessFacts?
}

/**
 * The only real Android facts adapter, explicitly requiring the trusted
 * Application (not a caller-supplied UI/Intent package name).
 * This adapter is not yet composed into a shipping or DEV host.
 */
internal class AndroidPlatformSandboxFactsReader(
    private val application: Application,
) : AndroidSandboxFactsReadPort {
    override fun read(): AndroidSandboxProcessFacts {
        val uid = Process.myUid()
        return AndroidSandboxProcessFacts(
            processUid = uid,
            applicationUid = application.applicationInfo.uid,
            processIsApplicationUid = Process.isApplicationUid(uid),
            processIsIsolated = Process.isIsolated(),
            processIsSdkSandbox =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    Process.isSdkSandbox(),
            applicationId = application.packageName,
        )
    }
}

/** Process evidence, NOT a user identity, object permission or Apply ticket. */
internal data class AndroidSandboxTechnicalEvidence(
    val sessionNonce: String,
    val applicationId: String,
    val applicationUid: Int,
    val pinnedGeneration: ActiveGenerationPointer,
)

/**
 * Session-scoped read-only evidence of an Android app sandbox.
 * Never grants an operation or emits an authenticated human principal.
 * Failures permanently invalidate this instance, even after A -> B -> A.
 * No backing store, no import/export, no host/Apply wiring.
 */
internal class FaroV5AndroidSandboxPrincipal(
    private val expectedApplicationId: String,
    private val pinned: ActiveGenerationPointer,
    private val activePointer: ActiveGenerationPointerPort,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val facts: AndroidSandboxFactsReadPort,
) : AutoCloseable {

    init {
        require(validApplicationId(expectedApplicationId)) {
            "Trusted Android application ID required"
        }
        require(expectedHostEpoch.isNotBlank() && expectedHostEpoch == expectedHostEpoch.trim()) {
            "Trusted host lifecycle epoch required"
        }
    }

    private val monitor = Any()
    private val sessionNonce = UUID.randomUUID().toString()
    private var firstAcceptedFacts: AndroidSandboxProcessFacts? = null
    private var invalidated = false

    private fun validApplicationId(value: String): Boolean =
        value.length in 3..200 &&
            value.count { it == '.' } >= 1 &&
            value == value.trim() &&
            value.split('.').all { segment ->
                segment.isNotEmpty() &&
                    (segment[0].isLetter() || segment[0] == '_') &&
                    segment.all { it.isLetterOrDigit() || it == '_' }
            }

    private fun matchesExpectedSandbox(observed: AndroidSandboxProcessFacts): Boolean =
        observed.processUid > 0 &&
            observed.processUid == observed.applicationUid &&
            observed.processIsApplicationUid &&
            !observed.processIsIsolated &&
            !observed.processIsSdkSandbox &&
            observed.applicationId == expectedApplicationId

    private fun currentHost(): Boolean = runCatching {
        activePointer.read() == pinned &&
            hostEpochOrNull() == expectedHostEpoch
    }.getOrDefault(false)

    private fun sample(): AndroidSandboxProcessFacts? =
        runCatching { facts.read() }.getOrNull()

    /**
     * Recheck process facts twice and the exact durable pointer/lifecycle
     * before/after. No old nonce is ever restored when this owner is revoked.
     */
    fun currentEvidenceOrNull(): AndroidSandboxTechnicalEvidence? =
        synchronized(monitor) {
            if (invalidated) return@synchronized null
            if (!currentHost()) {
                invalidated = true
                return@synchronized null
            }
            val first = sample()
            if (first == null || !matchesExpectedSandbox(first) ||
                (firstAcceptedFacts != null && firstAcceptedFacts != first)
            ) {
                invalidated = true
                return@synchronized null
            }
            val second = sample()
            if (second != first || !currentHost()) {
                invalidated = true
                return@synchronized null
            }
            if (firstAcceptedFacts == null) firstAcceptedFacts = first
            AndroidSandboxTechnicalEvidence(
                sessionNonce = sessionNonce,
                applicationId = first.applicationId,
                applicationUid = first.applicationUid,
                pinnedGeneration = pinned,
            )
        }

    override fun close() {
        synchronized(monitor) {
            invalidated = true
            firstAcceptedFacts = null
        }
    }
}
