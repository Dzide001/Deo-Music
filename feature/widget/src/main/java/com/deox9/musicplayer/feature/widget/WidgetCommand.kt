// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.widget

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.deox9.musicplayer.player.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.suspendCancellableCoroutine
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
 * Failure is silent by design. The widget has nowhere to show an error, and the state
 * it draws comes from the persisted session — so if the command did not land, the
 * next redraw simply shows that nothing changed.
 */
@OptIn(UnstableApi::class)
suspend fun sendWidgetCommand(context: Context, command: WidgetCommand) {
    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val controller = awaitController(context, token) ?: return

    try {
        when (command) {
            WidgetCommand.PlayPause -> if (controller.isPlaying) controller.pause() else controller.play()
            WidgetCommand.Next -> controller.seekToNextMediaItem()
            WidgetCommand.Previous -> controller.seekToPreviousMediaItem()
        }
    } finally {
        controller.release()
    }
}

private suspend fun awaitController(context: Context, token: SessionToken): MediaController? =
    suspendCancellableCoroutine { continuation ->
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull()
                if (continuation.isActive) continuation.resume(controller)
            },
            MoreExecutors.directExecutor(),
        )
        continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
    }
