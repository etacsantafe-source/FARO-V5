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
        val store = try {
            AiControlPreferenceStore(
                File(context.noBackupFilesDir, FaroV5TrustedModelGatewayFactory.CONTROL_FILENAME),
                FaroV5TrustedModelGatewayFactory.DEFAULT_PROFILE,
            )
        } catch (_: Exception) { return }
        val components = try {
            FaroV5TrustedModelGatewayFactory(
                readControl = { runBlocking { store.read() } },
                secretReader = AndroidKeystoreSecretStore(context),
            ).create()
        } catch (_: Exception) { null }
        if (components == null) {
            store.close()
            return
        }
        try {
            root.configureTrustedAssistantRuntime(
                components.gateway, components.profile, components.egressAdmission,
            )
            // Store remains process-scoped to re-read revocation before every egress.
        } catch (_: Exception) {
            store.close()
        }
    }
}
