package ar.com.faro.v5.control

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

internal class OnboardingPreferenceStore(
    private val file: File,
    private val currentVersion: Int,
) : AutoCloseable {
    init {
        require(currentVersion > 0) { "current onboarding version must be positive" }
        require(file.name.endsWith(".preferences_pb")) {
            "Onboarding DataStore file must end with .preferences_pb"
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { file },
    )

    suspend fun read(): OnboardingSnapshot {
        if (!file.exists()) {
            return OnboardingSnapshot(
                currentVersion = currentVersion,
                storedVersion = 0,
                lifecycle = OnboardingLifecycle.PENDING,
                revision = 0L,
            )
        }
        return dataStore.data.first().toSnapshot()
    }

    suspend fun mutate(request: OnboardingMutationRequest): OnboardingMutationOutcome {
        require(request.onboardingVersion == currentVersion) {
            "request onboarding version must equal current version"
        }
        file.parentFile?.let { parent ->
            check(parent.exists() || parent.mkdirs()) {
                "cannot create onboarding control directory"
            }
        }

        var outcome: OnboardingMutationOutcome? = null
        dataStore.updateData { currentPreferences ->
            val current = currentPreferences.toSnapshot()

            if (current.storedVersion > currentVersion) {
                outcome = OnboardingMutationOutcome.RejectedFutureVersion(current)
                return@updateData currentPreferences
            }

            val lastKey = currentPreferences[LAST_IDEMPOTENCY_KEY]
            if (lastKey == request.idempotencyKey.value) {
                val lastTarget = currentPreferences[LAST_TARGET]
                val lastOutcomeRevision = currentPreferences[LAST_OUTCOME_REVISION]
                outcome =
                    if (
                        lastTarget == request.target.name &&
                        lastOutcomeRevision == current.revision
                    ) {
                        OnboardingMutationOutcome.Committed(
                            snapshot = current,
                            changed = false,
                            replayed = true,
                            idempotencyKey = request.idempotencyKey,
                        )
                    } else {
                        OnboardingMutationOutcome.RejectedIdentityConflict(
                            snapshot = current,
                            idempotencyKey = request.idempotencyKey,
                        )
                    }
                return@updateData currentPreferences
            }

            if (current.revision != request.expectedRevision) {
                outcome = OnboardingMutationOutcome.RejectedStale(
                    snapshot = current,
                    expectedRevision = request.expectedRevision,
                )
                return@updateData currentPreferences
            }

            val changed =
                current.storedVersion != currentVersion ||
                    current.lifecycle != request.target
            val nextRevision =
                if (changed) Math.addExact(current.revision, 1L)
                else current.revision

            val next = currentPreferences.toMutablePreferences()
            next[STORED_VERSION] = currentVersion
            next[LIFECYCLE] = request.target.name
            next[REVISION] = nextRevision
            next[LAST_IDEMPOTENCY_KEY] = request.idempotencyKey.value
            next[LAST_TARGET] = request.target.name
            next[LAST_OUTCOME_REVISION] = nextRevision

            val snapshot = OnboardingSnapshot(
                currentVersion = currentVersion,
                storedVersion = currentVersion,
                lifecycle = request.target,
                revision = nextRevision,
            )
            outcome = OnboardingMutationOutcome.Committed(
                snapshot = snapshot,
                changed = changed,
                replayed = false,
                idempotencyKey = request.idempotencyKey,
            )
            next
        }

        return checkNotNull(outcome) {
            "Onboarding DataStore update completed without a typed outcome"
        }
    }

    override fun close() {
        scope.cancel()
    }

    private fun androidx.datastore.preferences.core.Preferences.toSnapshot(): OnboardingSnapshot {
        val storedVersion = this[STORED_VERSION] ?: 0
        val storedLifecycle =
            this[LIFECYCLE]?.let { encoded ->
                runCatching { OnboardingLifecycle.valueOf(encoded) }
                    .getOrElse { error("unsupported onboarding lifecycle: $encoded") }
            } ?: OnboardingLifecycle.PENDING
        val effectiveLifecycle =
            if (storedVersion < currentVersion) OnboardingLifecycle.PENDING
            else storedLifecycle

        return OnboardingSnapshot(
            currentVersion = currentVersion,
            storedVersion = storedVersion,
            lifecycle = effectiveLifecycle,
            revision = this[REVISION] ?: 0L,
        )
    }

    companion object {
        private val STORED_VERSION = intPreferencesKey("onboarding.version")
        private val LIFECYCLE = stringPreferencesKey("onboarding.lifecycle")
        private val REVISION = longPreferencesKey("onboarding.revision")
        private val LAST_IDEMPOTENCY_KEY =
            stringPreferencesKey("onboarding.last_idempotency_key")
        private val LAST_TARGET =
            stringPreferencesKey("onboarding.last_target")
        private val LAST_OUTCOME_REVISION =
            longPreferencesKey("onboarding.last_outcome_revision")
    }
}
