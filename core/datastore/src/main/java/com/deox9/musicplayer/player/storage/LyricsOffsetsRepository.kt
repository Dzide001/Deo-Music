// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How far a track's lyrics need nudging, per track.
 *
 * Per track and not global, because the fault is in the file: one badly timed LRC
 * says nothing about the next one, and a single global offset would make every
 * correctly timed track wrong to fix one that is not.
 *
 * Stored beside the other playback preferences rather than in the library database.
 * The offset describes a listener's correction, not the track, and it should survive
 * a rescan wiping and rebuilding the index — which is exactly what the library
 * database does.
 */
@Singleton
class LyricsOffsetsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** Milliseconds to shift the lyrics of [trackUri]; positive delays them. */
    suspend fun offsetFor(trackUri: String): Long {
        if (trackUri.isBlank()) return 0L
        val raw = context.playbackDataStore.data.first()[KEY_OFFSETS] ?: return 0L
        return runCatching { JSONObject(raw).optLong(trackUri, 0L) }.getOrDefault(0L)
    }

    suspend fun setOffset(trackUri: String, offsetMs: Long) {
        if (trackUri.isBlank()) return
        context.playbackDataStore.edit { prefs ->
            val json = runCatching { JSONObject(prefs[KEY_OFFSETS] ?: "{}") }.getOrDefault(JSONObject())
            val clamped = offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
            // Zero is removed rather than written. An offset of nothing is not a
            // correction, and keeping it would grow this map by one entry for every
            // track anyone ever opened the lyrics panel on.
            if (clamped == 0L) json.remove(trackUri) else json.put(trackUri, clamped)
            prefs[KEY_OFFSETS] = json.toString()
        }
    }

    companion object {
        /**
         * Ten seconds either way.
         *
         * Past this the lyrics are not mistimed, they belong to a different file, and
         * an unbounded nudge lets someone scroll a whole song out of view by holding
         * a button.
         */
        const val MAX_OFFSET_MS = 10_000L

        /** One nudge. Small enough to be precise, large enough to be audible. */
        const val STEP_MS = 250L

        private val KEY_OFFSETS = stringPreferencesKey("lyrics_offsets")
    }
}
