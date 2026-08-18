// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.widget

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.deox9.musicplayer.player.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** The transport a widget can drive. */
enum class WidgetCommand { PlayPause, Next, Previous }

/**
 * Sends one command to the playback service and lets go.
 *
 * A widget has no process of its own and no controller to keep alive, so it binds,
 * issues the command and releases. Binding rather than starting the service matters:
 * a background `startService` is blocked from API 26 when nothing is running, which is
 * exactly the case where the widget's play button is most likely to be pressed.
 *
 * Runs on the main thread, and this is the whole reason the buttons did nothing. A
 * MediaController may only be touched from the thread it was built on, and Glance
 * runs an ActionCallback on a background dispatcher — so every press built a
 * controller off-main and then threw on the first call. The throw went nowhere,
 * because failure here was silent by design and the widget draws from the persisted
 * session, so a press that never landed looked exactly like a press that did nothing.
 *
 * Silence is still right for a widget with nowhere to show an error, but it hid this
 * for as long as it existed, so the thread is logged rather than assumed.
 */
@OptIn(UnstableApi::class)
suspend fun sendWidgetCommand(context: Context, command: WidgetCommand) = withContext(Dispatchers.Main) {
    check(Looper.myLooper() == Looper.getMainLooper()) {
        "MediaController must be built and used on the main thread"
    }
    val appContext = context.applicationContext
    val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
    val controller = awaitController(appContext, token) ?: return@withContext

    try {
        when (command) {
            WidgetCommand.PlayPause -> if (controller.isPlaying) controller.pause() else controller.play()
            WidgetCommand.Next -> controller.seekToNextMediaItem()
            WidgetCommand.Previous -> controller.seekToPreviousMediaItem()
        }
        // Let the command reach the service before letting go. release() tears down
        // the connection, and these calls are asynchronous — issuing one and
        // releasing in the same breath can cancel it in flight.
        awaitDispatch()
    } finally {
        controller.release()
    }
}

/**
 * Yields long enough for the queued command to cross to the service.
 *
 * A main-looper post rather than a delay: it returns as soon as the messages already
 * queued ahead of it — which includes the transport call — have been dispatched.
 */
private suspend fun awaitDispatch() = suspendCancellableCoroutine { continuation ->
    android.os.Handler(Looper.getMainLooper()).post {
        if (continuation.isActive) continuation.resume(Unit)
    }
}

/**
 * Connects a controller, from the application context rather than the one handed in.
 *
 * This is what made every widget button do nothing. Glance delivers an action through
 * the widget's BroadcastReceiver, so `onAction` receives a ReceiverRestrictedContext,
 * and Android forbids `bindService` from one:
 *
 *     ReceiverCallNotAllowedException: BroadcastReceiver components are not
 *     allowed to bind to services
 *
 * A MediaController binds, so it threw on construction every single time. The throw
 * was swallowed by the runCatching below — which existed so a failed connection would
 * degrade quietly — and the result was a control that looked fine and did nothing.
 *
 * `applicationContext` is the same process and the same permissions; it simply is not
 * the restricted wrapper, so it may bind.
 */
private suspend fun awaitController(context: Context, token: SessionToken): MediaController? =
    suspendCancellableCoroutine { continuation ->
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (continuation.isActive) continuation.resume(controller)
            },
            MoreExecutors.directExecutor(),
        )
        continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
    }
