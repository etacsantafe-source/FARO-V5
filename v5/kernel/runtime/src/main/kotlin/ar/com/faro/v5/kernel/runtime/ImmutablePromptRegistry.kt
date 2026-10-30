package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.PromptArtifact
import ar.com.faro.v5.kernel.contracts.PromptId
import ar.com.faro.v5.kernel.contracts.PromptRef
import ar.com.faro.v5.kernel.contracts.PromptRegistry
import ar.com.faro.v5.kernel.contracts.PromptVersion

class ImmutablePromptRegistry private constructor(
    private val artifactsByRef: Map<PromptRef, PromptArtifact>,
    private val versionsById: Map<PromptId, List<PromptVersion>>,
) : PromptRegistry {
    override fun resolve(ref: PromptRef): PromptArtifact? =
        artifactsByRef[ref]

    override fun versions(promptId: PromptId): List<PromptVersion> =
        versionsById[promptId].orEmpty()

    companion object {
        fun of(artifacts: Iterable<PromptArtifact>): ImmutablePromptRegistry {
            val byRef = LinkedHashMap<PromptRef, PromptArtifact>()
            artifacts.forEach { artifact ->
                require(byRef.putIfAbsent(artifact.ref, artifact) == null) {
                    "duplicate prompt ref: " +
                        artifact.ref.promptId.value + "@" + artifact.ref.version.value
                }
            }

            val versions = byRef.keys
                .groupBy { it.promptId }
                .mapValues { (_, refs) ->
                    refs.map { it.version }
                        .distinct()
                        .sortedBy { it.value }
                }

            return ImmutablePromptRegistry(
                artifactsByRef = byRef.toMap(),
                versionsById = versions,
            )
        }
    }
}
