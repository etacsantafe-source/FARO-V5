package ar.com.faro.v5.maintenance

class UpdateInstallControlCoordinator(
    private val store: UpdateControlStorePort,
) {
    fun prepare(expected: ExpectedInstall): UpdateControlEntry {
        val existing = store.read(expected.expectedInstallId)
        if (existing == null) {
            store.create(expected)
            return checkNotNull(store.read(expected.expectedInstallId)) {
                "update control missing after create"
            }
        }

        require(existing.expectedInstall == expected) {
            "existing update control does not match expected install"
        }
        require(existing.phase == UpdateLifecyclePhase.INSTALL_PREPARED) {
            "update install is not in prepared phase"
        }
        return existing
    }

    fun bindPlatformSession(
        prepared: UpdateControlEntry,
        platformSessionIdentity: String,
    ): UpdateControlEntry {
        require(prepared.phase == UpdateLifecyclePhase.INSTALL_PREPARED) {
            "platform session may bind only while install is prepared"
        }
        require(prepared.platformSessionIdentity == null) {
            "platform session is already bound"
        }

        val updated =
            UpdateControlEntry(
                expectedInstall = prepared.expectedInstall,
                phase = UpdateLifecyclePhase.INSTALL_PREPARED,
                platformSessionIdentity = platformSessionIdentity,
            )
        check(store.compareAndSet(prepared, updated)) {
            "stale update control while binding platform session"
        }
        return updated
    }

    fun markOsHandoff(bound: UpdateControlEntry): UpdateControlEntry {
        require(bound.phase == UpdateLifecyclePhase.INSTALL_PREPARED) {
            "OS handoff requires prepared install"
        }
        require(bound.platformSessionIdentity != null) {
            "OS handoff requires durable platform session identity"
        }

        val updated =
            UpdateControlEntry(
                expectedInstall = bound.expectedInstall,
                phase = UpdateLifecyclePhase.OS_HANDOFF,
                platformSessionIdentity = bound.platformSessionIdentity,
            )
        check(store.compareAndSet(bound, updated)) {
            "stale update control while marking OS handoff"
        }
        return updated
    }

    fun markFailed(
        current: UpdateControlEntry,
        failure: UpdateFailure,
    ): UpdateControlEntry {
        require(
            current.phase == UpdateLifecyclePhase.INSTALL_PREPARED ||
                current.phase == UpdateLifecyclePhase.OS_HANDOFF
        ) {
            "install failure can be recorded only before callback/reconcile phases"
        }
        require(
            failure.kind != UpdateFailureKind.CANCELLED &&
                failure.kind != UpdateFailureKind.EFFECT_UNKNOWN
        ) {
            "known install failure must be terminal and non-uncertain"
        }

        val updated =
            UpdateControlEntry(
                expectedInstall = current.expectedInstall,
                phase = UpdateLifecyclePhase.FAILED,
                platformSessionIdentity = current.platformSessionIdentity,
                failure = failure,
            )
        check(store.compareAndSet(current, updated)) {
            "stale update control while recording install failure"
        }
        return updated
    }

    fun markEffectUnknown(
        handoff: UpdateControlEntry,
        reason: String,
    ): UpdateControlEntry {
        require(handoff.phase == UpdateLifecyclePhase.OS_HANDOFF) {
            "effect unknown requires OS handoff phase"
        }
        require(handoff.platformSessionIdentity != null) {
            "effect unknown requires platform session identity"
        }

        val updated =
            UpdateControlEntry(
                expectedInstall = handoff.expectedInstall,
                phase = UpdateLifecyclePhase.EFFECT_UNKNOWN,
                platformSessionIdentity = handoff.platformSessionIdentity,
                failure =
                    UpdateFailure(
                        kind = UpdateFailureKind.EFFECT_UNKNOWN,
                        reason = reason,
                    ),
            )
        check(store.compareAndSet(handoff, updated)) {
            "stale update control while recording unknown install effect"
        }
        return updated
    }
}
