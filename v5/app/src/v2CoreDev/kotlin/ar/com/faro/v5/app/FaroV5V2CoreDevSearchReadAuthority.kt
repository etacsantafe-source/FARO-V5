package ar.com.faro.v5.app

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * DEV-variant ONLY, process-local search-read grant for a verified five-owner
 * Workspace session. This is never a shipping ACL or a persisted permission.
 *
 * Every new verified host session gets an unpredictable fresh grant identity.
 * On close or failed recovery admission all old locators fail closed. A
 * StateFlow version is notification-only: callers must revalidate authority
 * on every canonical read and navigation, including after process restart.
 */
internal class FaroV5V2CoreDevSearchReadAuthority(
    private val admitted: () -> Boolean,
) : AutoCloseable {
    private val guard = Any()
    private val version = MutableStateFlow(0L)
    private var currentGrant: String? = null
    private var closed = false

    /** Replays the last version to late subscribers; never conveys authority. */
    val invalidations: Flow<Unit> = version.map { Unit }

    /** Only the already-verified DEV bootstrap host calls this, never the UI. */
    fun grantAfterVerifiedOpen() {
        check(runCatching { admitted() }.getOrDefault(false)) {
            "DEV Workspace search cannot grant before verified recovery"
        }
        synchronized(guard) {
            check(!closed && currentGrant == null) { "DEV search grant already issued" }
            currentGrant = UUID.randomUUID().toString()
            version.value += 1L
        }
    }

    fun epochOrNull(): String? {
        val initial = synchronized(guard) { if (closed) null else currentGrant } ?: return null
        if (!runCatching { admitted() }.getOrDefault(false)) return null
        return synchronized(guard) {
            if (closed || currentGrant != initial ||
                !runCatching { admitted() }.getOrDefault(false)
            ) null else initial
        }
    }

    /** Pointer changes invalidate visible results, even if no query is running. */
    fun invalidate() {
        synchronized(guard) {
            if (!closed) version.value += 1L
        }
    }

    override fun close() {
        synchronized(guard) {
            if (!closed) {
                closed = true
                currentGrant = null
                version.value += 1L
            }
        }
    }
}
