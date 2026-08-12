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
import com.deox9.musicplayer.audio.EqBand
import com.deox9.musicplayer.audio.EqBandType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
private const val DEFAULT_WEB_HOME_URL = "https://m.youtube.com"

/**
 * Marker for "the user has never chosen a theme mode".
 *
 * Distinct from "system" so an existing `darkThemeEnabled` preference can be carried
 * across exactly once. Without it, everyone who had picked dark would silently be
 * moved back to following the system on upgrade.
 */
const val THEME_MODE_UNSET = ""

data class AppSettings(
    val webHomeUrl: String = DEFAULT_WEB_HOME_URL,
    val crossfadeEnabled: Boolean = false,
    val gaplessEnabled: Boolean = true,
    /**
     * Superseded by [themeMode]. Kept so an existing preference can be read once and
     * carried across; nothing writes it any more.
     */
    val darkThemeEnabled: Boolean = true,
    /** "system" | "light" | "dark". Stored as a string so the enum can gain cases. */
    val themeMode: String = THEME_MODE_UNSET,
    val dynamicColorEnabled: Boolean = true,
    val amoledEnabled: Boolean = false,
    val suggestionsEnabled: Boolean = true,
    val eqEnabled: Boolean = false,
    val replayGainEnabled: Boolean = false,
    val replayGainDb: Float = 0f,
    val eqBandLevels: List<Int> = List(10) { 0 },
    /**
     * The parametric bands actually applied.
     *
     * [eqBandLevels] is the older ten-slider representation and is kept only so an
     * existing setup migrates; once bands are stored, this is the truth.
     */
    val eqBands: List<EqBand> = emptyList(),
    /**
     * Whether the library scan opens every file to read its tags.
     *
     * Off by default: it is the only way to get ReplayGain and reliable album
     * artist, but it is far slower than reading MediaStore.
     */
    val thoroughScanEnabled: Boolean = false
)

class AppSettingsRepository(private val context: Context) {

