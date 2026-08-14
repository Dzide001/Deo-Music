// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.deox9.musicplayer.audio.CrossfadeCurve
import com.deox9.musicplayer.audio.CrossfadeSettings
import com.deox9.musicplayer.audio.EqBand
import com.deox9.musicplayer.audio.EqBandType
import com.deox9.musicplayer.audio.OutputProfile
import com.deox9.musicplayer.audio.OutputProfiles
import com.deox9.musicplayer.audio.ShuffleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    /**
     * When to fade between tracks, for how long, and along which curve.
     *
     * Replaces a bare `crossfadeEnabled` switch. That switch could only mean "fade
     * everywhere", which is the setting nobody wants: it dug a hole in every album
     * as well as smoothing every skip.
     */
    val crossfade: CrossfadeSettings = CrossfadeSettings(),
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
    val thoroughScanEnabled: Boolean = false,
    /**
     * Per-output equaliser settings.
     *
     * Off by default: with it on, the equaliser someone carefully set stops applying
     * the moment they plug in headphones, which is astonishing rather than helpful
     * until they have asked for it.
     */
    val outputProfiles: OutputProfiles = OutputProfiles(),
    /**
     * Whether lyrics may be looked up online.
     *
     * Off by default, and that is the whole point. Fetching lyrics sends the track
     * title, artist, album and duration to lrclib.net, which means a third party
     * learns what is being played. That may well be a trade someone wants, but it is
     * theirs to make rather than something the app does on their behalf the first
     * time they open the lyrics panel.
     */
    val onlineLyricsEnabled: Boolean = false,
    /**
     * Whether playback picks up again after a call or another app takes over.
     *
     * Defaults to on, which is what the app already did and what most people expect.
     * Off is for the case that makes the setting worth having: a podcast or a mix
     * you were half-listening to, where having it start again ten minutes after a
     * call ended is startling rather than helpful.
     */
    val resumeAfterInterruption: Boolean = true,
    /** Playback rate, 1.0 being unchanged. */
    val playbackSpeed: Float = 1f,
    /**
     * Pitch, independent of speed.
     *
     * Separate from speed because the two are only linked if you resample. Sonic
     * time-stretches instead, which is what makes it possible to slow a lecture down
     * without everyone sounding drunk, or drop a song a tone to sing along without
     * it running slow.
     */
    val playbackPitch: Float = 1f,
    /**
     * Folders excluded from the library, with everything under them.
     *
     * A phone's audio is not all music — voice notes, ringtones and game sounds land
     * in the same MediaStore — and this is how someone says which of it is not.
     */
    val hiddenFolders: Set<String> = emptySet(),
    /** Tracks shorter than this are not indexed. Zero means index everything. */
    val minimumTrackDurationMs: Long = 0L,
    /**
     * How shuffle groups tracks.
     *
     * Separate from the player's own shuffle flag, which only knows "on" or "off".
     * Album and folder shuffle need the queue reordered rather than a flag set.
     */
    val shuffleMode: ShuffleMode = ShuffleMode.Off,
)

class AppSettingsRepository(private val context: Context) {

    fun observe(): Flow<AppSettings> {
        return context.appSettingsDataStore.data.map { prefs ->
            AppSettings(
                webHomeUrl = prefs[Keys.WEB_HOME_URL] ?: DEFAULT_WEB_HOME_URL,
                crossfade = readCrossfade(prefs),
                thoroughScanEnabled = prefs[Keys.THOROUGH_SCAN_ENABLED] ?: false,
                onlineLyricsEnabled = prefs[Keys.ONLINE_LYRICS_ENABLED] ?: false,
                suggestionsEnabled = prefs[Keys.SUGGESTIONS_ENABLED] ?: true,
            )
                .withAppearance(prefs)
                .withAudio(prefs)
                .withLibraryFilters(prefs)
        }
    }

