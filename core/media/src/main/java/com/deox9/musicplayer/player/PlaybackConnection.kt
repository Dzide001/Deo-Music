// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Binds a [MediaController] to [PlaybackService] and exposes player state as a Flow.
 *
 * This is the supported way to drive a MediaSessionService. The previous approach
 * sent ~20 custom `startService` intent actions and read state back out of DataStore,
 * which the service wrote once per second — so the UI lagged the player by up to a
 * second, the two could disagree, and `startService` threw whenever the process was
 * in the background.
 *
 * Transport and playlist commands are defined by the [Player] interface and are
 * forwarded to the session automatically, so no custom command plumbing is needed.
 */
@OptIn(markerClass = [UnstableApi::class])
@Singleton
class PlaybackConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var positionTicker: Job? = null

    init {
        // App-scoped: the controller outlives any single screen, so binding is
        // tied to the graph rather than to a composition that comes and goes.
        connect()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
            if (player.isPlaying) startPositionTicker() else stopPositionTicker()
        }
    }

    fun connect() {
        if (controllerFuture != null) return

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                val bound = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = bound
                bound.addListener(listener)
                publish()
                if (bound.isPlaying) startPositionTicker()
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun release() {
        stopPositionTicker()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        scope.cancel()
    }

    // ---- Commands -------------------------------------------------------------

    fun playNow(uri: String, title: String?, artist: String?) = withController {
        setMediaItem(buildMediaItem(uri, title, artist))
        prepare()
        play()
    }

    fun addToQueue(uri: String, title: String?, artist: String?) = withController {
        val item = buildMediaItem(uri, title, artist)
        if (mediaItemCount == 0) {
            setMediaItem(item)
            prepare()
        } else {
            addMediaItem(item)
        }
    }

    fun playNext(uri: String, title: String?, artist: String?) = withController {
        val item = buildMediaItem(uri, title, artist)
        if (mediaItemCount == 0) {
            setMediaItem(item)
            prepare()
            play()
            return@withController
        }
        val insertIndex = (currentMediaItemIndex + 1).coerceIn(0, mediaItemCount)
        addMediaItem(insertIndex, item)
    }

    fun togglePlayPause() = withController {
        if (isPlaying) pause() else play()
    }

    fun skipNext() = withController {
        if (hasNextMediaItem()) {
            seekToNextMediaItem()
            play()
        }
    }

    fun skipPrevious() = withController {
        if (hasPreviousMediaItem()) {
            seekToPreviousMediaItem()
            play()
        } else {
            seekTo(0)
        }
    }

    fun seekTo(positionMs: Long) = withController {
        seekTo(positionMs.coerceAtLeast(0))
    }

    fun playQueueIndex(index: Int) = withController {
        if (index in 0 until mediaItemCount) {
            seekToDefaultPosition(index)
            play()
        }
    }

    fun removeQueueIndex(index: Int) = withController {
        if (index in 0 until mediaItemCount) {
            removeMediaItem(index)
            if (mediaItemCount == 0) stop()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) = withController {
        if (fromIndex in 0 until mediaItemCount && toIndex in 0 until mediaItemCount) {
            moveMediaItem(fromIndex, toIndex)
        }
    }

    fun swapQueueItems(fromIndex: Int, toIndex: Int) = withController {
        if (fromIndex in 0 until mediaItemCount && toIndex in 0 until mediaItemCount) {
            QueueSwap.movesFor(fromIndex, toIndex).forEach { (from, to) -> moveMediaItem(from, to) }
        }
    }

    fun clearQueue() = withController {
        clearMediaItems()
        stop()
    }

    fun toggleShuffle() = withController {
        shuffleModeEnabled = !shuffleModeEnabled
    }

    fun cycleRepeat() = withController {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setVolume(volume: Float) = withController {
        this.volume = volume.coerceIn(0f, 1f)
    }

    // ---- Internals ------------------------------------------------------------

    private inline fun withController(block: MediaController.() -> Unit) {
        controller?.let { if (it.isConnected) it.block() }
    }

    private fun buildMediaItem(uri: String, title: String?, artist: String?): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaId(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title ?: "Unknown title")
                    .setArtist(artist ?: "Unknown artist")
                    .build(),
            )
            .build()

    /**
     * Polls position while playing.
     *
     * The player does not emit an event per frame, so the progress bar needs its own
     * tick. 250 ms keeps the slider smooth; the old design round-tripped through
     * DataStore once a second.
     */
    private fun startPositionTicker() {
        if (positionTicker?.isActive == true) return
        positionTicker = scope.launch {
            while (isActive) {
                publish()
                delay(POSITION_TICK_MS)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTicker?.cancel()
        positionTicker = null
    }

    private fun publish() {
        val player = controller
        if (player == null || !player.isConnected) {
            _state.value = PlaybackState()
            return
        }

        val current = player.currentMediaItem
        val rawDuration = player.duration
        _state.value = PlaybackState(
            uri = current?.mediaId.orEmpty(),
            title = current?.mediaMetadata?.title?.toString() ?: "",
            artist = current?.mediaMetadata?.artist?.toString() ?: "",
            album = current?.mediaMetadata?.albumTitle?.toString() ?: "",
            albumArtUri = current?.mediaMetadata?.artworkUri?.toString().orEmpty(),
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = if (rawDuration == C.TIME_UNSET || rawDuration < 0) 0L else rawDuration,
            isPlaying = player.isPlaying,
            queue = (0 until player.mediaItemCount).map { index ->
                val item = player.getMediaItemAt(index)
                QueueEntry(
                    uri = item.mediaId,
                    title = item.mediaMetadata.title?.toString() ?: "Unknown title",
                    artist = item.mediaMetadata.artist?.toString() ?: "Unknown artist",
                )
            },
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            playerVolume = player.volume,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    private companion object {
        const val POSITION_TICK_MS = 250L
    }
}
