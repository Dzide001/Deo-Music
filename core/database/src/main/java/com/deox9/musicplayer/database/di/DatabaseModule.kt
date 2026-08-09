// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.deox9.musicplayer.database.MusicDatabase
import com.deox9.musicplayer.database.dao.LibraryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMusicDatabase(
        @ApplicationContext context: Context,
    ): MusicDatabase = Room.databaseBuilder(context, MusicDatabase::class.java, MusicDatabase.NAME)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        // No fallbackToDestructiveMigration: a music library is expensive to rebuild
        // and silently wiping it on a schema change is not an acceptable default.
        // Favourites and play history live in here too and are not re-derivable.
        .build()

    /**
     * Adds albums.mediaStoreAlbumId.
     *
     * Artwork was being resolved with a track id against MediaStore's albumart
     * provider, which is keyed by album, so no album art loaded. Existing rows get
     * null and are backfilled by the next scan.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE albums ADD COLUMN mediaStoreAlbumId INTEGER DEFAULT NULL")
        }
    }

    /**
     * Adds playlists.mediaStorePlaylistId, so importing MediaStore playlists can
     * resolve an existing row on re-scan instead of inserting a duplicate.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlists ADD COLUMN mediaStorePlaylistId INTEGER DEFAULT NULL")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_playlists_mediaStorePlaylistId " +
                    "ON playlists (mediaStorePlaylistId)",
            )
        }
    }

    @Provides
    fun provideLibraryDao(database: MusicDatabase): LibraryDao = database.libraryDao()
}
