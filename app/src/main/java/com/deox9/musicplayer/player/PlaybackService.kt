package com.deox9.musicplayer.player

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.DEFAULT,
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_URI -> {
                val uri = intent.getStringExtra(EXTRA_URI)
                if (!uri.isNullOrBlank()) {
                    playSingleTrack(
                        uri = uri,
                        title = intent.getStringExtra(EXTRA_TITLE),
                        artist = intent.getStringExtra(EXTRA_ARTIST)
                    )
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun playSingleTrack(
        uri: String,
        title: String?,
        artist: String?
    ) {
        val session = mediaSession ?: return
        val player = session.player

        val metadata = MediaMetadata.Builder()
            .setTitle(title ?: "Unknown title")
            .setArtist(artist ?: "Unknown artist")
            .build()

        val item = MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaId(uri)
            .setMediaMetadata(metadata)
            .build()

        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val SESSION_ID = "music-player-session"
        const val ACTION_PLAY_URI = "com.deox9.musicplayer.action.PLAY_URI"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
    }
}
