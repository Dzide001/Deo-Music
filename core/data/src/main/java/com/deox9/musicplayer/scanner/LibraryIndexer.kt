// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import com.deox9.musicplayer.database.dao.LibraryDao
import com.deox9.musicplayer.database.entity.AlbumEntity
import com.deox9.musicplayer.database.entity.ArtistEntity
import com.deox9.musicplayer.database.entity.FolderEntity
import com.deox9.musicplayer.database.entity.GenreEntity
import com.deox9.musicplayer.database.entity.TrackEntity
import java.io.File
import javax.inject.Inject

/**
 * Turns [ScannedTrack]s into database rows, resolving artists, albums, genres and
 * folders and reusing existing ids.
 *
 * Split from the source that produced the tracks so the grouping rules — which are
 * where libraries usually go wrong — can be tested without Android.
 */
class LibraryIndexer @Inject constructor(
    private val dao: LibraryDao,
) {

    suspend fun index(tracks: List<ScannedTrack>) {
        if (tracks.isEmpty()) return

        val artistIds = resolveArtists(tracks)
        val genreIds = resolveGenres(tracks)
        val folderIds = resolveFolders(tracks)
        val albumIds = resolveAlbums(tracks, artistIds)

        dao.upsertScannedTracks(
            tracks.map { scanned -> scanned.toEntity(artistIds, albumIds, genreIds, folderIds) },
        )
    }

    private suspend fun resolveArtists(tracks: List<ScannedTrack>): Map<String, Long> {
        // Both performers and album artists need rows: an album's artist may never
        // appear as a track artist on any of its tracks.
        val names = buildSet {
            tracks.forEach { track ->
                track.artist?.takeIf { it.isNotBlank() }?.let(::add)
                add(track.effectiveAlbumArtist())
            }
        }
        if (names.isEmpty()) return emptyMap()

        val ordered = names.toList()
        val ids = dao.resolveArtists(
            ordered.map { ArtistEntity(name = it, sortName = SortKeys.forTitle(it)) },
        )
        return ordered.zip(ids).toMap()
    }

    private suspend fun resolveGenres(tracks: List<ScannedTrack>): Map<String, Long> {
        val names = tracks.mapNotNull { it.genre?.takeIf(String::isNotBlank) }.distinct()
        if (names.isEmpty()) return emptyMap()

        val ids = dao.resolveGenres(names.map { GenreEntity(name = it) })
        return names.zip(ids).toMap()
    }

    private suspend fun resolveFolders(tracks: List<ScannedTrack>): Map<String, Long> {
        val paths = tracks.mapNotNull { it.folderPath?.takeIf(String::isNotBlank) }.distinct()
        if (paths.isEmpty()) return emptyMap()

        val ids = dao.resolveFolders(
            paths.map { path ->
                FolderEntity(path = path, name = File(path).name.ifBlank { path })
            },
        )
        return paths.zip(ids).toMap()
    }

    /**
     * Groups tracks into albums by (album title, album artist).
     *
     * Keying on title alone would merge every "Greatest Hits" in the library into one
     * album; keying on the track artist would split a compilation per performer.
     */
    private suspend fun resolveAlbums(
        tracks: List<ScannedTrack>,
        artistIds: Map<String, Long>,
    ): Map<AlbumKey, Long> {
        val grouped = tracks
            .filter { !it.album.isNullOrBlank() }
            .groupBy { AlbumKey(it.album.orEmpty(), it.effectiveAlbumArtist()) }

        if (grouped.isEmpty()) return emptyMap()

        val keys = grouped.keys.toList()
        val entities = keys.map { key ->
            val members = grouped.getValue(key)
            AlbumEntity(
                title = key.title,
                sortTitle = SortKeys.forTitle(key.title),
                albumArtistId = artistIds[key.albumArtist],
                // Earliest year wins: reissue tags often differ per track.
                year = members.mapNotNull { it.year }.minOrNull(),
                mediaStoreAlbumId = members.firstNotNullOfOrNull { it.albumSourceId },
                discCount = members.mapNotNull { it.discNumber }.maxOrNull() ?: 1,
                isCompilation = members.any { it.isCompilation } ||
                    key.albumArtist == ScannedTrack.VARIOUS_ARTISTS,
            )
        }
        val ids = dao.resolveAlbums(entities)
        return keys.zip(ids).toMap()
    }

    private fun ScannedTrack.toEntity(
        artistIds: Map<String, Long>,
        albumIds: Map<AlbumKey, Long>,
        genreIds: Map<String, Long>,
        folderIds: Map<String, Long>,
    ): TrackEntity {
        val resolvedTitle = title.ifBlank { ScannedTrack.UNKNOWN_TITLE }
        return TrackEntity(
            title = resolvedTitle,
            sortTitle = SortKeys.forTitle(resolvedTitle),
            mediaUri = mediaUri,
            filePath = filePath,
            sourceId = sourceId,
            artistId = artist?.takeIf(String::isNotBlank)?.let(artistIds::get),
            albumId = album?.takeIf(String::isNotBlank)
                ?.let { albumIds[AlbumKey(it, effectiveAlbumArtist())] },
            genreId = genre?.takeIf(String::isNotBlank)?.let(genreIds::get),
            folderId = folderPath?.takeIf(String::isNotBlank)?.let(folderIds::get),
            trackNumber = trackNumber,
            discNumber = discNumber,
            year = year,
            durationMs = durationMs,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            bitrateKbps = bitrateKbps,
            dateAddedMs = dateAddedMs,
            dateModifiedMs = dateModifiedMs,
        )
    }

    data class AlbumKey(val title: String, val albumArtist: String)
}
