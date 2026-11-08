package ar.com.faro.v5.app

import ar.com.faro.v5.maintenance.ActiveGenerationPointer
import ar.com.faro.v5.maintenance.ActiveGenerationPointerPort
import ar.com.faro.v5.store.reality.RealityStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/** Source-tagged due facts; a domain-local ID must NEVER masquerade as global. */
internal enum class FaroV5N031DueSource {
    MATTER_DUE, REMINDER_OCCURRENCE, MONTHLY_PLAN_ITEM,
}

internal data class FaroV5N031DueFact(
    val source: FaroV5N031DueSource,
    val opaqueId: String,
    val dueOn: LocalDate,
    val dueAtEpochMillis: Long?,
    val provenance: String,
) {
    init {
        require(opaqueId.isNotBlank() && provenance.isNotBlank())
        require(dueAtEpochMillis == null || dueAtEpochMillis >= 0L)
    }
}

internal data class FaroV5N031ThreeOwnerDuePage(
    val items: List<FaroV5N031DueFact>,
    val matterRevision: Long,
    val reminderRevision: Long,
    val monthlyPlanRevision: Long,
    val completeWithinStoredDues: Boolean,
) {
    val canonicalSources: Set<String> get() = setOf(
        "reality.matter_record",
        "reality.reminder_occurrence",
        "reality.monthly_plan_item",
    )
    /** Never a global Home Pendientes total or a recurrence/priority engine. */
    val globalAttentionComplete: Boolean get() = false
    val recurrenceExpanded: Boolean get() = false
    val priorityOrSnoozeInferred: Boolean get() = false
    val crossOwnerAtomicSnapshot: Boolean get() = false
    val coverage: String get() = "REALITY8_PERSISTED_THREE_OWNER_DUES_ONLY"
}

/**
 * A fail-closed read-only aggregation on ONE real schema8 Reality owner graph.
 * Reuses the previously certified Matter and two Planning host read projections.
 * Independent per-domain local read grants must exist BEFORE any owner read;
 * they are rechecked across BOTH sequential snapshots and once at return.
 *
 * This is not a new table, reminder scheduler, account identity or Home count.
 * Not mounted in shipping/Android until a separately audited trusted UI host.
 */
