// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.database.di

import android.content.Context
import androidx.room.Room
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
        // No fallbackToDestructiveMigration: a music library is expensive to rebuild
        // and silently wiping it on a schema change is not an acceptable default.
        .build()

    @Provides
    fun provideLibraryDao(database: MusicDatabase): LibraryDao = database.libraryDao()
}
