package ar.com.faro.v5.store.reality

import ar.com.faro.v5.owner.objectives.api.ObjectiveSnapshot
import ar.com.faro.v5.owner.matters.api.MatterSnapshot

/** Internal read-only snapshot, not exportable/restorable until versioned G4 backup. */
data class RealityDomainBackupSnapshot(
    val schemaVersion: Int,
    val objectives: ObjectiveSnapshot,
    val matters: MatterSnapshot,
)

fun interface RealityDomainBackupReadPort {
    fun snapshot(): RealityDomainBackupSnapshot
}
