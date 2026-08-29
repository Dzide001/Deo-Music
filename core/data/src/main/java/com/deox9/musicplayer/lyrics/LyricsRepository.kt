// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.lyrics

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.deox9.musicplayer.database.dao.LibraryDao
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val Context.lyricsCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "lyrics_cache")

data class SyncedLyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsData(
    val plainLyrics: String,
    val syncedLines: List<SyncedLyricLine>,
    val source: String,
    val cached: Boolean
)

class LyricsRepository(
    private val context: Context,
    private val embeddedLyrics: EmbeddedLyrics,
    private val dao: LibraryDao
) {
    /**
     * Lyrics for a track, from the nearest source that has them.
     *
     * In order: the file's own tag, then anything fetched before, then the network
     * if that has been allowed. Nearest first is deliberate — the tag is what the
     * person who made the file intended, and the cache is a guess from a previous
     * lookup that may have matched a different recording of the same song.
     */
    suspend fun getLyrics(
        trackKey: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        allowOnlineLookup: Boolean,
    ): LyricsData? {
        val normalizedTitle = title.trim()
        val normalizedArtist = artist.trim()
        // A lookup needs something to look up by. Without both, every source below
        // would either fail or match the wrong song.
        if (normalizedTitle.isBlank() || normalizedArtist.isBlank()) return null

        // The path is resolved here rather than passed in: the caller holds a content
        // URI, and turning that into a path is the library's job, not the player UI's.
        val embedded = embeddedLyrics.read(dao.filePathFor(trackKey))
        if (embedded != null) return embedded

        val cacheKey = buildCacheKey(trackKey, normalizedTitle, normalizedArtist)
        val cached = loadFromCache(cacheKey)
        if (cached != null) return cached.copy(cached = true)

        // Nothing leaves the device unless the listener has asked for it. The cache
        // above is still consulted, because a lyric already fetched is already here
        // and re-reading it tells no one anything.
        if (!allowOnlineLookup) return null

        return fetchFromLrcLib(
            title = normalizedTitle,
            artist = normalizedArtist,
            album = album.trim(),
            durationSeconds = (durationMs / 1000L).toInt().coerceAtLeast(0),
        )?.also { saveToCache(cacheKey, it) }
    }

    private fun buildCacheKey(trackKey: String, title: String, artist: String): String {
        return if (trackKey.isNotBlank()) {
            "uri:${trackKey.trim()}"
        } else {
            "meta:${title.lowercase()}|${artist.lowercase()}"
        }
    }

    private suspend fun loadFromCache(cacheKey: String): LyricsData? {
        val prefs = context.lyricsCacheDataStore.data.first()
        val raw = prefs[Keys.CACHE_JSON] ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val entry = root.optJSONObject(cacheKey) ?: return@runCatching null
            val plain = entry.optString("plainLyrics")
            val syncedArray = entry.optJSONArray("synced") ?: JSONArray()
            val synced = buildList {
                for (i in 0 until syncedArray.length()) {
                    val line = syncedArray.optJSONObject(i) ?: continue
                    val timeMs = line.optLong("timeMs", -1L)
                    val text = line.optString("text")
                    if (timeMs >= 0 && text.isNotBlank()) {
                        add(SyncedLyricLine(timeMs = timeMs, text = text))
                    }
                }
            }.sortedBy { it.timeMs }

            if (plain.isBlank() && synced.isEmpty()) {
                null
            } else {
                LyricsData(
                    plainLyrics = plain,
                    syncedLines = synced,
                    source = entry.optString("source", "cache"),
                    cached = true
                )
            }
        }.getOrNull()
    }

    private suspend fun saveToCache(cacheKey: String, data: LyricsData) {
        context.lyricsCacheDataStore.edit { prefs ->
            val root = runCatching {
                JSONObject(prefs[Keys.CACHE_JSON] ?: "{}")
            }.getOrElse { JSONObject() }

            val synced = JSONArray()
            data.syncedLines.forEach { line ->
                synced.put(
                    JSONObject().apply {
                        put("timeMs", line.timeMs)
                        put("text", line.text)
                    }
                )
            }

            root.put(
                cacheKey,
                JSONObject().apply {
                    put("plainLyrics", data.plainLyrics)
                    put("synced", synced)
                    put("source", data.source)
                    put("cachedAtMs", System.currentTimeMillis())
                }
            )

            prefs[Keys.CACHE_JSON] = root.toString()
        }
    }

    private fun fetchFromLrcLib(
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int
    ): LyricsData? {
        val direct = fetchFromGetEndpoint(title, artist, album, durationSeconds)
        if (direct != null) return direct
        return fetchFromSearchEndpoint(title, artist)
    }

    private fun fetchFromGetEndpoint(
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int
    ): LyricsData? {
        val query = buildString {
            append("track_name=${encode(title)}")
            append("&artist_name=${encode(artist)}")
            if (album.isNotBlank()) {
                append("&album_name=${encode(album)}")
            }
            if (durationSeconds > 0) {
                append("&duration=$durationSeconds")
            }
        }

        val response = request("https://lrclib.net/api/get?$query") ?: return null
        return parseResponseObject(response)
    }

    private fun fetchFromSearchEndpoint(title: String, artist: String): LyricsData? {
        val query = "track_name=${encode(title)}&artist_name=${encode(artist)}"
        val response = request("https://lrclib.net/api/search?$query") ?: return null
        return runCatching {
            val arr = JSONArray(response)
            if (arr.length() == 0) return@runCatching null
            parseJsonObject(arr.optJSONObject(0) ?: return@runCatching null)
        }.getOrNull()
    }

    private fun parseResponseObject(raw: String): LyricsData? {
        return runCatching {
            parseJsonObject(JSONObject(raw))
        }.getOrNull()
    }

    private fun parseJsonObject(obj: JSONObject): LyricsData? {
        val plain = obj.optString("plainLyrics").orEmpty()
        val syncedRaw = obj.optString("syncedLyrics").orEmpty()
        val synced = parseLrc(syncedRaw)

        if (plain.isBlank() && synced.isEmpty()) {
            return null
        }

        return LyricsData(
            plainLyrics = plain,
            syncedLines = synced,
            source = "lrclib",
            cached = false
        )
    }

    /** Delegated, so embedded and online lyrics agree on what a timestamp means. */
    private fun parseLrc(raw: String): List<SyncedLyricLine> = LrcParser.parse(raw)

    private fun request(url: String): String? {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 6_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "DeoMusic/0.1.0")
            }
            try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private object Keys {
        val CACHE_JSON = stringPreferencesKey("lyrics_cache_json")
    }
}
