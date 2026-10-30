package ar.com.faro.v5.maintenance

@JvmInline
value class UpdateOperationId(val value: String) {
    init {
        require(value.isNotBlank()) { "update operation id must not be blank" }
        require(value == value.trim()) { "update operation id must be canonical" }
        require(value.length <= 160) { "update operation id is too long" }
    }
}

@JvmInline
value class ExpectedInstallId(val value: String) {
    init {
        require(value.isNotBlank()) { "expected install id must not be blank" }
        require(value == value.trim()) { "expected install id must be canonical" }
        require(value.length <= 160) { "expected install id is too long" }
    }
}

@JvmInline
value class StagedUpdateArtifactId(val value: String) {
    init {
        require(value.isNotBlank()) { "staged update artifact id must not be blank" }
        require(value == value.trim()) { "staged update artifact id must be canonical" }
        require(value.length <= 160) { "staged update artifact id is too long" }
    }
}

@JvmInline
value class UpdateSha256(val value: String) {
    init {
        require(value.matches(Regex("[0-9a-f]{64}"))) {
            "update SHA-256 must be lowercase hexadecimal"
        }
    }
}

sealed interface UpdateSigningIdentity {
    data class Lineage(
        val certificatesSha256: List<UpdateSha256>,
    ) : UpdateSigningIdentity {
        init {
            require(certificatesSha256.isNotEmpty()) {
                "signing lineage must contain at least one certificate"
            }
            require(certificatesSha256.distinct().size == certificatesSha256.size) {
                "signing lineage cannot contain duplicate certificates"
            }
        }

        val currentCertificateSha256: UpdateSha256
            get() = certificatesSha256.last()
    }

    data class MultipleSigners(
        val certificatesSha256: Set<UpdateSha256>,
    ) : UpdateSigningIdentity {
        init {
            require(certificatesSha256.size >= 2) {
                "multiple-signers identity requires at least two certificates"
            }
        }
    }
}

data class InstalledUpdateIdentity(
    val packageName: String,
    val versionCode: Long,
    val signingIdentity: UpdateSigningIdentity,
) {
    init {
        requirePackageName(packageName)
        require(versionCode > 0L) { "installed update versionCode must be positive" }
    }
}

data class UpdateManifestCandidate(
    val sourceIdentity: String,
    val channel: String,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minimumSourceVersionCode: Long,
    val artifactUri: String,
    val artifactSha256: UpdateSha256,
    val declaredSizeBytes: Long,
) {
    init {
        requireCanonicalBounded(sourceIdentity, 256, "update source identity")
        requireCanonicalBounded(channel, 128, "update channel")
        requirePackageName(packageName)
        require(versionCode > 0L) { "update target versionCode must be positive" }
        requireCanonicalBounded(versionName, 128, "update versionName")
        require(minimumSourceVersionCode in 1..versionCode) {
            "update minimum source version must be positive and not exceed target"
        }
        requireCanonicalBounded(artifactUri, 2048, "update artifact URI")
        require(declaredSizeBytes > 0L) { "update declared size must be positive" }
    }
}

enum class UpdateEligibilityRejection {
    CHANNEL_MISMATCH,
    PACKAGE_MISMATCH,
    DOWNGRADE,
    SOURCE_TOO_OLD,
}

sealed interface UpdateEligibility {
    data object UpToDate : UpdateEligibility

    data class Available(
        val candidate: UpdateManifestCandidate,
    ) : UpdateEligibility

    data class Rejected(
        val reason: UpdateEligibilityRejection,
    ) : UpdateEligibility
}

object UpdateCheckPolicy {
    fun evaluate(
        installed: InstalledUpdateIdentity,
        candidate: UpdateManifestCandidate,
        expectedChannel: String,
    ): UpdateEligibility {
        requireCanonicalBounded(expectedChannel, 128, "expected update channel")
        if (candidate.channel != expectedChannel) {
            return UpdateEligibility.Rejected(UpdateEligibilityRejection.CHANNEL_MISMATCH)
        }
        if (candidate.packageName != installed.packageName) {
            return UpdateEligibility.Rejected(UpdateEligibilityRejection.PACKAGE_MISMATCH)
        }
        if (candidate.versionCode == installed.versionCode) {
            return UpdateEligibility.UpToDate
        }
        if (candidate.versionCode < installed.versionCode) {
            return UpdateEligibility.Rejected(UpdateEligibilityRejection.DOWNGRADE)
        }
        if (installed.versionCode < candidate.minimumSourceVersionCode) {
            return UpdateEligibility.Rejected(UpdateEligibilityRejection.SOURCE_TOO_OLD)
        }
        return UpdateEligibility.Available(candidate)
    }
}

