package ar.com.faro.v5.maintenance

class UpdateReconcileCoordinator(
    private val store: UpdateControlStorePort,
    private val installedIdentity: InstalledUpdateIdentityPort,
    private val sessionInspection: UpdateInstallSessionInspectionPort,
    private val sessionCleanup: UpdateInstallSessionCleanupPort,
    private val artifactCleanup: UpdateArtifactCleanupPort,
) {
    fun reconcilePending(): List<UpdateControlEntry> =
        store.pending()
            .map { it.expectedInstallId }
            .distinct()
            .map(::reconcile)

    fun reconcile(expectedInstallId: ExpectedInstallId): UpdateControlEntry {
        repeat(MAX_RECONCILE_ATTEMPTS) {
            val current =
                checkNotNull(store.read(expectedInstallId)) {
                    "update reconcile has no durable expected-install control"
                }
            if (UpdateLifecycleTransitionPolicy.isTerminal(current.phase)) {
                return current
            }

            when (current.phase) {
                UpdateLifecyclePhase.INSTALL_PREPARED -> {
                    val cancelled =
                        current.copy(
                            phase = UpdateLifecyclePhase.CANCELLED,
                        )
                    if (!store.compareAndSet(current, cancelled)) {
                        return@repeat
                    }
                    cleanupPreparedSession(cancelled)
                    cleanupArtifact(cancelled)
                    return cancelled
                }

                UpdateLifecyclePhase.OS_HANDOFF,
                UpdateLifecyclePhase.AWAITING_USER_ACTION,
                UpdateLifecyclePhase.RECONCILE_REQUIRED,
                UpdateLifecyclePhase.EFFECT_UNKNOWN,
                -> {
                    val installed =
                        try {
                            installedIdentity.current()
                        } catch (_: RuntimeException) {
                            val unknown = markInspectionUnknown(current)
                            if (unknown != null) {
                                return unknown
                            }
                            return@repeat
                        }

                    if (installed == targetIdentity(current.expectedInstall)) {
                        val reconciled = ensureReconcileRequired(current)
                        if (reconciled == null) {
                            return@repeat
                        }
                        if (reconciled.phase != UpdateLifecyclePhase.RECONCILE_REQUIRED) {
                            return@repeat
                        }
                        val terminal =
                            reconciled.copy(
                                phase = UpdateLifecyclePhase.INSTALLED,
                                failure = null,
                            )
                        if (!store.compareAndSet(reconciled, terminal)) {
                            return@repeat
                        }
                        cleanupArtifact(terminal)
                        return terminal
                    }

                    val request = inspectionRequest(current)
                    val observation =
                        try {
                            sessionInspection.inspect(request)
                        } catch (_: RuntimeException) {
                            val unknown = markInspectionUnknown(current)
                            if (unknown != null) {
                                return unknown
                            }
                            return@repeat
                        }
                    requireObservationMatches(request, observation)

                    when (observation.kind) {
                        UpdateInstallObservationKind.SESSION_ACTIVE -> {
                            if (
                                current.phase == UpdateLifecyclePhase.OS_HANDOFF ||
                                    current.phase == UpdateLifecyclePhase.AWAITING_USER_ACTION ||
                                    current.phase == UpdateLifecyclePhase.RECONCILE_REQUIRED
                            ) {
                                return current
                            }
                            val reconciled = ensureReconcileRequired(current)
                            if (reconciled == null) {
                                return@repeat
                            }
                            return reconciled
                        }

                        UpdateInstallObservationKind.PENDING_USER_ACTION -> {
                            if (current.phase == UpdateLifecyclePhase.AWAITING_USER_ACTION) {
                                return current
                            }
                            if (current.phase == UpdateLifecyclePhase.OS_HANDOFF) {
                                val awaiting =
                                    current.copy(
                                        phase = UpdateLifecyclePhase.AWAITING_USER_ACTION,
                                        failure = null,
                                    )
                                if (!store.compareAndSet(current, awaiting)) {
                                    return@repeat
                                }
                                return awaiting
                            }
                            val reconciled = ensureReconcileRequired(current)
                            if (reconciled == null) {
                                return@repeat
                            }
                            return reconciled
                        }

                        UpdateInstallObservationKind.SUCCESS_REPORTED -> {
                            val reconciled = ensureReconcileRequired(current)
                            if (reconciled == null) {
                                return@repeat
                            }
                            return reconciled
                        }

                        UpdateInstallObservationKind.FAILURE_REPORTED -> {
                            val failure = checkNotNull(observation.failure)
                            val failed = markFailed(current, failure)
                            if (failed != null) {
                                return failed
                            }
                            return@repeat
                        }

                        UpdateInstallObservationKind.SESSION_MISSING -> {
                            val confirmedInstalled =
                                try {
                                    installedIdentity.current()
                                } catch (_: RuntimeException) {
                                    val unknown = markInspectionUnknown(current)
                                    if (unknown != null) {
                                        return unknown
                                    }
                                    return@repeat
                                }
                            if (confirmedInstalled == targetIdentity(current.expectedInstall)) {
                                return@repeat
                            }

                            val failure =
                                if (confirmedInstalled == current.expectedInstall.installedIdentityAtPreparation) {
                                    UpdateFailure(
                                        kind = UpdateFailureKind.SESSION_LOST,
                                        reason = "package_installer_session_missing_before_target_install",
                                    )
                                } else {
                                    UpdateFailure(
                                        kind = UpdateFailureKind.INSTALLED_IDENTITY_MISMATCH,
                                        reason = "installed_identity_does_not_match_expected_target",
                                    )
                                }
                            val failed = markFailed(current, failure)
                            if (failed != null) {
                                return failed
                            }
                            return@repeat
                        }

                        UpdateInstallObservationKind.EFFECT_UNKNOWN -> {
                            val failure = checkNotNull(observation.failure)
                            if (current.phase == UpdateLifecyclePhase.EFFECT_UNKNOWN) {
                                require(current.failure == failure) {
                                    "reconcile uncertainty changed durable failure"
                                }
                                return current
                            }
                            val unknown =
                                current.copy(
                                    phase = UpdateLifecyclePhase.EFFECT_UNKNOWN,
                                    failure = failure,
                                )
                            if (!store.compareAndSet(current, unknown)) {
                                return@repeat
                            }
                            return unknown
                        }
                    }
                }

                else -> error("unexpected pending update phase: ${current.phase}")
            }
        }
        error("update reconcile did not converge after concurrent durable changes")
    }

    private fun ensureReconcileRequired(current: UpdateControlEntry): UpdateControlEntry? {
        if (current.phase == UpdateLifecyclePhase.RECONCILE_REQUIRED) {
            return current
        }
        val updated =
            current.copy(
                phase = UpdateLifecyclePhase.RECONCILE_REQUIRED,
                failure = null,
            )
        return if (store.compareAndSet(current, updated)) updated else null
    }

    private fun markFailed(
        current: UpdateControlEntry,
        failure: UpdateFailure,
    ): UpdateControlEntry? {
        require(
            failure.kind != UpdateFailureKind.CANCELLED &&
                failure.kind != UpdateFailureKind.EFFECT_UNKNOWN
        ) {
            "reconcile terminal failure must be known"
        }

        val base =
            if (current.phase == UpdateLifecyclePhase.EFFECT_UNKNOWN) {
                ensureReconcileRequired(current) ?: return null
            } else {
                current
            }
        val failed =
            base.copy(
                phase = UpdateLifecyclePhase.FAILED,
                failure = failure,
            )
        if (!store.compareAndSet(base, failed)) {
            return null
        }
        cleanupArtifact(failed)
        return failed
    }

    private fun markInspectionUnknown(current: UpdateControlEntry): UpdateControlEntry? {
        if (current.phase == UpdateLifecyclePhase.EFFECT_UNKNOWN) {
            return current
        }
        val failure =
            UpdateFailure(
                kind = UpdateFailureKind.EFFECT_UNKNOWN,
                reason = "update_reconcile_platform_inspection_unknown",
            )
        val unknown =
            current.copy(
                phase = UpdateLifecyclePhase.EFFECT_UNKNOWN,
                failure = failure,
            )
        return if (store.compareAndSet(current, unknown)) unknown else null
    }

    private fun targetIdentity(expected: ExpectedInstall): InstalledUpdateIdentity =
        InstalledUpdateIdentity(
            packageName = expected.packageName,
            versionCode = expected.targetVersionCode,
            signingIdentity = expected.staged.artifactIdentity.signingIdentity,
        )

    private fun inspectionRequest(current: UpdateControlEntry): UpdateInstallSessionInspectionRequest =
        UpdateInstallSessionInspectionRequest(
            expectedInstallId = current.expectedInstallId.value,
            platformSessionIdentity =
                checkNotNull(current.platformSessionIdentity) {
                    "handed-off update is missing durable platform session identity"
                },
            packageName = current.expectedInstall.packageName,
        )

    private fun requireObservationMatches(
        request: UpdateInstallSessionInspectionRequest,
        observation: UpdateInstallSessionObservation,
    ) {
        require(observation.expectedInstallId.value == request.expectedInstallId) {
            "session inspection expected-install identity mismatch"
        }
        require(observation.platformSessionIdentity == request.platformSessionIdentity) {
            "session inspection platform identity mismatch"
        }
    }

    private fun cleanupPreparedSession(cancelled: UpdateControlEntry) {
        if (cancelled.platformSessionIdentity == null) {
            return
        }
        runCatching {
            sessionCleanup.abandon(inspectionRequest(cancelled))
        }
    }

    private fun cleanupArtifact(entry: UpdateControlEntry) {
        runCatching {
            artifactCleanup.cleanup(entry.expectedInstall.staged.artifactId)
        }
    }

    private companion object {
        const val MAX_RECONCILE_ATTEMPTS = 8
    }
}
