// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deox9.musicplayer.library.Album
import com.deox9.musicplayer.library.FavouritesRepository
import com.deox9.musicplayer.library.FolderInfo
import com.deox9.musicplayer.library.GenreInfo
import com.deox9.musicplayer.library.LocalMusicRepository
import com.deox9.musicplayer.library.LocalTrack
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.library.RecommendationSignals
import com.deox9.musicplayer.library.RecommendationSignalsRepository
import com.deox9.musicplayer.library.RoomLibraryRepository
import com.deox9.musicplayer.player.PlaybackConnection
import com.deox9.musicplayer.scanner.LibraryScanner
import com.deox9.musicplayer.settings.AppSettings
import com.deox9.musicplayer.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * State and actions shared by the library tabs.
 *
 * Holds the repositories the screens used to construct from a Context, and keeps
 * favourites and recommendation signals hot across tab switches instead of
 * re-collecting them per screen.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    val playback: PlaybackConnection,
    private val libraryRepository: RoomLibraryRepository,
    private val localMusicRepository: LocalMusicRepository,
    private val scanner: LibraryScanner,
    private val favouritesRepository: FavouritesRepository,
    private val recommendationSignalsRepository: RecommendationSignalsRepository,
    private val settingsRepository: AppSettingsRepository,
) : ViewModel() {

    val favourites: StateFlow<Set<String>> = favouritesRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    val recommendationSignals: StateFlow<RecommendationSignals> =
        recommendationSignalsRepository.observe()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                RecommendationSignals(),
            )

    val settings: StateFlow<AppSettings> = settingsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppSettings())

    // Library reads come from the indexed database rather than MediaStore, so rows
    // carry album artist, disc numbers and sort keys.
    val tracks: StateFlow<List<LocalTrack>> = libraryRepository.observeTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val albums: StateFlow<List<Album>> = libraryRepository.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val genres: StateFlow<List<GenreInfo>> = libraryRepository.observeGenres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val folders: StateFlow<List<FolderInfo>> = libraryRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val scanState: StateFlow<LibraryScanner.State> = scanner.state

    suspend fun tracksByAlbum(albumId: Long): List<LocalTrack> =
        libraryRepository.tracksByAlbum(albumId)

    suspend fun searchTracks(query: String): List<LocalTrack> = libraryRepository.search(query)

    /**
     * Playlists still come from MediaStore.
     *
     * The schema has playlists and playlist_entries, but nothing imports the user's
     * existing MediaStore playlists into them yet, and reading from an empty table
     * would look like data loss.
     */
    suspend fun tracksByPlaylist(playlistId: Long): List<LocalTrack> =
        withContext(Dispatchers.IO) { localMusicRepository.getTracksByPlaylist(playlistId) }

    suspend fun tracksByGenre(genreId: Long): List<LocalTrack> =
        withContext(Dispatchers.IO) { localMusicRepository.getTracksByGenre(genreId) }

    suspend fun tracksByFolder(folderPath: String): List<LocalTrack> =
        withContext(Dispatchers.IO) { localMusicRepository.getTracksByFolder(folderPath) }

    fun toggleFavourite(uri: String) {
        viewModelScope.launch { favouritesRepository.toggle(uri) }
    }

    fun setRecommendationLiked(uri: String, liked: Boolean) {
        viewModelScope.launch { recommendationSignalsRepository.setLiked(uri, liked) }
    }

    fun setRecommendationHidden(uri: String, hidden: Boolean) {
        viewModelScope.launch { recommendationSignalsRepository.setHidden(uri, hidden) }
    }

    suspend fun playlists(): List<PlaylistInfo> =
        withContext(Dispatchers.IO) { localMusicRepository.getPlaylists() }

    suspend fun addTrackToPlaylist(playlistId: Long, trackUri: String): Boolean =
        withContext(Dispatchers.IO) { localMusicRepository.addTrackToPlaylist(playlistId, trackUri) }

    suspend fun createPlaylist(name: String): Long? =
        withContext(Dispatchers.IO) { localMusicRepository.createPlaylist(name) }

    suspend fun deleteTrack(trackUri: String): Boolean =
        withContext(Dispatchers.IO) { localMusicRepository.deleteTrack(trackUri) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
