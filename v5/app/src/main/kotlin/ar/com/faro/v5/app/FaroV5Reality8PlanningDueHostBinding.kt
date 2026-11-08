package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.reality.RealityStore
import java.time.Instant
import java.time.ZoneId

/**
 * Future Android host MUST supply authenticated local principal and a
 * separately authorized, revocable per-domain read policy. Null is DENY.
 * This is NOT the Planning8 object WRITE grant or a Search read token.
 */
internal fun interface Planning8TrustedDueReadPolicyPort {
    fun epochOrNull(
        principalId: String,
        domain: FaroV5PlanningDueDomain,
        pointer: ActiveGenerationPointer,
    ): String?
}

/**
 * N031 dormant JVM-only binding of REAL Reality8 SQL owners to the previously
 * B7-tested due projection. No Activity/Chat/Surface/CompositionRoot caller.
 * Technical host principal and test fixtures are NOT user authentication.
 *
 * The read policy is independent from any Apply/Undo grants. A missing real
 * production policy makes EVERY read null; never display a synthetic zero.
 */
internal class FaroV5Reality8PlanningDueHostBinding(
    private val generationId: String,
    private val pinned: ActiveGenerationPointer,
    private val activePointer: ActiveGenerationPointerPort,
    private val reality: RealityStore,
    private val principalId: String,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
    private val readPolicy: Planning8TrustedDueReadPolicyPort? = null,
) : AutoCloseable {
    @Volatile private var closed: Boolean = false

    init {
        require(generationId.isNotBlank() && pinned.generationId.value == generationId)
        require(principalId.isNotBlank() && principalId == principalId.trim() &&
            principalId.length <= 160 && principalId.none { it.code < 32 || it.code == 127 })
        require(expectedHostEpoch.isNotBlank())
        check(activePointer.read() == pinned) {
            "N031 host cannot bind a stale generation"
        }
    }

    /** Only canonical Reality8 sources; never read through the backup API. */
    private val sources = reality.planningDueReadOwnersForTrustedHost()

    private fun hostCurrent(): Boolean = runCatching {
        !closed && hostReady() && activePointer.read() == pinned &&
            hostEpochOrNull() == expectedHostEpoch
    }.getOrDefault(false)

    private fun policyEpoch(domain: FaroV5PlanningDueDomain): String? = runCatching {
        if (!hostCurrent()) return@runCatching null
        val epoch = readPolicy?.epochOrNull(principalId, domain, pinned)
            ?.takeIf { it.isNotBlank() && it == it.trim() && it.length <= 160 }
        if (hostCurrent()) epoch else null
    }.getOrNull()

    private val projection = FaroV5RealityPlanningDueReadSource(
        expectedGenerationId = generationId,
        reminders = sources.reminders,
        monthlyPlan = sources.monthlyPlan,
        currentGenerationId = { activePointer.read()?.generationId?.value },
        pointerCurrent = ::hostCurrent,
        pointerEpoch = {
            if (hostCurrent()) pinned.generationId.value + ":" + pinned.sequence else null
        },
        reminderAclEpoch = { policyEpoch(FaroV5PlanningDueDomain.REMINDER_OCCURRENCE) },
        monthlyPlanAclEpoch = { policyEpoch(FaroV5PlanningDueDomain.MONTHLY_PLAN_ITEM) },
        reminderReadAdmitted = {
            policyEpoch(FaroV5PlanningDueDomain.REMINDER_OCCURRENCE) != null
        },
        monthlyPlanReadAdmitted = {
            policyEpoch(FaroV5PlanningDueDomain.MONTHLY_PLAN_ITEM) != null
        },
    )

    fun readAuthorized(
        now: Instant,
        zone: ZoneId,
        limit: Int = 50,
    ): FaroV5PlanningDuePage? = projection.readAuthorized(now, zone, limit)

    override fun close() {
        closed = true
    }
}
