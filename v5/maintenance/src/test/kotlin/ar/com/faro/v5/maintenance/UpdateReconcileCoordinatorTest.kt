package ar.com.faro.v5.maintenance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class UpdateReconcileCoordinatorTest {
    @Test
    fun `prepared install never handed to OS is cancelled and safe resources are cleaned`() {
        val expected = expectedInstall()
        val store = MemoryStore(entry(expected, UpdateLifecyclePhase.INSTALL_PREPARED, "42"))
        val abandoned = mutableListOf<String>()
        val cleaned = mutableListOf<StagedUpdateArtifactId>()
        val coordinator =
            coordinator(
                store = store,
                installed = expected.installedIdentityAtPreparation,
                inspect = { error("prepared recovery must not inspect handed-off session") },
                abandon = { abandoned += it.platformSessionIdentity },
                cleanup = { cleaned += it },
            )

        val result = coordinator.reconcile(expected.expectedInstallId)

        assertEquals(UpdateLifecyclePhase.CANCELLED, result.phase)
        assertEquals(listOf("42"), abandoned)
        assertEquals(listOf(expected.staged.artifactId), cleaned)
    }

    @Test
    fun `exact installed target is final truth even when callback was lost`() {
        val expected = expectedInstall()
        val store = MemoryStore(entry(expected, UpdateLifecyclePhase.OS_HANDOFF, "42"))
        val cleaned = mutableListOf<StagedUpdateArtifactId>()
        val coordinator =
            coordinator(
                store = store,
                installed = targetIdentity(expected),
                inspect = { error("installed target must win before session inspection") },
                cleanup = { cleaned += it },
            )

        val result = coordinator.reconcile(expected.expectedInstallId)

        assertEquals(UpdateLifecyclePhase.INSTALLED, result.phase)
        assertEquals(listOf(expected.staged.artifactId), cleaned)
        assertEquals(result, coordinator.reconcile(expected.expectedInstallId))
    }

    @Test
    fun `owned active session with source still installed preserves handoff for later user action`() {
        val expected = expectedInstall()
        val store = MemoryStore(entry(expected, UpdateLifecyclePhase.OS_HANDOFF, "42"))
        val coordinator =
            coordinator(
                store = store,
                installed = expected.installedIdentityAtPreparation,
                inspect = { request ->
                    observation(request, UpdateInstallObservationKind.SESSION_ACTIVE)
                },
            )

        val result = coordinator.reconcile(expected.expectedInstallId)

        assertEquals(UpdateLifecyclePhase.OS_HANDOFF, result.phase)
        assertEquals("42", result.platformSessionIdentity)
    }

    @Test
    fun `awaiting user action is preserved while its owned session remains active`() {
        val expected = expectedInstall()
        val store = MemoryStore(entry(expected, UpdateLifecyclePhase.AWAITING_USER_ACTION, "42"))
        val coordinator =
            coordinator(
                store = store,
                installed = expected.installedIdentityAtPreparation,
                inspect = { request ->
                    observation(request, UpdateInstallObservationKind.SESSION_ACTIVE)
                },
            )

        val result = coordinator.reconcile(expected.expectedInstallId)

        assertEquals(UpdateLifecyclePhase.AWAITING_USER_ACTION, result.phase)
    }

    @Test
    fun `missing handed-off session with unchanged source closes as session lost`() {
        val expected = expectedInstall()
        val store = MemoryStore(entry(expected, UpdateLifecyclePhase.RECONCILE_REQUIRED, "42"))
        val cleaned = mutableListOf<StagedUpdateArtifactId>()
        val coordinator =
            coordinator(
                store = store,
                installed = expected.installedIdentityAtPreparation,
                inspect = { request ->
                    observation(request, UpdateInstallObservationKind.SESSION_MISSING)
                },
                cleanup = { cleaned += it },
            )

        val result = coordinator.reconcile(expected.expectedInstallId)

        assertEquals(UpdateLifecyclePhase.FAILED, result.phase)
        assertEquals(UpdateFailureKind.SESSION_LOST, result.failure?.kind)
        assertEquals(listOf(expected.staged.artifactId), cleaned)
    }

    @Test
    fun `missing session plus unexpected installed identity fails closed as mismatch`() {
        val expected = expectedInstall()
        val unexpected =
            expected.installedIdentityAtPreparation.copy(
                versionCode = expected.targetVersionCode + 1L,
            )
        val store = MemoryStore(entry(expected, UpdateLifecyclePhase.EFFECT_UNKNOWN, "42",
            UpdateFailure(UpdateFailureKind.EFFECT_UNKNOWN, "prior_uncertainty")))
        val coordinator =
            coordinator(
                store = store,
                installed = unexpected,
                inspect = { request ->
                    observation(request, UpdateInstallObservationKind.SESSION_MISSING)
                },
            )

        val result = coordinator.reconcile(expected.expectedInstallId)

        assertEquals(UpdateLifecyclePhase.FAILED, result.phase)
        assertEquals(UpdateFailureKind.INSTALLED_IDENTITY_MISMATCH, result.failure?.kind)
    }

    @Test
    fun `missing session rechecks installed identity before declaring failure`() {
        val expected = expectedInstall()
        val store = MemoryStore(entry(expected, UpdateLifecyclePhase.OS_HANDOFF, "42"))
        var installedReads = 0
        val coordinator =
            UpdateReconcileCoordinator(
                store = store,
                installedIdentity =
                    InstalledUpdateIdentityPort {
                        installedReads += 1
                        if (installedReads == 1) {
                            expected.installedIdentityAtPreparation
                        } else {
                            targetIdentity(expected)
                        }
                    },
                sessionInspection =
                    UpdateInstallSessionInspectionPort { request ->
                        observation(request, UpdateInstallObservationKind.SESSION_MISSING)
                    },
                sessionCleanup = UpdateInstallSessionCleanupPort {},
                artifactCleanup = UpdateArtifactCleanupPort {},
            )

        val result = coordinator.reconcile(expected.expectedInstallId)

        assertEquals(UpdateLifecyclePhase.INSTALLED, result.phase)
        assertEquals(3, installedReads)
    }

    @Test
    fun `platform inspection failure becomes durable uncertainty instead of guessing`() {
        val expected = expectedInstall()
        val store = MemoryStore(entry(expected, UpdateLifecyclePhase.OS_HANDOFF, "42"))
        val coordinator =
            coordinator(
                store = store,
                installed = expected.installedIdentityAtPreparation,
                inspect = { throw IllegalStateException("platform unavailable") },
            )

        val first = coordinator.reconcile(expected.expectedInstallId)
        val second = coordinator.reconcile(expected.expectedInstallId)

        assertEquals(UpdateLifecyclePhase.EFFECT_UNKNOWN, first.phase)
        assertEquals(UpdateFailureKind.EFFECT_UNKNOWN, first.failure?.kind)
        assertEquals(first, second)
    }

    @Test
    fun `session evidence with wrong durable identity is rejected fail closed`() {
        val expected = expectedInstall()
        val store = MemoryStore(entry(expected, UpdateLifecyclePhase.OS_HANDOFF, "42"))
        val coordinator =
            coordinator(
                store = store,
                installed = expected.installedIdentityAtPreparation,
                inspect = { request ->
                    UpdateInstallSessionObservation(
                        expectedInstallId = ExpectedInstallId(request.expectedInstallId),
                        platformSessionIdentity = "99",
                        kind = UpdateInstallObservationKind.SESSION_ACTIVE,
                    )
                },
            )

        assertThrows(IllegalArgumentException::class.java) {
            coordinator.reconcile(expected.expectedInstallId)
        }
        assertEquals(UpdateLifecyclePhase.OS_HANDOFF, store.read(expected.expectedInstallId)?.phase)
    }

    private fun coordinator(
        store: MemoryStore,
        installed: InstalledUpdateIdentity,
        inspect: (UpdateInstallSessionInspectionRequest) -> UpdateInstallSessionObservation,
        abandon: (UpdateInstallSessionInspectionRequest) -> Unit = {},
        cleanup: (StagedUpdateArtifactId) -> Unit = {},
    ): UpdateReconcileCoordinator =
        UpdateReconcileCoordinator(
            store = store,
            installedIdentity = InstalledUpdateIdentityPort { installed },
            sessionInspection = UpdateInstallSessionInspectionPort(inspect),
            sessionCleanup = UpdateInstallSessionCleanupPort(abandon),
            artifactCleanup = UpdateArtifactCleanupPort(cleanup),
        )

    private fun observation(
        request: UpdateInstallSessionInspectionRequest,
        kind: UpdateInstallObservationKind,
    ): UpdateInstallSessionObservation =
        UpdateInstallSessionObservation(
            expectedInstallId = ExpectedInstallId(request.expectedInstallId),
            platformSessionIdentity = request.platformSessionIdentity,
            kind = kind,
        )

    private fun targetIdentity(expected: ExpectedInstall): InstalledUpdateIdentity =
        InstalledUpdateIdentity(
            packageName = expected.packageName,
            versionCode = expected.targetVersionCode,
            signingIdentity = expected.staged.artifactIdentity.signingIdentity,
        )

    private fun entry(
        expected: ExpectedInstall,
        phase: UpdateLifecyclePhase,
        session: String?,
        failure: UpdateFailure? = null,
    ): UpdateControlEntry =
        UpdateControlEntry(
            expectedInstall = expected,
            phase = phase,
            platformSessionIdentity = session,
            failure = failure,
        )

    private fun expectedInstall(): ExpectedInstall {
        val sourceSigner = digest("11")
        val targetSigner = digest("22")
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
                    signingIdentity = UpdateSigningIdentity.Lineage(listOf(sourceSigner)),
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
                            signingIdentity =
                                UpdateSigningIdentity.Lineage(
                                    listOf(sourceSigner, targetSigner),
                                ),
                            byteCount = candidate.declaredSizeBytes,
                            sha256 = candidate.artifactSha256,
                        ),
                ),
        )
    }

    private fun digest(prefix: String): UpdateSha256 =
        UpdateSha256(prefix.repeat(64 / prefix.length))

    private class MemoryStore(
        private var entry: UpdateControlEntry,
    ) : UpdateControlStorePort {
        override fun read(expectedInstallId: ExpectedInstallId): UpdateControlEntry? =
            entry.takeIf { it.expectedInstallId == expectedInstallId }

        override fun pending(): List<UpdateControlEntry> =
            entry.takeIf { !UpdateLifecycleTransitionPolicy.isTerminal(it.phase) }
                ?.let(::listOf)
                ?: emptyList()

        override fun create(expectedInstall: ExpectedInstall) {
            error("not used")
        }

        override fun compareAndSet(
            expected: UpdateControlEntry,
            updated: UpdateControlEntry,
        ): Boolean {
            if (entry != expected) {
                return false
            }
            UpdateLifecycleTransitionPolicy.transition(expected.phase, updated.phase)
            require(updated.expectedInstall == expected.expectedInstall)
            require(updated.platformSessionIdentity == expected.platformSessionIdentity)
            entry = updated
            return true
        }
    }
}
