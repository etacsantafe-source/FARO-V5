package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.DependencyStampSet

/**
 * Owner/boundary-supplied capture of only the dependencies material to
 * preparation of a typed input.
 *
 * The runtime does not invent a global revision, timestamp, or registry
 * revision as a substitute for semantic dependency stamps.
 */
fun interface DependencyStampSource<I : Any> {
    fun capture(input: I): DependencyStampSet
}
