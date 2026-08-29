// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.backup

import com.deox9.musicplayer.database.dao.LibraryDao
import com.deox9.musicplayer.database.dao.TrackIdentity
import com.deox9.musicplayer.database.entity.FavouriteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the database into a backup, and puts one back.
 *
 * The matching itself lives in Backup.kt as pure functions, which is what makes it
 * testable without a database; this is the part that knows where the rows are.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val dao: LibraryDao,
    private val settings: BackupSettingsBridge,
) {

    /** Everything worth keeping, as a document. */
    suspend fun export(appVersion: String, nowMs: Long): BackupData = withContext(Dispatchers.IO) {
        val tracksById = dao.allTrackIdentities().associateBy { it.id }

        val playlists = dao.allPlaylists().map { playlist ->
            BackupPlaylist(
                name = playlist.name,
                tracks = dao.playlistTracksWithNames(playlist.id).mapNotNull { entry ->
                    tracksById[entry.id]?.toRef()
                },
            )
        }

        BackupData(
            createdAtMs = nowMs,
            appVersion = appVersion,
            playlists = playlists,
            favourites = dao.allFavourites().mapNotNull { tracksById[it.trackId]?.toRef() },
            playCounts = dao.allPlayCounts().mapNotNull { count ->
                tracksById[count.trackId]?.let {
                    BackupPlayCount(it.toRef(), count.playCount, count.lastPlayedAtMs)
                }
            },
            settings = settings.export(),
        )
    }

    /**
     * Puts a backup back, and reports what it could not find.
     *
     * Additive rather than destructive: restoring adds playlists and favourites
     * without deleting what is already here. Someone restoring onto a phone they
     * have been using is far more likely to want both than to want the last hour
     * wiped, and the destructive version is not undoable.
     */
    suspend fun restore(data: BackupData, nowMs: Long): RestoreReport = withContext(Dispatchers.IO) {
        val identities = dao.allTrackIdentities()
        val candidates = identities.map { it.toCandidate() }
        val idByUri = identities.associate { it.mediaUri to it.id }

        var matched = 0
        var missing = 0

        data.playlists.forEach { playlist ->
            val trackIds = playlist.tracks.mapNotNull { ref ->
                val found = resolveTrack(ref, candidates)
                if (found == null) missing++ else matched++
                found?.let { idByUri[it.contentUri] }
            }
            // Created even when nothing in it could be found. An empty playlist with
            // the right name says something was lost; no playlist at all says nothing.
            val id = dao.createPlaylist(playlist.name, nowMs)
            dao.replacePlaylistEntries(id, trackIds)
        }

        var favouritesMatched = 0
        var favouritesMissing = 0
        data.favourites.forEach { ref ->
            val found = resolveTrack(ref, candidates)?.let { idByUri[it.contentUri] }
            if (found == null) {
                favouritesMissing++
            } else {
                favouritesMatched++
                dao.addFavourite(FavouriteEntity(trackId = found, addedAtMs = nowMs))
            }
        }

        val settingsRestored = settings.restore(data.settings)

        RestoreReport(
            playlistsRestored = data.playlists.size,
            playlistTracksMatched = matched,
            playlistTracksMissing = missing,
            favouritesMatched = favouritesMatched,
            favouritesMissing = favouritesMissing,
            // Play counts are recorded but not written back: play_history rows carry
            // when and how much was heard, and inventing rows to reach a total would
            // put fiction into the table the recommendations are built from.
            playCountsMatched = 0,
            settingsRestored = settingsRestored,
        )
    }

    private fun TrackIdentity.toRef() = BackupTrackRef(
        path = filePath.orEmpty(),
        title = title,
        artist = artistName.orEmpty(),
        album = albumTitle.orEmpty(),
        durationMs = durationMs,
    )

    private fun TrackIdentity.toCandidate() = RestoreCandidate(
        contentUri = mediaUri,
        path = filePath.orEmpty(),
        title = title,
        artist = artistName.orEmpty(),
        album = albumTitle.orEmpty(),
        durationMs = durationMs,
    )
}
