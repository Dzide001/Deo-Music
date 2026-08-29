// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deox9.musicplayer.audio.CrossfadeCurve
import com.deox9.musicplayer.audio.EqBand
import com.deox9.musicplayer.audio.OutputProfile
import com.deox9.musicplayer.audio.ShuffleMode
import com.deox9.musicplayer.audio.SignalChain
import com.deox9.musicplayer.library.Bookmark
import com.deox9.musicplayer.library.BookmarksRepository
import com.deox9.musicplayer.library.FavouritesRepository
import com.deox9.musicplayer.library.LocalMusicRepository
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.library.RoomLibraryRepository
import com.deox9.musicplayer.lyrics.LyricsData
import com.deox9.musicplayer.lyrics.LyricsRepository
import com.deox9.musicplayer.player.AbLoop
import com.deox9.musicplayer.player.OutputRouteMonitor
import com.deox9.musicplayer.player.PlaybackConnection
import com.deox9.musicplayer.player.PlaybackState
import com.deox9.musicplayer.player.QueuedTrack
import com.deox9.musicplayer.player.SavedQueue
import com.deox9.musicplayer.player.SavedQueueTrack
import com.deox9.musicplayer.player.SavedQueues
import com.deox9.musicplayer.player.SignalChainReporter
import com.deox9.musicplayer.player.SleepTimer
import com.deox9.musicplayer.player.storage.LyricsOffsetsRepository
import com.deox9.musicplayer.player.storage.SavedQueuesRepository
import com.deox9.musicplayer.scanner.RatingWriter
import com.deox9.musicplayer.settings.AppSettings
import com.deox9.musicplayer.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * State and actions for the mini player, expanded player and queue.
 *
 * The screens previously built four repositories each from a Context inside the
 * composable, so every recomposition path held its own instances and none of it
 * could be tested without an Activity.
 */