    /**
     * Read in groups rather than as one expression.
     *
     * Every default is an elvis and every elvis is a branch, so a single builder
     * grew past the complexity limit purely by the settings screen gaining rows —
     * which says nothing about how hard the code is to follow. Splitting it by
     * subject keeps each piece short and puts related settings together.
     */
    private fun AppSettings.withAppearance(prefs: Preferences) = copy(
        darkThemeEnabled = prefs[Keys.DARK_THEME_ENABLED] ?: true,
        themeMode = prefs[Keys.THEME_MODE] ?: THEME_MODE_UNSET,
        dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: true,
        amoledEnabled = prefs[Keys.AMOLED_ENABLED] ?: false,
    )

    private fun AppSettings.withAudio(prefs: Preferences): AppSettings {
        val legacyLevels = decodeEqBands(prefs[Keys.EQ_BAND_LEVELS_JSON] ?: "[]")
        return copy(
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            replayGainEnabled = prefs[Keys.REPLAY_GAIN_ENABLED] ?: false,
            replayGainDb = prefs[Keys.REPLAY_GAIN_DB] ?: 0f,
            eqBandLevels = legacyLevels,
            eqBands = decodeParametricBands(prefs[Keys.EQ_BANDS_JSON], legacyLevels),
            resumeAfterInterruption = prefs[Keys.RESUME_AFTER_INTERRUPTION] ?: true,
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1f,
            playbackPitch = prefs[Keys.PLAYBACK_PITCH] ?: 1f,
            outputProfiles = OutputProfiles(
                profiles = decodeOutputProfiles(prefs[Keys.OUTPUT_PROFILES_JSON]),
                enabled = prefs[Keys.OUTPUT_PROFILES_ENABLED] ?: false,
            ),
        )
    }

    private fun AppSettings.withLibraryFilters(prefs: Preferences) = copy(
        hiddenFolders = prefs[Keys.HIDDEN_FOLDERS] ?: emptySet(),
        minimumTrackDurationMs = prefs[Keys.MIN_TRACK_DURATION_MS] ?: 0L,
        // Stored by name. An unknown name falls back to Off rather than throwing,
        // so a downgrade that removes a mode does not make the settings unreadable.
        shuffleMode = runCatching {
            ShuffleMode.valueOf(prefs[Keys.SHUFFLE_MODE] ?: ShuffleMode.Off.name)
        }.getOrDefault(ShuffleMode.Off),
    )

    /**
     * Every stored preference as text, for a backup.
     *
     * Read straight off DataStore rather than off [AppSettings], so a preference
     * added later is backed up without anyone remembering to add it here. The cost
     * is that the type is not known — hence the marker on each value, which is what
     * lets [importAll] put it back as the type it was.
     */
    suspend fun exportAll(): Map<String, String> {
        val prefs = context.appSettingsDataStore.data.first()
        return prefs.asMap().entries.associate { (key, value) ->
            key.name to "${value.typeMarker()}:$value"
        }
    }

