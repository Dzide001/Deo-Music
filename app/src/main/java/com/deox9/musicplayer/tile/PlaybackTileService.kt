// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.tile

import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.deox9.musicplayer.R
import com.google.common.util.concurrent.MoreExecutors

/**
 * Play and pause from the quick settings panel.
 *
 * The panel is two swipes from anywhere, including the lock screen, which is the
 * point: it is faster than finding the notification when the phone is already in
 * your hand and you just want the music to stop.
 *
 * A controller is connected only while the tile is listening. Holding one open for
 * the life of the process would keep the session bound for a tile most people never
 * pull down.
 */
class PlaybackTileService : TileService() {

    private var controller: MediaController? = null

    override fun onStartListening() {
        super.onStartListening()
        connect { render(it) }
    }

    override fun onStopListening() {
        super.onStopListening()
        release()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val player = controller
        if (player == null) {
            // Nothing bound yet: connect, act, and let the render that follows show
            // the result rather than guessing at it.
            connect {
                it.togglePlayPause()
                render(it)
            }
            return
        }
        player.togglePlayPause()
        render(player)
    }

    private fun MediaController.togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    private fun connect(onReady: (MediaController) -> Unit) {
        controller?.let {
            onReady(it)
            return
        }

        val token = SessionToken(this, ComponentName(this, SERVICE_CLASS))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener(
            {
                runCatching { future.get() }.getOrNull()?.let { built ->
                    controller = built
                    onReady(built)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun release() {
        controller?.release()
        controller = null
    }

    /**
     * The tile reflects what is actually playing.
     *
     * Inactive with nothing loaded rather than showing a play button that would do
     * nothing — a control that does nothing when pressed is worse than one that is
     * visibly unavailable.
     */
    private fun render(player: Player) {
        val tile = qsTile ?: return
        val hasTrack = player.currentMediaItem != null
        tile.state = when {
            !hasTrack -> Tile.STATE_INACTIVE
            player.isPlaying -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = if (player.isPlaying) "Pause" else "Play"
        // Subtitles arrived in API 29; below that the label alone carries the tile,
        // which is why the label says Play or Pause rather than the app's name.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
        }
        tile.icon = Icon.createWithResource(
            this,
            if (player.isPlaying) R.drawable.ic_tile_pause else R.drawable.ic_tile_play,
        )
        tile.updateTile()
    }

    private companion object {
        const val SERVICE_CLASS = "com.deox9.musicplayer.player.PlaybackService"
    }
}
