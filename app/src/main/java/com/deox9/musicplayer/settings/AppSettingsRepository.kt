// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
private const val DEFAULT_WEB_HOME_URL = "https://m.youtube.com"

data class AppSettings(
    val webHomeUrl: String = DEFAULT_WEB_HOME_URL,
    val crossfadeEnabled: Boolean = false,
    val gaplessEnabled: Boolean = true,
    val darkThemeEnabled: Boolean = true,
    val suggestionsEnabled: Boolean = true,
    val eqEnabled: Boolean = false,
    val replayGainEnabled: Boolean = false,
    val replayGainDb: Float = 0f,
    val eqBandLevels: List<Int> = List(10) { 0 }
)

class AppSettingsRepository(private val context: Context) {

    fun observe(): Flow<AppSettings> {
        return context.appSettingsDataStore.data.map { prefs ->
            AppSettings(
                webHomeUrl = prefs[Keys.WEB_HOME_URL] ?: DEFAULT_WEB_HOME_URL,
                crossfadeEnabled = prefs[Keys.CROSSFADE_ENABLED] ?: false,
                gaplessEnabled = prefs[Keys.GAPLESS_ENABLED] ?: true,
                darkThemeEnabled = prefs[Keys.DARK_THEME_ENABLED] ?: true,
                suggestionsEnabled = prefs[Keys.SUGGESTIONS_ENABLED] ?: true,
                eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
                replayGainEnabled = prefs[Keys.REPLAY_GAIN_ENABLED] ?: false,
                replayGainDb = prefs[Keys.REPLAY_GAIN_DB] ?: 0f,
                eqBandLevels = decodeEqBands(prefs[Keys.EQ_BAND_LEVELS_JSON] ?: "[]")
            )
        }
    }

    suspend fun setWebHomeUrl(url: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.WEB_HOME_URL] = url
        }
    }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.CROSSFADE_ENABLED] = enabled
        }
    }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.GAPLESS_ENABLED] = enabled
        }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME_ENABLED] = enabled
        }
    }

    suspend fun setSuggestionsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.SUGGESTIONS_ENABLED] = enabled
        }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.EQ_ENABLED] = enabled
        }
    }

    suspend fun setReplayGainEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.REPLAY_GAIN_ENABLED] = enabled
        }
    }

    suspend fun setReplayGainDb(db: Float) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.REPLAY_GAIN_DB] = db.coerceIn(-18f, 0f)
        }
    }

    suspend fun setEqBandLevels(levels: List<Int>) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.EQ_BAND_LEVELS_JSON] = encodeEqBands(levels)
        }
    }

    suspend fun resetDefaults() {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.WEB_HOME_URL] = DEFAULT_WEB_HOME
            prefs[Keys.CROSSFADE_ENABLED] = false
            prefs[Keys.GAPLESS_ENABLED] = true
            prefs[Keys.DARK_THEME_ENABLED] = true
            prefs[Keys.SUGGESTIONS_ENABLED] = true
            prefs[Keys.EQ_ENABLED] = false
            prefs[Keys.REPLAY_GAIN_ENABLED] = false
            prefs[Keys.REPLAY_GAIN_DB] = 0f
            prefs[Keys.EQ_BAND_LEVELS_JSON] = encodeEqBands(List(10) { 0 })
        }
    }

    private fun decodeEqBands(raw: String): List<Int> {
        return try {
            val arr = JSONArray(raw)
            val decoded = MutableList(10) { 0 }
            for (i in 0 until minOf(arr.length(), decoded.size)) {
                decoded[i] = arr.optInt(i, 0).coerceIn(-1500, 1500)
            }
            decoded
        } catch (_: Exception) {
            List(10) { 0 }
        }
    }

    private fun encodeEqBands(levels: List<Int>): String {
        val normalized = MutableList(10) { idx ->
            levels.getOrElse(idx) { 0 }.coerceIn(-1500, 1500)
        }
        val arr = JSONArray()
        normalized.forEach { arr.put(it) }
        return arr.toString()
    }

    private object Keys {
        val WEB_HOME_URL = stringPreferencesKey("web_home_url")
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val GAPLESS_ENABLED = booleanPreferencesKey("gapless_enabled")
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val SUGGESTIONS_ENABLED = booleanPreferencesKey("suggestions_enabled")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val REPLAY_GAIN_ENABLED = booleanPreferencesKey("replay_gain_enabled")
        val REPLAY_GAIN_DB = floatPreferencesKey("replay_gain_db")
        val EQ_BAND_LEVELS_JSON = stringPreferencesKey("eq_band_levels_json")
    }

    companion object {
        const val DEFAULT_WEB_HOME = DEFAULT_WEB_HOME_URL
    }
}
