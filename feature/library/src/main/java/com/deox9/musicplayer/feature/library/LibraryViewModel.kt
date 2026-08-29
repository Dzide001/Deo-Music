// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deox9.musicplayer.library.Album
import com.deox9.musicplayer.library.ArtistInfo
import com.deox9.musicplayer.library.FavouritesRepository
import com.deox9.musicplayer.library.FolderInfo
import com.deox9.musicplayer.library.GenreInfo
import com.deox9.musicplayer.library.LocalMusicRepository
import com.deox9.musicplayer.library.LocalTrack
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.library.RecommendationSignals
import com.deox9.musicplayer.library.RecommendationSignalsRepository
import com.deox9.musicplayer.library.RoomLibraryRepository
import com.deox9.musicplayer.library.TrackLoudness
import com.deox9.musicplayer.player.PlaybackConnection
import com.deox9.musicplayer.playlist.PlaylistTransfer
import com.deox9.musicplayer.scanner.LibraryFilters
import com.deox9.musicplayer.scanner.LibraryScanner
import com.deox9.musicplayer.settings.AppSettings
import com.deox9.musicplayer.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val playlistTransfer: PlaylistTransfer,
) : ViewModel() {

    /** What the last playlist import or export did, for the screen to report. */
    private val _transferStatus = MutableStateFlow<String?>(null)
    val transferStatus: StateFlow<String?> = _transferStatus.asStateFlow()

    /**
     * Counts changes to the playlists, for screens that need to re-read them.
     *
     * A counter rather than the status message, which was the first attempt and was
     * wrong: deleting two playlists produces the same sentence twice, and a key that
     * does not change does not re-run the read. The list then showed rows that were
     * already gone from the database.
     */
    private val _playlistRevision = MutableStateFlow(0)
    val playlistRevision: StateFlow<Int> = _playlistRevision.asStateFlow()

    fun deletePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch {
            libraryRepository.deletePlaylist(playlistId)
            // Reuses the transfer status, which is already what this screen watches
            // to know its list is stale.
            _transferStatus.value = "Deleted $name."
            _playlistRevision.value += 1
        }
    }

    /**
     * Hides a folder.
     *
     * Only the setting is written. The library screen watches the filters and
     * rescans when they change, so hiding from here and unhiding from Settings both
     * take effect the same way rather than through two different mechanisms.
     */
    fun hideFolder(path: String) {
        viewModelScope.launch { settingsRepository.hideFolder(path) }
    }

    fun clearTransferStatus() {
        _transferStatus.value = null
    }

    /**
     * Exports a playlist, handing the text to whatever the picker chose.
     *
     * The writer is passed in rather than a path returned, because the destination
     * is a document the caller holds a resolver for and this layer never sees a file.
     */
    fun exportPlaylist(playlistId: Long, name: String, write: (String) -> Unit) {
        viewModelScope.launch {
            _transferStatus.value = runCatching {
                write(playlistTransfer.export(playlistId))
                "Exported $name."
            }.getOrElse { "Could not export: ${it.message}" }
        }
    }

    fun importPlaylist(name: String, text: String) {
        viewModelScope.launch {
            _transferStatus.value = runCatching {
                playlistTransfer.import(name, text).summary()
            }.getOrElse { "Could not import: ${it.message}" }
            _playlistRevision.value += 1
        }
    }

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

    val artists: StateFlow<List<ArtistInfo>> = libraryRepository.observeArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val genres: StateFlow<List<GenreInfo>> = libraryRepository.observeGenres()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Folders, without the hidden ones.
     *
     * Filtered here as well as at scan time. The scan stops their tracks being
     * indexed, but the folder row survives — so a folder someone had just hidden
     * stayed on the list showing zero tracks, which reads as the hide not having
     * worked.
     */
    val folders: StateFlow<List<FolderInfo>> = combine(
        libraryRepository.observeFolders(),
        settingsRepository.observe(),
    ) { folders, settings ->
        folders.filterNot { LibraryFilters.isHidden(it.path, settings.hiddenFolders) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val scanState: StateFlow<LibraryScanner.State> = scanner.state

    suspend fun tracksByAlbum(albumId: Long): List<LocalTrack> =
        libraryRepository.tracksByAlbum(albumId)

    suspend fun tracksByArtist(artistId: Long): List<LocalTrack> =
        libraryRepository.tracksByArtist(artistId)

    suspend fun searchTracks(query: String): List<LocalTrack> = libraryRepository.search(query)

    suspend fun replayGainFor(mediaUri: String): TrackLoudness? =
        libraryRepository.replayGainFor(mediaUri)

    suspend fun tracksByPlaylist(playlistId: Long): List<LocalTrack> =
        libraryRepository.tracksByPlaylist(playlistId)

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

    suspend fun playlists(): List<PlaylistInfo> = libraryRepository.observePlaylists().first()

    suspend fun addTrackToPlaylist(playlistId: Long, trackUri: String): Boolean =
        libraryRepository.addTrackToPlaylist(playlistId, trackUri)

    suspend fun createPlaylist(name: String): Long? = libraryRepository.createPlaylist(name)

    suspend fun deleteTrack(trackUri: String): Boolean =
        withContext(Dispatchers.IO) { localMusicRepository.deleteTrack(trackUri) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
