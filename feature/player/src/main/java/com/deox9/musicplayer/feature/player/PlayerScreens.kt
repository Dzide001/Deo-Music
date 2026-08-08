// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.lyrics.LyricsData
import com.deox9.musicplayer.player.PlaybackState
import com.deox9.musicplayer.player.QueueEntry
import com.deox9.musicplayer.ui.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

@Composable
fun MiniPlayerBar(
    viewModel: PlayerViewModel = hiltViewModel(),
    session: PlaybackState?,
    onExpand: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val context = LocalContext.current
    val playback = viewModel.playback
    val title = session?.title ?: "Nothing playing"
    val artist = session?.artist ?: "Select a track from Library"
    val isPlaying = session?.isPlaying ?: false

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = artist,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            OutlinedButton(
                enabled = session != null,
                onClick = {
                    playback.skipPrevious()
                }
            ) {
                Text("Prev")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                enabled = session != null,
                onClick = {
                    playback.togglePlayPause()
                }
            ) {
                Text(if (isPlaying) "Pause" else "Play")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                enabled = session != null,
                onClick = {
                    playback.seekTo(((session?.positionMs ?: 0L) + 10_000L).coerceAtLeast(0L))
                }
            ) {
                Text("+10s")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                enabled = session != null,
                onClick = {
                    playback.skipNext()
                }
            ) {
                Text("Next")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                enabled = session != null,
                onClick = onOpenQueue
            ) {
                Text("Queue")
            }
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandedNowPlayingScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    session: PlaybackState?,
    onMinimize: () -> Unit,
    onOpenQueue: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onViewAlbum: (String) -> Unit
) {
    val context = LocalContext.current
    val playback = viewModel.playback
    val appSettings by viewModel.settings.collectAsState()
    val currentTrackUri = session?.uri ?: ""
    val isFavourite by viewModel.favourites.collectAsState()
    val currentIsFavourite = currentTrackUri in isFavourite
    val scope = rememberCoroutineScope()
    var showLyricsPanel by remember { mutableStateOf(false) }
    var showAudioSettings by remember { mutableStateOf(false) }
    var showEqPanel by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    val lyricCredits by produceState<String?>(initialValue = null, key1 = currentTrackUri) {
        value = if (currentTrackUri.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                extractLyricCredits(context, currentTrackUri)
            }
        }
    }

    val title = session?.title ?: "Nothing playing"
    val artist = session?.artist ?: "Choose a track from Library"
    val isPlaying = session?.isPlaying ?: false
    val durationMs = (session?.durationMs ?: 0L).coerceAtLeast(1L)
    var sliderPosition by remember(session?.updatedAtMs) {
        mutableStateOf((session?.positionMs ?: 0L).coerceIn(0L, durationMs).toFloat())
    }
    var playerVolume by remember { mutableStateOf(session?.playerVolume ?: 1f) }
    val shuffleEnabled = session?.shuffleEnabled ?: false
    val repeatMode = session?.repeatMode ?: 0
    val isCompactWidth = LocalConfiguration.current.screenWidthDp < 600
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var availablePlaylists by remember { mutableStateOf<List<PlaylistInfo>>(emptyList()) }
    var fetchedLyrics by remember { mutableStateOf<LyricsData?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(session?.positionMs, session?.durationMs) {
        sliderPosition = (session?.positionMs ?: 0L).coerceIn(0L, durationMs).toFloat()
    }

    LaunchedEffect(currentTrackUri, session?.title, session?.artist, session?.album, session?.durationMs) {
        if (currentTrackUri.isBlank()) {
            fetchedLyrics = null
            lyricsLoading = false
            return@LaunchedEffect
        }
        lyricsLoading = true
        fetchedLyrics = withContext(Dispatchers.IO) {
            viewModel.lyricsFor(
                trackKey = currentTrackUri,
                title = session?.title.orEmpty(),
                artist = session?.artist.orEmpty(),
                album = session?.album.orEmpty(),
                durationMs = session?.durationMs ?: 0L
            )
        }
        lyricsLoading = false
    }

    val nowPlayingPagerState = rememberPagerState(pageCount = { 2 })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY > 100f) {
                            onMinimize()
                        }
                        dragOffsetY = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dragOffsetY += dragAmount
                }
            }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMinimize) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Minimize"
                )
            }
            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Box {
                IconButton(onClick = { showMoreMenu = !showMoreMenu }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options"
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    // onOpenQueue was plumbed into this screen but never wired to a
                    // control, so the queue was unreachable from the expanded player.
                    DropdownMenuItem(
                        text = { Text("Show queue") },
                        onClick = {
                            showMoreMenu = false
                            onOpenQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        onClick = {
                            showMoreMenu = false
                            if (session?.uri.isNullOrBlank()) return@DropdownMenuItem
                            scope.launch {
                                availablePlaylists = withContext(Dispatchers.IO) {
                                    viewModel.playlists()
                                }
                                showAddToPlaylistDialog = true
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Go to artist") },
                        onClick = {
                            showMoreMenu = false
                            val artistName = session?.artist.orEmpty().trim()
                            if (artistName.isNotBlank()) {
                                onGoToArtist(artistName)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("View album") },
                        onClick = {
                            showMoreMenu = false
                            val albumName = session?.album.orEmpty().trim()
                            if (albumName.isNotBlank()) {
                                onViewAlbum(albumName)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            showMoreMenu = false
                            val shareText = buildString {
                                append("Now playing: ${session?.title.orEmpty()}")
                                if (!session?.artist.isNullOrBlank()) {
                                    append(" by ${session?.artist.orEmpty()}")
                                }
                                if (!session?.uri.isNullOrBlank()) {
                                    append("\n${session?.uri.orEmpty()}")
                                }
                            }
                            runCatching {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share track")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                    if (session?.uri?.startsWith("content://") == true) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMoreMenu = false
                                showDeleteConfirmDialog = true
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        TabRow(selectedTabIndex = nowPlayingPagerState.currentPage) {
            Tab(
                selected = nowPlayingPagerState.currentPage == 0,
                onClick = { scope.launch { nowPlayingPagerState.animateScrollToPage(0) } },
                text = { Text("Artwork") }
            )
            Tab(
                selected = nowPlayingPagerState.currentPage == 1,
                onClick = { scope.launch { nowPlayingPagerState.animateScrollToPage(1) } },
                text = { Text("Synced Lyrics") }
            )
        }
        HorizontalPager(
            state = nowPlayingPagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
        ) { page ->
            if (page == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (session?.albumArtUri?.isNotBlank() == true) {
                        AsyncImage(
                            model = session.albumArtUri,
                            contentDescription = "Album art for ${session.title}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("♪", style = MaterialTheme.typography.displayLarge)
                    }
                }
            } else {
                val lyrics = fetchedLyrics
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        lyricsLoading -> Text("Loading synced lyrics...")
                        lyrics != null && lyrics.syncedLines.isNotEmpty() -> {
                            val activeLine = currentSyncedLyricLine(
                                lines = lyrics.syncedLines,
                                positionMs = session?.positionMs ?: 0L
                            )
                            if (!activeLine.isNullOrBlank()) {
                                Text(
                                    text = activeLine,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                HorizontalDivider()
                            }
                            lyrics.syncedLines.forEach { line ->
                                val isActive = line.text == activeLine
                                Text(
                                    text = line.text,
                                    style = if (isActive) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                        lyrics != null && lyrics.plainLyrics.isNotBlank() -> {
                            Text(text = lyrics.plainLyrics, style = MaterialTheme.typography.bodyMedium)
                        }
                        else -> {
                            Text(
                                text = "No synced lyrics available for this track yet.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = artist, style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(16.dp))
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                playback.seekTo(sliderPosition.toLong())
            },
            valueRange = 0f..durationMs.toFloat(),
            enabled = session != null
        )
        Text(
            text = "${formatDuration(sliderPosition.toLong())} / ${formatDuration(durationMs)}",
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text("Volume", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🔇",
                modifier = Modifier.size(20.dp)
            )
            Slider(
                value = playerVolume,
                onValueChange = { newVolume ->
                    playerVolume = newVolume
                    playback.setVolume(newVolume)
                },
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                enabled = session != null
            )
            Text(
                text = "🔊",
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (isCompactWidth) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconActionButton(
                    modifier = Modifier.weight(1f),
                    enabled = session != null,
                    symbol = "⏮",
                    contentDescription = "Previous",
                    onClick = {
                        playback.skipPrevious()
                    }
                )
                IconActionButton(
                    modifier = Modifier.weight(1f),
                    enabled = session != null,
                    symbol = if (shuffleEnabled) "🔀" else "↺",
                    contentDescription = "Shuffle",
                    selected = shuffleEnabled,
                    onClick = {
                        playback.toggleShuffle()
                    }
                )
                IconActionButton(
                    modifier = Modifier.weight(1f),
                    enabled = session != null,
                    symbol = if (isPlaying) "⏸" else "▶",
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    selected = isPlaying,
                    onClick = {
                        playback.togglePlayPause()
                    }
                )
                IconActionButton(
                    modifier = Modifier.weight(1f),
                    enabled = session != null,
                    symbol = when (repeatMode) {
                        0 -> "🔁"
                        1 -> "①"
                        else -> "∞"
                    },
                    contentDescription = "Repeat",
                    selected = repeatMode != 0,
                    onClick = {
                        playback.cycleRepeat()
                    }
                )
                IconActionButton(
                    modifier = Modifier.weight(1f),
                    enabled = session != null,
                    symbol = "⏭",
                    contentDescription = "Next",
                    onClick = {
                        playback.skipNext()
                    }
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    enabled = session != null,
                    onClick = {
                        playback.skipPrevious()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Prev") }
                OutlinedButton(
                    enabled = session != null,
                    onClick = {
                        playback.toggleShuffle()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (shuffleEnabled) "🔀 ON" else "🔀") }
                OutlinedButton(
                    enabled = session != null,
                    onClick = {
                        playback.togglePlayPause()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (isPlaying) "Pause" else "Play") }
                OutlinedButton(
                    enabled = session != null,
                    onClick = {
                        playback.cycleRepeat()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when (repeatMode) {
                            0 -> "🔁"
                            1 -> "🔁₁"
                            else -> "🔁∞"
                        }
                    )
                }
                OutlinedButton(
                    enabled = session != null,
                    onClick = {
                        playback.skipNext()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Next") }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                modifier = Modifier.weight(1f),
                enabled = currentTrackUri.isNotBlank(),
                onClick = {
                    scope.launch {
                        viewModel.toggleFavourite(currentTrackUri)
                    }
                }
            ) {
                Icon(
                    imageVector = if (currentIsFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (currentIsFavourite) "Remove from Favourites" else "Add to Favourites",
                    tint = if (currentIsFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    showLyricsPanel = true
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Lyrics"
                )
            }
            IconButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    showAudioSettings = true
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Audio Settings"
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onMinimize) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Minimize"
                )
            }
        }

        if (showAddToPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showAddToPlaylistDialog = false },
                title = { Text("Add to playlist") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (availablePlaylists.isEmpty()) {
                            Text("No playlists found. Create one first.")
                        } else {
                            availablePlaylists.forEach { playlist ->
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        val trackUri = session?.uri.orEmpty()
                                        if (trackUri.isBlank()) return@OutlinedButton
                                        scope.launch {
                                            val added = withContext(Dispatchers.IO) {
                                                viewModel.addTrackToPlaylist(playlist.id, trackUri)
                                            }
                                            Toast
                                                .makeText(
                                                    context,
                                                    if (added) "Added to ${playlist.name}" else "Could not add to playlist",
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                            showAddToPlaylistDialog = false
                                        }
                                    }
                                ) {
                                    Text(playlist.name)
                                }
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddToPlaylistDialog = false
                        showCreatePlaylistDialog = true
                    }) {
                        Text("Create new")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddToPlaylistDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                title = { Text("Create playlist") },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist name") },
                        singleLine = true
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylistDialog = false }) {
                        Text("Cancel")
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = newPlaylistName.isNotBlank(),
                        onClick = {
                            val trackUri = session?.uri.orEmpty()
                            if (trackUri.isBlank()) return@TextButton
                            scope.launch {
                                val playlistId = withContext(Dispatchers.IO) {
                                    viewModel.createPlaylist(newPlaylistName.trim())
                                }
                                val added = if (playlistId != null) {
                                    withContext(Dispatchers.IO) {
                                        viewModel.addTrackToPlaylist(playlistId, trackUri)
                                    }
                                } else {
                                    false
                                }
                                Toast
                                    .makeText(
                                        context,
                                        if (added) "Playlist created and track added" else "Could not create playlist",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                                showCreatePlaylistDialog = false
                                newPlaylistName = ""
                            }
                        }
                    ) {
                        Text("Create")
                    }
                }
            )
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete track") },
                text = { Text("This will delete the local audio file from your device.") },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val trackUri = session?.uri.orEmpty()
                            if (trackUri.isBlank()) return@TextButton
                            scope.launch {
                                val deleted = withContext(Dispatchers.IO) {
                                    viewModel.deleteTrack(trackUri)
                                }
                                Toast
                                    .makeText(
                                        context,
                                        if (deleted) "Track deleted" else "Could not delete track",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                                showDeleteConfirmDialog = false
                                if (deleted) {
                                    onMinimize()
                                }
                            }
                        }
                    ) {
                        Text("Delete")
                    }
                }
            )
        }

        if (showLyricsPanel) {
            AlertDialog(
                onDismissRequest = { showLyricsPanel = false },
                title = { Text("Lyrics") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (session == null) {
                            Text("Start playback to open lyrics.")
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = session.artist,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                HorizontalDivider()

                                if (lyricsLoading) {
                                    Text("Fetching lyrics...")
                                }

                                val lyrics = fetchedLyrics
                                if (lyrics != null && lyrics.syncedLines.isNotEmpty()) {
                                    val activeLine = currentSyncedLyricLine(
                                        lines = lyrics.syncedLines,
                                        positionMs = session.positionMs
                                    )
                                    Text(
                                        text = "Synced lyrics${if (lyrics.cached) " (cached)" else ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (!activeLine.isNullOrBlank()) {
                                        Text(
                                            text = activeLine,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    lyrics.syncedLines.forEach { line ->
                                        Text(
                                            text = line.text,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                } else if (lyrics != null && lyrics.plainLyrics.isNotBlank()) {
                                    Text(
                                        text = "Lyrics${if (lyrics.cached) " (cached)" else ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = lyrics.plainLyrics,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    Text(
                                        text = lyricCredits ?: "No lyrics found yet. You can search web or try again later.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                },
                dismissButton = {
                    if (session != null) {
                        TextButton(
                            onClick = {
                                val query = "${session.title} ${session.artist} lyrics"
                                val url = "https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                }
                            }
                        ) {
                            Text("Search Web")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLyricsPanel = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showAudioSettings) {
            AlertDialog(
                onDismissRequest = { showAudioSettings = false },
                title = { Text("Audio Settings") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Crossfade")
                            Switch(
                                checked = appSettings.crossfadeEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        viewModel.setCrossfadeEnabled(enabled)
                                    }
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Gapless")
                            Switch(
                                checked = appSettings.gaplessEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        viewModel.setGaplessEnabled(enabled)
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Replay gain")
                            Switch(
                                checked = appSettings.replayGainEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        viewModel.setReplayGainEnabled(enabled)
                                    }
                                }
                            )
                        }

                        if (appSettings.replayGainEnabled) {
                            Text(
                                text = "Replay gain: ${appSettings.replayGainDb.toInt()} dB",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Slider(
                                value = appSettings.replayGainDb,
                                onValueChange = { value ->
                                    scope.launch {
                                        viewModel.setReplayGainDb(value)
                                    }
                                },
                                valueRange = -18f..0f
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Equalizer")
                            Switch(
                                checked = appSettings.eqEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        viewModel.setEqEnabled(enabled)
                                    }
                                }
                            )
                        }
                        TextButton(onClick = { showEqPanel = true }) {
                            Text("Open Equalizer")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioSettings = false }) {
                        Text("Done")
                    }
                }
            )
        }

        if (showEqPanel) {
            AlertDialog(
                onDismissRequest = { showEqPanel = false },
                title = { Text("Equalizer (10-band)") },
                text = {
                    val levels = appSettings.eqBandLevels
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.setEqEnabled(true)
                                        viewModel.setEqBandLevels(List(10) { 0 })
                                    }
                                }
                            ) { Text("Flat") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.setEqEnabled(true)
                                        viewModel.setEqBandLevels(
                                            listOf(350, 300, 220, 120, 40, -40, -100, -180, -220, -260)
                                        )
                                    }
                                }
                            ) { Text("Bass") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.setEqEnabled(true)
                                        viewModel.setEqBandLevels(
                                            listOf(-200, -120, -40, 140, 260, 260, 140, -20, -120, -200)
                                        )
                                    }
                                }
                            ) { Text("Vocal") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.setEqEnabled(true)
                                        viewModel.setEqBandLevels(
                                            listOf(-260, -220, -160, -80, 40, 140, 240, 320, 380, 430)
                                        )
                                    }
                                }
                            ) { Text("Treble") }
                        }

                        for (index in 0 until 10) {
                            val bandValue = levels.getOrElse(index) { 0 }
                            Text(
                                text = "Band ${index + 1}: ${"%.1f".format(bandValue / 100f)} dB",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Slider(
                                value = bandValue.toFloat(),
                                onValueChange = { newValue ->
                                    val updated = levels.toMutableList().apply {
                                        this[index] = newValue.toInt().coerceIn(-1500, 1500)
                                    }
                                    scope.launch {
                                        viewModel.setEqEnabled(true)
                                        viewModel.setEqBandLevels(updated)
                                    }
                                },
                                valueRange = -1500f..1500f
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEqPanel = false }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}

private fun extractLyricCredits(context: Context, trackUri: String): String? {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.parse(trackUri))
        val lyricist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
        val composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
        val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
        retriever.release()

        val lines = buildList {
            if (!lyricist.isNullOrBlank()) add("Lyricist: $lyricist")
            if (!composer.isNullOrBlank()) add("Composer: $composer")
            if (!author.isNullOrBlank()) add("Author: $author")
        }

        if (lines.isEmpty()) null else lines.joinToString("\n")
    }.getOrNull()
}

@Composable
private fun IconActionButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    symbol: String,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    OutlinedButton(
        // The label is a bare glyph, so without this every transport control in the
        // expanded player was unlabelled for TalkBack: contentDescription was
        // accepted as a parameter and then dropped. Applied on the button and the
        // glyph marked decorative, so the control is announced once.
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
        enabled = enabled,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Text(
            text = symbol,
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun currentSyncedLyricLine(lines: List<com.deox9.musicplayer.lyrics.SyncedLyricLine>, positionMs: Long): String? {
    if (lines.isEmpty()) return null
    return lines.lastOrNull { it.timeMs <= positionMs }?.text ?: lines.firstOrNull()?.text
}

@Composable
fun QueueSidebar(
    viewModel: PlayerViewModel = hiltViewModel(),
    queue: List<QueueEntry>,
    currentIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playback = viewModel.playback
    var reorderableQueue by remember { mutableStateOf(queue) }
    val rowHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(queue) {
        reorderableQueue = queue
        if (draggedIndex != null && draggedIndex !in queue.indices) {
            draggedIndex = null
            dragOffsetY = 0f
        }
    }

    fun swapQueueItems(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in reorderableQueue.indices || toIndex !in reorderableQueue.indices || fromIndex == toIndex) {
            return
        }
        playback.swapQueueItems(fromIndex, toIndex)
        reorderableQueue = reorderableQueue.toMutableList().apply {
            val temp = this[fromIndex]
            this[fromIndex] = this[toIndex]
            this[toIndex] = temp
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onDismiss)
        )

        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
            Text(
                text = "Upcoming tracks",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Tip: long-press and drag a row to reorder.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (reorderableQueue.isEmpty()) {
                Text("Queue is empty.")
            } else {
                LazyColumn {
                    items(reorderableQueue.size, key = { index -> "${reorderableQueue[index].uri}-$index" }) { index ->
                        val item = reorderableQueue[index]
                        val isDragged = draggedIndex == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isDragged) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .pointerInput(reorderableQueue, index) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedIndex = index
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggedIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val activeIndex = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                            dragOffsetY += dragAmount.y

                                            var workingIndex = activeIndex
                                            while (dragOffsetY >= rowHeightPx && workingIndex < reorderableQueue.lastIndex) {
                                                swapQueueItems(workingIndex, workingIndex + 1)
                                                workingIndex += 1
                                                dragOffsetY -= rowHeightPx
                                            }
                                            while (dragOffsetY <= -rowHeightPx && workingIndex > 0) {
                                                swapQueueItems(workingIndex, workingIndex - 1)
                                                workingIndex -= 1
                                                dragOffsetY += rowHeightPx
                                            }

                                            draggedIndex = workingIndex
                                        }
                                    )
                                }
                                .clickable {
                                    playback.playQueueIndex(index)
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (index == currentIndex) "▶ ${item.title}" else item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (index == currentIndex) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = item.artist,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        playback.playQueueIndex(index)
                                    }
                                ) {
                                    Text("Now")
                                }
                                if (index > 0) {
                                    TextButton(
                                        onClick = {
                                            playback.moveQueueItem(index, 0)
                                            reorderableQueue = reorderableQueue.toMutableList().apply {
                                                val moved = removeAt(index)
                                                add(0, moved)
                                            }
                                        }
                                    ) {
                                        Text("⇤")
                                    }
                                }
                                if (index > 0) {
                                    TextButton(
                                        onClick = {
                                            swapQueueItems(index, index - 1)
                                        }
                                    ) {
                                        Text("↑")
                                    }
                                }
                                if (index < reorderableQueue.size - 1) {
                                    TextButton(
                                        onClick = {
                                            swapQueueItems(index, index + 1)
                                        }
                                    ) {
                                        Text("↓")
                                    }
                                }
                                if (index < reorderableQueue.size - 1) {
                                    TextButton(
                                        onClick = {
                                            playback.moveQueueItem(index, reorderableQueue.lastIndex)
                                            reorderableQueue = reorderableQueue.toMutableList().apply {
                                                val moved = removeAt(index)
                                                add(moved)
                                            }
                                        }
                                    ) {
                                        Text("⇥")
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        playback.removeQueueIndex(index)
                                        reorderableQueue = reorderableQueue.toMutableList().apply { removeAt(index) }
                                    }
                                ) {
                                    Text("✕")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        playback.clearQueue()
                        reorderableQueue = emptyList()
                    }
                ) {
                    Text("Clear queue")
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
