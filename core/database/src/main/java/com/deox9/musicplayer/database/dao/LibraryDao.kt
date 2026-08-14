// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.deox9.musicplayer.database.entity.AlbumEntity
import com.deox9.musicplayer.database.entity.ArtistEntity
import com.deox9.musicplayer.database.entity.FavouriteEntity
import com.deox9.musicplayer.database.entity.FolderEntity
import com.deox9.musicplayer.database.entity.GenreEntity
import com.deox9.musicplayer.database.entity.PlayHistoryEntity
import com.deox9.musicplayer.database.entity.PlaylistEntity
import com.deox9.musicplayer.database.entity.PlaylistEntryEntity
import com.deox9.musicplayer.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    // ---- Reads ---------------------------------------------------------------

    @Query("SELECT * FROM tracks ORDER BY sortTitle COLLATE NOCASE ASC")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun track(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE mediaUri = :mediaUri")
    suspend fun trackByUri(mediaUri: String): TrackEntity?

    /** Multi-disc albums order by disc first, which is what the index is for. */
    @Query(
        """
        SELECT * FROM tracks
        WHERE albumId = :albumId
        ORDER BY discNumber ASC, trackNumber ASC, sortTitle COLLATE NOCASE ASC
        """,
    )
    fun observeAlbumTracks(albumId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM albums ORDER BY sortTitle COLLATE NOCASE ASC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM artists ORDER BY sortName COLLATE NOCASE ASC")
    fun observeArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM folders WHERE isBlacklisted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM genres ORDER BY name COLLATE NOCASE ASC")
    fun observeGenres(): Flow<List<GenreEntity>>

    @Query("SELECT * FROM genres ORDER BY name COLLATE NOCASE ASC")
    suspend fun observeGenresList(): List<GenreEntity>

    /**
     * Full-text search over the FTS index.
     *
     * The caller passes a raw term; the `*` suffix makes it a prefix match so results
     * appear while typing.
     */
    @Query(
        """
        SELECT tracks.* FROM tracks
        JOIN tracks_fts ON tracks.rowid = tracks_fts.rowid
        WHERE tracks_fts MATCH :query
        ORDER BY tracks.sortTitle COLLATE NOCASE ASC
        """,
    )
    suspend fun searchTracks(query: String): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun trackCount(): Int

    /**
     * Tracks with no track-gain figure from either a tag or a measurement.
     *
     * Ordered newest first so a freshly copied album is measured before the rest of
     * the library, which is the part the user is most likely to play next.
     */
    @Query(
        """
        SELECT id, mediaUri FROM tracks
        WHERE replayGainTrackDb IS NULL
        ORDER BY dateAddedMs DESC
        LIMIT :limit
        """,
    )
    suspend fun tracksWithoutReplayGain(limit: Int): List<TrackGainTarget>

    @Query(
        """
        UPDATE tracks
        SET replayGainTrackDb = :gainDb, replayGainTrackPeak = :peak
        WHERE id = :trackId
        """,
    )
    suspend fun setMeasuredReplayGain(trackId: Long, gainDb: Float, peak: Float)

    /** The gain and peak to apply for one track at playback. */
    @Query(
        """
        SELECT replayGainTrackDb, replayGainTrackPeak, replayGainAlbumDb, replayGainAlbumPeak
        FROM tracks WHERE mediaUri = :mediaUri
        """,
    )
    suspend fun replayGainFor(mediaUri: String): TrackReplayGain?

    // ---- Joined reads --------------------------------------------------------

    @Query(
        """
        SELECT t.id, t.title, t.mediaUri, t.durationMs,
               ar.name AS artistName, al.title AS albumTitle, t.albumId,
               t.trackNumber, t.discNumber, f.path AS folderPath, g.name AS genreName,
               t.dateAddedMs, al.mediaStoreAlbumId AS albumMediaStoreId
        FROM tracks t
        LEFT JOIN artists ar ON ar.id = t.artistId
        LEFT JOIN albums  al ON al.id = t.albumId
        LEFT JOIN folders f  ON f.id  = t.folderId
        LEFT JOIN genres  g  ON g.id  = t.genreId
        ORDER BY t.sortTitle COLLATE NOCASE ASC
        """,
    )
    fun observeTracksWithNames(): Flow<List<TrackWithNames>>

    @Query(
        """
        SELECT t.id, t.title, t.mediaUri, t.durationMs,
               ar.name AS artistName, al.title AS albumTitle, t.albumId,
               t.trackNumber, t.discNumber, f.path AS folderPath, g.name AS genreName,
               t.dateAddedMs, al.mediaStoreAlbumId AS albumMediaStoreId
        FROM tracks t
        LEFT JOIN artists ar ON ar.id = t.artistId
        LEFT JOIN albums  al ON al.id = t.albumId
        LEFT JOIN folders f  ON f.id  = t.folderId
        LEFT JOIN genres  g  ON g.id  = t.genreId
        WHERE t.albumId = :albumId
        ORDER BY t.discNumber ASC, t.trackNumber ASC, t.sortTitle COLLATE NOCASE ASC
        """,
    )
    suspend fun albumTracksWithNames(albumId: Long): List<TrackWithNames>

    @Query(
        """
        SELECT al.id, al.title, ar.name AS artistName, al.artworkUri,
               COUNT(t.id) AS trackCount, al.year, al.isCompilation,
               al.mediaStoreAlbumId
        FROM albums al
        LEFT JOIN artists ar ON ar.id = al.albumArtistId
        LEFT JOIN tracks  t  ON t.albumId = al.id
        GROUP BY al.id
        ORDER BY al.sortTitle COLLATE NOCASE ASC
        """,
    )
    fun observeAlbumsWithArtist(): Flow<List<AlbumWithArtist>>

    /**
     * Artists with what the list needs to render a row.
     *
     * The join is on the track's artist, not the album's: an artist with one guest
     * appearance should still show up, and `albums.albumArtistId` deliberately does
     * not record those. `COUNT(DISTINCT t.albumId)` therefore counts the albums the
     * artist appears on, which is what the subtitle claims.
     *
     * `HAVING trackCount > 0` drops artists whose tracks were all removed — the rows
     * survive because tracks null out the reference rather than cascading, and an
     * artist with no music is not something to show.
     */
    @Query(
        """
        SELECT ar.id, ar.name, COUNT(t.id) AS trackCount,
               COUNT(DISTINCT t.albumId) AS albumCount
        FROM artists ar
        LEFT JOIN tracks t ON t.artistId = ar.id
        GROUP BY ar.id
        HAVING trackCount > 0
        ORDER BY ar.sortName COLLATE NOCASE ASC
        """,
    )
    fun observeArtistCounts(): Flow<List<ArtistWithCounts>>

    @Query(
        """
        SELECT t.id, t.title, t.mediaUri, t.durationMs,
               ar.name AS artistName, al.title AS albumTitle, t.albumId,
               t.trackNumber, t.discNumber, f.path AS folderPath, g.name AS genreName,
               t.dateAddedMs, al.mediaStoreAlbumId AS albumMediaStoreId
        FROM tracks t
        LEFT JOIN artists ar ON ar.id = t.artistId
        LEFT JOIN albums  al ON al.id = t.albumId
        LEFT JOIN folders f  ON f.id  = t.folderId
        LEFT JOIN genres  g  ON g.id  = t.genreId
        WHERE t.artistId = :artistId
        ORDER BY al.sortTitle COLLATE NOCASE ASC, t.discNumber ASC, t.trackNumber ASC
        """,
    )
    suspend fun artistTracksWithNames(artistId: Long): List<TrackWithNames>

    @Query(
        """
        SELECT g.id, g.name, COUNT(t.id) AS trackCount
        FROM genres g
        LEFT JOIN tracks t ON t.genreId = g.id
        GROUP BY g.id
        ORDER BY g.name COLLATE NOCASE ASC
        """,
    )
    fun observeGenreCounts(): Flow<List<NamedCount>>

    @Query(
        """
        SELECT f.id, f.path AS name, COUNT(t.id) AS trackCount
        FROM folders f
        LEFT JOIN tracks t ON t.folderId = f.id
        WHERE f.isBlacklisted = 0
        GROUP BY f.id
        ORDER BY f.name COLLATE NOCASE ASC
        """,
    )
    fun observeFolderCounts(): Flow<List<NamedCount>>

    @Query(
        """
        SELECT t.id, t.title, t.mediaUri, t.durationMs,
               ar.name AS artistName, al.title AS albumTitle, t.albumId,
               t.trackNumber, t.discNumber, f.path AS folderPath, g.name AS genreName,
               t.dateAddedMs, al.mediaStoreAlbumId AS albumMediaStoreId
        FROM tracks t
        JOIN tracks_fts ON t.rowid = tracks_fts.rowid
        LEFT JOIN artists ar ON ar.id = t.artistId
        LEFT JOIN albums  al ON al.id = t.albumId
        LEFT JOIN folders f  ON f.id  = t.folderId
        LEFT JOIN genres  g  ON g.id  = t.genreId
        WHERE tracks_fts MATCH :query
        ORDER BY t.sortTitle COLLATE NOCASE ASC
        """,
    )
    suspend fun searchTracksWithNames(query: String): List<TrackWithNames>

    // ---- Writes --------------------------------------------------------------

    @Upsert
    suspend fun upsertArtists(artists: List<ArtistEntity>): List<Long>

    @Upsert
    suspend fun upsertAlbums(albums: List<AlbumEntity>): List<Long>

    @Upsert
    suspend fun upsertGenres(genres: List<GenreEntity>): List<Long>

    @Upsert
    suspend fun upsertFolders(folders: List<FolderEntity>): List<Long>

    @Query("SELECT id FROM artists WHERE name = :name")
    suspend fun artistIdByName(name: String): Long?

    @Query("SELECT id FROM genres WHERE name = :name")
    suspend fun genreIdByName(name: String): Long?

    @Query("SELECT id FROM folders WHERE path = :path")
    suspend fun folderIdByPath(path: String): Long?

    @Query("SELECT id FROM albums WHERE title = :title AND albumArtistId IS :albumArtistId")
    suspend fun albumId(title: String, albumArtistId: Long?): Long?

    /**
     * Resolve-or-insert helpers for the lookup tables.
     *
     * These exist for the same reason as [upsertScannedTracks]. @Upsert matches on the
     * primary key, and a freshly built entity has id 0, so on a rescan the insert
     * collides with the unique natural key, the fallback update finds no row with
     * id 0, and the returned id is meaningless. Tracks then reference an artist or
     * album that does not exist and the whole scan dies on a foreign-key violation —
     * on the *second* scan, not the first.
     */
    @Transaction
    suspend fun resolveArtists(artists: List<ArtistEntity>): List<Long> =
        artists.map { artist ->
            artistIdByName(artist.name)
                ?: upsertArtists(listOf(artist)).first()
        }

    @Transaction
    suspend fun resolveGenres(genres: List<GenreEntity>): List<Long> =
        genres.map { genre -> genreIdByName(genre.name) ?: upsertGenres(listOf(genre)).first() }

    @Transaction
    suspend fun resolveFolders(folders: List<FolderEntity>): List<Long> =
        folders.map { folder -> folderIdByPath(folder.path) ?: upsertFolders(listOf(folder)).first() }

    @Transaction
    suspend fun resolveAlbums(albums: List<AlbumEntity>): List<Long> =
        albums.map { album ->
            val existing = albumId(album.title, album.albumArtistId)
            if (existing != null) {
                // Refresh derived fields — disc count and year can change as more of
                // an album is indexed.
                upsertAlbums(listOf(album.copy(id = existing)))
                existing
            } else {
                upsertAlbums(listOf(album)).first()
            }
        }

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("SELECT id FROM tracks WHERE mediaUri = :mediaUri")
    suspend fun trackIdForUri(mediaUri: String): Long?

    /**
     * Upserts scanned tracks, carrying over the existing row id for any file already
     * indexed. Scanners should always use this rather than [upsertTracks].
     *
     * @Upsert matches on the primary key, and a freshly scanned entity has id 0, so
     * a rescan would insert, collide with the unique mediaUri index, and then fail to
     * update anything — silently discarding re-read tags.
     *
     * Switching the conflict strategy to REPLACE would be worse: REPLACE deletes the
     * old row first, and the cascades from favourites and play_history would take the
     * user's favourites and listening history with it on every rescan.
     */
    @Transaction
    suspend fun upsertScannedTracks(tracks: List<TrackEntity>) {
        val withExistingIds = tracks.map { incoming ->
            val existingId = trackIdForUri(incoming.mediaUri)
            if (existingId != null) incoming.copy(id = existingId) else incoming
        }
        upsertTracks(withExistingIds)
    }

    @Query("DELETE FROM tracks WHERE mediaUri IN (:mediaUris)")
    suspend fun deleteTracksByUri(mediaUris: List<String>)

    /**
     * URIs of tracks that came from MediaStore.
     *
     * Scoped to that source so pruning after a MediaStore pass cannot delete rows
     * indexed from anywhere else.
     */
    @Query("SELECT mediaUri FROM tracks WHERE sourceId IS NOT NULL")
    suspend fun mediaStoreTrackUris(): List<String>

    /**
     * Removes rows a rescan no longer found.
     *
     * Scoped by folder so a scan of one library root cannot delete tracks belonging
     * to another that was not scanned this pass.
     */
    @Query("DELETE FROM tracks WHERE folderId = :folderId AND mediaUri NOT IN (:keptUris)")
    suspend fun deleteMissingTracksInFolder(folderId: Long, keptUris: List<String>)

    @Transaction
    suspend fun replaceFolderContents(folderId: Long, tracks: List<TrackEntity>) {
        upsertScannedTracks(tracks)
        deleteMissingTracksInFolder(folderId, tracks.map { it.mediaUri })
    }

    // ---- History and favourites ---------------------------------------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlay(play: PlayHistoryEntity)

    @Query(
        """
        SELECT tracks.* FROM tracks
        JOIN play_history ON play_history.trackId = tracks.id
        WHERE play_history.playedAtMs >= :sinceMs
        GROUP BY tracks.id
        ORDER BY COUNT(play_history.id) DESC
        LIMIT :limit
        """,
    )
    suspend fun mostPlayedSince(sinceMs: Long, limit: Int): List<TrackEntity>

    @Query(
        """
        SELECT tracks.* FROM tracks
        LEFT JOIN play_history ON play_history.trackId = tracks.id
        WHERE play_history.id IS NULL
        ORDER BY tracks.dateAddedMs DESC
        """,
    )
    suspend fun neverPlayed(): List<TrackEntity>

    @Query("SELECT * FROM favourites")
    fun observeFavourites(): Flow<List<FavouriteEntity>>

    // ---- backup ---------------------------------------------------------------

    /**
     * Every track, with each identity a restore might match on.
     *
     * The whole table at once because a restore compares each saved reference
     * against all of them, and doing that as a query per reference would be one
     * round trip per track in every playlist.
     */
    @Query(
        """
        SELECT t.id AS id, t.mediaUri AS mediaUri, t.filePath AS filePath,
               t.title AS title, ar.name AS artistName, al.title AS albumTitle,
               t.durationMs AS durationMs
        FROM tracks t
        LEFT JOIN artists ar ON ar.id = t.artistId
        LEFT JOIN albums al ON al.id = t.albumId
        """,
    )
    suspend fun allTrackIdentities(): List<TrackIdentity>

    @Query(
        """
        SELECT p.id AS id, p.name AS name, COUNT(e.trackId) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_entries e ON e.playlistId = p.id
        GROUP BY p.id
        """,
    )
    suspend fun allPlaylists(): List<NamedCount>

    @Query("SELECT * FROM favourites")
    suspend fun allFavourites(): List<FavouriteEntity>

    /** The track's own path, for reading tags or embedded art out of the file. */
    @Query("SELECT filePath FROM tracks WHERE mediaUri = :mediaUri LIMIT 1")
    suspend fun filePathFor(mediaUri: String): String?

    /**
     * Play counts, counting only what was actually heard.
     *
     * A skip writes a history row too — that is what makes the recommendations work
     * — so counting rows would report a track skipped twenty times as a favourite.
     */
    @Query(
        """
        SELECT trackId AS trackId, COUNT(*) AS playCount, MAX(playedAtMs) AS lastPlayedAtMs
        FROM play_history
        WHERE completed = 1
        GROUP BY trackId
        """,
    )
    suspend fun allPlayCounts(): List<TrackPlayCount>

    @Upsert
    suspend fun addFavourite(favourite: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE trackId = :trackId")
    suspend fun removeFavourite(trackId: Long)

    // ---- Playlists -------------------------------------------------------------

    @Query(
        """
        SELECT p.id, p.name, COUNT(pe.id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_entries pe ON pe.playlistId = p.id
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE ASC
        """,
    )
    fun observePlaylistsWithCounts(): Flow<List<NamedCount>>

    @Query(
        """
        SELECT t.id, t.title, t.mediaUri, t.durationMs,
               ar.name AS artistName, al.title AS albumTitle, t.albumId,
               t.trackNumber, t.discNumber, f.path AS folderPath, g.name AS genreName,
               t.dateAddedMs, al.mediaStoreAlbumId AS albumMediaStoreId
        FROM playlist_entries pe
        JOIN tracks t ON t.id = pe.trackId
        LEFT JOIN artists ar ON ar.id = t.artistId
        LEFT JOIN albums  al ON al.id = t.albumId
        LEFT JOIN folders f  ON f.id  = t.folderId
        LEFT JOIN genres  g  ON g.id  = t.genreId
        WHERE pe.playlistId = :playlistId
        ORDER BY pe.position ASC
        """,
    )
    suspend fun playlistTracksWithNames(playlistId: Long): List<TrackWithNames>

    @Query("SELECT id FROM playlists WHERE mediaStorePlaylistId = :mediaStorePlaylistId")
    suspend fun playlistIdByMediaStoreId(mediaStorePlaylistId: Long): Long?

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    /** Creates a playlist typed in-app, which has no MediaStore counterpart. */
    suspend fun createPlaylist(name: String, nowMs: Long): Long =
        insertPlaylist(PlaylistEntity(name = name, createdAtMs = nowMs, updatedAtMs = nowMs))

    /**
     * Resolves an imported playlist to its existing row, or inserts a new one.
     *
     * Mirrors [resolveArtists] and friends: matching by the MediaStore id means a
     * re-import updates the row in place instead of accumulating a duplicate on
     * every scan.
     */
    @Transaction
    suspend fun resolveImportedPlaylist(playlist: PlaylistEntity): Long {
        val mediaStoreId = requireNotNull(playlist.mediaStorePlaylistId) {
            "resolveImportedPlaylist requires mediaStorePlaylistId; use insertPlaylist for a manual playlist"
        }
        val existing = playlistIdByMediaStoreId(mediaStoreId)
        return if (existing != null) {
            updatePlaylistName(existing, playlist.name, playlist.updatedAtMs)
            existing
        } else {
            insertPlaylist(playlist)
        }
    }

    @Query("UPDATE playlists SET name = :name, updatedAtMs = :updatedAtMs WHERE id = :playlistId")
    suspend fun updatePlaylistName(playlistId: Long, name: String, updatedAtMs: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun nextPlaylistPosition(playlistId: Long): Int

    @Insert
    suspend fun insertPlaylistEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearPlaylistEntries(playlistId: Long)

    /**
     * Replaces a playlist's members wholesale, in the given order.
     *
     * This is a one-way mirror of MediaStore, not a merge: the app does not yet
     * support reordering or removing within a playlist, so there is no in-app state
     * that a naive overwrite could lose. Once that lands this needs to become a
     * proper diff instead of clear-and-reinsert.
     */
    @Transaction
    suspend fun replacePlaylistEntries(playlistId: Long, trackIds: List<Long>) {
        clearPlaylistEntries(playlistId)
        insertPlaylistEntries(
            trackIds.mapIndexed { position, trackId ->
                PlaylistEntryEntity(playlistId = playlistId, trackId = trackId, position = position)
            },
        )
    }

    @Transaction
    suspend fun appendTrackToPlaylist(playlistId: Long, trackId: Long) {
        val position = nextPlaylistPosition(playlistId)
        insertPlaylistEntries(listOf(PlaylistEntryEntity(playlistId = playlistId, trackId = trackId, position = position)))
    }
}
