package ar.com.faro.v5.generation

import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilitySnapshot
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.store.reality.RealityHousehold10Row
import java.nio.file.Path
import java.util.Collections
import ar.com.faro.v5.evidence.CommercialEvidence2DevReadScopeOwner
import ar.com.faro.v5.evidence.CommercialEvidenceStagedTypedPortabilityPort
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.owner.household.api.HouseholdNeed
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import ar.com.faro.v5.owner.household.api.HouseholdNeedName
import ar.com.faro.v5.owner.household.api.HouseholdNeedPriority
import ar.com.faro.v5.owner.household.api.HouseholdNeedQuantity
import ar.com.faro.v5.owner.household.api.HouseholdNeedStatus
import ar.com.faro.v5.owner.household.api.HouseholdNeedUnit
import ar.com.faro.v5.owner.household.spi.Household10DevReadScopeOwner
import ar.com.faro.v5.owner.household.spi.HouseholdNeedsSnapshot
import java.time.LocalDate

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
    // Only the new real-owner DEV path uses this non-recursive SQLite fence.
    // Both owner scopes are checked immediately before AND after the SQL read.
    private val ownerScopeSqlAdmission: (() -> Boolean)? = null,
    private val ownerScopes: AutoCloseable? = null,
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
                session.readNeeds {
                    // The new owner-backed policy reads its source from this SAME
                    // Reality connection. While SQL BEGIN DEFERRED is open, do
                    // not recursively call the owner, which would nest BEGIN.
                    ownerScopeSqlAdmission?.invoke()
                        ?: admitted(needId, productKey, explicitlyChosen)
                }
            }.getOrNull() ?: return@withMaterialWrite null
            // The guarded SQL transaction has ended. Recheck BOTH independent
            // owner grants against fresh source data before reading Evidence.
            if (!admitted(needId, productKey, explicitlyChosen)) return@withMaterialWrite null
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
            runCatching { ownerScopes?.close() } // Revoke BOTH owner grants before lease release.
            session.close() // Releases the same F2B1 root OS lease.
        }
    }

    companion object {

        /**
         * F2C1B1B1B0: two REAL-domain DEV owner source callbacks are bound to
         * one already-selected five-store ACTIVE graph and ONE root OS lease.
         *
         * 'trustedHumanDecision' is supplied by a future trusted Android DEV
         * host; a JVM true callback is only a fixture, not human auth. No
         * Activity, Intent, checkout, Apply, or productive V2 wiring here.
         */
        fun openOwnerBackedDev(
            storageRoot: Path,
            staged: StagedStorageGeneration,
            selected: ActiveGenerationPointer,
            materialWriteGate: MaterialWriteGate,
            principal: String,
            selectedNeed: HouseholdNeedId,
            selectedProduct: CommercialProductKey,
            hostEpoch: String,
            currentHostEpoch: () -> String?,
            isForeground: () -> Boolean,
            ownerAdmission: () -> Boolean,
            trustedHumanDecision: () -> Boolean,
        ): FaroV5Household10Evidence2DevSelectionReadPort {
            require(hostEpoch.length in 1..160 && hostEpoch.none(Char::isISOControl))
            require(principal.length in 1..160 && principal.isNotBlank() &&
                principal == principal.trim() && principal.none(Char::isISOControl))
            val pointer = FileActiveGenerationPointer(storageRoot)
            fun hostCurrent(): Boolean = runCatching {
                selected.sequence > 0L && pointer.read() == selected &&
                    currentHostEpoch() == hostEpoch && isForeground() && ownerAdmission()
            }.getOrDefault(false)
            fun selectedByHost(): Boolean = runCatching {
                hostCurrent() && trustedHumanDecision() && hostCurrent()
            }.getOrDefault(false)
            check(selectedByHost()) { "Selected DEV owner decision absent" }
            // Opens exactly once; subsequent owner snapshots use this session.
            val session = FaroV5GenerationStoreGraph
                .openActiveHousehold10Evidence2DevForTrustedHost(
                    storageRoot, staged, selected, materialWriteGate, ::hostCurrent,
                )
            var householdOwner: Household10DevReadScopeOwner? = null
            var evidenceOwner: CommercialEvidence2DevReadScopeOwner? = null
            return try {
                check(selectedByHost())
                val householdIdentity = Household10DevReadScopeOwner.Identity(
                    principal, selected.generationId.value, selected.sequence, hostEpoch,
                )
                val evidenceIdentity = CommercialEvidence2DevReadScopeOwner.Identity(
                    principal, selected.generationId.value, selected.sequence, hostEpoch,
                )
                val household = Household10DevReadScopeOwner(
                    principal, selected.generationId.value, selected.sequence, hostEpoch,
                    currentIdentity = { householdIdentity.takeIf { selectedByHost() } },
                    ownerReady = ::selectedByHost,
                    source = {
                        // The ONLY source is this session's actual Reality store.
                        val rows = session.readNeeds(::selectedByHost)
                        HouseholdNeedsSnapshot(rows.revision, rows.needs
                            .filter { it.id == selectedNeed.value }.map { row ->
                                HouseholdNeed(
                                    HouseholdNeedId(row.id), HouseholdNeedName(row.name),
                                    HouseholdNeedQuantity(row.quantityThousandths.toLong(),
                                        HouseholdNeedUnit.valueOf(row.unit)),
                                    HouseholdNeedPriority.valueOf(row.priority),
                                    HouseholdNeedStatus.valueOf(row.status), row.revision,
                                    row.dueEpochDay?.let(LocalDate::ofEpochDay),
                                )
                            })
                    },
                )
                householdOwner = household
                val evidence = CommercialEvidence2DevReadScopeOwner(
                    principal, selected.generationId.value, selected.sequence, hostEpoch,
                    currentIdentity = { evidenceIdentity.takeIf { selectedByHost() } },
                    ownerReady = ::selectedByHost,
                    source = CommercialEvidenceStagedTypedPortabilityPort {
                        checkNotNull(session.typedOwnerSourceForTrustedDevDecision(
                            ::selectedByHost,
                        ))
                    },
                )
                evidenceOwner = evidence
                val householdNonce = checkNotNull(
                    household.issueAfterTrustedOwnerDecision(selectedNeed, ::selectedByHost)
                ) { "Household owner did not admit exact active selected need" }
                val commercialNonce = checkNotNull(
                    evidence.issueAfterTrustedOwnerDecision(selectedProduct, ::selectedByHost)
                ) { "Evidence owner did not admit exact active typed product" }
                check(selectedByHost())
                FaroV5Household10Evidence2DevSelectionReadPort(
                    pointer, selected, materialWriteGate, session, hostEpoch,
                    currentHostEpoch, isForeground, ownerAdmission,
                    householdGrant = { id -> runCatching {
                        household.admitted(HouseholdNeedId(id), householdNonce)
                    }.getOrDefault(false) },
                    commercialGrant = { key -> runCatching {
                        evidence.admitted(CommercialProductKey(key), commercialNonce)
                    }.getOrDefault(false) },
                    ownerScopeSqlAdmission = ::selectedByHost,
                    ownerScopes = object : AutoCloseable {
                        override fun close() {
                            runCatching { evidence.close() }
                            household.close()
                        }
                    },
                )
            } catch (failure: Throwable) {
                runCatching { evidenceOwner?.close() }
                runCatching { householdOwner?.close() }
                session.close()
                throw failure
            }
        }

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
