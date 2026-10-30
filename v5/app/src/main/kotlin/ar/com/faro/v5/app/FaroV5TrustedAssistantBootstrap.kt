package ar.com.faro.v5.app

import android.content.Context
import ar.com.faro.v5.control.AiControlPreferenceStore
import ar.com.faro.v5.platform.android.security.AndroidKeystoreSecretStore
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Startup-executor-only, fail-closed installation. The default preference
 * disables remote reasoning. No credential, profile or consent is invented.
 * The resulting graph remains dormant while V2_ACTIVE_ENABLED is false.
 */
object FaroV5TrustedAssistantBootstrap {
    @JvmStatic
    fun installIfAuthorized(context: Context, root: FaroV5CompositionRoot) {
        val owner = try {
            FaroV5TrustedAssistantControlOwner.get(context)
        } catch (_: Exception) { return }
        val components = try {
            FaroV5TrustedModelGatewayFactory(
                readControl = { runBlocking { owner.read() } },
                secretReader = owner.secretReader(),
            ).create()
        } catch (_: Exception) { null }
        if (components == null) return
        try {
            root.configureTrustedAssistantRuntime(
                components.gateway, components.profile, components.egressAdmission,
            )
        } catch (_: Exception) {
            // No active graph is modified. A subsequent process start may
            // load newly approved settings; V2 stays dormant meanwhile.
        }
    }
}
