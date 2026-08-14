// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.deox9.musicplayer.audio.CrossfadeSettings
import com.deox9.musicplayer.audio.EqBand
import com.deox9.musicplayer.audio.FadeDirection
import com.deox9.musicplayer.audio.OutputProfiles
import com.deox9.musicplayer.audio.OutputRoute
import com.deox9.musicplayer.audio.ReplayGain
import com.deox9.musicplayer.audio.StreamFormat
import com.deox9.musicplayer.audio.codecLabelFor
import com.deox9.musicplayer.audio.resolveChainConfig
import com.deox9.musicplayer.database.dao.LibraryDao
import com.deox9.musicplayer.library.AlbumArt
import com.deox9.musicplayer.library.RecommendationSignalsRepository
import com.deox9.musicplayer.player.storage.PlaybackSessionEntity
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

    /** Shared with the UI, which shows what the audio path is actually doing. */
    @Inject
    lateinit var signalChainReporter: SignalChainReporter

    @Inject
    lateinit var outputRouteMonitor: OutputRouteMonitor

    @Inject
    lateinit var sleepTimer: SleepTimer
    private var mediaSession: MediaSession? = null
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var playbackSessionRepository: PlaybackSessionRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var recommendationSignalsRepository: RecommendationSignalsRepository
    private var positionPersistJob: Job? = null
    private var settingsJob: Job? = null
    private var routeJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var pendingSleepJob: Job? = null
    private var skipFadeJob: Job? = null
    private var endOfTrackFadeJob: Job? = null
    private var crossfade: CrossfadeSettings = CrossfadeSettings()
    private var replayGainEnabled: Boolean = false
    private var replayGainDb: Float = 0f
    private var eqEnabled: Boolean = false
    private var eqBands: List<EqBand> = emptyList()
    private var resumeAfterInterruption: Boolean = true
    private var outputProfiles: OutputProfiles = OutputProfiles()
    private var outputRoute: OutputRoute = OutputRoute.Speaker
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

    /**
     * Items that have failed in a row, reset the moment anything plays.
     *
     * Bounds the auto-advance so a queue that fails end to end stops rather than
     * sprinting through every item. See [errorRecoveryFor].
     */
    private var consecutiveFailures: Int = 0

    /** Counts up per failure so the UI can tell a repeat from a redraw. */
    private var errorSequence: Long = 0L

    /** The track the last reported failure was about, if it has not been cleared. */
    private var lastReportedErrorMediaId: String? = null

    /**
     * The item the player was moved off automatically after it failed.
     *
     * The resulting transition arrives with REASON_SEEK, indistinguishable from the
     * user pressing Next, and recording it as a skip would teach the recommendations
     * that they disliked a track they never heard.
     */
    private var autoAdvancedFromMediaId: String? = null

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
            // Keeps the CPU alive while playing with the screen off. The permission
            // was already declared and never used, so playback was relying on the
            // foreground service alone — which holds up under normal conditions and
            // not under Doze, where it stutters or stops on exactly the long
            // screen-off listening this is for. WAKE_MODE_LOCAL rather than NETWORK:
            // local files need no wifi lock, and taking one would cost battery for
            // nothing.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addAnalyticsListener(signalChainListener())

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
                val leaving = previousMediaId
                val steppedOverFailure = leaving != null && leaving == autoAdvancedFromMediaId
                if (steppedOverFailure) autoAdvancedFromMediaId = null

                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && !steppedOverFailure) {
                    recordSkip(leaving)
                }
                // The other half of the end-of-track fade. The outgoing track faded
                // itself out; this brings the incoming one up from silence. A seek
                // transition is not handled here — that is the skip's own fade, which
                // brings the level back itself.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && crossfade.onAutoAdvance) {
                    audioProcessor.startFade(
                        crossfade.curve,
                        FadeDirection.In,
                        crossfade.effectiveDurationMs,
                    )
                }
                loadTrackGain(mediaItem)
                previousMediaId = mediaItem?.mediaId
            }

            /**
             * Reports the failure and steps over the track that caused it.
             *
             * Handled on the service rather than only in the UI because the queue has to
             * keep moving whether or not anything is bound to the session — the same
             * unplayable file skips past from the notification and the widget too.
             */
            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(error)
            }

            /**
             * Honours the choice about resuming after a call or another app.
             *
             * Media3 always resumes when it gets audio focus back, and attaches
             * AUDIO_FOCUS_LOSS as the reason to both halves — the pause and the
             * resume. So a resume carrying that reason is the player restarting
             * itself rather than the listener asking, which is the only case this
             * should override.
             */
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (
                    playWhenReady &&
                    !resumeAfterInterruption &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
                ) {
                    player.pause()
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    retractErrorOncePlaybackRecovers(player)
                }

                if (
                    events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                ) {
                    if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        recordCurrentTrackPlay(player)
                    }

                    persistCurrentSession(player)
                    if (player.isPlaying) {
                        startPositionPersistence(player)
                    } else {
                        stopPositionPersistence()
                    }

                    updateFadeState(player)
                }
            }
        })

        mediaSession = MediaSession.Builder(this, FadingPlayer(player))
            .setId(SESSION_ID)
            .setCallback(MediaItemResolver())
            .build()

        restoreLastSession()

        sleepTimerJob = mainScope.launch {
            sleepTimer.state.collect { state -> armSleepTimer(player, state) }
        }

        outputRouteMonitor.start()
        routeJob = mainScope.launch {
            outputRouteMonitor.route.collect { route ->
                outputRoute = route
                // Re-resolved rather than merely recorded: plugging headphones in is
                // the moment their profile has to take effect, not the next time
                // some other setting happens to change.
                applyAudioChain()
            }
        }

        settingsJob = mainScope.launch {
            appSettingsRepository.observe().collect { settings ->
                crossfade = settings.crossfade
                replayGainEnabled = settings.replayGainEnabled
                replayGainDb = settings.replayGainDb
                eqEnabled = settings.eqEnabled
                eqBands = settings.eqBands
                outputProfiles = settings.outputProfiles
                resumeAfterInterruption = settings.resumeAfterInterruption
                // Set on the player rather than folded into the DSP: Sonic sits in
                // the sink's own chain and time-stretches, so pitch stays put when
                // speed changes instead of the two moving together as they would if
                // this were done by resampling.
                player.playbackParameters =
                    PlaybackParameters(settings.playbackSpeed, settings.playbackPitch)
                applyAudioChain()

                // There is no gapless setting to honour any more. Playback here is
                // gapless unconditionally: the extractors trim encoder delay and
                // padding, and the sink keeps its AudioTrack across a join between
                // items of the same format — measured in GaplessJoinTest, which is
                // what makes the claim checkable rather than a comment.
                // Turning fading off has to release any fade currently holding the
                // signal down, or the setting would leave playback silent until the
                // next track change.
                if (!settings.crossfade.onSkip && !settings.crossfade.onAutoAdvance) {
                    cancelFades()
                }
                scheduleEndOfTrackFade(player)
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

    /**
     * Fills in the parts of the chain only the renderer knows.
     *
     * These are the same callbacks the gapless measurement used, which is why they are
     * trusted here: onAudioTrackInitialized is the sink's real output configuration
     * rather than what was asked for, and asking is not the same as getting — a device
     * may refuse float output or resample.
     */
    private fun signalChainListener() = object : AnalyticsListener {
        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            signalChainReporter.setSource(
                StreamFormat(
                    codec = codecLabelFor(format.sampleMimeType),
                    sampleRateHz = format.sampleRate.takeIf { it != Format.NO_VALUE },
                    channelCount = format.channelCount.takeIf { it != Format.NO_VALUE },
                    bitrateKbps = format.bitrate.takeIf { it != Format.NO_VALUE }?.div(BITS_PER_KILOBIT),
                ),
            )
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            signalChainReporter.setDecoder(decoderName)
        }

        override fun onAudioTrackInitialized(
            eventTime: AnalyticsListener.EventTime,
            audioTrackConfig: AudioSink.AudioTrackConfig,
        ) {
            signalChainReporter.setOutput(
                StreamFormat(
                    sampleRateHz = audioTrackConfig.sampleRate,
                    bitDepth = bitDepthOf(audioTrackConfig.encoding),
                ),
            )
        }
    }

    /**
     * The output encoding as a bit depth, where it has one.
     *
     * Null for anything compressed or passed through: those have no depth to report,
     * and inventing one would be the same mistake as claiming an MP3 is 16-bit.
     */
    private fun bitDepthOf(encoding: Int): Int? = when (encoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
        C.ENCODING_PCM_FLOAT -> 32
        else -> null
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
                PlaybackSessionEntity(
                    uri = uri,
                    title = title,
                    artist = artist,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    queue = queue,
                    currentIndex = currentIndex,
                    updatedAtMs = System.currentTimeMillis(),
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    playerVolume = playerVolume,
                    album = album,
                    albumArtUri = albumArtUri,
                ),
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

    /**
     * Waits out the sleep timer, then fades down and pauses.
     *
     * Pause rather than stop, so the queue and position survive — someone who set a
     * timer and stayed awake should be able to press play and carry on, not find
     * their place gone.
     *
     * The fade is the reason this does not simply schedule a pause: cutting the
     * music dead is what wakes people up, which is the opposite of what a sleep
     * timer is for.
     */
    private fun armSleepTimer(player: Player, state: SleepTimerState) {
        pendingSleepJob?.cancel()
        if (!state.isActive) {
            // Cancelling the timer must also release a fade it had already begun, or
            // the music would stay quiet with nothing left to bring it back.
            audioProcessor.clearFade()
            return
        }

        pendingSleepJob = mainScope.launch {
            val untilExpiry = state.remainingMs(SystemClock.elapsedRealtime()) ?: return@launch
            // The fade has to finish as the timer expires, so it starts before it.
            delay((untilExpiry - SleepTimer.FADE_OUT_MS).coerceAtLeast(0L))

            if (state.finishTrack) {
                // Nothing to fade yet: the stop waits for the track to end, and
                // Media3 reports that as a transition or an ended state.
                awaitTrackEnd(player)
            } else {
                audioProcessor.startFade(crossfade.curve, FadeDirection.Out, SleepTimer.FADE_OUT_MS)
                delay(SleepTimer.FADE_OUT_MS.toLong())
            }

            player.pause()
            // Released after the pause, so the next play does not start silent.
            audioProcessor.clearFade()
            sleepTimer.cancel()
        }
    }

    /** Suspends until the current track finishes or playback stops. */
    private suspend fun awaitTrackEnd(player: Player) {
        while (player.isPlaying) {
            val remaining = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                ?.minus(player.currentPosition)
                ?: break
            if (remaining <= SLEEP_POLL_MS) {
                delay(remaining.coerceAtLeast(0L))
                return
            }
            delay(SLEEP_POLL_MS)
        }
    }

    /**
     * Puts a fade in front of the track changes the listener asks for.
     *
     * This has to be a wrapper rather than a listener, because by the time a
     * listener hears about a skip it has already happened — and the whole point is
     * to do something before it does. Controllers call these four; each is
     * intercepted so a skip from the notification, the watch or the headset button
     * fades exactly like one from the app.
     *
     * Only skips are wrapped. Seeking within a track, play, pause and everything
     * else pass straight through.
     */
    private inner class FadingPlayer(player: Player) : ForwardingPlayer(player) {

        override fun seekToNext() = fadeOrDo { super.seekToNext() }

        override fun seekToNextMediaItem() = fadeOrDo { super.seekToNextMediaItem() }

        override fun seekToPrevious() = fadeOrDo { super.seekToPrevious() }

        override fun seekToPreviousMediaItem() = fadeOrDo { super.seekToPreviousMediaItem() }

        /**
         * Fades across the change, or performs it immediately.
         *
         * Immediately when there is nothing to fade: fading silence just delays the
         * skip by the fade duration for no audible benefit, and a listener skipping
         * while paused would sit watching nothing happen.
         */
        private fun fadeOrDo(change: () -> Unit) {
            if (!crossfade.onSkip || !isPlaying) {
                change()
                return
            }
            fadeAcross(change)
        }
    }

    /**
     * Keeps the fade state honest as playback changes.
     *
     * Pausing part-way through a fade-out is the case that matters: the fade holds
     * the signal at silence, so without releasing it here, pressing play would
     * resume into nothing. A skip's own fade is left alone, because it releases
     * itself on the other side of the track change.
     */
    private fun updateFadeState(player: Player) {
        if (!player.isPlaying) {
            endOfTrackFadeJob?.cancel()
            endOfTrackFadeJob = null
            if (skipFadeJob?.isActive != true) audioProcessor.clearFade()
            return
        }
        scheduleEndOfTrackFade(player)
    }

    /**
     * Schedules the fade that lands on the end of the current track.
     *
     * Scheduled rather than polled. The old version woke four times a second to ask
     * whether the track was nearly over; this sleeps until the moment the fade should
     * start and is re-armed whenever the position could have changed underneath it.
     *
     * It also does not touch the queue. The previous implementation called
     * seekToNextMediaItem the moment a track came within 1.2 s of its end, which cut
     * the last second off every track and tore down the AudioTrack that makes the
     * join gapless. The track is left to finish on its own; only the level is
     * touched.
     */
    private fun scheduleEndOfTrackFade(player: Player) {
        endOfTrackFadeJob?.cancel()
        if (!crossfade.onAutoAdvance || !player.isPlaying || !player.hasNextMediaItem()) return

        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return

        val fadeMs = crossfade.effectiveDurationMs.toLong()
        val untilFadeStart = duration - player.currentPosition - fadeMs
        endOfTrackFadeJob = mainScope.launch {
            if (untilFadeStart > 0) delay(untilFadeStart)
            audioProcessor.startFade(crossfade.curve, FadeDirection.Out, fadeMs.toInt())
        }
    }

    /**
     * Fades out, changes track, fades back in.
     *
     * The skip genuinely waits for the fade-out — with one decoder there is no way
     * to have both tracks audible at once, so the outgoing one has to finish before
     * the incoming one starts. That is why the duration is short by default: it is a
     * delay between pressing the button and hearing the result.
     *
     * [change] is the seek to perform, so next and previous share this.
     */
    private fun fadeAcross(change: () -> Unit) {
        skipFadeJob?.cancel()
        val fadeMs = crossfade.effectiveDurationMs
        skipFadeJob = mainScope.launch {
            audioProcessor.startFade(crossfade.curve, FadeDirection.Out, fadeMs)
            delay(fadeMs.toLong())
            change()
            audioProcessor.startFade(crossfade.curve, FadeDirection.In, fadeMs)
        }
    }

    /**
     * Drops any fade and returns the signal to full level.
     *
     * The safety net for the whole feature. A completed fade-out holds silence until
     * something releases it, so every path that could leave one stranded — pausing
     * mid-fade, turning the setting off, an error — has to come through here or
     * playback simply stays silent.
     */
    private fun cancelFades() {
        skipFadeJob?.cancel()
        skipFadeJob = null
        endOfTrackFadeJob?.cancel()
        endOfTrackFadeJob = null
        audioProcessor.clearFade()
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
        val config = resolveChainConfig(
            gainDb = if (replayGainEnabled) effectiveGainDb() else 0.0,
            defaultBands = if (eqEnabled) eqBands else emptyList(),
            // Always on by default. It costs a few milliseconds of latency and is
            // the only thing standing between a boost and a clipped output; a
            // profile may still turn it off for an output that does not need it.
            defaultLimiterEnabled = true,
            profiles = outputProfiles,
            route = outputRoute,
        )
        audioProcessor.setConfig(config)
        signalChainReporter.setStages(config, audioProcessor.limiterReductionDb)
        signalChainReporter.setRoute(outputRoute, outputProfiles.forRoute(outputRoute) != null)
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

    private fun recordCurrentTrackPlay(player: Player) {
        val current = player.currentMediaItem ?: return
        val uri = current.localConfiguration?.uri?.toString().orEmpty()
        if (uri.isBlank()) return
        val artist = current.mediaMetadata.artist?.toString()
        ioScope.launch {
            recommendationSignalsRepository.recordPlay(uri = uri, artist = artist)
        }
    }

    /**
     * Publishes a failure to the UI and advances past the track that caused it.
     *
     * Both halves matter. Without the first, a track that will not decode reports
     * `state=ERROR(7)` to the media session and nothing at all to the person holding
     * the phone. Without the second, the queue stops dead on that one file.
     */
    private fun handlePlayerError(error: PlaybackException) {
        val player = mediaSession?.player ?: return
        val failedItem = player.currentMediaItem
        val failedMediaId = failedItem?.mediaId.orEmpty()

        consecutiveFailures += 1
        errorSequence += 1
        lastReportedErrorMediaId = failedMediaId
        publishPlaybackError(
            PlaybackError(
                trackUri = failedMediaId,
                trackTitle = failedItem?.mediaMetadata?.title?.toString().orEmpty(),
                message = playbackErrorMessage(error.errorCode),
                id = errorSequence,
            ),
        )

        // Advancing is only ever a repair to playback that was wanted. A failure raised
        // while paused — restoring a queue whose current track has since been deleted,
        // say — has no queue to keep moving, and stepping it forward here would start
        // playing something nobody asked for. Pressing Play re-prepares and, if it
        // fails again, lands back here with playback genuinely in progress.
        if (!player.playWhenReady) return

        val recovery = errorRecoveryFor(
            hasNextItem = player.hasNextMediaItem(),
            consecutiveFailures = consecutiveFailures,
            queueSize = player.mediaItemCount,
        )
        if (recovery != ErrorRecovery.AdvanceToNext) return

        autoAdvancedFromMediaId = failedMediaId
        player.seekToNextMediaItem()
        // A PlaybackException leaves the player idle, where a seek lands but play()
        // does nothing. Without this the queue moves to the next track and sits on it
        // at 0:00, which looks identical to the stall it was meant to escape.
        player.prepare()
        player.play()
    }

    /**
     * Withdraws a reported failure as soon as anything plays.
     *
     * Anything, not just the track that failed. Keying the retraction to that one track
     * looked tidier and left the report standing forever in the common case, because a
     * file the device cannot decode never does reach READY: the notice then outlived
     * the session and greeted the user again on next launch, long after they had
     * happily played something else.
     *
     * Retracting this eagerly is only safe because the UI does not depend on the state
     * staying put to finish showing it — see the snackbar in `AppRoot`.
     */
    private fun retractErrorOncePlaybackRecovers(player: Player) {
        if (player.playbackState != Player.STATE_READY) return

        consecutiveFailures = 0
        if (lastReportedErrorMediaId != null) {
            lastReportedErrorMediaId = null
            mediaSession?.setSessionExtras(Bundle())
        }
    }

    private fun publishPlaybackError(error: PlaybackError) {
        mediaSession?.setSessionExtras(PlaybackErrorExtras.toBundle(error))
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
        cancelFades()
        settingsJob?.cancel()
        routeJob?.cancel()
        sleepTimerJob?.cancel()
        pendingSleepJob?.cancel()
        outputRouteMonitor.stop()
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
        /** Media3 reports bitrate in bits per second. */
        private const val BITS_PER_KILOBIT = 1000

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

        /** How often the finish-the-track wait checks how much is left. */
        private const val SLEEP_POLL_MS = 1_000L
    }
}
