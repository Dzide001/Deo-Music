// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.deox9.musicplayer.player.SavedQueue
import com.deox9.musicplayer.player.SavedQueueTrack
import com.deox9.musicplayer.player.SavedQueues
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the saved queues live.
 *
 * Stored beside the playback session rather than in Room: a queue is a handful of
 * URIs and a position, it is written on every switch, and it has no relationships to
 * anything the database models. A table for it would be schema churn for no query.
 *
 * The rules — capping, eviction, which one is active — are in SavedQueues and tested
 * without any of this.
 */
@Singleton
class SavedQueuesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun observe(): Flow<SavedQueues> =
        context.playbackDataStore.data.map { prefs -> decode(prefs[KEY_QUEUES]) }

    suspend fun current(): SavedQueues = observe().first()

    /** Applies a change to whatever is stored, in one edit so two saves cannot race. */
    suspend fun update(change: (SavedQueues) -> SavedQueues) {
        context.playbackDataStore.edit { prefs ->
            prefs[KEY_QUEUES] = encode(change(decode(prefs[KEY_QUEUES])))
        }
    }

    private fun encode(queues: SavedQueues): String = JSONObject().apply {
        put("activeId", queues.activeId ?: JSONObject.NULL)
        put(
            "queues",
            JSONArray().apply {
                queues.queues.forEach { queue ->
                    put(
                        JSONObject()
                            .put("id", queue.id)
                            .put("name", queue.name)
                            .put("currentIndex", queue.currentIndex)
                            .put("positionMs", queue.positionMs)
                            .put("updatedAtMs", queue.updatedAtMs)
                            .put(
                                "tracks",
                                JSONArray().apply {
                                    queue.tracks.forEach {
                                        put(
                                            JSONObject()
                                                .put("uri", it.uri)
                                                .put("title", it.title)
                                                .put("artist", it.artist),
                                        )
                                    }
                                },
                            ),
                    )
                }
            },
        )
    }.toString()

    /**
     * Reads what is stored, losing at most one queue to damage.
     *
     * Same rule as the backup file: this is the only copy, so recovering the rest
     * beats refusing all of it.
     */
    private fun decode(raw: String?): SavedQueues {
        if (raw.isNullOrBlank()) return SavedQueues()
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("queues") ?: JSONArray()
            val queues = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                runCatching {
                    SavedQueue(
                        id = item.getString("id"),
                        name = item.optString("name", SavedQueues.DEFAULT_NAME),
                        tracks = item.optJSONArray("tracks").toTracks(),
                        currentIndex = item.optInt("currentIndex", 0),
                        positionMs = item.optLong("positionMs", 0L),
                        updatedAtMs = item.optLong("updatedAtMs", 0L),
                    )
                }.getOrNull()
            }
            SavedQueues(
                queues = queues,
                activeId = root.optString("activeId").takeIf { it.isNotBlank() && it != "null" },
            )
        }.getOrDefault(SavedQueues())
    }

    private fun JSONArray?.toTracks(): List<SavedQueueTrack> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let {
                val uri = it.optString("uri")
                if (uri.isBlank()) return@mapNotNull null
                SavedQueueTrack(uri, it.optString("title"), it.optString("artist"))
            }
        }
    }

    private companion object {
        val KEY_QUEUES = stringPreferencesKey("saved_queues_json")
    }
}
