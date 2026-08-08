// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.data.di

import android.content.Context
import com.deox9.musicplayer.library.LocalMusicRepository
import com.deox9.musicplayer.lyrics.LyricsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Library and lyrics data sources.
 *
 * [LocalMusicRepository] keeps process-wide caches in its companion object, so a
 * single instance is what the caching already assumed.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideLocalMusicRepository(
        @ApplicationContext context: Context,
    ): LocalMusicRepository = LocalMusicRepository(context)

    @Provides
    @Singleton
    fun provideLyricsRepository(
        @ApplicationContext context: Context,
    ): LyricsRepository = LyricsRepository(context)
}
