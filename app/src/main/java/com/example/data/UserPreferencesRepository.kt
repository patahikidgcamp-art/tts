package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppThemeMode
import com.example.model.AudioFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vanivox_prefs")

data class UserSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val selectedLanguageTag: String = "hi-IN",
    val selectedProfileId: String = "female_sweet",
    val selectedSystemVoiceName: String? = null,
    val offlineOnlyMode: Boolean = true,
    val batterySaverMode: Boolean = true,
    val pitchOverride: Float = 1.0f,
    val speedOverride: Float = 1.0f,
    val pauseCadenceMs: Long = 240L,
    val preferredExportFormat: AudioFormat = AudioFormat.MP3
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val PROFILE_ID = stringPreferencesKey("profile_id")
        val SYSTEM_VOICE_NAME = stringPreferencesKey("system_voice_name")
        val OFFLINE_ONLY = booleanPreferencesKey("offline_only")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val PITCH_OVERRIDE = floatPreferencesKey("pitch_override")
        val SPEED_OVERRIDE = floatPreferencesKey("speed_override")
        val PAUSE_CADENCE_MS = longPreferencesKey("pause_cadence_ms")
        val EXPORT_FORMAT = stringPreferencesKey("export_format")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.SYSTEM,
            selectedLanguageTag = prefs[Keys.LANGUAGE_TAG] ?: "hi-IN",
            selectedProfileId = prefs[Keys.PROFILE_ID] ?: "female_sweet",
            selectedSystemVoiceName = prefs[Keys.SYSTEM_VOICE_NAME],
            offlineOnlyMode = prefs[Keys.OFFLINE_ONLY] ?: true,
            batterySaverMode = prefs[Keys.BATTERY_SAVER] ?: true,
            pitchOverride = prefs[Keys.PITCH_OVERRIDE] ?: 1.0f,
            speedOverride = prefs[Keys.SPEED_OVERRIDE] ?: 1.0f,
            pauseCadenceMs = prefs[Keys.PAUSE_CADENCE_MS] ?: 240L,
            preferredExportFormat = prefs[Keys.EXPORT_FORMAT]?.let {
                runCatching { AudioFormat.valueOf(it) }.getOrNull()
            } ?: AudioFormat.MP3
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguageTag(tag: String) {
        context.dataStore.edit {
            it[Keys.LANGUAGE_TAG] = tag
            it.remove(Keys.SYSTEM_VOICE_NAME)
        }
    }

    suspend fun setVocalProfile(profileId: String, pauseMs: Long) {
        context.dataStore.edit {
            it[Keys.PROFILE_ID] = profileId
            it[Keys.PAUSE_CADENCE_MS] = pauseMs
            it.remove(Keys.SYSTEM_VOICE_NAME)
        }
    }

    suspend fun setSystemVoiceName(voiceName: String?) {
        context.dataStore.edit {
            if (voiceName.isNullOrBlank()) {
                it.remove(Keys.SYSTEM_VOICE_NAME)
            } else {
                it[Keys.SYSTEM_VOICE_NAME] = voiceName
            }
        }
    }

    suspend fun setOfflineOnlyMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OFFLINE_ONLY] = enabled }
    }

    suspend fun setBatterySaverMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BATTERY_SAVER] = enabled }
    }

    suspend fun setPitchOverride(pitch: Float) {
        context.dataStore.edit { it[Keys.PITCH_OVERRIDE] = pitch.coerceIn(0.6f, 1.5f) }
    }

    suspend fun setSpeedOverride(speed: Float) {
        context.dataStore.edit { it[Keys.SPEED_OVERRIDE] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setPauseCadenceMs(ms: Long) {
        context.dataStore.edit { it[Keys.PAUSE_CADENCE_MS] = ms.coerceIn(0L, 600L) }
    }

    suspend fun setPreferredExportFormat(format: AudioFormat) {
        context.dataStore.edit { it[Keys.EXPORT_FORMAT] = format.name }
    }
}
