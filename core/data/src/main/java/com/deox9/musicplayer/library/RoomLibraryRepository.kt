// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import com.deox9.musicplayer.database.dao.LibraryDao
import com.deox9.musicplayer.database.dao.TrackWithNames
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Library reads backed by the indexed database.
 *
 * Returns the models the UI already renders, so the screens did not change when the
 * source moved off MediaStore. What changes is what the rows contain: album artist,
 * disc numbers and proper sort keys, none of which MediaStore gave us directly, plus
 * artist and album names joined in one query instead of a lookup per row.
 */
@Singleton
class RoomLibraryRepository @Inject constructor(
    private val dao: LibraryDao,
) {

    fun observeTracks(): Flow<List<LocalTrack>> =
        dao.observeTracksWithNames().map { rows -> rows.map(TrackWithNames::toLocalTrack) }

    fun observeAlbums(): Flow<List<Album>> =
        dao.observeAlbumsWithArtist().map { rows ->
            rows.map { row ->
                Album(
                    id = row.id,
                    title = row.title,
                    artist = row.artistName ?: UNKNOWN_ARTIST,
                    // Artwork comes from MediaStore's albumart provider, which is
                    // keyed by album id. Embedded-art extraction is a later step.
                    artworkUri = row.mediaStoreAlbumId?.let { AlbumArt.forAlbumId(it) },
                    trackCount = row.trackCount,
                )
            }
        }

    fun observeArtists(): Flow<List<ArtistInfo>> =
        dao.observeArtistCounts().map { rows ->
            rows.map {
                ArtistInfo(
                    id = it.id,
                    name = it.name,
                    trackCount = it.trackCount,
                    albumCount = it.albumCount,
                )
            }
        }

    fun observeGenres(): Flow<List<GenreInfo>> =
        dao.observeGenreCounts().map { rows ->
            rows.map { GenreInfo(id = it.id, name = it.name, trackCount = it.trackCount) }
        }

    fun observeFolders(): Flow<List<FolderInfo>> =
        dao.observeFolderCounts().map { rows ->
            rows.map {
                FolderInfo(
                    path = it.name,
                    name = it.name.substringAfterLast('/').ifBlank { it.name },
                    trackCount = it.trackCount,
                )
            }
        }

    suspend fun tracksByAlbum(albumId: Long): List<LocalTrack> =
        dao.albumTracksWithNames(albumId).map(TrackWithNames::toLocalTrack)

    suspend fun tracksByArtist(artistId: Long): List<LocalTrack> =
        dao.artistTracksWithNames(artistId).map(TrackWithNames::toLocalTrack)

    /**
     * Full-text search.
     *
     * The term is quoted so characters that FTS would otherwise read as operators
     * cannot break the query, and suffixed with `*` for prefix matching while typing.
     */
    suspend fun search(query: String): List<LocalTrack> {
        val term = query.trim().replace("\"", "")
        if (term.isEmpty()) return emptyList()
        return dao.searchTracksWithNames("\"$term\"*").map(TrackWithNames::toLocalTrack)
    }

    suspend fun trackCount(): Int = dao.trackCount()

    /**
     * The loudness figures stored for a track, if any.
     *
     * Surfaced because ReplayGain is otherwise entirely invisible: whether a track
     * was tagged, measured or neither changes what you hear and nothing said so.
     */
    suspend fun replayGainFor(mediaUri: String): TrackLoudness? =
        dao.replayGainFor(mediaUri)?.let {
            TrackLoudness(
                trackGainDb = it.replayGainTrackDb,
                trackPeak = it.replayGainTrackPeak,
                albumGainDb = it.replayGainAlbumDb,
                albumPeak = it.replayGainAlbumPeak,
            )
        }

    fun observePlaylists(): Flow<List<PlaylistInfo>> =
        dao.observePlaylistsWithCounts().map { rows ->
            rows.map { PlaylistInfo(id = it.id, name = it.name, trackCount = it.trackCount) }
        }

    suspend fun tracksByPlaylist(playlistId: Long): List<LocalTrack> =
        dao.playlistTracksWithNames(playlistId).map(TrackWithNames::toLocalTrack)

    suspend fun createPlaylist(name: String): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return dao.createPlaylist(trimmed, System.currentTimeMillis())
    }

    /**
     * Adds a track to a playlist by content URI, resolving it against the index.
     *
     * Returns false rather than throwing when the track is not indexed yet — a race
     * with an in-progress scan, say — since the caller only needs to know the add did
     * not happen, not why.
     */
    suspend fun addTrackToPlaylist(playlistId: Long, trackContentUri: String): Boolean {
        val trackId = dao.trackIdForUri(trackContentUri) ?: return false
        dao.appendTrackToPlaylist(playlistId, trackId)
        return true
    }

    private companion object {
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = ""
    }
}

/**
 * The scan drops MediaStore's literal "<unknown>" artist rather than storing it as a
 * real name, so an untagged file arrives here with a null artist. Substituting the
 * display fallback happens at the edge, keeping the stored data honest about what the
 * file actually claims.
 */
private fun TrackWithNames.toLocalTrack(): LocalTrack = LocalTrack(
    id = id,
    title = title,
    artist = artistName?.takeIf(String::isNotBlank) ?: "Unknown artist",
    album = albumTitle.orEmpty(),
    durationMs = durationMs,
    contentUri = mediaUri,
    artworkUri = albumMediaStoreId?.let { AlbumArt.forAlbumId(it).toString() },
)
