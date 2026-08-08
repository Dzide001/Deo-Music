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
)
