// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.lyrics

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    private val context: Context
) {
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
        if (normalizedTitle.isBlank() || normalizedArtist.isBlank()) {
            return null
        }

        val cacheKey = buildCacheKey(trackKey, normalizedTitle, normalizedArtist)

        loadFromCache(cacheKey)?.let {
            return it.copy(cached = true)
        }

        // Nothing leaves the device unless the listener has asked for it. The cache
        // above is still consulted, because a lyric already fetched is already here
        // and re-reading it tells no one anything.
        if (!allowOnlineLookup) return null

        val fetched = fetchFromLrcLib(
            title = normalizedTitle,
            artist = normalizedArtist,
            album = album.trim(),
            durationSeconds = (durationMs / 1000L).toInt().coerceAtLeast(0)
        ) ?: return null

        saveToCache(cacheKey, fetched)
        return fetched
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

            if (plain.isBlank() && synced.isEmpty()) null else {
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

    private fun parseLrc(raw: String): List<SyncedLyricLine> {
        if (raw.isBlank()) return emptyList()
        val lines = mutableListOf<SyncedLyricLine>()
        val regex = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]([^\\n\\r]*)")

        raw.lineSequence().forEach { row ->
            regex.findAll(row).forEach { match ->
                val min = match.groupValues.getOrNull(1)?.toLongOrNull() ?: 0L
                val sec = match.groupValues.getOrNull(2)?.toLongOrNull() ?: 0L
                val fracRaw = match.groupValues.getOrNull(3).orEmpty()
                val fracMs = when (fracRaw.length) {
                    1 -> fracRaw.toLongOrNull()?.times(100L) ?: 0L
                    2 -> fracRaw.toLongOrNull()?.times(10L) ?: 0L
                    3 -> fracRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val text = (match.groupValues.getOrNull(4).orEmpty()).trim()
                val timeMs = (min * 60_000L) + (sec * 1_000L) + fracMs
                if (text.isNotBlank()) {
                    lines += SyncedLyricLine(timeMs = timeMs, text = text)
                }
            }
        }

        return lines.sortedBy { it.timeMs }
    }

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
