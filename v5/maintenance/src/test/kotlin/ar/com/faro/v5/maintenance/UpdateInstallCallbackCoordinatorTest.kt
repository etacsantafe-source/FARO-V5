package ar.com.faro.v5.maintenance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class UpdateInstallCallbackCoordinatorTest {
    @Test
    fun `pending user action is durable and duplicate callback is idempotent`() {
        val store = storeAtHandoff()
        val coordinator = UpdateInstallCallbackCoordinator(store)
        val event =
            UpdateInstallCallbackEvent(
                expectedInstallId = "expected-1",
                platformSessionIdentity = "42",
                kind = UpdateInstallCallbackKind.PENDING_USER_ACTION,
            )

        val pending = coordinator.onCallback(event)
        assertEquals(UpdateLifecyclePhase.AWAITING_USER_ACTION, pending.phase)
        assertEquals(pending, coordinator.onCallback(event))
    }

    @Test
    fun `success callback requires reconcile and never marks installed`() {
        val store = storeAtHandoff()
        val coordinator = UpdateInstallCallbackCoordinator(store)

        coordinator.onCallback(
            UpdateInstallCallbackEvent(
                expectedInstallId = "expected-1",
                platformSessionIdentity = "42",
                kind = UpdateInstallCallbackKind.PENDING_USER_ACTION,
            ),
        )
        val success =
            coordinator.onCallback(
                UpdateInstallCallbackEvent(
                    expectedInstallId = "expected-1",
                    platformSessionIdentity = "42",
                    kind = UpdateInstallCallbackKind.SUCCESS_REPORTED,
                ),
            )

        assertEquals(UpdateLifecyclePhase.RECONCILE_REQUIRED, success.phase)
        assertEquals(success, coordinator.onCallback(
            UpdateInstallCallbackEvent(
                expectedInstallId = "expected-1",
                platformSessionIdentity = "42",
                kind = UpdateInstallCallbackKind.SUCCESS_REPORTED,
            ),
        ))
    }

    @Test
    fun `known failure becomes terminal and later callbacks cannot reopen truth`() {
        val store = storeAtHandoff()
        val coordinator = UpdateInstallCallbackCoordinator(store)
        val failure =
            UpdateFailure(
                kind = UpdateFailureKind.INSTALL_REJECTED,
                reason = "package_installer_status_failure_blocked",
            )
        val event =
            UpdateInstallCallbackEvent(
                expectedInstallId = "expected-1",
                platformSessionIdentity = "42",
                kind = UpdateInstallCallbackKind.FAILURE_REPORTED,
                failure = failure,
            )

        val failed = coordinator.onCallback(event)
        assertEquals(UpdateLifecyclePhase.FAILED, failed.phase)
        assertEquals(failure, failed.failure)

        val late =
            coordinator.onCallback(
                event.copy(
                    failure =
                        UpdateFailure(
                            kind = UpdateFailureKind.INSTALL_REJECTED,
                            reason = "different_failure",
                        ),
                ),
            )
        assertEquals(failed, late)
    }

    @Test
    fun `late success after uncertain commit reenters reconcile`() {
        val store = storeAtHandoff()
        val coordinator = UpdateInstallCallbackCoordinator(store)
        coordinator.onCallback(
            UpdateInstallCallbackEvent(
                expectedInstallId = "expected-1",
                platformSessionIdentity = "42",
                kind = UpdateInstallCallbackKind.EFFECT_UNKNOWN,
                failure = UpdateFailure(UpdateFailureKind.EFFECT_UNKNOWN, "late_unknown"),
            ),
        )
        val result =
            coordinator.onCallback(
                UpdateInstallCallbackEvent(
                    expectedInstallId = "expected-1",
                    platformSessionIdentity = "42",
                    kind = UpdateInstallCallbackKind.SUCCESS_REPORTED,
                ),
            )
        assertEquals(UpdateLifecyclePhase.RECONCILE_REQUIRED, result.phase)
    }

    @Test
    fun `late failure after uncertain commit returns to reconcile`() {
        val store = storeAtHandoff()
        val coordinator = UpdateInstallCallbackCoordinator(store)
        coordinator.onCallback(
            UpdateInstallCallbackEvent(
                expectedInstallId = "expected-1",
                platformSessionIdentity = "42",
                kind = UpdateInstallCallbackKind.EFFECT_UNKNOWN,
                failure = UpdateFailure(UpdateFailureKind.EFFECT_UNKNOWN, "late_unknown"),
            ),
        )
        val result =
            coordinator.onCallback(
                UpdateInstallCallbackEvent(
                    expectedInstallId = "expected-1",
                    platformSessionIdentity = "42",
                    kind = UpdateInstallCallbackKind.FAILURE_REPORTED,
                    failure =
                        UpdateFailure(
                            UpdateFailureKind.INSTALL_REJECTED,
                            "package_installer_status_failure_blocked",
                        ),
                ),
            )
        assertEquals(UpdateLifecyclePhase.RECONCILE_REQUIRED, result.phase)
        assertEquals(null, result.failure)
    }

    @Test
    fun `callback session mismatch fails closed`() {
        val store = storeAtHandoff()
        val coordinator = UpdateInstallCallbackCoordinator(store)

        assertThrows(IllegalArgumentException::class.java) {
            coordinator.onCallback(
                UpdateInstallCallbackEvent(
                    expectedInstallId = "expected-1",
                    platformSessionIdentity = "99",
                    kind = UpdateInstallCallbackKind.SUCCESS_REPORTED,
                ),
            )
        }
    }

    private fun storeAtHandoff(): MemoryStore {
        val store = MemoryStore()
        val install = expectedInstall()
        store.create(install)
        val prepared = checkNotNull(store.read(install.expectedInstallId))
        val bound =
            prepared.copy(platformSessionIdentity = "42")
        check(store.compareAndSet(prepared, bound))
        val handoff =
            bound.copy(phase = UpdateLifecyclePhase.OS_HANDOFF)
        check(store.compareAndSet(bound, handoff))
        return store
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
                    artifactIdentity =
                        UpdateArtifactIdentity(
                            packageName = candidate.packageName,
                            versionCode = candidate.versionCode,
                            versionName = candidate.versionName,
                            signingIdentity = UpdateSigningIdentity.Lineage(listOf(digest("11"))),
                            byteCount = candidate.declaredSizeBytes,
                            sha256 = candidate.artifactSha256,
                        ),
                ),
        )
    }

    private fun digest(prefix: String): UpdateSha256 =
        UpdateSha256(prefix.repeat(64 / prefix.length))

    private class MemoryStore : UpdateControlStorePort {
        private var entry: UpdateControlEntry? = null

        override fun read(expectedInstallId: ExpectedInstallId): UpdateControlEntry? =
            entry?.takeIf { it.expectedInstallId == expectedInstallId }

        override fun pending(): List<UpdateControlEntry> =
            entry?.takeIf { !UpdateLifecycleTransitionPolicy.isTerminal(it.phase) }
                ?.let(::listOf)
                ?: emptyList()

        override fun create(expectedInstall: ExpectedInstall) {
            check(entry == null)
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
