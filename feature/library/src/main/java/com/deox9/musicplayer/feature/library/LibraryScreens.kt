// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.library.Album
import com.deox9.musicplayer.library.FolderInfo
import com.deox9.musicplayer.library.GenreInfo
import com.deox9.musicplayer.library.LocalTrack
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.scanner.LibraryScanWorker
import com.deox9.musicplayer.ui.AlbumSortOption
import com.deox9.musicplayer.ui.CollectionSortOption
import com.deox9.musicplayer.ui.SongSortOption
import com.deox9.musicplayer.ui.buildAlphabetIndex
import com.deox9.musicplayer.ui.formatDuration
import com.deox9.musicplayer.ui.shouldShowFastScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlaylistsScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    searchQuery: String = "",
    sortOption: CollectionSortOption = CollectionSortOption.Name,
    listState: LazyListState
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<PlaylistInfo?>(null) }
    // A one-shot read from the index rather than a live StateFlow, matching the
    // existing screen structure; the scanner imports MediaStore playlists into the
    // schema as part of every scan.
    val playlists by produceState<List<PlaylistInfo>>(initialValue = emptyList()) {
        value = viewModel.playlists()
    }

    ListDetailPane(
        detail = selected?.let { playlist ->
            {
                PlaylistDetailScreen(playlist = playlist, onBack = { selected = null })
            }
        },
    ) {
        PlaylistsList(
            playlists = playlists,
            searchQuery = searchQuery,
            sortOption = sortOption,
            listState = listState,
            onSelect = { selected = it }
        )
    }
}

@Composable
private fun PlaylistsList(
    playlists: List<PlaylistInfo>,
    searchQuery: String,
    sortOption: CollectionSortOption,
    listState: LazyListState,
    onSelect: (PlaylistInfo) -> Unit
) {
    val filtered = remember(playlists, searchQuery, sortOption) {
        val searched = if (searchQuery.isBlank()) playlists else {
            val q = searchQuery.trim().lowercase()
            playlists.filter { it.name.lowercase().contains(q) }
        }

        when (sortOption) {
            CollectionSortOption.Name -> searched.sortedBy { it.name.lowercase() }
            CollectionSortOption.TrackCount -> searched.sortedByDescending { it.trackCount }
        }
    }

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(if (searchQuery.isBlank()) "No playlists found." else "No playlists match your search.")
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(filtered, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(playlist) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${playlist.trackCount} tracks",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Text("Open")
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun FoldersScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    searchQuery: String = "",
    sortOption: CollectionSortOption = CollectionSortOption.Name,
    listState: LazyListState
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<FolderInfo?>(null) }
    val folders by viewModel.folders.collectAsState()

    ListDetailPane(
        detail = selected?.let { folder ->
            {
                FolderDetailScreen(folder = folder, onBack = { selected = null })
            }
        },
    ) {
        FoldersList(
            folders = folders,
            searchQuery = searchQuery,
            sortOption = sortOption,
            listState = listState,
            onSelect = { selected = it }
        )
    }
}

@Composable
private fun FoldersList(
    folders: List<FolderInfo>,
    searchQuery: String,
    sortOption: CollectionSortOption,
    listState: LazyListState,
    onSelect: (FolderInfo) -> Unit
) {
    val filtered = remember(folders, searchQuery, sortOption) {
        val searched = if (searchQuery.isBlank()) folders else {
            val q = searchQuery.trim().lowercase()
            folders.filter { it.name.lowercase().contains(q) || it.path.lowercase().contains(q) }
        }

        when (sortOption) {
            CollectionSortOption.Name -> searched.sortedBy { it.name.lowercase() }
            CollectionSortOption.TrackCount -> searched.sortedByDescending { it.trackCount }
        }
    }

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(if (searchQuery.isBlank()) "No folders found." else "No folders match your search.")
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(filtered, key = { it.path }) { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(folder) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = folder.path,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text("${folder.trackCount}")
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun GenresScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    searchQuery: String = "",
    sortOption: CollectionSortOption = CollectionSortOption.Name,
    listState: LazyListState
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<GenreInfo?>(null) }
    val genres by viewModel.genres.collectAsState()

    ListDetailPane(
        detail = selected?.let { genre ->
            {
                GenreDetailScreen(genre = genre, onBack = { selected = null })
            }
        },
    ) {
        GenresList(
            genres = genres,
            searchQuery = searchQuery,
            sortOption = sortOption,
            listState = listState,
            onSelect = { selected = it }
        )
    }
}