internal class FaroV5N031ThreeOwnerDueHostBinding(
    private val generationId: String,
    private val pinned: ActiveGenerationPointer,
    private val activePointer: ActiveGenerationPointerPort,
    private val reality: RealityStore,
    private val principalId: String,
    private val expectedHostEpoch: String,
    private val hostEpochOrNull: () -> String?,
    private val hostReady: () -> Boolean,
    private val readAuthority: FaroV5LocalInstallReadAuthority? = null,
) : AutoCloseable {
    @Volatile private var closed = false

    init {
        require(generationId.isNotBlank() && pinned.generationId.value == generationId)
        require(principalId.isNotBlank() && expectedHostEpoch.isNotBlank())
        check(activePointer.read() == pinned) { "N031 host requires exact pinned pointer" }
    }

    private fun hostCurrent(): Boolean = try {
        !closed && hostReady() && activePointer.read() == pinned &&
            hostEpochOrNull() == expectedHostEpoch
    } catch (_: Exception) { false }

    private fun domainEpoch(domain: FaroV5LocalReadDomain): String? = try {
        if (!hostCurrent() || readAuthority?.technicalPrincipalOrNull() != principalId) null
        else readAuthority.epochOrNull(principalId, domain, pinned)
            ?.takeIf { it.isNotBlank() && hostCurrent() }
    } catch (_: Exception) { null }

    private val planning = FaroV5Reality8PlanningDueHostBinding(
        generationId = generationId,
        pinned = pinned,
        activePointer = activePointer,
        reality = reality,
        principalId = principalId,
        expectedHostEpoch = expectedHostEpoch,
        hostEpochOrNull = hostEpochOrNull,
        hostReady = ::hostCurrent,
        readPolicy = readAuthority?.planningDueReadPolicy(),
    )

    private val matter = FaroV5RealityMatterDueReadSource(
        expectedGenerationId = generationId,
        matters = reality.matterPersistence,
        currentGenerationId = { activePointer.read()?.generationId?.value },
        pointerCurrent = ::hostCurrent,
        pointerEpoch = {
            if (hostCurrent()) pinned.generationId.value + ":" + pinned.sequence else null
        },
        aclEpoch = { domainEpoch(FaroV5LocalReadDomain.MATTER_DUE) },
        readAdmitted = { domainEpoch(FaroV5LocalReadDomain.MATTER_DUE) != null },
    )

    private data class Admission(
        val matter: String, val reminder: String, val monthlyPlan: String,
    )

    private fun admission(): Admission? {
        if (!hostCurrent()) return null
        val m = domainEpoch(FaroV5LocalReadDomain.MATTER_DUE) ?: return null
        val r = domainEpoch(FaroV5LocalReadDomain.REMINDER_OCCURRENCE) ?: return null
        val p = domainEpoch(FaroV5LocalReadDomain.MONTHLY_PLAN_ITEM) ?: return null
        return if (hostCurrent() &&
            domainEpoch(FaroV5LocalReadDomain.MATTER_DUE) == m &&
            domainEpoch(FaroV5LocalReadDomain.REMINDER_OCCURRENCE) == r &&
            domainEpoch(FaroV5LocalReadDomain.MONTHLY_PLAN_ITEM) == p
        ) Admission(m, r, p) else null
    }

    fun readAuthorized(
        now: Instant, zone: ZoneId, limit: Int = 50,
    ): FaroV5N031ThreeOwnerDuePage? {
        require(limit in 1..100)
        val grant = admission() ?: return null
        val matterPage = matter.readAuthorized(now, zone, limit) ?: return null
        if (admission() != grant) return null
        val planningPage = planning.readAuthorized(now, zone, limit) ?: return null
        if (admission() != grant) return null
        return try {
            val matterFacts = matterPage.items.map { item ->
                FaroV5N031DueFact(
                    source = FaroV5N031DueSource.MATTER_DUE,
                    opaqueId = item.matterId,
                    dueOn = Instant.ofEpochMilli(item.dueAtEpochMillis)
                        .atZone(zone).toLocalDate(),
                    dueAtEpochMillis = item.dueAtEpochMillis,
                    provenance = matterPage.dueTimeProvenance,
                )
            }
            val planningFacts = planningPage.items.map { item ->
                val kind = when (item.domain) {
                    FaroV5PlanningDueDomain.REMINDER_OCCURRENCE ->
                        FaroV5N031DueSource.REMINDER_OCCURRENCE
                    FaroV5PlanningDueDomain.MONTHLY_PLAN_ITEM ->
                        FaroV5N031DueSource.MONTHLY_PLAN_ITEM
                }
                FaroV5N031DueFact(
                    source = kind,
                    opaqueId = item.opaqueId,
                    dueOn = item.dueOn,
                    dueAtEpochMillis = item.dueAtEpochMillis,
                    provenance = item.timeProvenance,
                )
            }
            val all = (matterFacts + planningFacts)
                .sortedWith(compareBy<FaroV5N031DueFact> { it.dueOn }
                    .thenBy { it.dueAtEpochMillis ?: Long.MAX_VALUE }
                    .thenBy { it.source.ordinal }
                    .thenBy { it.opaqueId })
            if (admission() != grant) null else FaroV5N031ThreeOwnerDuePage(
                items = all.take(limit),
                matterRevision = matterPage.ownerRevision,
                reminderRevision = planningPage.reminderRevision,
                monthlyPlanRevision = planningPage.monthlyPlanRevision,
                completeWithinStoredDues =
                    matterPage.completeWithinMatterDue &&
                    planningPage.completeWithinStoredDues &&
                    all.size <= limit,
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        closed = true
        planning.close()
    }
}