data class UpdateArtifactIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val signingIdentity: UpdateSigningIdentity,
    val byteCount: Long,
    val sha256: UpdateSha256,
) {
    init {
        requirePackageName(packageName)
        require(versionCode > 0L) { "artifact versionCode must be positive" }
        requireCanonicalBounded(versionName, 128, "artifact versionName")
        require(byteCount > 0L) { "artifact byte count must be positive" }
    }
}

data class VerifiedStagedUpdate(
    val artifactId: StagedUpdateArtifactId,
    val candidate: UpdateManifestCandidate,
    val artifactIdentity: UpdateArtifactIdentity,
) {
    init {
        require(artifactIdentity.packageName == candidate.packageName) {
            "verified artifact package does not match candidate"
        }
        require(artifactIdentity.versionCode == candidate.versionCode) {
            "verified artifact versionCode does not match candidate"
        }
        require(artifactIdentity.versionName == candidate.versionName) {
            "verified artifact versionName does not match candidate"
        }
        require(artifactIdentity.byteCount == candidate.declaredSizeBytes) {
            "verified artifact size does not match candidate"
        }
        require(artifactIdentity.sha256 == candidate.artifactSha256) {
            "verified artifact digest does not match candidate"
        }
    }
}

data class ExpectedInstall(
    val expectedInstallId: ExpectedInstallId,
    val operationId: UpdateOperationId,
    val installedIdentityAtPreparation: InstalledUpdateIdentity,
    val staged: VerifiedStagedUpdate,
) {
    init {
        require(staged.candidate.packageName == installedIdentityAtPreparation.packageName) {
            "expected install package must match installed package"
        }
        require(staged.candidate.versionCode > installedIdentityAtPreparation.versionCode) {
            "expected install must be a monotonic upgrade"
        }
    }

    val sourceVersionCode: Long
        get() = installedIdentityAtPreparation.versionCode

    val targetVersionCode: Long
        get() = staged.candidate.versionCode

    val packageName: String
        get() = staged.candidate.packageName
}

enum class UpdateLifecyclePhase {
    CANDIDATE_AVAILABLE,
    STAGING,
    STAGED_VERIFIED,
    INSTALL_PREPARED,
    OS_HANDOFF,
    AWAITING_USER_ACTION,
    RECONCILE_REQUIRED,
    INSTALLED,
    CANCELLED,
    FAILED,
    EFFECT_UNKNOWN,
}

object UpdateLifecycleTransitionPolicy {
    private val allowed =
        mapOf(
            UpdateLifecyclePhase.CANDIDATE_AVAILABLE to
                setOf(
                    UpdateLifecyclePhase.STAGING,
                    UpdateLifecyclePhase.CANCELLED,
                ),
            UpdateLifecyclePhase.STAGING to
                setOf(
                    UpdateLifecyclePhase.STAGED_VERIFIED,
                    UpdateLifecyclePhase.FAILED,
                    UpdateLifecyclePhase.CANCELLED,
                ),
            UpdateLifecyclePhase.STAGED_VERIFIED to
                setOf(
                    UpdateLifecyclePhase.INSTALL_PREPARED,
                    UpdateLifecyclePhase.CANCELLED,
                ),
            UpdateLifecyclePhase.INSTALL_PREPARED to
                setOf(
                    UpdateLifecyclePhase.OS_HANDOFF,
                    UpdateLifecyclePhase.FAILED,
                    UpdateLifecyclePhase.CANCELLED,
                ),
            UpdateLifecyclePhase.OS_HANDOFF to
                setOf(
                    UpdateLifecyclePhase.AWAITING_USER_ACTION,
                    UpdateLifecyclePhase.RECONCILE_REQUIRED,
                    UpdateLifecyclePhase.FAILED,
                    UpdateLifecyclePhase.EFFECT_UNKNOWN,
                ),
            UpdateLifecyclePhase.AWAITING_USER_ACTION to
                setOf(
                    UpdateLifecyclePhase.RECONCILE_REQUIRED,
                    UpdateLifecyclePhase.CANCELLED,
                    UpdateLifecyclePhase.FAILED,
                    UpdateLifecyclePhase.EFFECT_UNKNOWN,
                ),
            UpdateLifecyclePhase.RECONCILE_REQUIRED to
                setOf(
                    UpdateLifecyclePhase.INSTALLED,
                    UpdateLifecyclePhase.FAILED,
                    UpdateLifecyclePhase.EFFECT_UNKNOWN,
                ),
            UpdateLifecyclePhase.EFFECT_UNKNOWN to
                setOf(
                    UpdateLifecyclePhase.RECONCILE_REQUIRED,
                ),
            UpdateLifecyclePhase.INSTALLED to emptySet(),
            UpdateLifecyclePhase.CANCELLED to emptySet(),
            UpdateLifecyclePhase.FAILED to emptySet(),
        )

