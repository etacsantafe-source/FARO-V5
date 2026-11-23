package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityHousehold10Row
import java.nio.file.Path
import java.util.Collections

/**
 * F2C0: DEV-only selected, read-only adapter around the already certified F2B1
 * single-leased Household10/Evidence2 ACTIVE read session. This is NOT a
 * production host, UI permission issuer, cutover, or writable owner.
 *
 * The caller supplies actual trusted Android foreground/epoch/owner grants.
 * F2C1 must authenticate the isolated DEV app before constructing this port,
 * close it on pause/stop, and require explicit human observation selection.
 */
class FaroV5Household10Evidence2DevSelectionReadPort private constructor(
    private val pointer: FileActiveGenerationPointer,
    private val selected: ActiveGenerationPointer,
    private val materialWriteGate: MaterialWriteGate,
    private val session: FaroV5GenerationStoreGraph.Household10DevReadSession,
    private val hostEpoch: String,
    private val currentHostEpoch: () -> String?,
    private val isForeground: () -> Boolean,
    private val ownerAdmission: () -> Boolean,
    private val householdGrant: (String) -> Boolean,
    private val commercialGrant: (String) -> Boolean,
) : AutoCloseable {
    /** No other Household need or Commercial product is included in this return. */
    data class SelectedRead(
        val generation: ActiveGenerationPointer,
        val householdRevision: Long,
        val need: RealityHousehold10Row,
        val typedHistory: CommercialEvidenceStagedTypedPortabilitySnapshot,
    )

    private val monitor = Any()
    @Volatile private var closed = false

    fun readSelected(
        needId: String,
        productKey: String,
        explicitlyChosen: () -> Boolean,
    ): SelectedRead? = synchronized(monitor) {
        materialWriteGate.withMaterialWrite {
            if (!admitted(needId, productKey, explicitlyChosen)) return@withMaterialWrite null
            val household = runCatching {
                session.readNeeds { admitted(needId, productKey, explicitlyChosen) }
            }.getOrNull() ?: return@withMaterialWrite null
            val exactNeed = household.needs.singleOrNull {
                it.id == needId && it.status == "OPEN"
            } ?: return@withMaterialWrite null
            val evidence = runCatching {
                session.authorizedCommercialTypedSnapshot(
                    { admitted(needId, productKey, explicitlyChosen) },
                    { admitted(needId, productKey, explicitlyChosen) },
                )
            }.getOrNull() ?: return@withMaterialWrite null
            if (evidence.schemaVersion != 2) return@withMaterialWrite null
            // Preserve the full-source watermark: any other Evidence mutation
            // invalidates rechecks. Never reveal another product's observation.
            val selectedRecords = Collections.unmodifiableList(ArrayList(
                evidence.records.filter { it.observation.productKey == productKey }
            ))
            if (!admitted(needId, productKey, explicitlyChosen)) return@withMaterialWrite null
            SelectedRead(selected, household.revision, exactNeed,
                evidence.copy(records = selectedRecords))
        }
    }

    private fun admitted(needId: String, productKey: String, explicitlyChosen: () -> Boolean): Boolean =
        runCatching {
            !closed && needId.length in 1..128 && productKey.length in 1..160 &&
                needId.none { it.isWhitespace() || it.isISOControl() } &&
                productKey.none(Char::isISOControl) &&
                pointer.read() == selected &&
                currentHostEpoch() == hostEpoch &&
                isForeground() && ownerAdmission() && explicitlyChosen() &&
                householdGrant(needId) && commercialGrant(productKey)
        }.getOrDefault(false)

    override fun close() = synchronized(monitor) {
        if (!closed) {
            closed = true
            session.close() // Releases the same F2B1 root OS lease.
        }
    }

    companion object {
        /**
         * Opens only an ALREADY selected typed DEV generation, never switches it.
         * F2B1's internal factory enforces exact canonical descriptor+pointer,
         * schema2 five-store health, and the exclusive finance9-dev-root lock.
         */
        fun open(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            selected: ActiveGenerationPointer,
            materialWriteGate: MaterialWriteGate,
            hostEpoch: String,
            currentHostEpoch: () -> String?,
            isForeground: () -> Boolean,
            ownerAdmission: () -> Boolean,
            householdGrant: (String) -> Boolean,
            commercialGrant: (String) -> Boolean,
        ): FaroV5Household10Evidence2DevSelectionReadPort {
            require(hostEpoch.length in 1..160 && hostEpoch.none(Char::isISOControl))
            val pointer = FileActiveGenerationPointer(storageRoot)
            val currentlyAdmitted = {
                pointer.read() == selected && currentHostEpoch() == hostEpoch &&
                    isForeground() && ownerAdmission()
            }
            check(currentlyAdmitted()) { "F2C0 DEV selected read host is not admitted" }
            val session = FaroV5GenerationStoreGraph
                .openActiveHousehold10Evidence2DevForTrustedHost(
                    storageRoot, staged, selected, materialWriteGate, currentlyAdmitted,
                )
            return try {
                check(currentlyAdmitted()) { "F2C0 DEV host changed during open" }
                FaroV5Household10Evidence2DevSelectionReadPort(
                    pointer, selected, materialWriteGate, session, hostEpoch, currentHostEpoch,
                    isForeground, ownerAdmission, householdGrant, commercialGrant,
                )
            } catch (failure: Throwable) {
                session.close()
                throw failure
            }
        }
    }
}
