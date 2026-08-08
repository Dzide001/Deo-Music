// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.recommendationSignalsDataStore: DataStore<Preferences> by preferencesDataStore(name = "recommendation_signals")

data class RecommendationSignals(
    val artistPlayCounts: Map<String, Int> = emptyMap(),
    val trackPlayCounts: Map<String, Int> = emptyMap(),
    val trackSkipCounts: Map<String, Int> = emptyMap(),
    val likedTrackUris: Set<String> = emptySet(),
    val hiddenTrackUris: Set<String> = emptySet()
)

class RecommendationSignalsRepository(private val context: Context) {

    fun observe(): Flow<RecommendationSignals> {
        return context.recommendationSignalsDataStore.data.map { prefs ->
            RecommendationSignals(
                artistPlayCounts = parseIntMap(prefs[Keys.ARTIST_PLAY_COUNTS]),
                trackPlayCounts = parseIntMap(prefs[Keys.TRACK_PLAY_COUNTS]),
                trackSkipCounts = parseIntMap(prefs[Keys.TRACK_SKIP_COUNTS]),
                likedTrackUris = parseStringSet(prefs[Keys.LIKED_TRACK_URIS]),
                hiddenTrackUris = parseStringSet(prefs[Keys.HIDDEN_TRACK_URIS])
            )
        }
    }

    suspend fun recordPlay(uri: String, artist: String?) {
        context.recommendationSignalsDataStore.edit { prefs ->
            val trackCounts = parseIntMap(prefs[Keys.TRACK_PLAY_COUNTS]).toMutableMap()
            trackCounts[uri] = (trackCounts[uri] ?: 0) + 1
            prefs[Keys.TRACK_PLAY_COUNTS] = toIntMapJson(trackCounts)

            val artistKey = artist?.trim()?.lowercase().orEmpty()
            if (artistKey.isNotBlank()) {
                val artistCounts = parseIntMap(prefs[Keys.ARTIST_PLAY_COUNTS]).toMutableMap()
                artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
                prefs[Keys.ARTIST_PLAY_COUNTS] = toIntMapJson(artistCounts)
            }
        }
    }

    suspend fun recordSkip(uri: String) {
        context.recommendationSignalsDataStore.edit { prefs ->
            val skipCounts = parseIntMap(prefs[Keys.TRACK_SKIP_COUNTS]).toMutableMap()
            skipCounts[uri] = (skipCounts[uri] ?: 0) + 1
            prefs[Keys.TRACK_SKIP_COUNTS] = toIntMapJson(skipCounts)
        }
    }

    suspend fun setLiked(uri: String, liked: Boolean) {
        context.recommendationSignalsDataStore.edit { prefs ->
            val likedSet = parseStringSet(prefs[Keys.LIKED_TRACK_URIS]).toMutableSet()
            if (liked) likedSet.add(uri) else likedSet.remove(uri)
            prefs[Keys.LIKED_TRACK_URIS] = toStringSetJson(likedSet)

            if (liked) {
                val hiddenSet = parseStringSet(prefs[Keys.HIDDEN_TRACK_URIS]).toMutableSet()
                hiddenSet.remove(uri)
                prefs[Keys.HIDDEN_TRACK_URIS] = toStringSetJson(hiddenSet)
            }
        }
    }

    suspend fun setHidden(uri: String, hidden: Boolean) {
        context.recommendationSignalsDataStore.edit { prefs ->
            val hiddenSet = parseStringSet(prefs[Keys.HIDDEN_TRACK_URIS]).toMutableSet()
            if (hidden) hiddenSet.add(uri) else hiddenSet.remove(uri)
            prefs[Keys.HIDDEN_TRACK_URIS] = toStringSetJson(hiddenSet)

            if (hidden) {
                val likedSet = parseStringSet(prefs[Keys.LIKED_TRACK_URIS]).toMutableSet()
                likedSet.remove(uri)
                prefs[Keys.LIKED_TRACK_URIS] = toStringSetJson(likedSet)
            }
        }
    }

    private fun parseIntMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    put(key, obj.optInt(key, 0))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun toIntMapJson(map: Map<String, Int>): String {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        return obj.toString()
    }

    private fun parseStringSet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split("\u001F").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    private fun toStringSetJson(values: Set<String>): String {
        return values.joinToString(separator = "\u001F")
    }

    private object Keys {
        val ARTIST_PLAY_COUNTS = stringPreferencesKey("artist_play_counts")
        val TRACK_PLAY_COUNTS = stringPreferencesKey("track_play_counts")
        val TRACK_SKIP_COUNTS = stringPreferencesKey("track_skip_counts")
        val LIKED_TRACK_URIS = stringPreferencesKey("liked_track_uris")
        val HIDDEN_TRACK_URIS = stringPreferencesKey("hidden_track_uris")
    }
}
