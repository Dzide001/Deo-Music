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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.deox9.musicplayer.library.Album
import com.deox9.musicplayer.library.FolderInfo
import com.deox9.musicplayer.library.GenreInfo
import com.deox9.musicplayer.library.LocalTrack
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.scanner.LibraryScanWorker
import com.deox9.musicplayer.ui.AlbumSortOption
import com.deox9.musicplayer.ui.CollectionSortOption
import com.deox9.musicplayer.ui.SongSortOption
import com.deox9.musicplayer.ui.formatDuration
import kotlinx.coroutines.Dispatchers
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
    // Still MediaStore: nothing imports the user's existing playlists into the
    // schema yet, and reading an empty table would look like data loss.
    val playlists by produceState<List<PlaylistInfo>>(initialValue = emptyList()) {
        value = viewModel.playlists()
    }

    if (selected != null) {
        PlaylistDetailScreen(
            playlist = selected!!,
            onBack = { selected = null }
        )
        return
    }

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
                    .clickable { selected = playlist }
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

    if (selected != null) {
        FolderDetailScreen(
            folder = selected!!,
            onBack = { selected = null }
        )
        return
    }

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
                    .clickable { selected = folder }
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

    if (selected != null) {
        GenreDetailScreen(
            genre = selected!!,
            onBack = { selected = null }
        )
        return
    }

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
                    .clickable { selected = genre }
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
private fun CollectionTrackListScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    title: String,
    tracks: List<LocalTrack>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playback = viewModel.playback
    val favourites by viewModel.favourites.collectAsState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
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
    LaunchedEffect(hasPermission) {
        if (hasPermission) LibraryScanWorker.enqueue(context)
    }

    if (!hasPermission) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Allow audio access to load your local music library.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { permissionLauncher.launch(audioPermission) }) {
                Text("Grant permission")
            }
        }
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (onToggleFavourite != null) {
            TextButton(onClick = onToggleFavourite) {
                Text(if (isFavourite) "★" else "☆")
            }
        }
        TextButton(onClick = onAddToQueue) {
            Text("Queue")
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Play next") },
                onClick = {
                    onPlayNext?.invoke() ?: onAddToQueue()
                    showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                onClick = {
                    onAddToQueue()
                    showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (isFavourite) "Remove favourite" else "Add to favourite") },
                onClick = {
                    onToggleFavourite?.invoke()
                    showContextMenu = false
                },
                enabled = onToggleFavourite != null
            )
            DropdownMenuItem(
                text = { Text("View details") },
                onClick = {
                    showDetails = true
                    showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { showContextMenu = false },
                enabled = false
            )
        }

        if (showDetails) {
            AlertDialog(
                onDismissRequest = { showDetails = false },
                confirmButton = {
                    TextButton(onClick = { showDetails = false }) {
                        Text("Close")
                    }
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
    }
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

    if (selectedAlbum != null) {
        AlbumDetailScreen(
            album = selectedAlbum!!,
            onBack = { selectedAlbum = null }
        )
        return
    }

    if (!hasPermission) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Allow audio access to load your music library.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { permissionLauncher.launch(audioPermission) }) {
                Text("Grant permission")
            }
        }
        return
    }

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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(filteredAlbums, key = { it.id }) { album ->
            AlbumCard(
                album = album,
                onClick = { selectedAlbum = album }
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
            AsyncImage(
                model = album.artworkUri,
                contentDescription = "Album art for ${album.title}",
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
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