@Composable
private fun GenresList(
    genres: List<GenreInfo>,
    searchQuery: String,
    sortOption: CollectionSortOption,
    listState: LazyListState,
    onSelect: (GenreInfo) -> Unit
) {
    val filtered = remember(genres, searchQuery, sortOption) {
        val searched = if (searchQuery.isBlank()) genres else {
            val q = searchQuery.trim().lowercase()
            genres.filter { it.name.lowercase().contains(q) }
        }

        when (sortOption) {
            CollectionSortOption.Name -> searched.sortedBy { it.name.lowercase() }
            CollectionSortOption.TrackCount -> searched.sortedByDescending { it.trackCount }
        }
    }

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(if (searchQuery.isBlank()) "No genres found." else "No genres match your search.")
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(filtered, key = { it.id }) { genre ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(genre) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = genre.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${genre.trackCount} tracks",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text("Open")
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun SuggestedScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    listState: LazyListState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appSettings by viewModel.settings.collectAsState()
    val playback = viewModel.playback
    val playbackState by playback.state.collectAsState()
    val session = playbackState.takeIf { it.hasTrack }
    val favourites by viewModel.favourites.collectAsState()
    val recommendationSignals by viewModel.recommendationSignals.collectAsState()
    var refreshNonce by remember { mutableStateOf(0) }

    if (!appSettings.suggestionsEnabled) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text("Suggestions are disabled in settings.")
        }
        return
    }

    val tracks by viewModel.tracks.collectAsState()

    data class SuggestedRecommendation(
        val track: LocalTrack,
        val reason: String,
        val score: Int
    )

    val recommendations = remember(tracks, session, favourites, recommendationSignals, refreshNonce) {
        if (tracks.isEmpty()) {
            emptyList()
        } else {
            val currentUri = session?.uri.orEmpty()
            val currentArtist = session?.artist.orEmpty().lowercase()
            val recentArtists = session?.queue
                ?.map { it.artist.lowercase() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                .orEmpty()

            val scored = tracks
                .asSequence()
                .filter { it.contentUri != currentUri }
                .filter { it.contentUri !in recommendationSignals.hiddenTrackUris }
                .map { track ->
                    val artistLower = track.artist.lowercase()
                    val titleLower = track.title.lowercase()
                    val artistPlayCount = recommendationSignals.artistPlayCounts[artistLower] ?: 0
                    val trackPlayCount = recommendationSignals.trackPlayCounts[track.contentUri] ?: 0
                    val trackSkipCount = recommendationSignals.trackSkipCounts[track.contentUri] ?: 0

                    var score = 0
                    val reasons = mutableListOf<String>()

                    if (artistLower == currentArtist && currentArtist.isNotBlank()) {
                        score += 120
                        reasons += "same artist"
                    } else if (artistLower in recentArtists) {
                        score += 70
                        reasons += "artist in your recent queue"
                    }

                    if (track.contentUri in favourites) {
                        score += 35
                        reasons += "you starred this"
                    }

                    if (track.contentUri in recommendationSignals.likedTrackUris) {
                        score += 90
                        reasons += "you liked this recommendation"
                    }

                    if (artistPlayCount > 0) {
                        val artistWeight = (artistPlayCount.coerceAtMost(20)) * 4
                        score += artistWeight
                        reasons += "frequently played artist"
                    }

                    if (trackPlayCount > 0) {
                        val replayWeight = (trackPlayCount.coerceAtMost(10)) * 2
                        score += replayWeight
                    }

                    if (trackSkipCount > 0) {
                        score -= (trackSkipCount.coerceAtMost(10)) * 9
                        reasons += "you often skip this"
                    }

                    if (session?.title?.isNotBlank() == true) {
                        val seedWord = session!!.title
                            .split(" ")
                            .firstOrNull()
                            ?.trim()
                            ?.lowercase()
                            .orEmpty()
                        if (seedWord.length >= 4 && titleLower.contains(seedWord)) {
                            score += 20
                            reasons += "title similarity"
                        }
                    }

                    if (track.durationMs in 150_000L..360_000L) {
                        score += 8
                    }

                    if (score == 0) {
                        score = 1
                        reasons += "library discovery"
                    }

                    SuggestedRecommendation(
                        track = track,
                        reason = reasons.firstOrNull() ?: "good match for your queue",
                        score = score
                    )
                }
                .sortedByDescending { it.score }
                .take(60)
                .toList()

            if (scored.isEmpty()) {
                scored
            } else {
                val shift = refreshNonce % scored.size
                if (shift == 0) scored else (scored.drop(shift) + scored.take(shift))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Suggested",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedButton(onClick = { refreshNonce += 1 }) {
                Text("Refresh")
            }
        }

        if (session?.artist?.isNotBlank() == true) {
            Text(
                text = "Based on your current listening: ${session?.artist}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (recommendations.isEmpty()) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text("No suggestions available yet.")
            }
        } else {
            LazyColumn(state = listState) {
                items(recommendations, key = { it.track.id }) { rec ->
                    LocalTrackRow(
                        track = rec.track,
                        isFavourite = rec.track.contentUri in favourites,
                        onToggleFavourite = {
                            viewModel.toggleFavourite(rec.track.contentUri)
                        },
                        onClick = {
                            playback.playNow(rec.track.contentUri, rec.track.title, rec.track.artist)
                        },
                        onPlayNext = {
                            playback.playNext(rec.track.contentUri, rec.track.title, rec.track.artist)
                        },
                        onAddToQueue = {
                            playback.addToQueue(rec.track.contentUri, rec.track.title, rec.track.artist)
                        }
                    )
                    Text(
                        text = "Suggested because: ${rec.reason}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    viewModel.setRecommendationLiked(rec.track.contentUri, liked = true)
                                }
                            }
                        ) {
                            Text("Like")
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    viewModel.setRecommendationHidden(rec.track.contentUri, hidden = true)
                                }
                            }
                        ) {
                            Text("Hide")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun FavouritesScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    searchQuery: String = "",
    sortOption: SongSortOption = SongSortOption.Title,
    listState: LazyListState
) {
    val context = LocalContext.current
    val playback = viewModel.playback
    val favourites by viewModel.favourites.collectAsState()
    val scope = rememberCoroutineScope()

    val tracks by viewModel.tracks.collectAsState()

    val favTracks by produceState(
        initialValue = emptyList<LocalTrack>(),
        key1 = tracks,
        key2 = favourites,
        key3 = Pair(searchQuery, sortOption)
    ) {
        value = withContext(Dispatchers.Default) {
            val searched = tracks
                .filter { it.contentUri in favourites }
                .filter {
                    if (searchQuery.isBlank()) true else {
                        val q = searchQuery.trim().lowercase()
                        it.title.lowercase().contains(q) ||
                            it.artist.lowercase().contains(q) ||
                            it.album.lowercase().contains(q)
                    }
                }

            when (sortOption) {
                SongSortOption.Title -> searched.sortedBy { it.title.lowercase() }
                SongSortOption.Artist -> searched.sortedBy { it.artist.lowercase() }
                SongSortOption.Album -> searched.sortedBy { it.album.lowercase() }
                SongSortOption.Duration -> searched.sortedByDescending { it.durationMs }
            }
        }
    }

    if (favTracks.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(if (searchQuery.isBlank()) "No favourites yet." else "No favourites match your search.")
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(favTracks, key = { it.id }) { track ->
            LocalTrackRow(
                track = track,
                isFavourite = track.contentUri in favourites,
                onToggleFavourite = {
                    viewModel.toggleFavourite(track.contentUri)
                },
                onClick = {
                    playback.playNow(track.contentUri, track.title, track.artist)
                },
                onPlayNext = {
                    playback.playNext(track.contentUri, track.title, track.artist)
                },
                onAddToQueue = {
                    playback.addToQueue(track.contentUri, track.title, track.artist)
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlaylistDetailScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    playlist: PlaylistInfo,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tracks by produceState<List<LocalTrack>>(initialValue = emptyList(), key1 = playlist.id) {
        value = withContext(Dispatchers.IO) {
            viewModel.tracksByPlaylist(playlist.id)
        }
    }

    CollectionTrackListScreen(
        title = playlist.name,
        tracks = tracks,
        onBack = onBack
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GenreDetailScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    genre: GenreInfo,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tracks by produceState<List<LocalTrack>>(initialValue = emptyList(), key1 = genre.id) {
        value = withContext(Dispatchers.IO) {
            viewModel.tracksByGenre(genre.id)
        }
    }

    CollectionTrackListScreen(
        title = genre.name,
        tracks = tracks,
        onBack = onBack
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FolderDetailScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    folder: FolderInfo,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tracks by produceState<List<LocalTrack>>(initialValue = emptyList(), key1 = folder.path) {
        value = withContext(Dispatchers.IO) {
            viewModel.tracksByFolder(folder.path)
        }
    }

    CollectionTrackListScreen(
        title = folder.name,
        tracks = tracks,
        onBack = onBack
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CollectionTrackListScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    title: String,
    tracks: List<LocalTrack>,
    onBack: () -> Unit,
    subtitle: String? = null,
) {
    val context = LocalContext.current
    val playback = viewModel.playback
    val favourites by viewModel.favourites.collectAsState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            // These detail screens open inside the tab content area, not at the top
            // of the window, so the bar must not re-apply the status-bar inset the
            // scaffold has already consumed — doing so left a band of empty space
            // above the title.
            windowInsets = WindowInsets(0),
            title = {
                Column {
                    Text(title, maxLines = 1)
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text("No tracks found.")
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tracks, key = { it.id }) { track ->
                LocalTrackRow(
                    track = track,
                    isFavourite = track.contentUri in favourites,
                    onToggleFavourite = {
                        viewModel.toggleFavourite(track.contentUri)
                    },
                    onClick = {
                        playback.playNow(track.contentUri, track.title, track.artist)
                    },
                    onPlayNext = {
                        playback.playNext(track.contentUri, track.title, track.artist)
                    },
                    onAddToQueue = {
                        playback.addToQueue(track.contentUri, track.title, track.artist)
                    }
                )
            }
        }
    }
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    searchQuery: String = "",
    sortOption: SongSortOption = SongSortOption.Title,
    listState: LazyListState
) {
    val context = LocalContext.current
    val playback = viewModel.playback
    val favourites by viewModel.favourites.collectAsState()
    val scope = rememberCoroutineScope()
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val tracks by viewModel.tracks.collectAsState()

    // The launch-time scan runs before the permission dialog is answered, so a fresh
    // install would otherwise show an empty library until something else triggered a
    // rescan.
    val librarySettings by viewModel.settings.collectAsState()
    LaunchedEffect(hasPermission, librarySettings.thoroughScanEnabled) {
        if (hasPermission) {
            LibraryScanWorker.enqueue(context, thorough = librarySettings.thoroughScanEnabled)
        }
    }

    if (!hasPermission) {
        AudioPermissionPrompt(
            message = "Allow audio access to load your local music library.",
            onGrant = { permissionLauncher.launch(audioPermission) }
        )
        return
    }

    val filteredTracks by produceState(
        initialValue = emptyList<LocalTrack>(),
        key1 = tracks,
        key2 = searchQuery,
        key3 = sortOption
    ) {
        value = withContext(Dispatchers.Default) {
            val searched = if (searchQuery.isBlank()) {
                tracks
            } else {
                val q = searchQuery.trim().lowercase()
                tracks.filter {
                    it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
                }
            }

            when (sortOption) {
                SongSortOption.Title -> searched.sortedBy { it.title.lowercase() }
                SongSortOption.Artist -> searched.sortedBy { it.artist.lowercase() }
                SongSortOption.Album -> searched.sortedBy { it.album.lowercase() }
                SongSortOption.Duration -> searched.sortedByDescending { it.durationMs }
            }
        }
    }

    if (filteredTracks.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(if (searchQuery.isBlank()) "No local tracks found." else "No songs match your search.")
        }
        return
    }

    // Buckets are built from the same list the rail scrolls, so a filtered list gets
    // a filtered rail rather than one pointing at indices that no longer exist.
    val buckets = remember(filteredTracks) { buildAlphabetIndex(filteredTracks.map { it.title }) }
    val showRail = shouldShowFastScroll(filteredTracks.size, sortOption == SongSortOption.Title)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Rows end before the rail rather than sliding underneath it.
            contentPadding = PaddingValues(end = if (showRail) FastScrollGutter else 0.dp)
        ) {
            items(filteredTracks, key = { it.id }) { track ->
                LocalTrackRow(
                    track = track,
                    isFavourite = track.contentUri in favourites,
                    onToggleFavourite = {
                        viewModel.toggleFavourite(track.contentUri)
                    },
                    onClick = {
                        playback.playNow(track.contentUri, track.title, track.artist)
                    },
                    onPlayNext = {
                        playback.playNext(track.contentUri, track.title, track.artist)
                    },
                    onAddToQueue = {
                        playback.addToQueue(track.contentUri, track.title, track.artist)
                    }
                )
                HorizontalDivider()
            }
        }

        if (showRail) {
            FastScrollRail(
                buckets = buckets,
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LocalTrackRow(
    track: LocalTrack,
    isFavourite: Boolean = false,
    onToggleFavourite: (() -> Unit)? = null,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true },
                onDoubleClick = { onToggleFavourite?.invoke() }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(
            artworkUri = track.artworkUri,
            // The row already announces the title; naming the art again would make
            // every row read twice.
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = trackSubtitle(track),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // A marker, not a button, and only present when it applies — so an
        // unfavourited row spends nothing on it.
        if (isFavourite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Favourite",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        // Duration rather than an overflow button. Both cost about the same width
        // and only one fits at 360dp: this one is information on every row, where
        // the button was an affordance for a menu that long-press already opens.
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TrackContextMenu(
            expanded = showContextMenu,
            isFavourite = isFavourite,
            onDismiss = { showContextMenu = false },
            onPlayNext = { onPlayNext?.invoke() ?: onAddToQueue() },
            onAddToQueue = onAddToQueue,
            onToggleFavourite = onToggleFavourite,
            onViewDetails = { showDetails = true }
        )

        if (showDetails) {
            TrackDetailsDialog(track = track, onDismiss = { showDetails = false })
        }
    }
}

/**
 * The row's long-press and overflow menu.
 *
 * Split out of the row so both entry points share one definition, and because the
 * row was long enough that adding the overflow button pushed it past detekt's limit.
 */
@Composable
private fun TrackContextMenu(
    expanded: Boolean,
    isFavourite: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavourite: (() -> Unit)?,
    onViewDetails: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Play next") },
            onClick = {
                onPlayNext()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Add to queue") },
            onClick = {
                onAddToQueue()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text(if (isFavourite) "Remove favourite" else "Add to favourite") },
            onClick = {
                onToggleFavourite?.invoke()
                onDismiss()
            },
            enabled = onToggleFavourite != null
        )
        DropdownMenuItem(
            text = { Text("View details") },
            onClick = {
                onViewDetails()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = onDismiss,
            enabled = false
        )
    }
}

@Composable
private fun TrackDetailsDialog(track: LocalTrack, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Track details") },
        text = {
            Text(
                "Title: ${track.title}\n" +
                    "Artist: ${track.artist}\n" +
                    "Album: ${track.album}\n" +
                    "Duration: ${formatDuration(track.durationMs)}"
            )
        }
    )
}

@Composable
fun AlbumsScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    searchQuery: String = "",
    sortOption: AlbumSortOption = AlbumSortOption.Name,
    listState: LazyListState
) {
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    var selectedAlbum by remember { mutableStateOf<Album?>(null) }

    // Collected, not read via .value: the flow is started WhileSubscribed, so
    // sampling .value without a collector returns the initial empty list forever.
    val albums by viewModel.albums.collectAsState()

    ListDetailPane(
        detail = selectedAlbum?.let { album ->
            {
                AlbumDetailScreen(album = album, onBack = { selectedAlbum = null })
            }
        },
    ) {
        if (hasPermission) {
            AlbumsList(
                albums = albums,
                searchQuery = searchQuery,
                sortOption = sortOption,
                listState = listState,
                onSelect = { selectedAlbum = it }
            )
        } else {
            AudioPermissionPrompt(
                message = "Allow audio access to load your music library.",
                onGrant = { permissionLauncher.launch(audioPermission) }
            )
        }
    }
}

/** Shared by the two screens that gate on the audio permission. */
@Composable
private fun AudioPermissionPrompt(message: String, onGrant: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onGrant) { Text("Grant permission") }
    }
}

@Composable
private fun AlbumsList(
    albums: List<Album>,
    searchQuery: String,
    sortOption: AlbumSortOption,
    listState: LazyListState,
    onSelect: (Album) -> Unit
) {
    val filteredAlbums by produceState(
        initialValue = emptyList<Album>(),
        key1 = albums,
        key2 = searchQuery,
        key3 = sortOption
    ) {
        value = withContext(Dispatchers.Default) {
            val searched = if (searchQuery.isBlank()) {
                albums
            } else {
                val q = searchQuery.trim().lowercase()
                albums.filter {
                    it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q)
                }
            }

            when (sortOption) {
                AlbumSortOption.Name -> searched.sortedBy { it.title.lowercase() }
                AlbumSortOption.Artist -> searched.sortedBy { it.artist.lowercase() }
                AlbumSortOption.TrackCount -> searched.sortedByDescending { it.trackCount }
            }
        }
    }

    if (filteredAlbums.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(if (searchQuery.isBlank()) "No albums found." else "No albums match your search.")
        }
        return
    }

    val buckets = remember(filteredAlbums) { buildAlphabetIndex(filteredAlbums.map { it.title }) }
    val showRail = shouldShowFastScroll(filteredAlbums.size, sortOption == AlbumSortOption.Name)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = if (showRail) FastScrollGutter else 0.dp)
        ) {
            items(filteredAlbums, key = { it.id }) { album ->
                AlbumCard(
                    album = album,
                    onClick = { onSelect(album) }
                )
            }
        }

        if (showRail) {
            FastScrollRail(
                buckets = buckets,
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors()
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            TrackArtwork(
                artworkUri = album.artworkUri?.toString(),
                contentDescription = "Album art for ${album.title}",
                size = 80.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${album.trackCount} track${if (album.trackCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AlbumDetailScreen(
    album: Album,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playback = viewModel.playback
    val favourites by viewModel.favourites.collectAsState()
    val scope = rememberCoroutineScope()

    val tracks by produceState<List<LocalTrack>>(
        initialValue = emptyList(),
        key1 = album.id
    ) {
        value = withContext(Dispatchers.IO) {
            viewModel.tracksByAlbum(album.id)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = { Text(album.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text("No tracks found in this album.")
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tracks, key = { it.id }) { track ->
                LocalTrackRow(
                    track = track,
                    isFavourite = track.contentUri in favourites,
                    onToggleFavourite = {
                        viewModel.toggleFavourite(track.contentUri)
                    },
                    onClick = {
                        playback.playNow(track.contentUri, track.title, track.artist)
                    },
                    onPlayNext = {
                        playback.playNext(track.contentUri, track.title, track.artist)
                    },
                    onAddToQueue = {
                        playback.addToQueue(track.contentUri, track.title, track.artist)
                    }
                )
            }
        }
    }
}

/**
 * Global search across the library.
 *
 * Its own destination rather than a field in the app bar, because the header field
 * only ever searched the tab you happened to be on — "Search Albums" could not find
 * a track. Tracks come from the FTS index; albums are filtered on the already-loaded
 * list, which is cheap and keeps one query path rather than two.
 */
@Composable
fun SearchScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    listState: LazyListState,
) {
    val playback = viewModel.playback
    val favourites by viewModel.favourites.collectAsState()
    val albums by viewModel.albums.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var trackResults by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }

    // Debounced so a query does not hit FTS on every keystroke.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            trackResults = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        trackResults = viewModel.searchTracks(query)
    }

    val albumResults = remember(albums, query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            val term = query.trim().lowercase()
            albums.filter { it.title.lowercase().contains(term) || it.artist.lowercase().contains(term) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            label = { Text("Search your library") },
        )

        if (query.isBlank()) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Search tracks, albums and artists.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        if (trackResults.isEmpty() && albumResults.isEmpty()) {
            Box(modifier = Modifier.padding(16.dp)) {
                Text("Nothing matches “$query”.")
            }
            return@Column
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (albumResults.isNotEmpty()) {
                item(key = "albums-header") {
                    SearchSectionHeader("Albums", albumResults.size)
                }
                items(albumResults, key = { "album-${it.id}" }) { album ->
                    AlbumCard(album = album, onClick = { })
                }
            }
            if (trackResults.isNotEmpty()) {
                item(key = "tracks-header") {
                    SearchSectionHeader("Tracks", trackResults.size)
                }
                items(trackResults, key = { "track-${it.id}" }) { track ->
                    LocalTrackRow(
                        track = track,
                        isFavourite = track.contentUri in favourites,
                        onToggleFavourite = { viewModel.toggleFavourite(track.contentUri) },
                        onClick = { playback.playNow(track.contentUri, track.title, track.artist) },
                        onPlayNext = { playback.playNext(track.contentUri, track.title, track.artist) },
                        onAddToQueue = { playback.addToQueue(track.contentUri, track.title, track.artist) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Artist, and album when there is one.
 *
 * The old row interpolated both unconditionally, so an untagged file rendered
 * "Unknown artist • " with a dangling separator — which is most of a fresh library
 * before the thorough scan has run.
 */
private fun trackSubtitle(track: LocalTrack): String =
    if (track.album.isBlank()) track.artist else "${track.artist} • ${track.album}"

@Composable
private fun SearchSectionHeader(title: String, count: Int) {
    Text(
        text = "$title · $count",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

private const val SEARCH_DEBOUNCE_MS = 220L
