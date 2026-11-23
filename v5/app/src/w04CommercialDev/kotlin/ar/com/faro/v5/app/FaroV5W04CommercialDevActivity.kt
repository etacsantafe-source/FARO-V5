package ar.com.faro.v5.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity

/** Completely inert application for the isolated commercial read-only renderer. */
class FaroV5W04CommercialDevApplication : Application()

/** W04-E isolated native renderer. W04-F must supply a real authorized D4 decision. */
class FaroV5W04CommercialDevActivity : ComponentActivity() {
    internal lateinit var reviewPanel: FaroV5W04CommercialReviewPanel
        private set

    @Volatile private var trustedForeground = false
    private var currentTrustedGate: FaroV5W04CommercialDevForegroundSessionGate? = null

    /** Only an internal, verified future F2C1B issuer may supply a session. */
    internal fun attachTrustedGate(gate: FaroV5W04CommercialDevForegroundSessionGate): Boolean {
        if (!trustedForeground || isFinishing || isDestroyed || currentTrustedGate != null) {
            gate.revoke()
            return false
        }
        currentTrustedGate = gate
        return true
    }

    internal fun isForegroundForTrustedRead(): Boolean = trustedForeground
    internal fun hasTrustedGate(): Boolean = currentTrustedGate != null

    internal fun readSelectedForTrustedHost(
        needId: String,
        productKey: String,
        explicitlyChosen: () -> Boolean,
    ) = currentTrustedGate?.readSelected(needId, productKey, explicitlyChosen)

    private fun revokeTrustedRead() {
        currentTrustedGate?.revoke()
        currentTrustedGate = null
        reviewPanel.clearForRevocation()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "ar.com.faro.alpha.w04commercialdev")
        check((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)
        reviewPanel = FaroV5W04CommercialReviewPanel(this)
        FaroV5NativeScreenInsets.protect(reviewPanel)
        setContentView(reviewPanel)
    }

    override fun onResume() {
        super.onResume()
        trustedForeground = true
        // Resuming NEVER restores the revoked session or an old review.
    }

    override fun onPause() {
        trustedForeground = false
        revokeTrustedRead()
        super.onPause()
    }

    override fun onStop() {
        trustedForeground = false
        revokeTrustedRead()
        super.onStop()
    }

    override fun onDestroy() {
        trustedForeground = false
        revokeTrustedRead()
        super.onDestroy()
    }
}
