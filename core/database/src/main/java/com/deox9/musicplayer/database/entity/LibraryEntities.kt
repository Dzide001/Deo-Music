// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The library schema.
 *
 * Deliberately modelled up front for things not built yet — album artist, disc and
 * track numbers, ReplayGain tags, play history, per-output DSP profiles — because
 * migrating a schema across a user's 40,000-track library later is far more painful
 * than carrying unused columns now.
 *
 * MediaStore is treated as one possible source, not the source of truth: [TrackEntity]
 * keeps its own ids and a [sourceId] back-reference, so a SAF/tag-parsing scan can
 * populate the same rows without the model changing.
 */

@Entity(
    tableName = "artists",
    indices = [Index(value = ["name"], unique = true), Index("sortName")],
)
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Name with leading articles stripped, so "The Beatles" files under B. */
    val sortName: String,
)

@Entity(
    tableName = "albums",
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumArtistId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("albumArtistId"), Index("title"), Index("sortTitle")],
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sortTitle: String,
    /**
     * Album artist, not track artist. Getting this wrong is what shatters a
     * "Various Artists" compilation into forty separate artists.
     */
    val albumArtistId: Long?,
    val year: Int? = null,
    val artworkUri: String? = null,
    /**
     * MediaStore ALBUM_ID, used to resolve artwork from its albumart provider.
     * A track id will not work there — the provider is keyed by album.
     */
    val mediaStoreAlbumId: Long? = null,
    val discCount: Int = 1,
    /** True when the album is a compilation, from the tag rather than inferred. */
    val isCompilation: Boolean = false,
)

@Entity(tableName = "genres", indices = [Index(value = ["name"], unique = true)])
data class GenreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "folders", indices = [Index(value = ["path"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val name: String,
    /** Excluded folders stay indexed but are hidden, so re-including is cheap. */
    val isBlacklisted: Boolean = false,
)

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(ArtistEntity::class, ["id"], ["artistId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(AlbumEntity::class, ["id"], ["albumId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(GenreEntity::class, ["id"], ["genreId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(FolderEntity::class, ["id"], ["folderId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["mediaUri"], unique = true),
        Index("artistId"), Index("albumId"), Index("genreId"), Index("folderId"),
        Index("sortTitle"), Index("dateAddedMs"),
        // Multi-disc albums must order by disc then track, so index the pair.
        Index(value = ["albumId", "discNumber", "trackNumber"]),
    ],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sortTitle: String,
    val mediaUri: String,
    /** Filesystem path when known; null for sources that do not expose one. */
    val filePath: String? = null,
    /** MediaStore _ID when this row came from MediaStore, for incremental rescans. */
    val sourceId: Long? = null,

    val artistId: Long?,
    val albumId: Long?,
    val genreId: Long?,
    val folderId: Long?,

    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val durationMs: Long = 0,

    val composer: String? = null,
    val grouping: String? = null,
    val comment: String? = null,
    val bpm: Int? = null,
    val musicalKey: String? = null,

    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val bitDepth: Int? = null,

    // ReplayGain, read from TXXX:REPLAYGAIN_* or Vorbis comments. Null means
    // untagged, which is different from 0 dB and must stay distinguishable.
    val replayGainTrackDb: Float? = null,
    val replayGainTrackPeak: Float? = null,
    val replayGainAlbumDb: Float? = null,
    val replayGainAlbumPeak: Float? = null,

    // Stars, 0-5, read from the file's own rating tag — POPM on ID3, a percentage on
    // Vorbis and MP4. Null means the file carries no rating, which is different from
    // a rating of zero: there is no way to say "worthless" in POPM, and the two must
    // stay distinguishable or a rescan would invent ratings nobody gave.
    val rating: Int? = null,

    val dateAddedMs: Long = 0,
    val dateModifiedMs: Long = 0,
)

/**
 * Full-text index over the fields users actually search.
 *
 * FTS4 rather than FTS5 for the wider platform floor at minSdk 26.
 */
@Fts4(contentEntity = TrackEntity::class)
@Entity(tableName = "tracks_fts")
data class TrackFtsEntity(
    val title: String,
    val sortTitle: String,
)

@Entity(tableName = "playlists", indices = [Index("name"), Index(value = ["mediaStorePlaylistId"], unique = true)])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    /**
     * Serialised rules for a smart playlist; null for a manual one. Present from the
     * start so adding smart playlists later needs no migration.
     */
    val smartRules: String? = null,
    /**
     * MediaStore's own playlist id, present only for playlists imported from there.
     * A unique index on this lets re-import resolve the existing row instead of
     * inserting a duplicate on every scan; a playlist created in-app has no
     * MediaStore counterpart and stays null.
     */
    val mediaStorePlaylistId: Long? = null,
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["playlistId", "position"]), Index("trackId")],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

@Entity(
    tableName = "play_history",
    foreignKeys = [
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("trackId"), Index("playedAtMs")],
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAtMs: Long,
    /** How much was actually heard, so a skip is distinguishable from a play. */
    val playedMs: Long,
    val completed: Boolean,
)

/**
 * A named point inside a track.
 *
 * For a two-hour DJ set, a lecture, or the one verse someone keeps coming back to.
 * Distinct from the A–B loop, which is a section being repeated right now and dies
 * with the track; a bookmark is meant to outlive the session.
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("trackId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val positionMs: Long,
    /** Blank means unnamed; the UI shows the timestamp instead. */
    val label: String = "",
    val createdAtMs: Long = 0,
)

@Entity(
    tableName = "favourites",
    foreignKeys = [
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["trackId"], unique = true)],
)
data class FavouriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val addedAtMs: Long,
)

/**
 * A DSP profile bound to an output route.
 *
 * Keyed by the stable part of AudioDeviceInfo so a given Bluetooth device or USB DAC
 * gets its own EQ automatically.
 */
@Entity(tableName = "dsp_profiles", indices = [Index(value = ["deviceKey"], unique = true)])
data class DspProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceKey: String,
    val displayName: String,
    val isEnabled: Boolean = true,
    /** Serialised biquad bands: frequency, gain and Q per band. */
    val equaliserBands: String? = null,
    val preampDb: Float = 0f,
    val replayGainMode: String? = null,
)
