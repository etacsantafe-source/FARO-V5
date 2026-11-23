package ar.com.faro.v5.app

import android.content.pm.ApplicationInfo
import ar.com.faro.v5.evidence.CommercialEvidenceOperations
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.CommercialObservationComparisonStatus
import ar.com.faro.v5.generation.FaroV5Household10Evidence2DevSelectionReadPort
import ar.com.faro.v5.kernel.contracts.CapabilityId
import ar.com.faro.v5.kernel.contracts.ContractExposure
import ar.com.faro.v5.kernel.contracts.ExecutionProfile
import ar.com.faro.v5.kernel.contracts.ExecutorId
import ar.com.faro.v5.kernel.contracts.MaterialWriteGate
import ar.com.faro.v5.kernel.contracts.OperationContractLookupPort
import ar.com.faro.v5.kernel.contracts.OperationId
import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.FileActiveGenerationPointer
import ar.com.faro.v5.maintenance.StagedStorageGeneration
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import java.nio.file.Path

/**
 * F2C1B1B0: DEV-only composition of certified F2C0 single-leased read port,
 * F2C1A foreground gate, F2C1B0 typed projection and F2C1B1A two local
 * consent scopes, with TWO extra operation-specific owner read policies.
 *
 * The catalog is metadata only. Trusted policy callbacks MUST come from
 * the actual owner composition, never an Intent, model, Activity flag or UID.
 * This host does not create/approve an owner ACL or display commercial data.
 */
