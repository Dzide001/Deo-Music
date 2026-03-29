package com.deox9.musicplayer.player

import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.deox9.musicplayer.player.storage.QueueItem
import com.deox9.musicplayer.player.storage.PlaybackSessionRepository
import com.deox9.musicplayer.library.RecommendationSignalsRepository
import com.deox9.musicplayer.settings.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlin.math.pow

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var playbackSessionRepository: PlaybackSessionRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var recommendationSignalsRepository: RecommendationSignalsRepository
    private var positionPersistJob: Job? = null
    private var settingsJob: Job? = null
    private var crossfadeMonitorJob: Job? = null
    private var crossfadeEnabled: Boolean = false
    private var crossfadeInProgress: Boolean = false
    private var lastCrossfadedMediaId: String? = null
    private var replayGainEnabled: Boolean = false
    private var replayGainDb: Float = 0f
    private var replayGainMultiplier: Float = 1f
    private var transitionVolumeMultiplier: Float = 1f
    private var eqEnabled: Boolean = false
    private var eqBandLevels: List<Int> = List(10) { 0 }
    private var equalizer: Equalizer? = null
    private var equalizerSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    override fun onCreate() {
        super.onCreate()

        playbackSessionRepository = PlaybackSessionRepository(this)
        appSettingsRepository = AppSettingsRepository(this)
        recommendationSignalsRepository = RecommendationSignalsRepository(this)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.DEFAULT,
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (
                    events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                ) {
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        recordCurrentTrackPlay(player)
                        if (!crossfadeInProgress) {
                            lastCrossfadedMediaId = null
                        }
                    }

                    if (
                        events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) &&
                        crossfadeEnabled &&
                        !crossfadeInProgress
                    ) {
                        runCrossfadePreview(player)
                    }

                    persistCurrentSession(player)
                    if (player.isPlaying) {
                        startPositionPersistence(player)
                    } else {
                        stopPositionPersistence()
                    }

                    val exoPlayer = player as? ExoPlayer
                    if (exoPlayer != null && player.isPlaying && crossfadeEnabled) {
                        startCrossfadeMonitor(exoPlayer)
                    } else {
                        stopCrossfadeMonitor(exoPlayer)
                    }

                    if (exoPlayer != null) {
                        applyEq(exoPlayer)
                        applyEffectivePlayerVolume(exoPlayer)
                    }
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .build()

        settingsJob = mainScope.launch {
            appSettingsRepository.observe().collect { settings ->
                crossfadeEnabled = settings.crossfadeEnabled
                replayGainEnabled = settings.replayGainEnabled
                replayGainDb = settings.replayGainDb
                replayGainMultiplier = if (replayGainEnabled) {
                    dbToLinearGain(replayGainDb)
                } else {
                    1f
                }
                eqEnabled = settings.eqEnabled
                eqBandLevels = settings.eqBandLevels

                player.setPauseAtEndOfMediaItems(!settings.gaplessEnabled)
                if (!settings.crossfadeEnabled) {
                    stopCrossfadeMonitor(player)
                    transitionVolumeMultiplier = 1f
                }

                applyEq(player)
                applyEffectivePlayerVolume(player)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_URI -> {
                val uri = intent.getStringExtra(EXTRA_URI)
                if (!uri.isNullOrBlank()) {
                    playTrackNow(
                        uri = uri,
                        title = intent.getStringExtra(EXTRA_TITLE),
                        artist = intent.getStringExtra(EXTRA_ARTIST)
                    )
                }
            }

            ACTION_ADD_TO_QUEUE -> {
                val uri = intent.getStringExtra(EXTRA_URI)
                if (!uri.isNullOrBlank()) {
                    addTrackToQueue(
                        uri = uri,
                        title = intent.getStringExtra(EXTRA_TITLE),
                        artist = intent.getStringExtra(EXTRA_ARTIST)
                    )
                }
            }

            ACTION_PLAY_NEXT -> {
                val uri = intent.getStringExtra(EXTRA_URI)
                if (!uri.isNullOrBlank()) {
                    addTrackAsNext(
                        uri = uri,
                        title = intent.getStringExtra(EXTRA_TITLE),
                        artist = intent.getStringExtra(EXTRA_ARTIST)
                    )
                }
            }

            ACTION_TOGGLE_PLAY_PAUSE -> {
                val player = mediaSession?.player ?: return START_NOT_STICKY
                if (player.isPlaying) player.pause() else player.play()
                persistCurrentSession(player)
            }

            ACTION_SKIP_NEXT -> {
                val player = mediaSession?.player ?: return START_NOT_STICKY
                recordCurrentTrackSkip(player)
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.playWhenReady = true
                    persistCurrentSession(player)
                }
            }

            ACTION_SKIP_PREV -> {
                val player = mediaSession?.player ?: return START_NOT_STICKY
                recordCurrentTrackSkip(player)
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                    player.playWhenReady = true
                    persistCurrentSession(player)
                } else {
                    player.seekTo(0)
                }
            }

            ACTION_REMOVE_QUEUE_INDEX -> {
                val index = intent.getIntExtra(EXTRA_QUEUE_INDEX, -1)
                val player = mediaSession?.player ?: return START_NOT_STICKY
                if (index in 0 until player.mediaItemCount) {
                    player.removeMediaItem(index)
                    if (player.mediaItemCount == 0) {
                        player.stop()
                    }
                    persistCurrentSession(player)
                }
            }

            ACTION_CLEAR_QUEUE -> {
                val player = mediaSession?.player ?: return START_NOT_STICKY
                player.clearMediaItems()
                player.stop()
                persistCurrentSession(player)
            }

            ACTION_RESTORE_LAST -> restoreLastSession()

            ACTION_SEEK_TO -> {
                val seekToMs = intent.getLongExtra(EXTRA_SEEK_TO_MS, -1L)
                val player = mediaSession?.player ?: return START_NOT_STICKY
                if (seekToMs >= 0) {
                    player.seekTo(seekToMs)
                    persistCurrentSession(player)
                }
            }

            ACTION_SWAP_QUEUE_ITEMS -> {
                val fromIndex = intent.getIntExtra(EXTRA_FROM_INDEX, -1)
                val toIndex = intent.getIntExtra(EXTRA_TO_INDEX, -1)
                val player = mediaSession?.player ?: return START_NOT_STICKY
                if (fromIndex >= 0 && toIndex >= 0 && 
                    fromIndex < player.mediaItemCount && 
                    toIndex < player.mediaItemCount) {
                    swapMediaItems(player, fromIndex, toIndex)
                }
            }

            ACTION_MOVE_QUEUE_ITEM -> {
                val fromIndex = intent.getIntExtra(EXTRA_FROM_INDEX, -1)
                val toIndex = intent.getIntExtra(EXTRA_TO_INDEX, -1)
                val player = mediaSession?.player ?: return START_NOT_STICKY
                if (
                    fromIndex >= 0 &&
                    toIndex >= 0 &&
                    fromIndex < player.mediaItemCount &&
                    toIndex < player.mediaItemCount
                ) {
                    moveMediaItem(player, fromIndex, toIndex)
                }
            }

            ACTION_PLAY_QUEUE_INDEX -> {
                val index = intent.getIntExtra(EXTRA_QUEUE_INDEX, -1)
                val player = mediaSession?.player ?: return START_NOT_STICKY
                if (index in 0 until player.mediaItemCount) {
                    player.seekToDefaultPosition(index)
                    player.playWhenReady = true
                    persistCurrentSession(player)
                }
            }

            ACTION_TOGGLE_SHUFFLE -> {
                val player = mediaSession?.player ?: return START_NOT_STICKY
                player.shuffleModeEnabled = !player.shuffleModeEnabled
            }

            ACTION_CYCLE_REPEAT -> {
                val player = mediaSession?.player ?: return START_NOT_STICKY
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
            }

            ACTION_SET_PLAYER_VOLUME -> {
                val volume = intent.getFloatExtra(EXTRA_PLAYER_VOLUME, 1f)
                val player = mediaSession?.player ?: return START_NOT_STICKY
                player.volume = volume.coerceIn(0f, 1f)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun playTrackNow(
        uri: String,
        title: String?,
        artist: String?
    ) {
        val session = mediaSession ?: return
        val player = session.player

        val item = buildMediaItem(uri = uri, title = title, artist = artist)

        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
        persistCurrentSession(player)
    }

    private fun addTrackToQueue(
        uri: String,
        title: String?,
        artist: String?
    ) {
        val session = mediaSession ?: return
        val player = session.player

        val item = buildMediaItem(uri = uri, title = title, artist = artist)

        if (player.mediaItemCount == 0) {
            player.setMediaItem(item)
            player.prepare()
        } else {
            player.addMediaItem(item)
        }

        persistCurrentSession(player)
    }

    private fun addTrackAsNext(
        uri: String,
        title: String?,
        artist: String?
    ) {
        val session = mediaSession ?: return
        val player = session.player
        val item = buildMediaItem(uri = uri, title = title, artist = artist)

        if (player.mediaItemCount == 0) {
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
            persistCurrentSession(player)
            return
        }

        val currentIndex = player.currentMediaItemIndex
        val insertIndex = if (currentIndex in 0 until player.mediaItemCount) {
            (currentIndex + 1).coerceAtMost(player.mediaItemCount)
        } else {
            player.mediaItemCount
        }
        player.addMediaItem(insertIndex, item)
        persistCurrentSession(player)
    }

    private fun buildMediaItem(
        uri: String,
        title: String?,
        artist: String?
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title ?: "Unknown title")
            .setArtist(artist ?: "Unknown artist")
            .build()

        return MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaId(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun swapMediaItems(player: Player, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        
        // Get both items
        val fromItem = player.getMediaItemAt(fromIndex)
        val toItem = player.getMediaItemAt(toIndex)
        
        // Remove from source
        player.removeMediaItem(fromIndex)
        
        // Insert at destination
        val insertIndex = if (toIndex > fromIndex) toIndex - 1 else toIndex
        player.addMediaItem(insertIndex, toItem)
        player.addMediaItem(fromIndex, fromItem)
        
        persistCurrentSession(player)
    }

    private fun moveMediaItem(player: Player, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        player.moveMediaItem(fromIndex, toIndex)
        persistCurrentSession(player)
    }

    private fun restoreLastSession() {
        mainScope.launch {
            val player = mediaSession?.player ?: return@launch
            val last = withContext(Dispatchers.IO) {
                playbackSessionRepository.getLatest()
            } ?: return@launch

            val item = MediaItem.Builder()
                .setUri(Uri.parse(last.uri))
                .setMediaId(last.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(last.title)
                        .setArtist(last.artist)
                        .build()
                )
                .build()

            player.setMediaItem(item)
            player.prepare()
            player.seekTo(last.positionMs)
            player.playWhenReady = last.isPlaying
        }
    }

    private fun persistCurrentSession(player: Player) {
        val current = player.currentMediaItem ?: return
        val uri = current.localConfiguration?.uri?.toString().orEmpty()
        if (uri.isBlank()) return

        val title = current.mediaMetadata.title?.toString() ?: "Unknown title"
        val artist = current.mediaMetadata.artist?.toString() ?: "Unknown artist"
        val album = current.mediaMetadata.albumTitle?.toString() ?: ""
        val positionMs = player.currentPosition
        val rawDuration = player.duration
        val durationMs = if (rawDuration == C.TIME_UNSET || rawDuration < 0) 0L else rawDuration
        val isPlaying = player.isPlaying
        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val queue = (0 until player.mediaItemCount).mapNotNull { idx ->
            val item = player.getMediaItemAt(idx)
            val itemUri = item.localConfiguration?.uri?.toString().orEmpty()
            if (itemUri.isBlank()) return@mapNotNull null
            QueueItem(
                uri = itemUri,
                title = item.mediaMetadata.title?.toString() ?: "Unknown title",
                artist = item.mediaMetadata.artist?.toString() ?: "Unknown artist"
            )
        }
        val shuffleEnabled = player.shuffleModeEnabled
        val repeatMode = player.repeatMode
        val playerVolume = player.volume
        val albumArtUri = queryAlbumArtUri(uri)

        ioScope.launch {
            playbackSessionRepository.save(
                uri = uri,
                title = title,
                artist = artist,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                queue = queue,
                currentIndex = currentIndex,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                playerVolume = playerVolume,
                album = album,
                albumArtUri = albumArtUri
            )
        }
    }

    private fun queryAlbumArtUri(trackUri: String): String {
        return try {
            val cursor = contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    android.provider.MediaStore.Audio.Media.ALBUM_ID,
                    android.provider.MediaStore.Audio.Media.ALBUM
                ),
                "${android.provider.MediaStore.Audio.Media.DATA} = ?",
                arrayOf(trackUri),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val albumId = it.getLong(0)
                    android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        albumId
                    ).toString()
                } else {
                    ""
                }
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun startPositionPersistence(player: Player) {
        if (positionPersistJob?.isActive == true) return
        positionPersistJob = mainScope.launch {
            while (isActive) {
                persistCurrentSession(player)
                delay(1_000)
            }
        }
    }

    private fun stopPositionPersistence() {
        positionPersistJob?.cancel()
        positionPersistJob = null
    }

    private fun startCrossfadeMonitor(player: ExoPlayer) {
        if (crossfadeMonitorJob?.isActive == true) return
        crossfadeMonitorJob = mainScope.launch {
            while (isActive) {
                if (!crossfadeEnabled || !player.isPlaying || crossfadeInProgress || !player.hasNextMediaItem()) {
                    delay(CROSSFADE_CHECK_INTERVAL_MS)
                    continue
                }

                val duration = player.duration
                if (duration == C.TIME_UNSET || duration <= 0L) {
                    delay(CROSSFADE_CHECK_INTERVAL_MS)
                    continue
                }

                val remaining = duration - player.currentPosition
                val mediaId = player.currentMediaItem?.mediaId
                if (
                    remaining in 0..CROSSFADE_WINDOW_MS &&
                    !mediaId.isNullOrBlank() &&
                    mediaId != lastCrossfadedMediaId
                ) {
                    lastCrossfadedMediaId = mediaId
                    performCrossfadeToNext(player)
                }

                delay(CROSSFADE_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun stopCrossfadeMonitor(player: ExoPlayer?) {
        crossfadeMonitorJob?.cancel()
        crossfadeMonitorJob = null
        crossfadeInProgress = false
        if (player != null) {
            transitionVolumeMultiplier = 1f
            applyEffectivePlayerVolume(player)
        }
    }

    private suspend fun performCrossfadeToNext(player: ExoPlayer) {
        if (!player.hasNextMediaItem() || crossfadeInProgress) return
        crossfadeInProgress = true
        try {
            fadeVolume(player, from = 1f, to = MIN_CROSSFADE_VOLUME, durationMs = CROSSFADE_FADE_MS)
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.playWhenReady = true
            }
            fadeVolume(player, from = MIN_CROSSFADE_VOLUME, to = 1f, durationMs = CROSSFADE_FADE_MS)
        } finally {
            crossfadeInProgress = false
        }
    }

    private suspend fun fadeVolume(
        player: ExoPlayer,
        from: Float,
        to: Float,
        durationMs: Long
    ) {
        val steps = CROSSFADE_STEPS
        if (steps <= 0) {
            player.volume = to
            return
        }
        val stepDelay = (durationMs / steps).coerceAtLeast(10L)
        val delta = (to - from) / steps
        transitionVolumeMultiplier = from
        applyEffectivePlayerVolume(player)
        repeat(steps) { step ->
            transitionVolumeMultiplier = (from + (delta * (step + 1))).coerceIn(0f, 1f)
            applyEffectivePlayerVolume(player)
            delay(stepDelay)
        }
    }

    private fun dbToLinearGain(db: Float): Float {
        return 10.0.pow((db / 20f).toDouble()).toFloat().coerceIn(0.1f, 1f)
    }

    private fun applyEffectivePlayerVolume(player: ExoPlayer) {
        val effective = (transitionVolumeMultiplier * replayGainMultiplier).coerceIn(0f, 1f)
        player.volume = effective
    }

    private fun applyEq(player: ExoPlayer) {
        if (!eqEnabled) {
            releaseEq()
            return
        }

        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) {
            return
        }

        if (equalizer == null || equalizerSessionId != sessionId) {
            releaseEq()
            equalizer = Equalizer(0, sessionId)
            equalizerSessionId = sessionId
        }

        val eq = equalizer ?: return
        eq.enabled = true
        val bandCount = eq.numberOfBands.toInt().coerceAtLeast(1)
        val bandRange = eq.bandLevelRange
        val minLevel = bandRange[0].toInt()
        val maxLevel = bandRange[1].toInt()
        val virtualLevels = MutableList(10) { idx ->
            eqBandLevels.getOrElse(idx) { 0 }
        }

        for (band in 0 until bandCount) {
            val virtualIndex = if (bandCount == 1) {
                0
            } else {
                ((band.toFloat() / (bandCount - 1).toFloat()) * (virtualLevels.size - 1)).toInt()
            }
            val desired = virtualLevels[virtualIndex].coerceIn(minLevel, maxLevel)
            eq.setBandLevel(band.toShort(), desired.toShort())
        }
    }

    private fun releaseEq() {
        equalizer?.release()
        equalizer = null
        equalizerSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun runCrossfadePreview(player: Player) {
        val exoPlayer = player as? ExoPlayer ?: return
        if (!player.playWhenReady) return

        mainScope.launch {
            fadeVolume(
                player = exoPlayer,
                from = 0f,
                to = 1f,
                durationMs = CROSSFADE_FADE_MS
            )
        }
    }

    private fun recordCurrentTrackPlay(player: Player) {
        val current = player.currentMediaItem ?: return
        val uri = current.localConfiguration?.uri?.toString().orEmpty()
        if (uri.isBlank()) return
        val artist = current.mediaMetadata.artist?.toString()
        ioScope.launch {
            recommendationSignalsRepository.recordPlay(uri = uri, artist = artist)
        }
    }

    private fun recordCurrentTrackSkip(player: Player) {
        val current = player.currentMediaItem ?: return
        val uri = current.localConfiguration?.uri?.toString().orEmpty()
        if (uri.isBlank()) return
        ioScope.launch {
            recommendationSignalsRepository.recordSkip(uri)
        }
    }

    override fun onDestroy() {
        mediaSession?.player?.let { persistCurrentSession(it) }
        stopPositionPersistence()
        stopCrossfadeMonitor(mediaSession?.player as? ExoPlayer)
        releaseEq()
        settingsJob?.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        mainScope.cancel()
        ioScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val SESSION_ID = "music-player-session"
        const val ACTION_PLAY_URI = "com.deox9.musicplayer.action.PLAY_URI"
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.deox9.musicplayer.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_RESTORE_LAST = "com.deox9.musicplayer.action.RESTORE_LAST"
        const val ACTION_SEEK_TO = "com.deox9.musicplayer.action.SEEK_TO"
        const val ACTION_ADD_TO_QUEUE = "com.deox9.musicplayer.action.ADD_TO_QUEUE"
        const val ACTION_PLAY_NEXT = "com.deox9.musicplayer.action.PLAY_NEXT"
        const val ACTION_SKIP_NEXT = "com.deox9.musicplayer.action.SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.deox9.musicplayer.action.SKIP_PREV"
        const val ACTION_REMOVE_QUEUE_INDEX = "com.deox9.musicplayer.action.REMOVE_QUEUE_INDEX"
        const val ACTION_CLEAR_QUEUE = "com.deox9.musicplayer.action.CLEAR_QUEUE"
        const val ACTION_SWAP_QUEUE_ITEMS = "com.deox9.musicplayer.action.SWAP_QUEUE_ITEMS"
        const val ACTION_MOVE_QUEUE_ITEM = "com.deox9.musicplayer.action.MOVE_QUEUE_ITEM"
        const val ACTION_PLAY_QUEUE_INDEX = "com.deox9.musicplayer.action.PLAY_QUEUE_INDEX"
        const val EXTRA_SEEK_TO_MS = "extra_seek_to_ms"
        const val EXTRA_QUEUE_INDEX = "extra_queue_index"
        const val EXTRA_FROM_INDEX = "extra_from_index"
        const val EXTRA_TO_INDEX = "extra_to_index"
        const val ACTION_TOGGLE_SHUFFLE = "com.deox9.musicplayer.action.TOGGLE_SHUFFLE"
        const val ACTION_CYCLE_REPEAT = "com.deox9.musicplayer.action.CYCLE_REPEAT"
        const val ACTION_SET_PLAYER_VOLUME = "com.deox9.musicplayer.action.SET_PLAYER_VOLUME"
        const val EXTRA_PLAYER_VOLUME = "extra_player_volume"

        private const val CROSSFADE_CHECK_INTERVAL_MS = 250L
        private const val CROSSFADE_WINDOW_MS = 1200L
        private const val CROSSFADE_FADE_MS = 350L
        private const val CROSSFADE_STEPS = 7
        private const val MIN_CROSSFADE_VOLUME = 0.15f
    }
}
