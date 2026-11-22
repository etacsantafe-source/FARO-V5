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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "ar.com.faro.alpha.w04commercialdev")
        check((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        check(!FaroV5HostCertification.V2_ACTIVE_ENABLED)
        reviewPanel = FaroV5W04CommercialReviewPanel(this)
        FaroV5NativeScreenInsets.protect(reviewPanel)
        setContentView(reviewPanel)
    }

    override fun onPause() {
        reviewPanel.clearForRevocation()
        super.onPause()
    }

    override fun onDestroy() {
        reviewPanel.clearForRevocation()
        super.onDestroy()
    }
}
