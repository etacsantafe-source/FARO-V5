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

/**
 * Paired FBS2-only Reality snapshot. Both components are read under the same
 * Reality mutex while the caller holds the global five-owner quiescence.
 * Never serialize the legacy component alone when G4 owners are nonempty.
 */
data class RealityFbs2BackupSnapshot(
    val legacy: RealityBackupSnapshot,
    val g4Domains: RealityDomainBackupSnapshot,
    /** Null is NOT a verified empty Planning owner; only schema8 exposes it. */
    val planningDomains: RealityPlanningBackupSnapshot? = null,
)

/** Internal capture-only source; not an FBS1 backup or restore entry point. */
fun interface RealityFbs2BackupReadPort {
    fun snapshot(): RealityFbs2BackupSnapshot
}
