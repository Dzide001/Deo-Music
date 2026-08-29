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
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
    /**
     * Adds bookmarks.
     *
     * A new table only, so nothing existing is rewritten and the migration cannot
     * lose anyone's library. The foreign key matches the entity exactly — Room
     * compares the schema it finds against the one it expects and fails the app at
     * startup over a difference as small as a missing ON DELETE clause.
     */
    /**
     * Adds tracks.rating.
     *
     * Null on every existing row, and left that way rather than defaulted to zero:
     * null is "this file carries no rating" and zero would be a rating. The next
     * scan fills in whatever the files actually say.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN rating INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `bookmarks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trackId` INTEGER NOT NULL,
                    `positionMs` INTEGER NOT NULL,
                    `label` TEXT NOT NULL,
                    `createdAtMs` INTEGER NOT NULL,
                    FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_bookmarks_trackId` ON `bookmarks` (`trackId`)",
            )
        }
    }

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
