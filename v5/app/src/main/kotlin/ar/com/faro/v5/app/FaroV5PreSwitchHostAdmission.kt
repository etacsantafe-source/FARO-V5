package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.maintenance.ExistingGenerationFormatReader
import ar.com.faro.v5.maintenance.FileStorageGenerationLayout
import ar.com.faro.v5.maintenance.GenerationPreSwitchAdmissionPort
import ar.com.faro.v5.maintenance.GenerationStoreFormat
import ar.com.faro.v5.maintenance.LegacyV1HostGenerationAdmission
import ar.com.faro.v5.maintenance.MaintenanceOperationKind
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.maintenance.StorageGenerationId

/**
 * Host compatibility check called both BEFORE and AFTER a durable pointer switch.
 * PRE-switch V2 probes the non-active staging graph; POST-switch V2 MUST NOT
 * probe it through quarantined admission (which correctly rejects active data).
 * At that stage the coordinator already required runtime activation/health and
 * the same exclusive maintenance lease. Recheck descriptor+format+pointer only.
 * This class never installs the replay fence or accepts material writes.
 */
internal class FaroV5PreSwitchHostAdmission(
    private val layout: FileStorageGenerationLayout,
    private val activePointer: ActiveGenerationPointerPort,
    private val v2HostCertified: Boolean,
    private val verifyQuarantinedV2: (StagedStorageGeneration) -> Boolean,
) : GenerationPreSwitchAdmissionPort {
    private val legacy = LegacyV1HostGenerationAdmission(layout)

    override fun canOpenAsActive(
        kind: MaintenanceOperationKind,
        targetGenerationId: StorageGenerationId,
    ): Boolean {
        if (legacy.canOpenAsActive(kind, targetGenerationId)) return true
        // V2 migration and reset are not authorized. Restore requires one
        // explicit global certificate, never just a parseable V2 marker.
        if (kind != MaintenanceOperationKind.RESTORE || !v2HostCertified) return false
        return runCatching {
            val staged = layout.requireStaged(targetGenerationId)
            if (ExistingGenerationFormatReader.requireAndRead(staged) !=
                    GenerationStoreFormat.WORKSPACE_V2) return@runCatching false

            val pointerBefore = activePointer.read()?.generationId
                ?: return@runCatching false
            if (pointerBefore == targetGenerationId) {
                // Already post-switch: coordinator has verified the ACTUAL active
                // runtime health before calling this policy at HEALTH_VERIFIED.
                // The quarantined opener is forbidden here: it would reject
                // the active graph and force an erroneous rollback.
                activePointer.read()?.generationId == targetGenerationId
            } else {
                // Still pre-pointer: prove five-owner health on the isolated
                // candidate, then refuse a concurrent pointer change.
                verifyQuarantinedV2(staged) &&
                    activePointer.read()?.generationId == pointerBefore
            }
        }.getOrDefault(false)
    }
}
