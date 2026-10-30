package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ApplyPlan
import ar.com.faro.v5.kernel.contracts.ApplyPlanNode
import ar.com.faro.v5.kernel.contracts.PlanAuthorizationRef
import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.kernel.contracts.PreparedPlanView

/**
 * Immutable Preparation output handed to review/Apply.
 *
 * Plan and fingerprint are created together so callers cannot pair an
 * ApplyPlan with an unrelated PlanFingerprint.
 */
class PreparedPlan private constructor(
    override val applyPlan: ApplyPlan,
    override val planFingerprint: PlanFingerprint,
) : PreparedPlanView {
    val authorizationRef: PlanAuthorizationRef
        get() = PlanAuthorizationRef(planFingerprint)

    override fun toString(): String =
        "PreparedPlan(nodes=" + applyPlan.topologicalOrder.size +
            ", planFingerprint=" + planFingerprint.value + ")"

    companion object {
        fun of(vararg nodes: ApplyPlanNode): PreparedPlan {
            val plan = ApplyPlan.of(*nodes)
            return PreparedPlan(
                applyPlan = plan,
                planFingerprint = PlanFingerprinter.fingerprint(plan),
            )
        }
    }
}