    fun transition(
        current: UpdateLifecyclePhase,
        next: UpdateLifecyclePhase,
    ): UpdateLifecyclePhase {
        require(next in checkNotNull(allowed[current])) {
            "illegal update lifecycle transition: $current -> $next"
        }
        return next
    }

    fun isTerminal(phase: UpdateLifecyclePhase): Boolean =
        phase == UpdateLifecyclePhase.INSTALLED ||
            phase == UpdateLifecyclePhase.CANCELLED ||
            phase == UpdateLifecyclePhase.FAILED
}

enum class UpdateFailureKind {
    SOURCE_UNAVAILABLE,
    INVALID_MANIFEST,
    NOT_ELIGIBLE,
    DOWNLOAD_FAILED,
    ARTIFACT_INVALID,
    INSTALL_PERMISSION_REQUIRED,
    INSTALL_REJECTED,
    SESSION_LOST,
    INSTALLED_IDENTITY_MISMATCH,
    CANCELLED,
    EFFECT_UNKNOWN,
}

data class UpdateFailure(
    val kind: UpdateFailureKind,
    val reason: String,
) {
    init {
        requireCanonicalBounded(reason, 256, "update failure reason")
    }
}

enum class UpdateInstallObservationKind {
    SESSION_ACTIVE,
    PENDING_USER_ACTION,
    SUCCESS_REPORTED,
    FAILURE_REPORTED,
    SESSION_MISSING,
    EFFECT_UNKNOWN,
}

data class UpdateInstallSessionObservation(
    val expectedInstallId: ExpectedInstallId,
    val platformSessionIdentity: String?,
    val kind: UpdateInstallObservationKind,
    val failure: UpdateFailure? = null,
) {
    init {
        if (platformSessionIdentity != null) {
            requireCanonicalBounded(platformSessionIdentity, 256, "platform install session identity")
        }
        val requiresFailure =
            kind == UpdateInstallObservationKind.FAILURE_REPORTED ||
                kind == UpdateInstallObservationKind.EFFECT_UNKNOWN
        require(requiresFailure == (failure != null)) {
            "failure or uncertainty observation must carry exactly one typed failure"
        }
        if (kind == UpdateInstallObservationKind.EFFECT_UNKNOWN) {
            require(
                failure?.kind == UpdateFailureKind.EFFECT_UNKNOWN ||
                    failure?.kind == UpdateFailureKind.SESSION_LOST
            ) {
                "effect-unknown observation requires an uncertainty failure"
            }
        }
    }
}

fun interface UpdateManifestSourcePort {
    fun check(): UpdateManifestCandidate?
}

fun interface UpdateArtifactStagePort {
    fun stage(candidate: UpdateManifestCandidate): StagedUpdateArtifactId
}

fun interface UpdateArtifactInspectionPort {
    fun inspect(artifactId: StagedUpdateArtifactId): UpdateArtifactIdentity
}

fun interface UpdateInstallPort {
    fun commit(expected: ExpectedInstall): UpdateInstallSessionObservation
}

fun interface InstalledUpdateIdentityPort {
    fun current(): InstalledUpdateIdentity
}

data class UpdateInstallSessionInspectionRequest(
    val expectedInstallId: String,
    val platformSessionIdentity: String,
    val packageName: String,
) {
    init {
        requireCanonicalBounded(expectedInstallId, 160, "session inspection expected-install id")
        requireCanonicalBounded(platformSessionIdentity, 256, "session inspection platform identity")
        requirePackageName(packageName)
    }
}

fun interface UpdateInstallSessionInspectionPort {
    fun inspect(request: UpdateInstallSessionInspectionRequest): UpdateInstallSessionObservation
}

fun interface UpdateInstallSessionCleanupPort {
    fun abandon(request: UpdateInstallSessionInspectionRequest)
}

private fun requirePackageName(value: String) {
    require(value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
        "invalid update package name"
    }
    require(value.length <= 256) { "update package name is too long" }
}

private fun requireCanonicalBounded(
    value: String,
    maxLength: Int,
    label: String,
) {
    require(value.isNotBlank()) { "$label must not be blank" }
    require(value == value.trim()) { "$label must be canonical" }
    require(value.length <= maxLength) { "$label is too long" }
}
