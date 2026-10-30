package ar.com.faro.v5.kernel.runtime

import ar.com.faro.v5.kernel.contracts.ModelProfile
import ar.com.faro.v5.kernel.contracts.ModelProfileId
import ar.com.faro.v5.kernel.contracts.ModelProfileRef
import ar.com.faro.v5.kernel.contracts.ModelProfileRegistry
import ar.com.faro.v5.kernel.contracts.ModelProfileVersion

class ImmutableModelProfileRegistry private constructor(
    private val profilesByRef: Map<ModelProfileRef, ModelProfile>,
    private val versionsById: Map<ModelProfileId, List<ModelProfileVersion>>,
) : ModelProfileRegistry {
    override fun resolve(ref: ModelProfileRef): ModelProfile? =
        profilesByRef[ref]

    override fun versions(profileId: ModelProfileId): List<ModelProfileVersion> =
        versionsById[profileId].orEmpty()

    companion object {
        fun of(profiles: Iterable<ModelProfile>): ImmutableModelProfileRegistry {
            val byRef = LinkedHashMap<ModelProfileRef, ModelProfile>()
            profiles.forEach { profile ->
                require(byRef.putIfAbsent(profile.ref, profile) == null) {
                    "duplicate model profile ref: " +
                        profile.ref.profileId.value + "@" + profile.ref.version.value
                }
            }

            val versions = byRef.keys
                .groupBy { it.profileId }
                .mapValues { (_, refs) ->
                    refs.map { it.version }
                        .distinct()
                        .sortedBy { it.value }
                }

            return ImmutableModelProfileRegistry(
                profilesByRef = byRef.toMap(),
                versionsById = versions,
            )
        }
    }
}
