package ar.com.faro.v5.maintenance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class UpdateInstallControlCoordinatorTest {
    @Test
    fun `prepare creates durable expected install and session binds exactly once before handoff`() {
        val store = InMemoryUpdateControlStore()
        val coordinator = UpdateInstallControlCoordinator(store)
        val expected = expectedInstall()

        val prepared = coordinator.prepare(expected)
        assertEquals(UpdateLifecyclePhase.INSTALL_PREPARED, prepared.phase)
        assertNull(prepared.platformSessionIdentity)

        val bound = coordinator.bindPlatformSession(prepared, "42")
        assertEquals("42", bound.platformSessionIdentity)

        val handoff = coordinator.markOsHandoff(bound)
        assertEquals(UpdateLifecyclePhase.OS_HANDOFF, handoff.phase)
        assertEquals("42", handoff.platformSessionIdentity)

        assertThrows(IllegalArgumentException::class.java) {
            coordinator.bindPlatformSession(bound, "43")
        }
    }

    @Test
    fun `known pre or post handoff failures become terminal failed while unknown post handoff remains recoverable`() {
        val store = InMemoryUpdateControlStore()
        val coordinator = UpdateInstallControlCoordinator(store)
        val expected = expectedInstall()

        val prepared = coordinator.prepare(expected)
        val bound = coordinator.bindPlatformSession(prepared, "42")
        val failed =
            coordinator.markFailed(
                bound,
                UpdateFailure(
                    kind = UpdateFailureKind.ARTIFACT_INVALID,
                    reason = "session_artifact_mismatch",
                ),
            )
        assertEquals(UpdateLifecyclePhase.FAILED, failed.phase)
        assertEquals(UpdateFailureKind.ARTIFACT_INVALID, failed.failure?.kind)

        val store2 = InMemoryUpdateControlStore()
        val coordinator2 = UpdateInstallControlCoordinator(store2)
        val prepared2 = coordinator2.prepare(expected.copy(expectedInstallId = ExpectedInstallId("expected-2")))
        val bound2 = coordinator2.bindPlatformSession(prepared2, "77")
        val handoff2 = coordinator2.markOsHandoff(bound2)
        val unknown = coordinator2.markEffectUnknown(handoff2, "commit_runtime_unknown")
        assertEquals(UpdateLifecyclePhase.EFFECT_UNKNOWN, unknown.phase)
        assertEquals(UpdateFailureKind.EFFECT_UNKNOWN, unknown.failure?.kind)
    }

    @Test
    fun `prepare refuses a conflicting existing expected install`() {
        val store = InMemoryUpdateControlStore()
        val coordinator = UpdateInstallControlCoordinator(store)
        val expected = expectedInstall()
        store.create(expected)

        assertThrows(IllegalArgumentException::class.java) {
            coordinator.prepare(
                expected.copy(
                    operationId = UpdateOperationId("different-op"),
                ),
            )
        }
    }

    private fun expectedInstall(): ExpectedInstall {
        val candidate =
            UpdateManifestCandidate(
                sourceIdentity = "https://updates.example.invalid/manifest.properties",
                channel = "dev",
                packageName = "ar.com.faro.v5.dev",
                versionCode = 11L,
                versionName = "5.0.11",
                minimumSourceVersionCode = 1L,
                artifactUri = "https://updates.example.invalid/faro-11.apk",
                artifactSha256 = digest("aa"),
                declaredSizeBytes = 128L,
            )
        val artifact =
            UpdateArtifactIdentity(
                packageName = candidate.packageName,
                versionCode = candidate.versionCode,
                versionName = candidate.versionName,
                signingIdentity = UpdateSigningIdentity.Lineage(listOf(digest("11"))),
                byteCount = candidate.declaredSizeBytes,
                sha256 = candidate.artifactSha256,
            )
        return ExpectedInstall(
            expectedInstallId = ExpectedInstallId("expected-1"),
            operationId = UpdateOperationId("operation-1"),
            installedIdentityAtPreparation =
                InstalledUpdateIdentity(
                    packageName = candidate.packageName,
                    versionCode = 10L,
                    signingIdentity = UpdateSigningIdentity.Lineage(listOf(digest("11"))),
                ),
            staged =
                VerifiedStagedUpdate(
                    artifactId = StagedUpdateArtifactId("artifact-11"),
                    candidate = candidate,
                    artifactIdentity = artifact,
                ),
        )
    }

    private fun digest(prefix: String): UpdateSha256 =
        UpdateSha256(prefix.repeat(64 / prefix.length))

    private class InMemoryUpdateControlStore : UpdateControlStorePort {
        private var entry: UpdateControlEntry? = null

        override fun read(expectedInstallId: ExpectedInstallId): UpdateControlEntry? =
            entry?.takeIf { it.expectedInstallId == expectedInstallId }

        override fun pending(): List<UpdateControlEntry> =
            entry?.takeIf { !UpdateLifecycleTransitionPolicy.isTerminal(it.phase) }
                ?.let(::listOf)
                ?: emptyList()

        override fun create(expectedInstall: ExpectedInstall) {
            check(entry == null) { "already exists" }
            entry =
                UpdateControlEntry(
                    expectedInstall = expectedInstall,
                    phase = UpdateLifecyclePhase.INSTALL_PREPARED,
                )
        }

        override fun compareAndSet(
            expected: UpdateControlEntry,
            updated: UpdateControlEntry,
        ): Boolean {
            if (entry != expected) return false
            entry = updated
            return true
        }
    }
}
