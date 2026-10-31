package ar.com.faro.v5.app

import androidx.activity.ComponentActivity
import ar.com.faro.v5.surface.FaroV5SurfaceHost

/**
 * F1 Android certification bridge: mount the REAL Compose chat shell against
 * the isolated five-owner Workspace/Review/assistant ports.
 *
 * This deliberately does NOT expose an Apply port, OS delivery, V1 replacement
 * or an active-generation switch. Production bootstrap must not invoke this
 * certification-only entry point. A quarantined generation remains isolated;
 * individual Workspace and assistant ports revalidate admission on every call.
 *
 * The mounting bridge exists so Android instrumentation can exercise actual
 * Composer -> Workspace -> trusted reasoning -> timeline -> human Review on the
 * same stage instead of building a parallel, synthetic UI data source.
 */
internal object FaroV5QuarantinedChatCertificationSurface {
    fun mount(
        activity: ComponentActivity,
        host: FaroV5QuarantinedV2ChatHostBinding,
    ) {
        check(host.verifyQuarantined()) {
            "Cannot mount a closed, unhealthy or active V2 candidate"
        }
        FaroV5SurfaceHost.install(
            activity = activity,
            workspaceReadPort = host.workspaceReadPort,
            timelineReadPort = host.timelineReadPort,
            composerPort = host.composerPort,
            reviewPort = host.reviewPort,
            selectionPort = host.selectionPort,
            applyRequestPort = null, // V2 uncertified: never offer the Apply UI action.
            assistantTurnPort = host.assistantTurnPort,
        )
    }
}