internal class FaroV5W04CommercialDevTrustedSelectedHost private constructor(
    private val activity: FaroV5W04CommercialDevActivity,
    private val issuer: FaroV5W04CommercialDevReadConsentIssuer,
    private val root: Path,
    private val pinned: ActiveGenerationPointer,
    private val needId: HouseholdNeedId,
    private val productKey: CommercialProductKey,
    private val principal: String,
    private val hostEpoch: String,
    private val currentHostEpoch: () -> String?,
    private val ownerReady: () -> Boolean,
    private val catalog: OperationContractLookupPort,
    private val householdPolicy: (String, OperationId, HouseholdNeedId) -> Boolean,
    private val commercialPolicy: (String, OperationId, CommercialProductKey) -> Boolean,
    private val gate: FaroV5W04CommercialDevForegroundSessionGate,
) : AutoCloseable {
    private val monitor = Any()
    private var closed = false

    /** A selected read is ALWAYS fresh; never return a cached snapshot or grant. */
    fun readProjectedOnce(
        explicitlyHumanSelected: () -> Boolean,
    ): S4W04SelectedActiveTypedReadProjection.Projected? = synchronized(monitor) {
        if (!admitted()) {
            closeLocked()
            return@synchronized null
        }
        val raw = runCatching {
            activity.readSelectedForTrustedHost(
                needId.value, productKey.value, explicitlyHumanSelected,
            )
        }.getOrNull()
        if (raw == null || !admitted()) {
            closeLocked()
            return@synchronized null
        }
        val projected = S4W04SelectedActiveTypedReadProjection.project(
            read = raw,
            expected = pinned,
            needId = needId,
            productKey = productKey,
            principal = principal,
            hostEpoch = hostEpoch,
            currentGeneration = { FileActiveGenerationPointer(root).read() },
            admitted = ::admitted,
        )
        if (projected == null || !admitted()) {
            closeLocked()
            return@synchronized null
        }
        projected
    }

    /**
     * F2C1B1B1B1B: mount a native chooser from the real ACTIVE single-lease
     * F1 read; only a native historical observation Button click can trigger
     * the already-certified D1..D4/E historical coordinator.
     * Never choose lowest observed / current price implicitly.
     */
    fun presentNativeHistoricalChoicesForTrustedDevHost(
        asOfEpochMillis: Long,
        maxObservationAgeMillis: Long,
        requestedPackCount: Int,
        readNow: () -> Long? = { System.currentTimeMillis() },
    ): Boolean = synchronized(monitor) {
        if (closed || requestedPackCount !in 1..10_000 ||
            asOfEpochMillis < 0L ||
            maxObservationAgeMillis !in 1L..365L * 24L * 60L * 60L * 1000L
        ) return@synchronized false

        // The two native READ consents already exist in the attached issuer.
        // The fresh source port checks both independent REAL owner grants.
        fun fresh() = readProjectedOnce {
            issuer.householdConsented(needId.value) &&
                issuer.commercialConsented(productKey.value)
        }
        val initial = fresh() ?: return@synchronized false
        val selected = S4W04CommercialNeedProductSelection.select(
            initial.household, needId, productKey, listOf(productKey),
            initial.fence, explicitlyChosen = true,
            isOwnerAdmitted = { fresh() != null },
        ) as? CommercialNeedProductDecision.Candidate ?: return@synchronized false
        val draft = (S4W04CommercialCartDraft.build(
            initial.household, initial.fence,
            listOf(S4W04CommercialCartDraft.PackChoice(
                selected.binding, requestedPackCount,
            )),
            isOwnerAdmitted = { fresh() != null },
        ) as? S4W04CommercialCartDraft.Decision.Draft)?.cartDraft
            ?: return@synchronized false

        fun admittedNeed(id: HouseholdNeedId) = id == needId && fresh() != null
        fun admittedProduct(key: CommercialProductKey) =
            key == productKey && fresh() != null
        fun coverage() = S4W04CommercialCartCoverage(
            currentHouseholdSnapshot = { fresh()?.household },
            currentFence = { fresh()?.fence },
            currentCommercialSnapshot = { fresh()?.evidence },
            householdAdmission = ::admittedNeed,
            commercialReadAdmission = ::admittedProduct,
        )
        val view = (coverage().project(draft, asOfEpochMillis,
            maxObservationAgeMillis) as? CommercialCartCoverageDecision.Observed)?.view
            ?: return@synchronized false
        val line = view.lines.singleOrNull {
            it.needId == needId && it.productKey == productKey
        } ?: return@synchronized false
        val entries = line.history.filter {
            it.comparisonStatus ==
                CommercialObservationComparisonStatus.COMPARABLE_BASE_OBSERVED &&
                it.item.terms != null
        }
        if (entries.isEmpty() || entries.size > 200) return@synchronized false

        activity.reviewPanel.renderExplicitHistoricalChoices(
            entries = entries,
            authorizedNow = { fresh() != null },
            onSelected = { tapped ->
                synchronized(monitor) {
                    if (closed || tapped !in entries || fresh() == null) {
                        activity.reviewPanel.clearForRevocation()
                        return@synchronized
                    }
                    // The exact record was selected by the native Button, not
                    // by a strategy, model, price sort or Activity Intent.
                    val choice = S4W04ObservedCartBudget.ExplicitChoice(
                        needId = needId,
                        productKey = productKey.value,
                        selectedObservation = tapped,
                        cartFingerprint = view.cartFingerprint,
                        sourceWatermark = view.sourceWatermark,
                        asOfEpochMillis = view.asOfEpochMillis,
                    )
                    val decision = S4W04HistoricalAuthorizedReadCoordinator(
                        currentHousehold = { fresh()?.household },
                        currentFence = { fresh()?.fence },
                        currentEvidence = { fresh()?.evidence },
                        householdAdmission = ::admittedNeed,
                        evidenceAdmission = ::admittedProduct,
                        readNow = readNow,
                    ).project(draft, listOf(choice), asOfEpochMillis,
                        maxObservationAgeMillis)
                    val historical =
                        (decision as? S4W04HistoricalAuthorizedReadCoordinator.Decision.Historical)
                            ?.display
                    activity.reviewPanel.renderAuthorized(historical) {
                        fresh() != null && !closed
                    }
                }
            },
        )
    }

    fun isClosed(): Boolean = synchronized(monitor) { closed || gate.isRevoked() }

    override fun close() = synchronized(monitor) { closeLocked() }

    private fun closeLocked() {
        if (!closed) {
            closed = true
            gate.revoke() // F2C1A closes the same F2C0 root OS lease.
            activity.reviewPanel.clearForRevocation() // No stale D4/E on host close.
        }
    }

    private fun admitted(): Boolean = runCatching {
        !closed && !gate.isRevoked() && ownerCurrent() &&
            readCatalogExact(catalog) &&
            householdPolicy(principal, HOUSEHOLD_READ, needId) &&
            commercialPolicy(principal, COMMERCIAL_READ, productKey) &&
            ownerCurrent()
    }.getOrDefault(false)

    private fun ownerCurrent(): Boolean = runCatching {
        activity.isForegroundForTrustedRead() &&
            !activity.isFinishing && !activity.isDestroyed &&
            !FaroV5HostCertification.V2_ACTIVE_ENABLED &&
            currentHostEpoch() == hostEpoch && ownerReady() &&
            issuer.technicalPrincipalOrNull() == principal &&
            issuer.selectedSourceBindingAdmitted(root, pinned, needId.value, productKey.value) &&
            currentHostEpoch() == hostEpoch && ownerReady()
    }.getOrDefault(false)

    companion object {
        private val HOUSEHOLD_READ = OperationId("shopping.snapshot.read")
        private val COMMERCIAL_READ = CommercialEvidenceOperations.PRICE_HISTORY_READ

        private fun readCatalogExact(catalog: OperationContractLookupPort): Boolean = runCatching {
            val need = catalog.current(HOUSEHOLD_READ)
            val commercial = catalog.current(COMMERCIAL_READ)
            need != null && commercial != null &&
                need.operationId == HOUSEHOLD_READ &&
                need.capabilityId == CapabilityId("N-033") &&
                need.canonicalExecutor == ExecutorId("household") &&
                need.executionProfile == ExecutionProfile.READ_ONLY &&
                need.exposure == ContractExposure.BINDABLE &&
                commercial.operationId == COMMERCIAL_READ &&
                commercial.capabilityId == CommercialEvidenceOperations.CAPABILITY_ID &&
                commercial.canonicalExecutor == CommercialEvidenceOperations.EXECUTOR_ID &&
                commercial.executionProfile == ExecutionProfile.READ_ONLY &&
                commercial.exposure == ContractExposure.BINDABLE
        }.getOrDefault(false)


        /**
         * F2C1B1B1B1A: POSITIVE owner-backed Android DEV bridge. It never
         * accepts owner policy predicates from callers: both owner nonces are
         * issued and rechecked by the REAL Household10/Evidence2 ACTIVE port
         * opened under one root lease. Routing predicates below merely check
         * identity and catalog and are NOT substitutes for owner authority.
         *
         * No automatic mount on Activity launch; native buttons must already
         * be attached, visible, separately clicked, and admitted by the
         * existing Android-sandbox consent issuer.
         */
        fun openOwnerBackedForTrustedDevHost(
            activity: FaroV5W04CommercialDevActivity,
            issuer: FaroV5W04CommercialDevReadConsentIssuer,
            storageRoot: Path,
            staged: StagedStorageGeneration,
            pinned: ActiveGenerationPointer,
            needId: HouseholdNeedId,
            productKey: CommercialProductKey,
            materialWriteGate: MaterialWriteGate,
            catalog: OperationContractLookupPort,
            hostEpoch: String,
            currentHostEpoch: () -> String?,
            ownerReady: () -> Boolean,
        ): FaroV5W04CommercialDevTrustedSelectedHost? {
            var port: FaroV5Household10Evidence2DevSelectionReadPort? = null
            var foregroundGate: FaroV5W04CommercialDevForegroundSessionGate? = null
            return try {
                check(activity.packageName == "ar.com.faro.alpha.w04commercialdev")
                check(activity.application is FaroV5W04CommercialDevApplication)
                check((activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                check(activity.isForegroundForTrustedRead() && !activity.isFinishing &&
                    !activity.isDestroyed && !FaroV5HostCertification.V2_ACTIVE_ENABLED)
                check(hostEpoch.length in 1..160 && hostEpoch == hostEpoch.trim() &&
                    hostEpoch.none(Char::isISOControl))
                val principal = checkNotNull(issuer.technicalPrincipalOrNull())
                fun hostCurrent(): Boolean = runCatching {
                    activity.isForegroundForTrustedRead() && !activity.isFinishing &&
                        !activity.isDestroyed && !FaroV5HostCertification.V2_ACTIVE_ENABLED &&
                        ownerReady() && currentHostEpoch() == hostEpoch &&
                        issuer.technicalPrincipalOrNull() == principal &&
                        issuer.selectedSourceBindingAdmitted(
                            storageRoot, pinned, needId.value, productKey.value,
                        ) &&
                        currentHostEpoch() == hostEpoch && ownerReady() &&
                        activity.isForegroundForTrustedRead()
                }.getOrDefault(false)
                fun catalogCurrent(): Boolean = readCatalogExact(catalog)
                // Two separate native taps are checked again inside each
                // generation owner policy before and after every source read.
                fun selectedNow(): Boolean = runCatching {
                    hostCurrent() && catalogCurrent() &&
                        issuer.householdConsented(needId.value) &&
                        issuer.commercialConsented(productKey.value) &&
                        hostCurrent() && catalogCurrent()
                }.getOrDefault(false)
                check(selectedNow()) { "Two native DEV read consents required" }
                // Routing is deliberately not an owner grant: the real scopes
                // are verified by openOwnerBackedDev/readSelected on the source.
                val needRouting = fun(id: String): Boolean =
                    id == needId.value && hostCurrent() && catalogCurrent()
                val commerceRouting = fun(key: String): Boolean =
                    key == productKey.value && hostCurrent() && catalogCurrent()
                val selectedPort = FaroV5Household10Evidence2DevSelectionReadPort
                    .openOwnerBackedDev(
                        storageRoot = storageRoot,
                        staged = staged,
                        selected = pinned,
                        materialWriteGate = materialWriteGate,
                        principal = principal,
                        selectedNeed = needId,
                        selectedProduct = productKey,
                        hostEpoch = hostEpoch,
                        currentHostEpoch = currentHostEpoch,
                        isForeground = { activity.isForegroundForTrustedRead() },
                        ownerAdmission = { hostCurrent() && catalogCurrent() },
                        trustedHumanDecision = ::selectedNow,
                    )
                port = selectedPort
                val gate = FaroV5W04CommercialDevForegroundSessionGate.fromCertifiedDevPort(
                    selectedPort, hostEpoch, currentHostEpoch,
                    { activity.isForegroundForTrustedRead() },
                    { hostCurrent() && catalogCurrent() },
                    needRouting, commerceRouting,
                )
                foregroundGate = gate
                check(activity.attachTrustedGate(gate)) { "DEV foreground source already mounted" }
                val selectedHost = FaroV5W04CommercialDevTrustedSelectedHost(
                    activity, issuer, storageRoot, pinned, needId, productKey,
                    principal, hostEpoch, currentHostEpoch, ownerReady, catalog,
                    { candidate, op, id ->
                        candidate == principal && op == HOUSEHOLD_READ &&
                            id == needId && needRouting(id.value)
                    },
                    { candidate, op, key ->
                        candidate == principal && op == COMMERCIAL_READ &&
                            key == productKey && commerceRouting(key.value)
                    },
                    gate,
                )
                check(selectedHost.admitted()) { "DEV source lost its host admission" }
                selectedHost
            } catch (_: Exception) {
                runCatching { foregroundGate?.revoke() }
                if (foregroundGate == null) runCatching { port?.close() }
                // A failed positive mount must not leave a reusable tap token.
                runCatching { issuer.close() }
                null
            }
        }

        /**
         * No boot/auto mount: only trusted DEV composition AFTER two real local
         * taps and two independent owner-policy admissions may call this.
         * The source must be an actual canonical Household10/Evidence2 ACTIVE
         * staged generation; F2C0 validates schema/pointer/root lease.
         */
        fun openForTrustedDevHost(
            activity: FaroV5W04CommercialDevActivity,
            issuer: FaroV5W04CommercialDevReadConsentIssuer,
            storageRoot: Path,
            staged: StagedStorageGeneration,
            pinned: ActiveGenerationPointer,
            needId: HouseholdNeedId,
            productKey: CommercialProductKey,
            materialWriteGate: MaterialWriteGate,
            catalog: OperationContractLookupPort,
            hostEpoch: String,
            currentHostEpoch: () -> String?,
            ownerReady: () -> Boolean,
            householdOwnerReadPolicy: (String, OperationId, HouseholdNeedId) -> Boolean,
            commercialOwnerReadPolicy: (String, OperationId, CommercialProductKey) -> Boolean,
        ): FaroV5W04CommercialDevTrustedSelectedHost? {
            var port: FaroV5Household10Evidence2DevSelectionReadPort? = null
            var foregroundGate: FaroV5W04CommercialDevForegroundSessionGate? = null
            return try {
                check(activity.isForegroundForTrustedRead() && !activity.isFinishing &&
                    !activity.isDestroyed && !FaroV5HostCertification.V2_ACTIVE_ENABLED)
                check(hostEpoch.length in 1..160 && hostEpoch == hostEpoch.trim() &&
                    hostEpoch.none(Char::isISOControl))
                val principal = checkNotNull(issuer.technicalPrincipalOrNull())
                check(issuer.selectedSourceBindingAdmitted(
                    storageRoot, pinned, needId.value, productKey.value,
                ))
                val host = fun(): Boolean = runCatching {
                    activity.isForegroundForTrustedRead() &&
                        currentHostEpoch() == hostEpoch && ownerReady() &&
                        issuer.technicalPrincipalOrNull() == principal &&
                        issuer.selectedSourceBindingAdmitted(
                            storageRoot, pinned, needId.value, productKey.value,
                        )
                }.getOrDefault(false)
                val needGrant = fun(id: String): Boolean = runCatching {
                    id == needId.value && host() && readCatalogExact(catalog) &&
                        householdOwnerReadPolicy(principal, HOUSEHOLD_READ, needId) &&
                        host()
                }.getOrDefault(false)
                val commerceGrant = fun(key: String): Boolean = runCatching {
                    key == productKey.value && host() && readCatalogExact(catalog) &&
                        commercialOwnerReadPolicy(principal, COMMERCIAL_READ, productKey) &&
                        host()
                }.getOrDefault(false)

                check(host() && needGrant(needId.value) && commerceGrant(productKey.value) && host()) {
                    "Selected DEV source needs both owner policies"
                }
                val selected = FaroV5Household10Evidence2DevSelectionReadPort.open(
                    storageRoot = storageRoot,
                    staged = staged,
                    selected = pinned,
                    materialWriteGate = materialWriteGate,
                    hostEpoch = hostEpoch,
                    currentHostEpoch = currentHostEpoch,
                    isForeground = { activity.isForegroundForTrustedRead() },
                    ownerAdmission = host,
                    householdGrant = needGrant,
                    commercialGrant = commerceGrant,
                )
                port = selected
                val session = FaroV5W04CommercialDevForegroundSessionGate.fromCertifiedDevPort(
                    selected, hostEpoch, currentHostEpoch,
                    { activity.isForegroundForTrustedRead() },
                    host, needGrant, commerceGrant,
                )
                foregroundGate = session
                check(activity.attachTrustedGate(session)) {
                    "Selected DEV foreground gate already attached"
                }
                val mounted = FaroV5W04CommercialDevTrustedSelectedHost(
                    activity, issuer, storageRoot, pinned, needId, productKey,
                    principal, hostEpoch, currentHostEpoch, ownerReady, catalog,
                    householdOwnerReadPolicy, commercialOwnerReadPolicy, session,
                )
                check(mounted.admitted()) { "Selected DEV owner admission changed after mount" }
                mounted
            } catch (_: Exception) {
                // close gate first: it owns the port and exact same root lease.
                runCatching { foregroundGate?.revoke() }
                if (foregroundGate == null) runCatching { port?.close() }
                null
            }
        }
    }
}
