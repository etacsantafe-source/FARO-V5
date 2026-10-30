package ar.com.faro.v5.maintenance

/**
 * Exact terminal-host compatibility policy while the production Android app
 * still mounts four V1 stores. A staged, marker-bound five-store V2 must NEVER
 * be published to the active pointer until a separately certified V2 host exists.
 *
 * Read-only: neither initializes SQLite nor changes journal/pointer/fence.
 * The caller owns the exclusive maintenance gate and pointer preconditions.
 */
class LegacyV1HostGenerationAdmission(
    private val layout: FileStorageGenerationLayout,
) : GenerationPreSwitchAdmissionPort {
    override fun canOpenAsActive(
        kind: MaintenanceOperationKind,
        targetGenerationId: StorageGenerationId,
    ): Boolean {
        if (kind !in setOf(MaintenanceOperationKind.RESTORE, MaintenanceOperationKind.MIGRATION)) {
            return false
        }
        return runCatching {
            ExistingGenerationFormatReader.requireAndRead(
                layout.requireStaged(targetGenerationId)
            ) == GenerationStoreFormat.LEGACY_V1
        }.getOrDefault(false)
    }
}
