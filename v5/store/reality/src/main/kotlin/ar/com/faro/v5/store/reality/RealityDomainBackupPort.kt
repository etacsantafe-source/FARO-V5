package ar.com.faro.v5.store.reality

import ar.com.faro.v5.owner.objectives.api.ObjectiveSnapshot
import ar.com.faro.v5.owner.matters.api.MatterSnapshot

/** Internal G4 owner snapshot. Not exportable until an authenticated FBS2 format is certified. */
data class RealityDomainBackupSnapshot(
    val schemaVersion: Int,
    val objectives: ObjectiveSnapshot,
    val matters: MatterSnapshot,
)

fun interface RealityDomainBackupReadPort {
    fun snapshot(): RealityDomainBackupSnapshot
}


/** Only an internal staged target may call this after archive identity admission.
 * Does not grant permission to export, change active generation or bypass FBS2.
 */
fun interface RealityDomainBackupRestorePort {
    fun restoreOrVerify(snapshot: RealityDomainBackupSnapshot): RealityDomainBackupSnapshot
}
