package ar.com.faro.v5.app

import android.content.Context
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import java.nio.file.Path
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking

/**
 * Debug-only F1 integration of the real provider-neutral ComposedModelGateway.
 *
 * Uses ONLY the Android owner for explicit consent and Keystore credentials.
 * A missing/revoked consent returns null before opening any staged store.
 * The gateway rechecks consent and egress at each reasoning operation.
 * The graph remains an inactive five-store generation, never V1/V2 cutover.
 *
 * Call from a background test/host worker, never the Android main thread.
 */
internal object FaroV5F1ConsentedQuarantineHost {
    fun open(
        context: Context,
        staged: StagedStorageGeneration,
        gate: MaterialWriteGate,
        activeDirectory: Supplier<Path>,
    ): FaroV5QuarantinedV2ChatHostBinding? {
        val owner = FaroV5TrustedAssistantControlOwner.get(context.applicationContext)
        val trusted = FaroV5TrustedModelGatewayFactory(
            readControl = { runBlocking { owner.read() } },
            secretReader = owner.secretReader(),
        ).create() ?: return null
        return FaroV5QuarantinedV2ChatHostBinding.openForCertification(
            staged = staged,
            gate = gate,
            activeDirectory = activeDirectory,
            gateway = trusted.gateway,
            profile = trusted.profile,
            egress = { message -> trusted.egressAdmission.test(message) },
        )
    }
}