    fun observe(): Flow<AppSettings> {
        return context.appSettingsDataStore.data.map { prefs ->
            AppSettings(
                webHomeUrl = prefs[Keys.WEB_HOME_URL] ?: DEFAULT_WEB_HOME_URL,
                crossfadeEnabled = prefs[Keys.CROSSFADE_ENABLED] ?: false,
                gaplessEnabled = prefs[Keys.GAPLESS_ENABLED] ?: true,
                darkThemeEnabled = prefs[Keys.DARK_THEME_ENABLED] ?: true,
                themeMode = prefs[Keys.THEME_MODE] ?: THEME_MODE_UNSET,
                dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: true,
                amoledEnabled = prefs[Keys.AMOLED_ENABLED] ?: false,
                suggestionsEnabled = prefs[Keys.SUGGESTIONS_ENABLED] ?: true,
                eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
                replayGainEnabled = prefs[Keys.REPLAY_GAIN_ENABLED] ?: false,
                replayGainDb = prefs[Keys.REPLAY_GAIN_DB] ?: 0f,
                eqBandLevels = decodeEqBands(prefs[Keys.EQ_BAND_LEVELS_JSON] ?: "[]"),
                eqBands = decodeParametricBands(
                    raw = prefs[Keys.EQ_BANDS_JSON],
                    legacyLevels = decodeEqBands(prefs[Keys.EQ_BAND_LEVELS_JSON] ?: "[]"),
                ),
                thoroughScanEnabled = prefs[Keys.THOROUGH_SCAN_ENABLED] ?: false
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

    suspend fun setThemeMode(mode: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    suspend fun setAmoledEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.AMOLED_ENABLED] = enabled
        }
    }

    suspend fun setThoroughScanEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.THOROUGH_SCAN_ENABLED] = enabled
        }
    }

    suspend fun resetDefaults() {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.WEB_HOME_URL] = DEFAULT_WEB_HOME
            prefs[Keys.CROSSFADE_ENABLED] = false
            prefs[Keys.GAPLESS_ENABLED] = true
            prefs[Keys.THEME_MODE] = THEME_MODE_UNSET
            prefs[Keys.DYNAMIC_COLOR_ENABLED] = true
            prefs[Keys.AMOLED_ENABLED] = false
            prefs[Keys.SUGGESTIONS_ENABLED] = true
            prefs[Keys.EQ_ENABLED] = false
            prefs[Keys.REPLAY_GAIN_ENABLED] = false
            prefs[Keys.REPLAY_GAIN_DB] = 0f
            prefs[Keys.EQ_BAND_LEVELS_JSON] = encodeEqBands(List(10) { 0 })
            prefs[Keys.EQ_BANDS_JSON] = encodeParametricBands(emptyList())
            prefs[Keys.THOROUGH_SCAN_ENABLED] = false
        }
    }

    suspend fun setEqBands(bands: List<EqBand>) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.EQ_BANDS_JSON] = encodeParametricBands(bands)
        }
    }

    /**
     * Reads the parametric bands, falling back to the old ten sliders.
     *
     * Absent — not empty — means the user has never touched the parametric editor,
     * so whatever their graphic equaliser was set to is converted and carried over.
     * An explicitly empty list is a real state, meaning "no bands", and must not be
     * overwritten by the old settings.
     */
    private fun decodeParametricBands(raw: String?, legacyLevels: List<Int>): List<EqBand> {
        if (raw == null) return EqBand.fromGraphicLevels(legacyLevels).filterNot { it.isTransparent }

        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                EqBand(
                    type = runCatching { EqBandType.valueOf(item.getString("type")) }
                        .getOrDefault(EqBandType.Peaking),
                    frequencyHz = item.optDouble("frequencyHz", 1000.0)
                        .coerceIn(MIN_BAND_HZ, MAX_BAND_HZ),
                    gainDb = item.optDouble("gainDb", 0.0).coerceIn(MIN_BAND_DB, MAX_BAND_DB),
                    q = item.optDouble("q", EqBand.DEFAULT_Q).coerceIn(MIN_BAND_Q, MAX_BAND_Q),
                    enabled = item.optBoolean("enabled", true),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeParametricBands(bands: List<EqBand>): String {
        val array = JSONArray()
        bands.forEach { band ->
            array.put(
                JSONObject()
                    .put("type", band.type.name)
                    .put("frequencyHz", band.frequencyHz.coerceIn(MIN_BAND_HZ, MAX_BAND_HZ))
                    .put("gainDb", band.gainDb.coerceIn(MIN_BAND_DB, MAX_BAND_DB))
                    .put("q", band.q.coerceIn(MIN_BAND_Q, MAX_BAND_Q))
                    .put("enabled", band.enabled),
            )
        }
        return array.toString()
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
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val AMOLED_ENABLED = booleanPreferencesKey("amoled_enabled")
        val SUGGESTIONS_ENABLED = booleanPreferencesKey("suggestions_enabled")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val REPLAY_GAIN_ENABLED = booleanPreferencesKey("replay_gain_enabled")
        val REPLAY_GAIN_DB = floatPreferencesKey("replay_gain_db")
        val EQ_BAND_LEVELS_JSON = stringPreferencesKey("eq_band_levels_json")
        val EQ_BANDS_JSON = stringPreferencesKey("eq_bands_json")
        val THOROUGH_SCAN_ENABLED = booleanPreferencesKey("thorough_scan_enabled")
    }

    companion object {
        const val DEFAULT_WEB_HOME = DEFAULT_WEB_HOME_URL

        /** Ranges the editor offers and stored values are clamped to. */
        const val MIN_BAND_HZ = 20.0
        const val MAX_BAND_HZ = 20_000.0
        const val MIN_BAND_DB = -24.0
        const val MAX_BAND_DB = 24.0
        const val MIN_BAND_Q = 0.1
        const val MAX_BAND_Q = 18.0
    }
}
