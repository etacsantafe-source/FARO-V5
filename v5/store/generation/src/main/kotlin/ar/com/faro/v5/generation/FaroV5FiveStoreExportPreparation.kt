package ar.com.faro.v5.generation

import ar.com.faro.v5.maintenance.BackupSetId
import ar.com.faro.v5.maintenance.MaintenanceOperationId
import ar.com.faro.v5.maintenance.MaterialWriteQuiescencePort
import ar.com.faro.v5.maintenance.PortableBackupArchiveStore
import ar.com.faro.v5.maintenance.PreparedBackupExternalWriteSource

/**
 * User consent for a private encrypted FAROBAK2 export.
 *
 * Both flags must originate from an explicit user interaction at the Android
 * boundary. This object is not a durable authorization or a sharing receipt.
 */
data class FiveStoreBackupExportConsent(
    val confirmedByUser: Boolean,
    val acknowledgedExternalShare: Boolean,
)

/**
 * F3: one five-owner quiescent snapshot -> authenticated FAROBAK2 -> verified
 * private encrypted staging -> existing SAF source. No URI, external write,
 * active pointer change, or V2 activation occurs here.
 *
 * The terminal Android host must provide activeAdmission bound to the actual
 * generation pointer, completed startup recovery and V2 certification.
 * Never substitute a per-store lock or a permanently true production lambda.
 */
class FaroV5FiveStoreExportPreparation(
    private val graph: FaroV5GenerationStoreGraph,
    private val quiescence: MaterialWriteQuiescencePort,
    private val archiveStore: PortableBackupArchiveStore,
    private val activeAdmission: () -> Boolean,
) {
    fun prepare(
        operationId: MaintenanceOperationId,
        backupSetId: BackupSetId,
        productVersion: String,
        createdAtEpochMillis: Long,
        passphrase: CharArray,
        consent: FiveStoreBackupExportConsent,
        effectIdentity: String,
    ): PreparedBackupExternalWriteSource {
        check(consent.confirmedByUser && consent.acknowledgedExternalShare) {
            "five-store external export requires explicit user consent"
        }
        require(effectIdentity.isNotBlank() && effectIdentity.length <= 256)
        check(activeAdmission() && graph.workspace != null) {
            "only a certified active five-store generation can prepare export"
        }

        val snapshot = FaroV5FiveStoreBackupCapture(graph, quiescence).capture(
            operationId = operationId,
            backupSetId = backupSetId,
            productVersion = productVersion,
            createdAtEpochMillis = createdAtEpochMillis,
        )
        // G4 FBS2 v2 can be captured and authenticated internally, but the
        // existing staged restore rejects it until full G4 restore is certified.
        // Never offer an exportable archive that cannot be restored.
        check(snapshot.g4Domains == null) {
            "G4 FBS2 external export blocked until matching restore is certified"
        }
        check(activeAdmission()) {
            "generation authority changed during five-store capture"
        }

        val encrypted = FaroV5FiveStorePortableBackupCrypto().seal(snapshot, passphrase)
        try {
            check(activeAdmission()) {
                "generation authority changed before encrypted staging"
            }
            val staged = archiveStore.stageBytes(encrypted, createdAtEpochMillis)
            try {
                check(activeAdmission() && archiveStore.verify(staged)) {
                    "generation authority or encrypted staging verification changed"
                }
                return PreparedBackupExternalWriteSource(effectIdentity, staged, archiveStore)
            } catch (failure: Throwable) {
                runCatching { archiveStore.delete(staged) }
                throw failure
            }
        } finally {
            encrypted.fill(0)
        }
    }
}
