// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.deox9.musicplayer.feature.widget.refreshNowPlayingWidgets
import com.deox9.musicplayer.feature.widget.toWidgetState
import com.deox9.musicplayer.player.storage.PlaybackSessionRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    override fun onCreate() {
        super.onCreate()
        keepWidgetsInStepWithPlayback()
    }

    /**
     * Redraws the home-screen widget whenever the track or play state changes.
     *
     * The widget collects the same session flow itself, and that is not enough: a
     * Glance composition stops once it has produced its RemoteViews, so the
     * collection ends with it and the widget freezes on whatever was playing when it
     * was last drawn. Changing track inside the app left it showing the previous one
     * indefinitely — verified on a phone, sixteen seconds and counting — and it
     * appeared to recover only when one of its own buttons was pressed, because that
     * forces a fresh composition.
     *
     * So the push has to come from something that outlives the composition. The
     * Application is the smallest such thing that is already alive whenever playback
     * is: the service runs in this process, so if there is anything to draw, this is
     * running.
     *
     * It lives here rather than in the service because the dependency may only point
     * this way. `:core:media` knows nothing about widgets, and should not — `:app` is
     * the one module that already depends on both.
     */
    private fun keepWidgetsInStepWithPlayback() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            PlaybackSessionRepository(this@DeoMusicApplication)
                .observe()
                // Mapped to what a widget draws before comparing, which is what makes
                // this affordable: the service rewrites the session every second to
                // keep the resume position fresh, and WidgetState deliberately has no
                // position in it. Comparing the raw session would redraw every widget
                // once a second, forever.
                .map { it.toWidgetState() }
                .distinctUntilChanged()
                // The first value is whatever was already on screen; redrawing for it
                // costs an update on every cold start and changes nothing.
                .drop(1)
                .collect { refreshNowPlayingWidgets(this@DeoMusicApplication) }
        }
    }
}
