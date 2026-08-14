// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * The backup file format.
 *
 * Plain JSON, written so a person can open it and see what the app knows about them.
 * That is a deliberate property rather than a convenience: a backup nobody can read
 * is a backup nobody can check, and this file is the one place all of someone's
 * listening habits sit in one document.
 */
object BackupCodec {

    /** Why a file could not be read, in words worth showing someone. */
    sealed interface Failure {
        data class NotABackup(val detail: String) : Failure
        data class TooNew(val fileVersion: Int, val supported: Int) : Failure

        val message: String
            get() = when (this) {
                is NotABackup -> "That file is not a backup from this app."
                is TooNew ->
                    "That backup was written by a newer version of the app " +
                        "(format $fileVersion, this build understands $supported)."
            }
    }

    fun encode(data: BackupData): String = JSONObject().apply {
        put("version", data.version)
        put("createdAtMs", data.createdAtMs)
        put("appVersion", data.appVersion)
        put("playlists", JSONArray().apply { data.playlists.forEach { put(encodePlaylist(it)) } })
        put("favourites", JSONArray().apply { data.favourites.forEach { put(encodeRef(it)) } })
        put(
            "playCounts",
            JSONArray().apply {
                data.playCounts.forEach {
                    put(
                        JSONObject()
                            .put("track", encodeRef(it.track))
                            .put("playCount", it.playCount)
                            .put("lastPlayedAtMs", it.lastPlayedAtMs),
                    )
                }
            },
        )
        put("settings", JSONObject().apply { data.settings.forEach { (k, v) -> put(k, v) } })
    }.toString(INDENT)

    /**
     * Reads a backup, or says why it could not.
     *
     * A version newer than this build understands is refused rather than read as far
     * as possible. Restoring the parts of a format you recognise and ignoring the
     * rest silently drops whatever was added — the user is told their playlists came
     * back, and does not learn until later that something else did not.
     */
    fun decode(raw: String): Result<BackupData> {
        val root = runCatching { JSONObject(raw) }.getOrElse {
            return Result.failure(BackupException(Failure.NotABackup(it.message.orEmpty())))
        }

        if (!root.has("version")) {
            return Result.failure(BackupException(Failure.NotABackup("no version field")))
        }
        val version = root.optInt("version", -1)
        if (version <= 0) {
            return Result.failure(BackupException(Failure.NotABackup("version $version")))
        }
        if (version > BackupData.CURRENT_VERSION) {
            return Result.failure(
                BackupException(Failure.TooNew(version, BackupData.CURRENT_VERSION)),
            )
        }

        return Result.success(
            BackupData(
                version = version,
                createdAtMs = root.optLong("createdAtMs", 0L),
                appVersion = root.optString("appVersion", ""),
                playlists = root.optJSONArray("playlists").mapObjects { decodePlaylist(it) },
                favourites = root.optJSONArray("favourites").mapObjects { decodeRef(it) },
                playCounts = root.optJSONArray("playCounts").mapObjects { item ->
                    BackupPlayCount(
                        track = decodeRef(item.optJSONObject("track") ?: JSONObject()),
                        playCount = item.optInt("playCount", 0),
                        lastPlayedAtMs = item.optLong("lastPlayedAtMs", 0L),
                    )
                },
                settings = root.optJSONObject("settings").toStringMap(),
            ),
        )
    }

    private fun encodePlaylist(playlist: BackupPlaylist) = JSONObject()
        .put("name", playlist.name)
        .put("tracks", JSONArray().apply { playlist.tracks.forEach { put(encodeRef(it)) } })

    private fun decodePlaylist(item: JSONObject) = BackupPlaylist(
        name = item.optString("name", ""),
        tracks = item.optJSONArray("tracks").mapObjects { decodeRef(it) },
    )

    private fun encodeRef(ref: BackupTrackRef) = JSONObject()
        .put("path", ref.path)
        .put("title", ref.title)
        .put("artist", ref.artist)
        .put("album", ref.album)
        .put("durationMs", ref.durationMs)

    private fun decodeRef(item: JSONObject) = BackupTrackRef(
        path = item.optString("path", ""),
        title = item.optString("title", ""),
        artist = item.optString("artist", ""),
        album = item.optString("album", ""),
        durationMs = item.optLong("durationMs", 0L),
    )

    /**
     * Maps an array's objects, skipping anything malformed.
     *
     * One corrupt entry loses that entry rather than the whole file. A backup is the
     * last copy of something by definition, so recovering most of it beats refusing
     * all of it — which is the opposite of the rule for the version, where a partial
     * read would be silently wrong rather than visibly short.
     */
    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let { runCatching { transform(it) }.getOrNull() }
        }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { optString(it, "") }
    }

    private const val INDENT = 2
}

/** Carries a [BackupCodec.Failure] so the reason survives as far as the UI. */
class BackupException(val failure: BackupCodec.Failure) : Exception(failure.message)
