// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.backup

/**
 * How a backup refers to a track.
 *
 * The whole difficulty of restoring on a different phone is here. A track's content
 * URI — `content://media/external/audio/media/1234` — is a MediaStore row id, local
 * to one device and one scan. Restore a backup keyed on those and every playlist
 * comes back either empty or, worse, pointing at whatever now occupies id 1234.
 *
 * So a reference carries several ways to find the track again, in descending order
 * of confidence, and the restore takes the first that matches. Nothing here is
 * guaranteed to resolve; a track that is genuinely absent stays absent, and saying
 * so is better than silently dropping it from a playlist.
 */
data class BackupTrackRef(
    /** Absolute path at backup time. Exact when restoring to the same device. */
    val path: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
) {
    /** The file name alone, which survives the library moving to a different folder. */
    val fileName: String
        get() = path.substringAfterLast('/')

    val hasAnyIdentity: Boolean
        get() = path.isNotBlank() || (title.isNotBlank() && artist.isNotBlank())
}

/**
 * A candidate on the device being restored to.
 *
 * Deliberately not the library's own track type: matching is pure logic and should
 * be testable without a database, and the backup format should not move every time
 * an unrelated column is added to the tracks table.
 */
data class RestoreCandidate(
    val contentUri: String,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

/**
 * Finds the track a reference means, or null.
 *
 * The order is the point:
 *
 * 1. The same absolute path. Restoring to the same phone, which is the common case.
 * 2. The same file name. The library moved — a new SD card, a different folder.
 * 3. Title, artist and a duration within a second or two. The file was re-encoded or
 *    renamed, so only the recording itself identifies it.
 *
 * Duration is compared with tolerance because re-encoding shifts it by a frame or
 * two, and requiring exactness would fail precisely the case step 3 exists for. It
 * is compared at all because title and artist alone match every live version, remix
 * and cover in a large library.
 */
fun resolveTrack(ref: BackupTrackRef, candidates: List<RestoreCandidate>): RestoreCandidate? {
    if (!ref.hasAnyIdentity) return null

    if (ref.path.isNotBlank()) {
        candidates.firstOrNull { it.path == ref.path }?.let { return it }

        val name = ref.fileName
        if (name.isNotBlank()) {
            val byName = candidates.filter { it.path.substringAfterLast('/') == name }
            // Only when it is unambiguous. Two files with the same name in different
            // folders are a coin flip, and a wrong track in a playlist is worse than
            // a missing one because nothing signals that it is wrong.
            if (byName.size == 1) return byName.single()
        }
    }

    if (ref.title.isBlank() || ref.artist.isBlank()) return null

    val byTags = candidates.filter {
        it.title.equals(ref.title, ignoreCase = true) &&
            it.artist.equals(ref.artist, ignoreCase = true)
    }
    if (byTags.isEmpty()) return null
    if (byTags.size == 1 && ref.durationMs <= 0L) return byTags.single()

    return byTags.firstOrNull {
        ref.durationMs > 0L && kotlin.math.abs(it.durationMs - ref.durationMs) <= DURATION_TOLERANCE_MS
    }
}

/** Two seconds, which covers a re-encode without matching a different recording. */
const val DURATION_TOLERANCE_MS = 2_000L

data class BackupPlaylist(
    val name: String,
    val tracks: List<BackupTrackRef> = emptyList(),
)

/**
 * Everything a person would be upset to lose, and nothing that can be rebuilt.
 *
 * The library itself is not in here. It is a scan of files that are still on the
 * device or still on the backup drive, so including it would multiply the file size
 * for something the app regenerates in ninety seconds. What cannot be regenerated is
 * what someone chose: their playlists, their favourites, how often they have played
 * something, and how they set the sound up.
 */
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val createdAtMs: Long = 0L,
    val appVersion: String = "",
    val playlists: List<BackupPlaylist> = emptyList(),
    val favourites: List<BackupTrackRef> = emptyList(),
    val playCounts: List<BackupPlayCount> = emptyList(),
    /** Settings as stored, so a new key added later needs no change here. */
    val settings: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * Bumped when the format changes in a way an older build cannot read.
         *
         * Restoring refuses a version it does not know rather than guessing, because
         * a half-understood restore silently corrupts the thing being restored.
         */
        const val CURRENT_VERSION = 1
    }
}

data class BackupPlayCount(
    val track: BackupTrackRef,
    val playCount: Int = 0,
    val lastPlayedAtMs: Long = 0L,
)

/** What a restore did, so it can be reported rather than assumed. */
data class RestoreReport(
    val playlistsRestored: Int = 0,
    val playlistTracksMatched: Int = 0,
    val playlistTracksMissing: Int = 0,
    val favouritesMatched: Int = 0,
    val favouritesMissing: Int = 0,
    val playCountsMatched: Int = 0,
    val settingsRestored: Int = 0,
) {
    val anythingMissing: Boolean
        get() = playlistTracksMissing > 0 || favouritesMissing > 0

    /** A sentence for the person who pressed restore. */
    fun summary(): String = buildString {
        append("$playlistsRestored playlists, $favouritesMatched favourites")
        if (anythingMissing) {
            val missing = playlistTracksMissing + favouritesMissing
            append(" · $missing track${if (missing == 1) "" else "s"} not found on this device")
        }
    }
}
