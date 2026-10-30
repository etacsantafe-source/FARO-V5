package ar.com.faro.v5.maintenance

enum class UpdateInstallCallbackKind {
    PENDING_USER_ACTION,
    SUCCESS_REPORTED,
    FAILURE_REPORTED,
    EFFECT_UNKNOWN,
}

data class UpdateInstallCallbackEvent(
    val expectedInstallId: String,
    val platformSessionIdentity: String,
    val kind: UpdateInstallCallbackKind,
    val failure: UpdateFailure? = null,
) {
    init {
        require(expectedInstallId.isNotBlank()) {
            "callback expected-install id must not be blank"
        }
        require(expectedInstallId == expectedInstallId.trim()) {
            "callback expected-install id must be canonical"
        }
        require(platformSessionIdentity.isNotBlank()) {
            "callback platform session identity must not be blank"
        }
        require(platformSessionIdentity == platformSessionIdentity.trim()) {
            "callback platform session identity must be canonical"
        }
        val requiresFailure =
            kind == UpdateInstallCallbackKind.FAILURE_REPORTED ||
                kind == UpdateInstallCallbackKind.EFFECT_UNKNOWN
        require(requiresFailure == (failure != null)) {
            "callback failure/uncertainty must carry exactly one typed failure"
        }
        if (kind == UpdateInstallCallbackKind.EFFECT_UNKNOWN) {
            require(
                failure?.kind == UpdateFailureKind.EFFECT_UNKNOWN ||
                    failure?.kind == UpdateFailureKind.SESSION_LOST
            ) {
                "unknown callback requires uncertainty failure"
            }
        }
    }
}

fun interface UpdateInstallCallbackPort {
    fun onCallback(event: UpdateInstallCallbackEvent): UpdateControlEntry
}

class UpdateInstallCallbackCoordinator(
    private val store: UpdateControlStorePort,
) : UpdateInstallCallbackPort {
    override fun onCallback(event: UpdateInstallCallbackEvent): UpdateControlEntry {
        val expectedInstallId = ExpectedInstallId(event.expectedInstallId)
        val current =
            checkNotNull(store.read(expectedInstallId)) {
                "update callback has no durable expected-install control"
            }
        require(current.platformSessionIdentity == event.platformSessionIdentity) {
            "update callback session identity mismatch"
        }
        if (
            UpdateLifecycleTransitionPolicy.isTerminal(current.phase) ||
                current.phase == UpdateLifecyclePhase.RECONCILE_REQUIRED
        ) {
            return current
        }

        return when (event.kind) {
            UpdateInstallCallbackKind.PENDING_USER_ACTION ->
                pendingUserAction(current)

            UpdateInstallCallbackKind.SUCCESS_REPORTED ->
                successReported(current)

            UpdateInstallCallbackKind.FAILURE_REPORTED ->
                failureReported(current, checkNotNull(event.failure))

            UpdateInstallCallbackKind.EFFECT_UNKNOWN ->
                effectUnknown(current, checkNotNull(event.failure))
        }
    }

    private fun pendingUserAction(current: UpdateControlEntry): UpdateControlEntry {
        if (
            current.phase == UpdateLifecyclePhase.AWAITING_USER_ACTION ||
                current.phase == UpdateLifecyclePhase.EFFECT_UNKNOWN
        ) {
            return current
        }
        require(current.phase == UpdateLifecyclePhase.OS_HANDOFF) {
            "pending-user-action callback requires OS handoff"
        }
        return update(
            current,
            UpdateControlEntry(
                expectedInstall = current.expectedInstall,
                phase = UpdateLifecyclePhase.AWAITING_USER_ACTION,
                platformSessionIdentity = current.platformSessionIdentity,
            ),
        )
    }

    private fun successReported(current: UpdateControlEntry): UpdateControlEntry {
        if (current.phase == UpdateLifecyclePhase.RECONCILE_REQUIRED) {
            return current
        }
        if (current.phase == UpdateLifecyclePhase.EFFECT_UNKNOWN) {
            return update(
                current,
                current.copy(
                    phase = UpdateLifecyclePhase.RECONCILE_REQUIRED,
                    failure = null,
                ),
            )
        }
        require(
            current.phase == UpdateLifecyclePhase.OS_HANDOFF ||
                current.phase == UpdateLifecyclePhase.AWAITING_USER_ACTION
        ) {
            "success callback requires handed-off install"
        }
        return update(
            current,
            UpdateControlEntry(
                expectedInstall = current.expectedInstall,
                phase = UpdateLifecyclePhase.RECONCILE_REQUIRED,
                platformSessionIdentity = current.platformSessionIdentity,
            ),
        )
    }

    private fun failureReported(
        current: UpdateControlEntry,
        failure: UpdateFailure,
    ): UpdateControlEntry {
        if (current.phase == UpdateLifecyclePhase.EFFECT_UNKNOWN) {
            return update(
                current,
                current.copy(
                    phase = UpdateLifecyclePhase.RECONCILE_REQUIRED,
                    failure = null,
                ),
            )
        }
        if (current.phase == UpdateLifecyclePhase.FAILED) {
            require(current.failure == failure) {
                "duplicate failure callback changed typed failure"
            }
            return current
        }
        require(
            current.phase == UpdateLifecyclePhase.OS_HANDOFF ||
                current.phase == UpdateLifecyclePhase.AWAITING_USER_ACTION
        ) {
            "failure callback requires handed-off install"
        }
        require(
            failure.kind != UpdateFailureKind.CANCELLED &&
                failure.kind != UpdateFailureKind.EFFECT_UNKNOWN
        ) {
            "failure callback must carry known terminal failure"
        }
        return update(
            current,
            UpdateControlEntry(
                expectedInstall = current.expectedInstall,
                phase = UpdateLifecyclePhase.FAILED,
                platformSessionIdentity = current.platformSessionIdentity,
                failure = failure,
            ),
        )
    }

    private fun effectUnknown(
        current: UpdateControlEntry,
        failure: UpdateFailure,
    ): UpdateControlEntry {
        if (current.phase == UpdateLifecyclePhase.EFFECT_UNKNOWN) {
            return current
        }
        require(
            current.phase == UpdateLifecyclePhase.OS_HANDOFF ||
                current.phase == UpdateLifecyclePhase.AWAITING_USER_ACTION
        ) {
            "unknown callback requires handed-off install"
        }
        return update(
            current,
            UpdateControlEntry(
                expectedInstall = current.expectedInstall,
                phase = UpdateLifecyclePhase.EFFECT_UNKNOWN,
                platformSessionIdentity = current.platformSessionIdentity,
                failure = failure,
            ),
        )
    }

    private fun update(
        current: UpdateControlEntry,
        updated: UpdateControlEntry,
    ): UpdateControlEntry {
        check(store.compareAndSet(current, updated)) {
            "stale update control while applying installer callback"
        }
        return updated
    }
}
