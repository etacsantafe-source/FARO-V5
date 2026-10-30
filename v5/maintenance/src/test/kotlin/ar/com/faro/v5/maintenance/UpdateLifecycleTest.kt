package ar.com.faro.v5.maintenance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateLifecycleTest {
    private val installed =
        InstalledUpdateIdentity(
            packageName = "ar.com.faro.v5.dev",
            versionCode = 10L,
            signingIdentity = lineage("11"),
        )

    @Test
    fun `update identities reject blank non canonical and malformed digests`() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateOperationId(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpectedInstallId(" expected-1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateSha256("ABCD")
        }
    }

    @Test
    fun `check policy keeps check pure and rejects mismatched unsafe candidates`() {
        assertInstanceOf(
            UpdateEligibility.UpToDate::class.java,
            UpdateCheckPolicy.evaluate(
                installed = installed,
                candidate = candidate(versionCode = 10L),
                expectedChannel = "dev",
            ),
        )

        assertEquals(
            UpdateEligibilityRejection.DOWNGRADE,
            rejectedReason(candidate(versionCode = 9L)),
        )
        assertEquals(
            UpdateEligibilityRejection.SOURCE_TOO_OLD,
            rejectedReason(candidate(versionCode = 12L, minimumSourceVersionCode = 11L)),
        )
        assertEquals(
            UpdateEligibilityRejection.CHANNEL_MISMATCH,
            rejectedReason(candidate(channel = "stable")),
        )
        assertEquals(
            UpdateEligibilityRejection.PACKAGE_MISMATCH,
            rejectedReason(candidate(packageName = "ar.com.other.app")),
        )

        val available =
            UpdateCheckPolicy.evaluate(
                installed = installed,
                candidate = candidate(versionCode = 11L),
                expectedChannel = "dev",
            )
        assertInstanceOf(UpdateEligibility.Available::class.java, available)
    }

    @Test
    fun `signing identities distinguish ordered lineage from multiple signers`() {
        val rotated =
            UpdateSigningIdentity.Lineage(
                listOf(
                    digest("11"),
                    digest("22"),
                ),
            )
        assertEquals(digest("22"), rotated.currentCertificateSha256)

        assertThrows(IllegalArgumentException::class.java) {
            UpdateSigningIdentity.Lineage(
                listOf(
                    digest("11"),
                    digest("11"),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateSigningIdentity.MultipleSigners(setOf(digest("11")))
        }

        val multiple =
            UpdateSigningIdentity.MultipleSigners(
                setOf(
                    digest("11"),
                    digest("22"),
                ),
            )
        assertEquals(2, multiple.certificatesSha256.size)
    }

    @Test
    fun `verified staged artifact is bound to exact manifest identity and bytes`() {
        val manifest = candidate(versionCode = 11L)
        val identity =
            UpdateArtifactIdentity(
                packageName = manifest.packageName,
                versionCode = manifest.versionCode,
                versionName = manifest.versionName,
                signingIdentity = lineage("22"),
                byteCount = manifest.declaredSizeBytes,
                sha256 = manifest.artifactSha256,
            )

        val staged =
            VerifiedStagedUpdate(
                artifactId = StagedUpdateArtifactId("artifact-11"),
                candidate = manifest,
                artifactIdentity = identity,
            )
        assertEquals(11L, staged.artifactIdentity.versionCode)

        assertThrows(IllegalArgumentException::class.java) {
            VerifiedStagedUpdate(
                artifactId = StagedUpdateArtifactId("artifact-wrong"),
                candidate = manifest,
                artifactIdentity = identity.copy(byteCount = identity.byteCount + 1L),
            )
        }
    }

    @Test
    fun `expected install refuses downgrade or same version`() {
        val staged = verified(versionCode = 11L)
        val expected =
            ExpectedInstall(
                expectedInstallId = ExpectedInstallId("expected-11"),
                operationId = UpdateOperationId("update-op-11"),
                installedIdentityAtPreparation = installed,
                staged = staged,
            )
        assertEquals(10L, expected.sourceVersionCode)
        assertEquals(11L, expected.targetVersionCode)
        assertEquals("ar.com.faro.v5.dev", expected.packageName)
        assertEquals(installed.signingIdentity, expected.installedIdentityAtPreparation.signingIdentity)

        assertThrows(IllegalArgumentException::class.java) {
            ExpectedInstall(
                expectedInstallId = ExpectedInstallId("expected-not-upgrade"),
                operationId = UpdateOperationId("update-op-not-upgrade"),
                installedIdentityAtPreparation = installed.copy(versionCode = 11L),
                staged = staged,
            )
        }
    }

    @Test
    fun `lifecycle cannot skip staging or treat OS handoff as installed`() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateLifecycleTransitionPolicy.transition(
                UpdateLifecyclePhase.CANDIDATE_AVAILABLE,
                UpdateLifecyclePhase.STAGED_VERIFIED,
            )
        }

        val staging =
            UpdateLifecycleTransitionPolicy.transition(
                UpdateLifecyclePhase.CANDIDATE_AVAILABLE,
                UpdateLifecyclePhase.STAGING,
            )
        val staged =
            UpdateLifecycleTransitionPolicy.transition(
                staging,
                UpdateLifecyclePhase.STAGED_VERIFIED,
            )
        val prepared =
            UpdateLifecycleTransitionPolicy.transition(
                staged,
                UpdateLifecyclePhase.INSTALL_PREPARED,
            )
        val handoff =
            UpdateLifecycleTransitionPolicy.transition(
                prepared,
                UpdateLifecyclePhase.OS_HANDOFF,
            )

        assertThrows(IllegalArgumentException::class.java) {
            UpdateLifecycleTransitionPolicy.transition(
                handoff,
                UpdateLifecyclePhase.INSTALLED,
            )
        }

        val reconcile =
            UpdateLifecycleTransitionPolicy.transition(
                handoff,
                UpdateLifecyclePhase.RECONCILE_REQUIRED,
            )
        val installed =
            UpdateLifecycleTransitionPolicy.transition(
                reconcile,
                UpdateLifecyclePhase.INSTALLED,
            )
        assertTrue(UpdateLifecycleTransitionPolicy.isTerminal(installed))
    }

    @Test
    fun `effect unknown remains reconcilable and terminal states stay terminal`() {
        val unknown =
            UpdateLifecycleTransitionPolicy.transition(
                UpdateLifecyclePhase.OS_HANDOFF,
                UpdateLifecyclePhase.EFFECT_UNKNOWN,
            )
        assertFalse(UpdateLifecycleTransitionPolicy.isTerminal(unknown))

        val reconcile =
            UpdateLifecycleTransitionPolicy.transition(
                unknown,
                UpdateLifecyclePhase.RECONCILE_REQUIRED,
            )
        assertEquals(UpdateLifecyclePhase.RECONCILE_REQUIRED, reconcile)

        assertThrows(IllegalArgumentException::class.java) {
            UpdateLifecycleTransitionPolicy.transition(
                UpdateLifecyclePhase.FAILED,
                UpdateLifecyclePhase.STAGING,
            )
        }
    }

    @Test
    fun `effect unknown observation requires uncertainty failure`() {
        val expectedId = ExpectedInstallId("expected-unknown")
        val unknown =
            UpdateInstallSessionObservation(
                expectedInstallId = expectedId,
                platformSessionIdentity = "session-99",
                kind = UpdateInstallObservationKind.EFFECT_UNKNOWN,
                failure =
                    UpdateFailure(
                        kind = UpdateFailureKind.EFFECT_UNKNOWN,
                        reason = "commit_outcome_unknown",
                    ),
            )
        assertEquals(UpdateFailureKind.EFFECT_UNKNOWN, unknown.failure?.kind)

        assertThrows(IllegalArgumentException::class.java) {
            UpdateInstallSessionObservation(
                expectedInstallId = expectedId,
                platformSessionIdentity = "session-99",
                kind = UpdateInstallObservationKind.EFFECT_UNKNOWN,
                failure =
                    UpdateFailure(
                        kind = UpdateFailureKind.INSTALL_REJECTED,
                        reason = "known_failure",
                    ),
            )
        }
    }

    @Test
    fun `failure observation requires typed failure and non failure forbids it`() {
        val expectedId = ExpectedInstallId("expected-11")
        val failure =
            UpdateFailure(
                kind = UpdateFailureKind.INSTALL_REJECTED,
                reason = "platform_rejected",
            )

        val observed =
            UpdateInstallSessionObservation(
                expectedInstallId = expectedId,
                platformSessionIdentity = "session-42",
                kind = UpdateInstallObservationKind.FAILURE_REPORTED,
                failure = failure,
            )
        assertEquals(failure, observed.failure)

        assertThrows(IllegalArgumentException::class.java) {
            UpdateInstallSessionObservation(
                expectedInstallId = expectedId,
                platformSessionIdentity = "session-42",
                kind = UpdateInstallObservationKind.SESSION_ACTIVE,
                failure = failure,
            )
        }
    }

    private fun rejectedReason(candidate: UpdateManifestCandidate): UpdateEligibilityRejection =
        (UpdateCheckPolicy.evaluate(installed, candidate, "dev") as UpdateEligibility.Rejected).reason

    private fun candidate(
        channel: String = "dev",
        packageName: String = "ar.com.faro.v5.dev",
        versionCode: Long = 11L,
        minimumSourceVersionCode: Long = 1L,
    ): UpdateManifestCandidate =
        UpdateManifestCandidate(
            sourceIdentity = "github-release-dev",
            channel = channel,
            packageName = packageName,
            versionCode = versionCode,
            versionName = "5.0.$versionCode",
            minimumSourceVersionCode = minimumSourceVersionCode,
            artifactUri = "https://example.invalid/faro-$versionCode.apk",
            artifactSha256 = digest("aa"),
            declaredSizeBytes = 1_024L,
        )

    private fun verified(versionCode: Long): VerifiedStagedUpdate {
        val candidate = candidate(versionCode = versionCode)
        return VerifiedStagedUpdate(
            artifactId = StagedUpdateArtifactId("artifact-$versionCode"),
            candidate = candidate,
            artifactIdentity =
                UpdateArtifactIdentity(
                    packageName = candidate.packageName,
                    versionCode = candidate.versionCode,
                    versionName = candidate.versionName,
                    signingIdentity = lineage("22"),
                    byteCount = candidate.declaredSizeBytes,
                    sha256 = candidate.artifactSha256,
                ),
        )
    }

    private fun lineage(prefix: String): UpdateSigningIdentity.Lineage =
        UpdateSigningIdentity.Lineage(listOf(digest(prefix)))

    private fun digest(prefix: String): UpdateSha256 =
        UpdateSha256(prefix.repeat(64 / prefix.length))
}
