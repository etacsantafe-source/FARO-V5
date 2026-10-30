package ar.com.faro.v5.control

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first

class PresentationPreferenceStore(
    private val file: File,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = {
            require(file.name.endsWith(".preferences_pb")) {
                "Presentation DataStore file must end with .preferences_pb"
            }
            file
        },
    )

    suspend fun read(): PresentationSnapshot =
        dataStore.data.first().toSnapshot()

    suspend fun setTheme(request: ThemeSetRequest): ThemeSetOutcome {
        var decision: ThemeSetOutcome? = null

        dataStore.updateData { currentPreferences ->
            val current = currentPreferences.toSnapshot()
            val lastKey = currentPreferences[LAST_THEME_IDEMPOTENCY_KEY]
            val lastTheme = currentPreferences[LAST_THEME_VALUE]
            val lastRevision = currentPreferences[LAST_THEME_OUTCOME_REVISION]
            val lastChanged = currentPreferences[LAST_THEME_CHANGED]

            if (lastKey == request.idempotencyKey.value) {
                decision =
                    if (
                        lastTheme == request.themeMode.name &&
                        lastRevision == current.revision &&
                        lastChanged != null
                    ) {
                        ThemeSetOutcome.Committed(
                            snapshot = current,
                            replayed = true,
                            changed = lastChanged,
                            idempotencyKey = request.idempotencyKey,
                        )
                    } else {
                        ThemeSetOutcome.RejectedIdentityConflict(
                            current = current,
                            idempotencyKey = request.idempotencyKey,
                        )
                    }
                return@updateData currentPreferences
            }

            if (current.revision != request.expectedRevision) {
                decision = ThemeSetOutcome.RejectedStale(
                    current = current,
                    expectedRevision = request.expectedRevision,
                )
                return@updateData currentPreferences
            }

            val changed = current.themeMode != request.themeMode
            val nextRevision =
                if (changed) Math.addExact(current.revision, 1L)
                else current.revision
            val next = currentPreferences.toMutablePreferences()
            next[THEME_MODE] = request.themeMode.name
            next[PRESENTATION_REVISION] = nextRevision
            next[LAST_THEME_IDEMPOTENCY_KEY] = request.idempotencyKey.value
            next[LAST_THEME_VALUE] = request.themeMode.name
            next[LAST_THEME_OUTCOME_REVISION] = nextRevision
            next[LAST_THEME_CHANGED] = changed

            val snapshot = PresentationSnapshot(
                themeMode = request.themeMode,
                revealAmounts = current.revealAmounts,
                revision = nextRevision,
            )
            decision = ThemeSetOutcome.Committed(
                snapshot = snapshot,
                replayed = false,
                changed = changed,
                idempotencyKey = request.idempotencyKey,
            )
            next
        }

        return checkNotNull(decision) {
            "Presentation DataStore update completed without a typed outcome"
        }
    }

    override fun close() {
        scope.cancel()
    }

    private fun Preferences.toSnapshot(): PresentationSnapshot {
        val rawTheme = this[THEME_MODE]
        val theme =
            rawTheme?.let { stored ->
                runCatching { PresentationThemeMode.valueOf(stored) }
                    .getOrElse {
                        error("unsupported presentation theme mode: $stored")
                    }
            } ?: PresentationThemeMode.SYSTEM

        return PresentationSnapshot(
            themeMode = theme,
            revealAmounts = this[REVEAL_AMOUNTS] ?: false,
            revision = this[PRESENTATION_REVISION] ?: 0L,
        )
    }

    companion object {
        private val THEME_MODE = stringPreferencesKey("presentation.theme_mode")
        private val REVEAL_AMOUNTS = booleanPreferencesKey("presentation.reveal_amounts")
        private val PRESENTATION_REVISION = longPreferencesKey("presentation.revision")

        private val LAST_THEME_IDEMPOTENCY_KEY =
            stringPreferencesKey("presentation.theme.last_idempotency_key")
        private val LAST_THEME_VALUE =
            stringPreferencesKey("presentation.theme.last_value")
        private val LAST_THEME_OUTCOME_REVISION =
            longPreferencesKey("presentation.theme.last_outcome_revision")
        private val LAST_THEME_CHANGED =
            booleanPreferencesKey("presentation.theme.last_changed")
    }
}
