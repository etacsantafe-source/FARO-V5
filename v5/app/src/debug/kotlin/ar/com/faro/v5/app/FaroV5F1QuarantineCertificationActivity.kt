package ar.com.faro.v5.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import ar.com.faro.v5.surface.FaroV5SurfaceHost

/**
 * Debug-only, non-exported F1 Compose certification surface. No shipping
 * CompositionRoot, active generation pointer, Reality Apply or OS Delivery.
 * Tests inject a previously validated staged five-owner host in-process.
 */
internal object FaroV5F1QuarantineUiFixture {
    @Volatile var host: FaroV5QuarantinedV2ChatHostBinding? = null
    @Volatile var applyPort: ar.com.faro.v5.kernel.contracts.ConversationApplyRequestPort? = null
}

class FaroV5F1QuarantineCertificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val staged = checkNotNull(FaroV5F1QuarantineUiFixture.host) {
            "No staged F1 certification host supplied"
        }
        check(staged.verifyQuarantined()) { "F1 certification host lost quarantine" }
        FaroV5SurfaceHost.install(
            activity = this,
            workspaceReadPort = staged.workspaceReadPort,
            timelineReadPort = staged.timelineReadPort,
            composerPort = staged.composerPort,
            reviewPort = staged.reviewPort,
            selectionPort = staged.selectionPort,
            applyRequestPort = FaroV5F1QuarantineUiFixture.applyPort,
            assistantTurnPort = staged.assistantTurnPort,
        )
    }
}
