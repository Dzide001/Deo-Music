// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.deox9.musicplayer.audio.AudioChainConfig
import com.deox9.musicplayer.audio.EqBand
import com.deox9.musicplayer.audio.ReplayGain
import com.deox9.musicplayer.database.dao.LibraryDao
import com.deox9.musicplayer.library.AlbumArt
import com.deox9.musicplayer.library.RecommendationSignalsRepository
import com.deox9.musicplayer.player.storage.PlaybackSessionRepository
import com.deox9.musicplayer.player.storage.QueueItem
import com.deox9.musicplayer.settings.AppSettingsRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Opts in to the Media3 APIs used here that are still marked unstable: the audio
 * session id feeding the platform Equalizer, and C.AUDIO_SESSION_ID_UNSET.
 *
 * Uses androidx.annotation.OptIn rather than Kotlin's — Android Lint's
 * UnsafeOptInUsageError check only recognises the AndroidX form. Unlike
 * annotating the class @UnstableApi, this does not mark PlaybackService unstable
 * for its callers. Worth re-checking on each Media3 version bump.
 */
@OptIn(markerClass = [UnstableApi::class])
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    /**
     * Injected rather than built from a Context like the other repositories here.
     * The DAO has to be the same instance the rest of the app uses — a second Room
     * instance over the same file is a different write journal.
     */
    @Inject
    lateinit var libraryDao: LibraryDao
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
    private var transitionVolumeMultiplier: Float = 1f
    private var eqEnabled: Boolean = false
    private var eqBands: List<EqBand> = emptyList()
    private val audioProcessor = DeoAudioProcessor()
    private val chainProbe = ChainProbeAudioProcessor()

    /** The probe reports sink rebuilds; the DSP does the work. See [PROBE_ONLY]. */

    /**
     * The gain the current track asked for, from its tag or a measurement.
     *
     * Null until one is looked up. ReplayGain is a per-track figure; applying the
     * user's single pre-amp to everything, which is what the settings value alone
     * amounts to, normalises nothing.
     */
    private var currentTrackGainDb: Double? = null
    private var currentTrackPeak: Double? = null
    private var gainLookupJob: Job? = null

    /** The track being played before the current one, used to attribute skips. */
    private var previousMediaId: String? = null

    override fun onCreate() {
        super.onCreate()

        playbackSessionRepository = PlaybackSessionRepository(this)
        appSettingsRepository = AppSettingsRepository(this)
        recommendationSignalsRepository = RecommendationSignalsRepository(this)

        // The DSP runs inside the player's own audio path rather than as a platform
        // AudioEffect on the session. That is what makes the equaliser behave the
        // same on every device, and what lets ReplayGain apply a boost at all —
        // player.volume is a multiplier that cannot exceed 1.
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                // Float output, so a boost has somewhere to go before the limiter
                // sees it. In 16-bit the intermediate would clip first.
                .setEnableFloatOutput(true)
                .setAudioProcessorChain(
                    if (PROBE_ONLY) {
                        DefaultAudioSink.DefaultAudioProcessorChain(chainProbe)
                    } else {
                        DefaultAudioSink.DefaultAudioProcessorChain(chainProbe, audioProcessor)
                    },
                )
                .build()
        }

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.DEFAULT,
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            /**
             * Records a skip when the user jumps tracks.
             *
             * Skip signals used to be recorded in the SKIP_NEXT/SKIP_PREV intent
             * handlers. Those are gone — controllers issue Player commands directly —
             * so the signal now comes from the transition reason. REASON_SEEK covers
             * both next and previous; REASON_AUTO means the track simply finished,
             * which is not a skip.
             */
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    recordSkip(previousMediaId)
                }
                loadTrackGain(mediaItem)
                previousMediaId = mediaItem?.mediaId
            }

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
                        applyEffectivePlayerVolume(exoPlayer)
                    }
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setCallback(MediaItemResolver())
            .build()

        restoreLastSession()

        settingsJob = mainScope.launch {
            appSettingsRepository.observe().collect { settings ->
                crossfadeEnabled = settings.crossfadeEnabled
                replayGainEnabled = settings.replayGainEnabled
                replayGainDb = settings.replayGainDb
                eqEnabled = settings.eqEnabled
                eqBands = settings.eqBands
                applyAudioChain()

                // ExoPlayer advances between items gaplessly by default. The previous
                // wiring called setPauseAtEndOfMediaItems(!gaplessEnabled), which does
                // not disable gapless — it halts playback at the end of every track.
                // Real gapless (LAME/Xing and iTunSMPB encoder delay/padding trimming)
                // is Phase 5 work; until then this setting must not touch the player.
                if (!settings.crossfadeEnabled) {
                    stopCrossfadeMonitor(player)
                    transitionVolumeMultiplier = 1f
                }

                applyEffectivePlayerVolume(player)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * Completes the sparse [MediaItem]s a controller sends.
     *
     * A controller can only pass metadata it already knows, and the artwork URI needs
     * a MediaStore lookup. This is the documented hook for resolving incomplete items,
     * and it keeps the query on the service side rather than doing content-resolver
     * work on the UI thread.
     */
    private inner class MediaItemResolver : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapTo(mutableListOf()) { item ->
                val artUri = queryAlbumArtUri(item.mediaId)
                if (artUri.isBlank()) {
                    item
                } else {
                    item.buildUpon()
                        .setMediaMetadata(
                            item.mediaMetadata.buildUpon()
                                .setArtworkUri(Uri.parse(artUri))
                                .build(),
                        )
                        .build()
                }
            }
            return Futures.immediateFuture(resolved)
        }
    }

    /**
     * Rebuilds the previous session on service start.
     *
     * Restores the whole queue, not just the current track. The queue was already
     * being persisted, but only the one item was ever restored, so the rest of the
     * user's queue was silently dropped on every restart.
     */
    private fun restoreLastSession() {
        mainScope.launch {
            val player = mediaSession?.player ?: return@launch
            val last = withContext(Dispatchers.IO) {
                playbackSessionRepository.getLatest()
            } ?: return@launch

            val items = last.queue
                .filter { it.uri.isNotBlank() }
                .map { entry -> restoredMediaItem(entry.uri, entry.title, entry.artist) }
                .ifEmpty { listOf(restoredMediaItem(last.uri, last.title, last.artist)) }

            val startIndex = last.currentIndex.coerceIn(0, items.lastIndex)

            player.setMediaItems(items, startIndex, last.positionMs)
            player.shuffleModeEnabled = last.shuffleEnabled
            player.repeatMode = last.repeatMode
            player.prepare()
            player.playWhenReady = last.isPlaying
        }
    }

    private fun restoredMediaItem(uri: String, title: String, artist: String): MediaItem {
        val artUri = queryAlbumArtUri(uri)
        return MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaId(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .apply { if (artUri.isNotBlank()) setArtworkUri(Uri.parse(artUri)) }
                    .build()
            )
            .build()
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

    /**
     * Resolves the album-art URI for a track.
     *
     * The track URI is a `content://media/external/audio/media/<id>` URI, so it is
     * queried directly for its ALBUM_ID rather than matched against the DATA column
     * (DATA holds a filesystem path and never equals a content URI).
     */
    private fun queryAlbumArtUri(trackUri: String): String {
        val uri = runCatching { Uri.parse(trackUri) }.getOrNull() ?: return ""
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return ""

        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    AlbumArt.forAlbumId(cursor.getLong(0)).toString()
                } else {
                    ""
                }
            }.orEmpty()
        }.getOrDefault("")
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

    /**
     * Pushes the current settings into the processor.
     *
     * Gain and equalisation both live there now. player.volume is left to the
     * crossfade alone, which is the one thing it is the right tool for: a transition
     * between tracks, not a property of either of them.
     */
    /**
     * Looks up the incoming track's stored gain and peak.
     *
     * Off the main thread and cancelling any lookup still in flight: skipping quickly
     * through a queue would otherwise let an earlier track's figures land after a
     * later one's and stick.
     */
    private fun loadTrackGain(mediaItem: MediaItem?) {
        gainLookupJob?.cancel()
        val uri = mediaItem?.localConfiguration?.uri?.toString()
        if (uri.isNullOrBlank()) {
            currentTrackGainDb = null
            currentTrackPeak = null
            applyAudioChain()
            return
        }

        gainLookupJob = ioScope.launch {
            val stored = runCatching { libraryDao.replayGainFor(uri) }.getOrNull()
            currentTrackGainDb = stored?.replayGainTrackDb?.toDouble()
            currentTrackPeak = stored?.replayGainTrackPeak?.toDouble()
            applyAudioChain()
        }
    }

    private fun applyAudioChain() {
        audioProcessor.setConfig(
            AudioChainConfig(
                gainDb = if (replayGainEnabled) effectiveGainDb() else 0.0,
                bands = if (eqEnabled) eqBands else emptyList(),
                // Always on. It costs a few milliseconds of latency and is the only
                // thing standing between a boost and a clipped output.
                limiterEnabled = true,
            ),
        )
    }

    /**
     * The track's own gain plus the user's pre-amp, capped so a boost cannot clip.
     *
     * The settings value is the pre-amp, not the gain — it was previously being used
     * as the whole figure, which applied the same adjustment to every track and so
     * normalised nothing.
     */
    private fun effectiveGainDb(): Double = ReplayGain.playbackGainDb(
        tagGainDb = currentTrackGainDb,
        preAmpDb = replayGainDb.toDouble(),
        // An untagged, unmeasured track is left alone rather than guessed at.
        fallbackGainDb = 0.0,
        peak = currentTrackPeak,
        preventClipping = true,
    )

    private fun applyEffectivePlayerVolume(player: ExoPlayer) {
        player.volume = transitionVolumeMultiplier.coerceIn(0f, 1f)
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

    /** Records that [mediaId] — the track being left — was skipped rather than finished. */
    private fun recordSkip(mediaId: String?) {
        if (mediaId.isNullOrBlank()) return
        ioScope.launch {
            recommendationSignalsRepository.recordSkip(mediaId)
        }
    }

    override fun onDestroy() {
        mediaSession?.player?.let { persistCurrentSession(it) }
        stopPositionPersistence()
        stopCrossfadeMonitor(mediaSession?.player as? ExoPlayer)
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
        /**
         * Control switch for the gapless measurement.
         *
         * True routes audio through no processor at all, leaving only the probe to
         * report when the sink rebuilds its path — the comparison that says whether
         * the DSP causes a reconfiguration at a track boundary or merely observes
         * one. False is the shipping path and must stay false.
         */
        const val PROBE_ONLY = false

        const val SESSION_ID = "music-player-session"

        private const val CROSSFADE_CHECK_INTERVAL_MS = 250L
        private const val CROSSFADE_WINDOW_MS = 1200L
        private const val CROSSFADE_FADE_MS = 350L
        private const val CROSSFADE_STEPS = 7
        private const val MIN_CROSSFADE_VOLUME = 0.15f
    }
}
