// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.datastore.di

import android.content.Context
import com.deox9.musicplayer.backup.BackupSettingsBridge
import com.deox9.musicplayer.library.FavouritesRepository
import com.deox9.musicplayer.library.RecommendationSignalsRepository
import com.deox9.musicplayer.player.storage.PlaybackSessionRepository
import com.deox9.musicplayer.settings.AppSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Preference-backed repositories, bound as singletons.
 *
 * These were previously built with `remember { XRepository(context) }` at roughly a
 * dozen call sites, so each screen held its own instance and nothing could be
 * substituted in a test.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideAppSettingsRepository(
        @ApplicationContext context: Context,
    ): AppSettingsRepository = AppSettingsRepository(context)

    /**
     * The settings half of a backup.
     *
     * Bound here rather than in :core:data because that module has no business
     * depending on preference storage — the dependency would run the wrong way.
     */
    @Provides
    @Singleton
    fun provideBackupSettingsBridge(
        repository: AppSettingsRepository,
    ): BackupSettingsBridge = object : BackupSettingsBridge {
        override suspend fun export(): Map<String, String> = repository.exportAll()
        override suspend fun restore(values: Map<String, String>): Int = repository.importAll(values)
    }

    @Provides
    @Singleton
    fun provideFavouritesRepository(
        @ApplicationContext context: Context,
    ): FavouritesRepository = FavouritesRepository(context)

    @Provides
    @Singleton
    fun provideRecommendationSignalsRepository(
        @ApplicationContext context: Context,
    ): RecommendationSignalsRepository = RecommendationSignalsRepository(context)

    @Provides
    @Singleton
    fun providePlaybackSessionRepository(
        @ApplicationContext context: Context,
    ): PlaybackSessionRepository = PlaybackSessionRepository(context)
}
