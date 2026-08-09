// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import com.deox9.musicplayer.database.dao.LibraryDao
import com.deox9.musicplayer.database.entity.PlaylistEntity
import javax.inject.Inject

/**
 * Mirrors MediaStore playlists into the schema.
 *
 * A one-way mirror, not a sync: the app does not yet support editing playlist
 * membership or order in-app, so there is no local state a re-import could
 * conflict with or lose. Once in-app editing exists, this needs to stop
 * clear-and-reinserting on every scan.
 */
class PlaylistImporter @Inject constructor(
    private val source: MediaStorePlaylistSource,
    private val dao: LibraryDao,
) {

    /**
     * Imports every MediaStore playlist.
     *
     * Must run after the track index is up to date: membership is matched by
     * content URI, so a track the indexer has not seen yet is silently dropped from
     * the imported playlist rather than the whole import failing.
     */
    suspend fun import(nowMs: Long = System.currentTimeMillis()) {
        importAll(source.readPlaylists(), nowMs)
    }

    /**
     * The resolve/match/replace logic, separated from reading MediaStore so it can
     * be tested with plain [ScannedPlaylist] values instead of a ContentResolver.
     */
    suspend fun importAll(playlists: List<ScannedPlaylist>, nowMs: Long = System.currentTimeMillis()) {
        playlists.forEach { scanned -> importOne(scanned, nowMs) }
    }

    private suspend fun importOne(scanned: ScannedPlaylist, nowMs: Long) {
        val playlistId = dao.resolveImportedPlaylist(
            PlaylistEntity(
                name = scanned.name,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
                mediaStorePlaylistId = scanned.mediaStoreId,
            ),
        )

        val trackIds = scanned.memberContentUris.mapNotNull { uri -> dao.trackIdForUri(uri) }
        dao.replacePlaylistEntries(playlistId, trackIds)
    }
}