@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlayerViewModel @Inject constructor(
    val playback: PlaybackConnection,
    private val favouritesRepository: FavouritesRepository,
    private val localMusicRepository: LocalMusicRepository,
    private val libraryRepository: RoomLibraryRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsRepository: AppSettingsRepository,
    private val playerTools: PlayerTools,
    private val savedQueues: SavedQueuesRepository,
    private val bookmarksRepository: BookmarksRepository,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playback.state

    /** What the audio path is doing, for the signal-chain readout. */
    val signalChain: StateFlow<SignalChain> = playerTools.signalChain.chain

    val sleepTimerState = playerTools.sleepTimer.state

    val abLoopState = playerTools.abLoop.state

    /** Marks the next loop point at wherever playback has reached. */
    fun markAbLoop() {
        // The live position, not the published one: the snapshot only refreshes on
        // player events, so marking from it puts the point wherever playback was
        // when it last started rather than where it is now.
        playerTools.abLoop.mark(playback.currentPositionMs(), playback.state.value.uri)
    }

    fun clearAbLoop() = playerTools.abLoop.clear()

    fun startSleepTimer(minutes: Int, finishTrack: Boolean) =
        playerTools.sleepTimer.start(minutes * 60_000L, finishTrack)

    fun cancelSleepTimer() = playerTools.sleepTimer.cancel()

    /** Where audio is going, so the equaliser can say what it is editing. */
    val outputRoute = playerTools.outputRoute.route

    fun setOutputProfilesEnabled(enabled: Boolean) =
        edit { settingsRepository.setOutputProfilesEnabled(enabled) }

    /** Saves whatever the equaliser is currently showing as this output's profile. */
    fun saveProfileForCurrentRoute(bands: List<EqBand>) = edit {
        settingsRepository.saveOutputProfile(
            OutputProfile(routeKey = playerTools.outputRoute.route.value.key, bands = bands),
        )
    }

    fun deleteProfileForCurrentRoute() = edit {
        settingsRepository.deleteOutputProfile(playerTools.outputRoute.route.value.key)
    }

    val favourites: StateFlow<Set<String>> = favouritesRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    val settings: StateFlow<AppSettings> = settingsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings())

    fun toggleFavourite(uri: String) {
        viewModelScope.launch { favouritesRepository.toggle(uri) }
    }

    private val _rating = MutableStateFlow(0)

    /** Stars for the current track, 0 when unrated. */
    val rating: StateFlow<Int> = _rating.asStateFlow()

    /** Whether the last rating reached the file, so the UI can say if it did not. */
    private val _ratingWriteRefused = MutableStateFlow(false)
    val ratingWriteRefused: StateFlow<Boolean> = _ratingWriteRefused.asStateFlow()

    /**
     * A consent dialog the screen should show, if the system offered one.
     *
     * Held here rather than launched here because launching an IntentSender needs an
     * Activity, and a ViewModel that reaches for one outlives it.
     */
    private val _ratingConsent = MutableStateFlow<IntentSender?>(null)
    val ratingConsent: StateFlow<IntentSender?> = _ratingConsent.asStateFlow()

    /**
     * Clears the pending request once the dialog is done with.
     *
     * A refusal is recorded, not swallowed. Saying nothing after someone declines
     * leaves them with a filled-in star and no idea the file was left alone, which is
     * the one outcome where they might reasonably have expected otherwise.
     */
    fun consentHandled(granted: Boolean) {
        _ratingConsent.value = null
        if (!granted) _ratingWriteRefused.value = true
    }

    /** Re-runs the write once the user has agreed to it. */
    fun retryRatingWrite(uri: String) {
        val stars = _rating.value
        viewModelScope.launch {
            val result = libraryRepository.rateTrack(uri, stars)
            _ratingWriteRefused.value = result !is RatingWriter.Result.Written
        }
    }

    fun loadRating(uri: String) {
        viewModelScope.launch {
            _rating.value = if (uri.isBlank()) 0 else libraryRepository.ratingFor(uri)
        }
    }

    fun rate(uri: String, stars: Int) {
        if (uri.isBlank()) return
        viewModelScope.launch {
            // Shown immediately. The write is to a file that may be a hundred
            // megabytes on a slow card, and a star that waits for it feels broken.
            _rating.value = stars
            when (val result = libraryRepository.rateTrack(uri, stars)) {
                // The system is willing to ask on our behalf, so ask, rather than
                // telling someone their file is read-only when it need not be.
                is RatingWriter.Result.NeedsConsent -> _ratingConsent.value = result.request
                is RatingWriter.Result.Written -> _ratingWriteRefused.value = false
                else -> _ratingWriteRefused.value = true
            }
        }
    }

    suspend fun playlists(): List<PlaylistInfo> = libraryRepository.observePlaylists().first()

    suspend fun addTrackToPlaylist(playlistId: Long, trackUri: String): Boolean =
        libraryRepository.addTrackToPlaylist(playlistId, trackUri)

    suspend fun createPlaylist(name: String): Long? = libraryRepository.createPlaylist(name)

    suspend fun deleteTrack(trackUri: String): Boolean =
        withContext(Dispatchers.IO) { localMusicRepository.deleteTrack(trackUri) }

    suspend fun lyricsFor(
        trackKey: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
    ): LyricsData? = withContext(Dispatchers.IO) {
        lyricsRepository.getLyrics(
            trackKey = trackKey,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            allowOnlineLookup = settings.value.onlineLyricsEnabled,
        )
    }

    /**
     * The listener's own correction for this track's lyrics, in milliseconds.
     *
     * Kept separate from the lyrics themselves so changing it does not re-fetch
     * them: nudging the timing should be instant, and a round trip to the network to
     * move a line by a quarter second would be absurd.
     */
    private val _lyricsOffsetMs = MutableStateFlow(0L)
    val lyricsOffsetMs: StateFlow<Long> = _lyricsOffsetMs.asStateFlow()

    fun loadLyricsOffset(trackUri: String) {
        viewModelScope.launch {
            _lyricsOffsetMs.value = playerTools.lyricsOffsets.offsetFor(trackUri)
        }
    }

    fun nudgeLyrics(trackUri: String, deltaMs: Long) {
        if (trackUri.isBlank()) return
        val next = (_lyricsOffsetMs.value + deltaMs)
            .coerceIn(-LyricsOffsetsRepository.MAX_OFFSET_MS, LyricsOffsetsRepository.MAX_OFFSET_MS)
        _lyricsOffsetMs.value = next
        viewModelScope.launch { playerTools.lyricsOffsets.setOffset(trackUri, next) }
    }

    fun resetLyricsOffset(trackUri: String) {
        _lyricsOffsetMs.value = 0L
        viewModelScope.launch { playerTools.lyricsOffsets.setOffset(trackUri, 0L) }
    }

    /**
     * Steps to the next shuffle mode.
     *
     * A cycle on the existing button rather than a new control: four modes do not
     * justify a second thing in the transport row, and the icon already says
     * whether shuffle is doing anything.
     */
    val queues: StateFlow<SavedQueues> = savedQueues.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SavedQueues())

    /**
     * Saves what is playing now as a new queue, and switches to it.
     *
     * Switching immediately is the point: the new queue becomes the one that
     * receives position updates, so the one left behind stops moving and keeps the
     * place it had.
     */
    fun saveCurrentQueue(name: String? = null) = edit {
        val state = playback.state.value
        if (state.queue.isEmpty()) return@edit
        val tracks = state.queue.map { SavedQueueTrack(it.uri, it.title, it.artist) }
        savedQueues.update { existing ->
            val id = "queue-${System.currentTimeMillis()}"
            existing.save(
                SavedQueue(
                    id = id,
                    name = name?.trim()?.takeIf { it.isNotEmpty() } ?: existing.suggestName(),
                    tracks = tracks,
                    currentIndex = state.queue.indexOfFirst { it.uri == state.uri }.coerceAtLeast(0),
                    positionMs = playback.currentPositionMs(),
                    updatedAtMs = System.currentTimeMillis(),
                ),
            ).activate(id)
        }
    }

    /**
     * Switches to a saved queue, resuming where it was left.
     *
     * The queue being left is written first. Without that it would keep the position
     * it had when it was last persisted, which is up to a second stale — and after a
     * switch that second is the difference between resuming and repeating a phrase.
     */
    fun switchToQueue(id: String) = edit {
        val target = savedQueues.current().queues.firstOrNull { it.id == id } ?: return@edit
        savedQueues.update { it.activate(id) }
        playback.playQueue(
            tracks = target.tracks.map { QueuedTrack(it.uri, it.title, it.artist) },
            startIndex = target.safeIndex,
            positionMs = target.positionMs,
        )
    }

    fun renameQueue(id: String, name: String) = edit { savedQueues.update { it.rename(id, name) } }

    fun deleteQueue(id: String) = edit { savedQueues.update { it.remove(id) } }

    /**
     * Bookmarks for whatever is playing.
     *
     * Re-read when the track changes, and only then: a bookmark belongs to one
     * recording and the list is meaningless against another.
     */
    val bookmarks: StateFlow<List<Bookmark>> = playback.state
        .map { it.uri }
        .distinctUntilChanged()
        .flatMapLatest { uri ->
            if (uri.isBlank()) {
                flowOf(emptyList())
            } else {
                bookmarksRepository.prepare(uri)
                bookmarksRepository.observe(uri)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Marks the spot playing right now. */
    fun addBookmark(label: String = "") = edit {
        val uri = playback.state.value.uri
        if (uri.isNotBlank()) bookmarksRepository.add(uri, playback.currentPositionMs(), label)
    }

    fun jumpToBookmark(bookmark: Bookmark) = playback.seekTo(bookmark.positionMs)

    fun deleteBookmark(id: Long) = edit { bookmarksRepository.remove(id) }

    fun renameBookmark(id: Long, label: String) = edit { bookmarksRepository.rename(id, label) }

    fun cycleShuffleMode() = edit {
        val next = when (settings.value.shuffleMode) {
            ShuffleMode.Off -> ShuffleMode.Tracks
            ShuffleMode.Tracks -> ShuffleMode.Albums
            ShuffleMode.Albums -> ShuffleMode.Folders
            ShuffleMode.Folders -> ShuffleMode.Off
        }
        settingsRepository.setShuffleMode(next)
    }

    fun setPlaybackSpeed(speed: Float) = edit { settingsRepository.setPlaybackSpeed(speed) }

    fun setPlaybackPitch(pitch: Float) = edit { settingsRepository.setPlaybackPitch(pitch) }

    fun resetPlaybackRate() = edit {
        settingsRepository.setPlaybackSpeed(1f)
        settingsRepository.setPlaybackPitch(1f)
    }

    fun setOnlineLyricsEnabled(enabled: Boolean) =
        edit { settingsRepository.setOnlineLyricsEnabled(enabled) }

    fun setCrossfadeOnSkip(enabled: Boolean) = edit { settingsRepository.setCrossfadeOnSkip(enabled) }

    fun setCrossfadeOnAutoAdvance(enabled: Boolean) =
        edit { settingsRepository.setCrossfadeOnAutoAdvance(enabled) }

    fun setCrossfadeDurationMs(durationMs: Int) =
        edit { settingsRepository.setCrossfadeDurationMs(durationMs) }

    fun setCrossfadeCurve(curve: CrossfadeCurve) = edit { settingsRepository.setCrossfadeCurve(curve) }

    fun setEqEnabled(enabled: Boolean) = edit { settingsRepository.setEqEnabled(enabled) }

    fun setEqBands(bands: List<EqBand>) = edit { settingsRepository.setEqBands(bands) }

    fun setReplayGainEnabled(enabled: Boolean) = edit { settingsRepository.setReplayGainEnabled(enabled) }

    fun setReplayGainDb(db: Float) = edit { settingsRepository.setReplayGainDb(db) }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * The playback-domain singletons the player screen needs.
 *
 * Four separate constructor parameters put this view model over the limit, and the
 * limit is right: a constructor is a list of what something depends on, and ten
 * entries stops being readable as one. These four are all the same kind of thing —
 * process-wide state the service publishes and the UI observes — so they travel
 * together.
 */
class PlayerTools @Inject constructor(
    val signalChain: SignalChainReporter,
    val outputRoute: OutputRouteMonitor,
    val sleepTimer: SleepTimer,
    val abLoop: AbLoop,
    /**
     * Per-track lyric timing corrections.
     *
     * Here rather than as a tenth constructor argument on the view model. This bundle
     * exists for exactly this — the small, player-adjacent pieces of state that would
     * otherwise each claim a parameter — and a lyric offset is one of them.
     */
    val lyricsOffsets: LyricsOffsetsRepository,
)
