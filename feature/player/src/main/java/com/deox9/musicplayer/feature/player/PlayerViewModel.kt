// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deox9.musicplayer.audio.CrossfadeCurve
import com.deox9.musicplayer.audio.EqBand
import com.deox9.musicplayer.audio.OutputProfile
import com.deox9.musicplayer.audio.SignalChain
import com.deox9.musicplayer.library.FavouritesRepository
import com.deox9.musicplayer.library.LocalMusicRepository
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.library.RoomLibraryRepository
import com.deox9.musicplayer.lyrics.LyricsData
import com.deox9.musicplayer.lyrics.LyricsRepository
import com.deox9.musicplayer.player.OutputRouteMonitor
import com.deox9.musicplayer.player.PlaybackConnection
import com.deox9.musicplayer.player.PlaybackState
import com.deox9.musicplayer.player.AbLoop
import com.deox9.musicplayer.player.SignalChainReporter
import com.deox9.musicplayer.player.SleepTimer
import com.deox9.musicplayer.settings.AppSettings
import com.deox9.musicplayer.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
class PlayerViewModel @Inject constructor(
    val playback: PlaybackConnection,
    private val favouritesRepository: FavouritesRepository,
    private val localMusicRepository: LocalMusicRepository,
    private val libraryRepository: RoomLibraryRepository,
    private val lyricsRepository: LyricsRepository,
    private val settingsRepository: AppSettingsRepository,
    signalChainReporter: SignalChainReporter,
    private val outputRouteMonitor: OutputRouteMonitor,
    private val sleepTimer: SleepTimer,
    private val abLoop: AbLoop,
) : ViewModel() {

    val state: StateFlow<PlaybackState> = playback.state

    /** What the audio path is doing, for the signal-chain readout. */
    val signalChain: StateFlow<SignalChain> = signalChainReporter.chain

    val sleepTimerState = sleepTimer.state

    val abLoopState = abLoop.state

    /** Marks the next loop point at wherever playback has reached. */
    fun markAbLoop() {
        // The live position, not the published one: the snapshot only refreshes on
        // player events, so marking from it puts the point wherever playback was
        // when it last started rather than where it is now.
        abLoop.mark(playback.currentPositionMs(), playback.state.value.uri)
    }

    fun clearAbLoop() = abLoop.clear()

    fun startSleepTimer(minutes: Int, finishTrack: Boolean) =
        sleepTimer.start(minutes * 60_000L, finishTrack)

    fun cancelSleepTimer() = sleepTimer.cancel()

    /** Where audio is going, so the equaliser can say what it is editing. */
    val outputRoute = outputRouteMonitor.route

    fun setOutputProfilesEnabled(enabled: Boolean) =
        edit { settingsRepository.setOutputProfilesEnabled(enabled) }

    /** Saves whatever the equaliser is currently showing as this output's profile. */
    fun saveProfileForCurrentRoute(bands: List<EqBand>) = edit {
        settingsRepository.saveOutputProfile(
            OutputProfile(routeKey = outputRouteMonitor.route.value.key, bands = bands),
        )
    }

    fun deleteProfileForCurrentRoute() = edit {
        settingsRepository.deleteOutputProfile(outputRouteMonitor.route.value.key)
    }

    val favourites: StateFlow<Set<String>> = favouritesRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    val settings: StateFlow<AppSettings> = settingsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings())

    fun toggleFavourite(uri: String) {
        viewModelScope.launch { favouritesRepository.toggle(uri) }
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
