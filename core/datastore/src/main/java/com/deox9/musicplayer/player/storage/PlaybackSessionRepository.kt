// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * The playback store, shared with [SavedQueuesRepository].
 *
 * Internal rather than private because the saved queues live in the same file: they
 * are the same kind of state, written at the same moments, and a second DataStore
 * would mean two files that have to agree about what is playing.
 */
internal val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_session")

class PlaybackSessionRepository(
    private val context: Context
) {
    fun observe(): Flow<PlaybackSessionEntity?> {
        return context.playbackDataStore.data.map { prefs ->
            val uri = prefs[Keys.URI] ?: return@map null
            val queueJson = prefs[Keys.QUEUE_JSON] ?: "[]"
            PlaybackSessionEntity(
                uri = uri,
                title = prefs[Keys.TITLE] ?: "Unknown title",
                artist = prefs[Keys.ARTIST] ?: "Unknown artist",
                positionMs = prefs[Keys.POSITION_MS] ?: 0L,
                durationMs = prefs[Keys.DURATION_MS] ?: 0L,
                isPlaying = prefs[Keys.IS_PLAYING] ?: false,
                queue = parseQueue(queueJson),
                currentIndex = prefs[Keys.CURRENT_INDEX] ?: 0,
                updatedAtMs = prefs[Keys.UPDATED_AT_MS] ?: 0L,
                shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
                repeatMode = prefs[Keys.REPEAT_MODE] ?: 0,
                playerVolume = prefs[Keys.PLAYER_VOLUME] ?: 1f,
                album = prefs[Keys.ALBUM] ?: "",
                albumArtUri = prefs[Keys.ALBUM_ART_URI] ?: ""
            )
        }
    }

    suspend fun getLatest(): PlaybackSessionEntity? = observe().first()

    /**
     * Writes the session.
     *
     * Takes the same type [observe] hands back rather than thirteen positional
     * parameters. Thirteen of anything is a data class that has not been written
     * down, and here the class already existed for the read side — so the two ends
     * of the same record had drifted into different shapes, which is exactly how a
     * field ends up saved into the wrong column.
     */
    suspend fun save(session: PlaybackSessionEntity): Unit = with(session) {
        context.playbackDataStore.edit { prefs ->
            prefs[Keys.URI] = uri
            prefs[Keys.TITLE] = title
            prefs[Keys.ARTIST] = artist
            prefs[Keys.POSITION_MS] = positionMs
            prefs[Keys.DURATION_MS] = durationMs
            prefs[Keys.IS_PLAYING] = isPlaying
            prefs[Keys.QUEUE_JSON] = toQueueJson(queue)
            prefs[Keys.CURRENT_INDEX] = currentIndex
            prefs[Keys.UPDATED_AT_MS] = System.currentTimeMillis()
            prefs[Keys.SHUFFLE_ENABLED] = shuffleEnabled
            prefs[Keys.REPEAT_MODE] = repeatMode
            prefs[Keys.PLAYER_VOLUME] = playerVolume
            prefs[Keys.ALBUM] = album
            prefs[Keys.ALBUM_ART_URI] = albumArtUri
        }
    }

    private fun parseQueue(raw: String): List<QueueItem> {
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        QueueItem(
                            uri = item.optString("uri"),
                            title = item.optString("title", "Unknown title"),
                            artist = item.optString("artist", "Unknown artist")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toQueueJson(queue: List<QueueItem>): String {
        val array = JSONArray()
        queue.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("uri", item.uri)
                    put("title", item.title)
                    put("artist", item.artist)
                }
            )
        }
        return array.toString()
    }

    private object Keys {
        val URI = stringPreferencesKey("uri")
        val TITLE = stringPreferencesKey("title")
        val ARTIST = stringPreferencesKey("artist")
        val POSITION_MS = longPreferencesKey("position_ms")
        val DURATION_MS = longPreferencesKey("duration_ms")
        val IS_PLAYING = booleanPreferencesKey("is_playing")
        val QUEUE_JSON = stringPreferencesKey("queue_json")
        val CURRENT_INDEX = intPreferencesKey("current_index")
        val UPDATED_AT_MS = longPreferencesKey("updated_at_ms")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val PLAYER_VOLUME = floatPreferencesKey("player_volume")
        val ALBUM = stringPreferencesKey("album")
        val ALBUM_ART_URI = stringPreferencesKey("album_art_uri")
    }
}