    /**
     * Writes exported preferences back, returning how many were applied.
     *
     * A value whose marker is missing or unknown is skipped rather than guessed at.
     * Writing a string into a key the app reads as a boolean does not fail here — it
     * fails much later, as a ClassCastException on a screen that has nothing to do
     * with restoring.
     */
    suspend fun importAll(values: Map<String, String>): Int {
        var applied = 0
        context.appSettingsDataStore.edit { prefs ->
            values.forEach { (name, encoded) ->
                val marker = encoded.substringBefore(':', missingDelimiterValue = "")
                val raw = encoded.substringAfter(':', missingDelimiterValue = "")
                val wrote = when (marker) {
                    "b" -> raw.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(name)] = it }
                    "i" -> raw.toIntOrNull()?.let { prefs[intPreferencesKey(name)] = it }
                    "f" -> raw.toFloatOrNull()?.let { prefs[floatPreferencesKey(name)] = it }
                    "s" -> prefs[stringPreferencesKey(name)] = raw
                    else -> null
                }
                if (wrote != null) applied++
            }
        }
        return applied
    }

    private fun Any.typeMarker(): String = when (this) {
        is Boolean -> "b"
        is Int -> "i"
        is Float -> "f"
        else -> "s"
    }

    suspend fun setWebHomeUrl(url: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.WEB_HOME_URL] = url
        }
    }

    suspend fun setCrossfadeOnSkip(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.CROSSFADE_ON_SKIP] = enabled
        }
    }

    suspend fun setCrossfadeOnAutoAdvance(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.CROSSFADE_ON_AUTO_ADVANCE] = enabled
        }
    }

    suspend fun setCrossfadeDurationMs(durationMs: Int) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.CROSSFADE_DURATION_MS] =
                durationMs.coerceIn(CrossfadeSettings.MIN_DURATION_MS, CrossfadeSettings.MAX_DURATION_MS)
        }
    }

    suspend fun setCrossfadeCurve(curve: CrossfadeCurve) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.CROSSFADE_CURVE] = curve.name
        }
    }

    /**
     * Reads the fade settings, carrying an old `crossfade_enabled` switch across once.
     *
     * Someone who had the old switch on wanted fades, so they get them where a fade
     * is actually an improvement — on a manual skip — rather than on every album join
     * as well. Silently turning the feature off for them would be the ruder default.
     */
    private fun readCrossfade(prefs: Preferences): CrossfadeSettings {
        val legacy = prefs[Keys.CROSSFADE_ENABLED]
        return CrossfadeSettings(
            onSkip = prefs[Keys.CROSSFADE_ON_SKIP] ?: legacy ?: false,
            onAutoAdvance = prefs[Keys.CROSSFADE_ON_AUTO_ADVANCE] ?: false,
            durationMs = prefs[Keys.CROSSFADE_DURATION_MS] ?: CrossfadeSettings.DEFAULT_DURATION_MS,
            curve = CrossfadeCurve.fromName(prefs[Keys.CROSSFADE_CURVE]),
        )
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
            prefs[Keys.CROSSFADE_ON_SKIP] = false
            prefs[Keys.CROSSFADE_ON_AUTO_ADVANCE] = false
            prefs[Keys.CROSSFADE_DURATION_MS] = CrossfadeSettings.DEFAULT_DURATION_MS
            prefs[Keys.CROSSFADE_CURVE] = CrossfadeSettings().curve.name
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
            prefs[Keys.ONLINE_LYRICS_ENABLED] = false
            prefs[Keys.RESUME_AFTER_INTERRUPTION] = true
            prefs[Keys.PLAYBACK_SPEED] = 1f
            prefs[Keys.PLAYBACK_PITCH] = 1f
            prefs[Keys.HIDDEN_FOLDERS] = emptySet()
            prefs[Keys.MIN_TRACK_DURATION_MS] = 0L
            prefs[Keys.SHUFFLE_MODE] = ShuffleMode.Off.name
            prefs[Keys.OUTPUT_PROFILES_ENABLED] = false
            prefs[Keys.OUTPUT_PROFILES_JSON] = encodeOutputProfiles(emptyMap())
        }
    }

    suspend fun setShuffleMode(mode: ShuffleMode) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.SHUFFLE_MODE] = mode.name }
    }

    suspend fun hideFolder(path: String) {
        val cleaned = path.trim().trimEnd('/')
        if (cleaned.isEmpty()) return
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.HIDDEN_FOLDERS] = (prefs[Keys.HIDDEN_FOLDERS] ?: emptySet()) + cleaned
        }
    }

    suspend fun unhideFolder(path: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.HIDDEN_FOLDERS] = (prefs[Keys.HIDDEN_FOLDERS] ?: emptySet()) - path
        }
    }

    suspend fun setMinimumTrackDurationMs(ms: Long) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.MIN_TRACK_DURATION_MS] = ms.coerceAtLeast(0L)
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.PLAYBACK_SPEED] = speed.coerceIn(MIN_RATE, MAX_RATE)
        }
    }

    suspend fun setPlaybackPitch(pitch: Float) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.PLAYBACK_PITCH] = pitch.coerceIn(MIN_RATE, MAX_RATE)
        }
    }

    suspend fun setResumeAfterInterruption(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.RESUME_AFTER_INTERRUPTION] = enabled
        }
    }

    suspend fun setOnlineLyricsEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.ONLINE_LYRICS_ENABLED] = enabled
        }
    }

    suspend fun setOutputProfilesEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.OUTPUT_PROFILES_ENABLED] = enabled
        }
    }

    suspend fun saveOutputProfile(profile: OutputProfile) {
        editOutputProfiles { it.with(profile) }
    }

    suspend fun deleteOutputProfile(routeKey: String) {
        editOutputProfiles { it.without(routeKey) }
    }

    /**
     * Reads, changes and writes the profiles in one edit.
     *
     * DataStore's edit block is the transaction, so doing this as a read followed by
     * a separate write would let two saves from different routes race and lose one.
     */
    private suspend fun editOutputProfiles(change: (OutputProfiles) -> OutputProfiles) {
        context.appSettingsDataStore.edit { prefs ->
            val current = OutputProfiles(profiles = decodeOutputProfiles(prefs[Keys.OUTPUT_PROFILES_JSON]))
            prefs[Keys.OUTPUT_PROFILES_JSON] = encodeOutputProfiles(change(current).profiles)
        }
    }

    private fun decodeOutputProfiles(raw: String?): Map<String, OutputProfile> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val key = item.optString("routeKey").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                key to OutputProfile(
                    routeKey = key,
                    bands = decodeBandArray(item.optJSONArray("bands")),
                    limiterEnabled = item.optBoolean("limiterEnabled", true),
                )
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun encodeOutputProfiles(profiles: Map<String, OutputProfile>): String {
        val array = JSONArray()
        profiles.values.forEach { profile ->
            array.put(
                JSONObject()
                    .put("routeKey", profile.routeKey)
                    .put("bands", JSONArray(encodeParametricBands(profile.bands)))
                    .put("limiterEnabled", profile.limiterEnabled),
            )
        }
        return array.toString()
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
            decodeBandArray(JSONArray(raw))
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Bands out of a JSON array, clamped to the ranges the editor offers.
     *
     * Shared by the global equaliser and the per-output profiles so a band saved by
     * one is read the same way by the other; two copies would drift the moment a
     * field was added.
     */
    private fun decodeBandArray(array: JSONArray?): List<EqBand> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
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

        // Read once and carried across; nothing writes it any more. It only ever
        // meant "fade everywhere", which is not a state the new settings can express.
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_ON_SKIP = booleanPreferencesKey("crossfade_on_skip")
        val CROSSFADE_ON_AUTO_ADVANCE = booleanPreferencesKey("crossfade_on_auto_advance")
        val CROSSFADE_DURATION_MS = intPreferencesKey("crossfade_duration_ms")
        val CROSSFADE_CURVE = stringPreferencesKey("crossfade_curve")

        // "gapless_enabled" was removed rather than renamed. DataStore ignores keys
        // nothing reads, so an existing install simply stops consulting it; the value
        // is left in place rather than migrated away, since deleting it would be work
        // in service of a setting that never did anything.
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
        val ONLINE_LYRICS_ENABLED = booleanPreferencesKey("online_lyrics_enabled")
        val RESUME_AFTER_INTERRUPTION = booleanPreferencesKey("resume_after_interruption")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
        val HIDDEN_FOLDERS = stringSetPreferencesKey("hidden_folders")
        val MIN_TRACK_DURATION_MS = longPreferencesKey("min_track_duration_ms")
        val SHUFFLE_MODE = stringPreferencesKey("shuffle_mode")
        val OUTPUT_PROFILES_ENABLED = booleanPreferencesKey("output_profiles_enabled")
        val OUTPUT_PROFILES_JSON = stringPreferencesKey("output_profiles_json")
    }

    companion object {
        /** Below a quarter speed the time-stretcher smears; above four it chirps. */
        const val MIN_RATE = 0.25f
        const val MAX_RATE = 4.0f

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
