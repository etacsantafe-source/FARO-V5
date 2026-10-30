package ar.com.faro.v5.owner.profile.api

import ar.com.faro.v5.kernel.contracts.AuthorityBoundaryId
import ar.com.faro.v5.kernel.contracts.DependencyKey
import ar.com.faro.v5.kernel.contracts.DependencyStamp
import ar.com.faro.v5.kernel.contracts.DependencyStampValue

@JvmInline
value class DeclaredDisplayName private constructor(
    val value: String,
) {
    companion object {
        const val MAX_LENGTH: Int = 80

        fun of(raw: String): DeclaredDisplayName {
            val normalized = raw.trim()
            require(normalized.isNotEmpty()) {
                "declared display name must not be blank"
            }
            require(normalized.length <= MAX_LENGTH) {
                "declared display name must be at most $MAX_LENGTH characters"
            }
            return DeclaredDisplayName(normalized)
        }
    }
}

enum class ProfileFieldProvenance {
    DECLARED_BY_USER,
}

data class ProfileSnapshot(
    val displayName: DeclaredDisplayName?,
    val revision: Long,
    val provenance: ProfileFieldProvenance?,
) {
    init {
        require(revision >= 0L) { "profile revision must be non-negative" }
        require((displayName == null) == (provenance == null)) {
            "profile display name and provenance must be present together"
        }
    }

    fun dependencyStamp(): DependencyStamp =
        DependencyStamp(
            authorityBoundary = PROFILE_AUTHORITY,
            dependencyKey = PROFILE_DECLARED_KEY,
            observedValue = DependencyStampValue("revision:$revision"),
        )

    companion object {
        val PROFILE_AUTHORITY = AuthorityBoundaryId("reality.profile")
        val PROFILE_DECLARED_KEY = DependencyKey("profile:declared")
    }
}

fun interface ProfileReadApi {
    fun read(): ProfileSnapshot
}
