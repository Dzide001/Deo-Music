// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.deox9.musicplayer.database.dao.LibraryDao
import com.deox9.musicplayer.database.entity.AlbumEntity
import com.deox9.musicplayer.database.entity.ArtistEntity
import com.deox9.musicplayer.database.entity.BookmarkEntity
import com.deox9.musicplayer.database.entity.DspProfileEntity
import com.deox9.musicplayer.database.entity.FavouriteEntity
import com.deox9.musicplayer.database.entity.FolderEntity
import com.deox9.musicplayer.database.entity.GenreEntity
import com.deox9.musicplayer.database.entity.PlayHistoryEntity
import com.deox9.musicplayer.database.entity.PlaylistEntity
import com.deox9.musicplayer.database.entity.PlaylistEntryEntity
import com.deox9.musicplayer.database.entity.TrackEntity
import com.deox9.musicplayer.database.entity.TrackFtsEntity

/**
 * The library database.
 *
 * The exported schema is committed under `schemas/`, so every future migration can be
 * written and tested against a recorded baseline instead of a remembered one.
 */
@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        GenreEntity::class,
        FolderEntity::class,
        TrackEntity::class,
        TrackFtsEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        PlayHistoryEntity::class,
        FavouriteEntity::class,
        DspProfileEntity::class,
        BookmarkEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val NAME = "deo-music.db"
    }
}
