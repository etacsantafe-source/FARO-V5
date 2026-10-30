package ar.com.faro.v5.control

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ar.com.faro.v5.control.spi.SecretId
import ar.com.faro.v5.kernel.contracts.AiMonetaryBudget
import ar.com.faro.v5.kernel.contracts.ModelBindingId
import ar.com.faro.v5.kernel.contracts.ModelProfileId
import ar.com.faro.v5.kernel.contracts.ModelProfileRef
import ar.com.faro.v5.kernel.contracts.ModelProfileVersion
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

data class AiControlUpdateRequest(
    val remoteReasoningEnabled: Boolean,
    val activeProfile: ModelProfileRef,
    val budget: AiMonetaryBudget,
    val credentialRefs: List<AiBindingCredentialRef>,
    val expectedRevision: Long,
) {
    init {
        require(expectedRevision >= 0L) {
            "expected AI control revision must be non-negative"
        }
        val bindingIds = credentialRefs.map { it.bindingId }
        require(bindingIds.size == bindingIds.distinct().size) {
            "AI credential refs must have unique binding ids"
        }
    }
}

sealed interface AiControlUpdateOutcome {
    data class Committed(
        val snapshot: AiControlSnapshot,
        val changed: Boolean,
    ) : AiControlUpdateOutcome

    data class RejectedStale(
        val current: AiControlSnapshot,
        val expectedRevision: Long,
    ) : AiControlUpdateOutcome
}

class AiControlPreferenceStore(
    private val file: File,
    private val defaultProfile: ModelProfileRef,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = {
            require(file.name.endsWith(".preferences_pb")) {
                "AI control DataStore file must end with .preferences_pb"
            }
            file
        },
    )

    suspend fun read(): AiControlSnapshot =
        dataStore.data.first().toSnapshot()

    suspend fun update(request: AiControlUpdateRequest): AiControlUpdateOutcome {
        var outcome: AiControlUpdateOutcome? = null

        dataStore.updateData { currentPreferences ->
            val current = currentPreferences.toSnapshot()
            if (current.revision != request.expectedRevision) {
                outcome = AiControlUpdateOutcome.RejectedStale(
                    current = current,
                    expectedRevision = request.expectedRevision,
                )
                return@updateData currentPreferences
            }

            val normalizedRefs =
                request.credentialRefs.sortedBy { it.bindingId.value }
            val changed =
                current.remoteReasoningEnabled != request.remoteReasoningEnabled ||
                    current.activeProfile != request.activeProfile ||
                    current.budget != request.budget ||
                    current.credentialRefs != normalizedRefs
            val nextRevision =
                if (changed) Math.addExact(current.revision, 1L)
                else current.revision

            val next = currentPreferences.toMutablePreferences()
            next[REMOTE_ENABLED] = request.remoteReasoningEnabled
            next[PROFILE_ID] = request.activeProfile.profileId.value
            next[PROFILE_VERSION] = request.activeProfile.version.value
            next[CREDENTIAL_REFS] =
                normalizedRefs.map(::encodeCredentialRef).toSet()
            next[REVISION] = nextRevision

            when (val budget = request.budget) {
                AiMonetaryBudget.Disabled -> {
                    next[BUDGET_MODE] = BUDGET_DISABLED
                    next.remove(BUDGET_CURRENCY)
                    next.remove(BUDGET_REMAINING_MINOR)
                }

                is AiMonetaryBudget.Capped -> {
                    next[BUDGET_MODE] = BUDGET_CAPPED
                    next[BUDGET_CURRENCY] = budget.currencyCode
                    next[BUDGET_REMAINING_MINOR] = budget.remainingMinor
                }
            }

            val snapshot = AiControlSnapshot(
                remoteReasoningEnabled = request.remoteReasoningEnabled,
                activeProfile = request.activeProfile,
                budget = request.budget,
                credentialRefs = normalizedRefs,
                revision = nextRevision,
            )
            outcome = AiControlUpdateOutcome.Committed(
                snapshot = snapshot,
                changed = changed,
            )
            next
        }

        return checkNotNull(outcome) {
            "AI control DataStore update completed without a typed outcome"
        }
    }

    override fun close() {
        scope.cancel()
    }

    private fun androidx.datastore.preferences.core.Preferences.toSnapshot(): AiControlSnapshot {
        val storedProfileId = this[PROFILE_ID]
        val storedProfileVersion = this[PROFILE_VERSION]
        val profile =
            when {
                storedProfileId == null && storedProfileVersion == null ->
                    defaultProfile
                storedProfileId != null && storedProfileVersion != null ->
                    ModelProfileRef(
                        profileId = ModelProfileId(storedProfileId),
                        version = ModelProfileVersion(storedProfileVersion),
                    )
                else ->
                    error("incomplete AI control profile state")
            }

        val budget =
            when (val mode = this[BUDGET_MODE]) {
                null, BUDGET_DISABLED ->
                    AiMonetaryBudget.Disabled
                BUDGET_CAPPED ->
                    AiMonetaryBudget.Capped(
                        currencyCode = checkNotNull(this[BUDGET_CURRENCY]) {
                            "capped AI budget missing currency"
                        },
                        remainingMinor = checkNotNull(this[BUDGET_REMAINING_MINOR]) {
                            "capped AI budget missing remaining amount"
                        },
                    )
                else ->
                    error("unsupported AI budget mode: $mode")
            }

        val refs =
            (this[CREDENTIAL_REFS] ?: emptySet())
                .map(::decodeCredentialRef)
                .sortedBy { it.bindingId.value }

        return AiControlSnapshot(
            remoteReasoningEnabled = this[REMOTE_ENABLED] ?: false,
            activeProfile = profile,
            budget = budget,
            credentialRefs = refs,
            revision = this[REVISION] ?: 0L,
        )
    }

    companion object {
        private const val BUDGET_DISABLED = "disabled"
        private const val BUDGET_CAPPED = "capped"

        private val REMOTE_ENABLED =
            booleanPreferencesKey("ai.remote_reasoning_enabled")
        private val PROFILE_ID =
            stringPreferencesKey("ai.profile.id")
        private val PROFILE_VERSION =
            intPreferencesKey("ai.profile.version")
        private val BUDGET_MODE =
            stringPreferencesKey("ai.budget.mode")
        private val BUDGET_CURRENCY =
            stringPreferencesKey("ai.budget.currency")
        private val BUDGET_REMAINING_MINOR =
            longPreferencesKey("ai.budget.remaining_minor")
        private val CREDENTIAL_REFS =
            stringSetPreferencesKey("ai.credential_refs")
        private val REVISION =
            longPreferencesKey("ai.revision")

        internal fun encodeCredentialRef(ref: AiBindingCredentialRef): String =
            "${ref.bindingId.value.length}:${ref.bindingId.value}${ref.secretId.value()}"

        internal fun decodeCredentialRef(encoded: String): AiBindingCredentialRef {
            val separator = encoded.indexOf(':')
            require(separator > 0) {
                "invalid AI credential reference encoding"
            }
            val bindingLength =
                encoded.substring(0, separator).toIntOrNull()
                    ?: error("invalid AI credential binding length")
            require(bindingLength > 0) {
                "AI credential binding length must be positive"
            }
            val bindingStart = separator + 1
            val bindingEnd = bindingStart + bindingLength
            require(bindingEnd < encoded.length) {
                "incomplete AI credential reference encoding"
            }
            return AiBindingCredentialRef(
                bindingId = ModelBindingId(
                    encoded.substring(bindingStart, bindingEnd)
                ),
                secretId = SecretId(
                    encoded.substring(bindingEnd)
                ),
            )
        }
    }
}
