package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.PlanFingerprint
import ar.com.faro.v5.kernel.contracts.PreparedPlanEnvelopeVerificationPort

/**
 * Canonical validation boundary for the Workspace plan owner.
 * The decoder reconstructs nodes, payloads, contracts, stamps and DAG,
 * checks the envelope fingerprint and canonical encoding. This port is
 * verification-only: it does not authorize a plan or execute an effect.
 */
object CanonicalPreparedPlanEnvelopeVerifier : PreparedPlanEnvelopeVerificationPort {
    override fun verify(envelope: ByteArray, expectedFingerprint: PlanFingerprint): Boolean =
        runCatching {
            PreparedPlanEnvelopeCodec.decode(envelope).planFingerprint == expectedFingerprint
        }.getOrDefault(false)
}
