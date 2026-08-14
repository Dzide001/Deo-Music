// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.playlist

import com.deox9.musicplayer.backup.BackupTrackRef
import com.deox9.musicplayer.backup.RestoreCandidate
import com.deox9.musicplayer.backup.resolveTrack
import com.deox9.musicplayer.database.dao.LibraryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playlists in and out of files other software understands.
 *
 * The format itself is in PlaylistFormats, which is pure and tested on its own; this
 * is the part that knows about the library. Matching an imported path back to a
 * track reuses the backup's resolver rather than a second, subtly different one —
 * an M3U from another device has exactly the problem a backup has, and solving it
 * twice would mean solving it two different ways.
 */
@Singleton
class PlaylistTransfer @Inject constructor(
    private val dao: LibraryDao,
) {

    /** How an import went, so it can be reported rather than assumed. */
    data class ImportResult(
        val playlistName: String,
        val matched: Int,
        val missing: Int,
    ) {
        fun summary(): String = buildString {
            append("Imported $playlistName with $matched track${if (matched == 1) "" else "s"}")
            if (missing > 0) {
                append(" · $missing not found on this device")
            }
        }
    }

    /**
     * A playlist as M3U8 text.
     *
     * Paths are absolute. A playlist leaving the app is going somewhere unknown —
     * a laptop, another player, a backup drive — and only an absolute path means
     * the same thing after the move.
     */
    suspend fun export(playlistId: Long): String = withContext(Dispatchers.IO) {
        val identities = dao.allTrackIdentities().associateBy { it.id }
        val entries = dao.playlistTracksWithNames(playlistId).mapNotNull { row ->
            val track = identities[row.id] ?: return@mapNotNull null
            PlaylistFileEntry(
                // Falls back to the content URI when there is no path, so a track
                // from a source without one is still listed rather than dropped
                // silently — even if only this app can resolve it.
                path = track.filePath?.takeIf { it.isNotBlank() } ?: track.mediaUri,
                durationSeconds = (track.durationMs / 1_000L).toInt(),
                title = listOfNotNull(track.artistName, track.title)
                    .filter { it.isNotBlank() }
                    .joinToString(" - "),
            )
        }
        PlaylistFormats.writeM3u(entries)
    }

    /**
     * Creates a playlist from a playlist file.
     *
     * Tracks that cannot be found are counted rather than skipped quietly, for the
     * same reason a restore counts them: a playlist that comes back half its length
     * with no explanation looks like the import worked.
     */
    suspend fun import(
        name: String,
        text: String,
        baseDirectory: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): ImportResult = withContext(Dispatchers.IO) {
        val entries = PlaylistFormats.parse(text, baseDirectory)
        val identities = dao.allTrackIdentities()

        val candidates = identities.map { track ->
            RestoreCandidate(
                contentUri = track.mediaUri,
                path = track.filePath.orEmpty(),
                title = track.title,
                artist = track.artistName.orEmpty(),
                album = track.albumTitle.orEmpty(),
                durationMs = track.durationMs,
            )
        }
        val idByUri = identities.associate { it.mediaUri to it.id }

        var missing = 0
        val trackIds = entries.mapNotNull { entry ->
            // The same resolver the backup uses: exact path, then an unambiguous
            // file name, then tags with a duration. An M3U from another device has
            // exactly the problem a backup has — paths that no longer point
            // anywhere — and solving it twice would mean solving it two ways.
            val reference = BackupTrackRef(
                path = entry.path,
                title = entry.title.substringAfter(" - ", missingDelimiterValue = entry.title).trim(),
                artist = entry.title.substringBefore(" - ", missingDelimiterValue = "").trim(),
                durationMs = entry.durationSeconds.coerceAtLeast(0) * 1_000L,
            )
            val found = resolveTrack(reference, candidates)
            if (found == null) missing++
            found?.let { idByUri[it.contentUri] }
        }

        val playlistId = dao.createPlaylist(name, nowMs)
        dao.replacePlaylistEntries(playlistId, trackIds)

        ImportResult(playlistName = name, matched = trackIds.size, missing = missing)
    }
}
