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

    // ---- Writes --------------------------------------------------------------

    @Upsert
    suspend fun upsertArtists(artists: List<ArtistEntity>): List<Long>

    @Upsert
    suspend fun upsertAlbums(albums: List<AlbumEntity>): List<Long>

    @Upsert
    suspend fun upsertGenres(genres: List<GenreEntity>): List<Long>

    @Upsert
    suspend fun upsertFolders(folders: List<FolderEntity>): List<Long>

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

    @Upsert
    suspend fun addFavourite(favourite: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE trackId = :trackId")
    suspend fun removeFavourite(trackId: Long)
}
