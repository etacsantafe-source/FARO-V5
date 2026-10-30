package ar.com.faro.v5.kernel.contracts

/**
 * The Workspace SQLite owner cannot decode or trust an ApplyPlan. A trusted
 * kernel:runtime adapter verifies the *entire* serialized plan and matches
 * the exact PlanFingerprint BEFORE Workspace can accept or expose it.
 *
 * The default staged store has no verifier and refuses all plan admission.
 */
fun interface PreparedPlanEnvelopeVerificationPort {
    fun verify(envelope: ByteArray, expectedFingerprint: PlanFingerprint): Boolean
}
