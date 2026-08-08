// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point and Hilt's dependency graph root.
 *
 * Also supplies WorkManager's factory: a @HiltWorker has injected constructor
 * parameters that the default factory cannot satisfy, so without this the library
 * scan would fail to instantiate at runtime.
 */
@HiltAndroidApp
class DeoMusicApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
