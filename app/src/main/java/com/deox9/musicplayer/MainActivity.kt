// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.provider.MediaStore
import android.view.Choreographer
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.feature.library.AlbumsScreen
import com.deox9.musicplayer.feature.library.FavouritesScreen
import com.deox9.musicplayer.feature.library.FoldersScreen
import com.deox9.musicplayer.feature.library.GenresScreen
import com.deox9.musicplayer.feature.library.LibraryScreen
import com.deox9.musicplayer.feature.library.PlaylistsScreen
import com.deox9.musicplayer.feature.library.SuggestedScreen
import com.deox9.musicplayer.feature.player.ExpandedNowPlayingScreen
import com.deox9.musicplayer.feature.player.MiniPlayerBar
import com.deox9.musicplayer.feature.player.PlayerViewModel
import com.deox9.musicplayer.feature.player.QueueSidebar
import com.deox9.musicplayer.feature.settings.SettingsSheet
import com.deox9.musicplayer.feature.settings.SettingsViewModel
import com.deox9.musicplayer.library.LocalMusicRepository
import com.deox9.musicplayer.ui.AlbumSortOption
import com.deox9.musicplayer.ui.CollectionSortOption
import com.deox9.musicplayer.ui.SongSortOption
import com.deox9.musicplayer.ui.isDebugBuild
import com.deox9.musicplayer.ui.label
import com.deox9.musicplayer.web.WebPlayback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableStrictModeInDebug()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val appSettings by settingsViewModel.settings.collectAsState()

            MaterialTheme(
                colorScheme = if (appSettings.darkThemeEnabled) darkColorScheme() else lightColorScheme()
            ) {
                AppRoot()
            }
        }
    }
}

/**
 * Turns on StrictMode for debug builds.
 *
 * Disk and network work on the main thread is the usual cause of scroll jank in a
 * music app — this library still queries MediaStore synchronously in several
 * places. Logged rather than fatal so existing violations surface without making
 * the app unusable while they are worked through.
 */
private fun MainActivity.enableStrictModeInDebug() {
    if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return

    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyLog()
            .build()
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .penaltyLog()
            .build()
    )
}

private enum class RootMode { LocalDevice, WebPlayback }

private enum class LocalCategoryTab {
    Songs,
    Albums,
    Playlists,
    Folders,
    Genres,
    Suggested,
    Favourites
}

/**
 * Asks for POST_NOTIFICATIONS once on API 33+.
 *
 * Without the grant, the media notification never appears, which also costs the
 * lock-screen and Bluetooth transport controls the media session would otherwise
 * provide. The permission was declared in the manifest but never requested.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Playback still works without it; only the notification is lost. */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun AppRoot(viewModel: PlayerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(RootMode.LocalDevice) }
    var localTab by rememberSaveable { mutableStateOf(LocalCategoryTab.Songs) }
    var localSearchQuery by rememberSaveable { mutableStateOf("") }
    var appliedLocalSearchQuery by rememberSaveable { mutableStateOf("") }
    var webSearchQuery by rememberSaveable { mutableStateOf("") }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var songSortOption by rememberSaveable { mutableStateOf(SongSortOption.Title) }
    var albumSortOption by rememberSaveable { mutableStateOf(AlbumSortOption.Name) }
    var collectionSortOption by rememberSaveable { mutableStateOf(CollectionSortOption.Name) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var prevQueueSize by rememberSaveable { mutableStateOf(0) }
    var prevQueueSignature by rememberSaveable { mutableStateOf("") }
    var suppressAutoExpand by rememberSaveable { mutableStateOf(false) }
    var didInitQueueSnapshot by rememberSaveable { mutableStateOf(false) }
    var showPerfOverlay by rememberSaveable { mutableStateOf(false) }
    var webPlaybackView by remember { mutableStateOf<WebView?>(null) }
    var lastPausedWebForLocalUri by rememberSaveable { mutableStateOf("") }

    val songsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val albumsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val playlistsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val foldersListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val genresListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val suggestedListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val favouritesListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val playback = viewModel.playback
    val playbackState by playback.state.collectAsState()
    // Null means "nothing loaded", which is what the rest of the UI already
    // branches on. Everything else reads straight off the bound controller.
    val session = playbackState.takeIf { it.hasTrack }

    RequestNotificationPermission()

    LaunchedEffect(localSearchQuery) {
        delay(180)
        appliedLocalSearchQuery = localSearchQuery
    }

    DisposableEffect(Unit) {
        // Restoring the last session is the service's job now: it happens in
        // PlaybackService.onCreate, which runs when the MediaController binds.
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                LocalMusicRepository.invalidateCaches()
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                LocalMusicRepository.invalidateCaches()
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    // Auto-expand Now Playing when queue is replaced (not appended)
    LaunchedEffect(session?.updatedAtMs) {
        session?.let { currentSession ->
            if (currentSession.queue.isEmpty()) {
                prevQueueSize = 0
                prevQueueSignature = ""
                suppressAutoExpand = false
                didInitQueueSnapshot = false
                return@let
            }

            val currentQueueUris = currentSession.queue.map { it.uri }
            val currentSignature = currentQueueUris.joinToString("|")

            if (!didInitQueueSnapshot) {
                prevQueueSize = currentSession.queue.size
                prevQueueSignature = currentSignature
                didInitQueueSnapshot = true
                return@let
            }

            if (suppressAutoExpand) {
                prevQueueSize = currentSession.queue.size
                prevQueueSignature = currentSignature
                return@let
            }

            if (currentSession.queue.isNotEmpty()) {
                val currentQueueSize = currentSession.queue.size
                val isAppend =
                    prevQueueSignature.isNotBlank() &&
                        currentQueueSize >= prevQueueSize &&
                        currentQueueUris.take(prevQueueSize).joinToString("|") == prevQueueSignature
                val replacedQueue =
                    prevQueueSize > 0 &&
                        currentSignature != prevQueueSignature &&
                        !isAppend

                if (replacedQueue) {
                    showNowPlaying = true
                }

                prevQueueSize = currentQueueSize
                prevQueueSignature = currentSignature
            }
        }
    }

    // Keep web playback active across mode switches, but pause it once local playback starts.
    LaunchedEffect(session?.updatedAtMs) {
        val currentSession = session ?: return@LaunchedEffect
        val localTrackStarted =
            currentSession.isPlaying &&
                currentSession.uri.startsWith("content://") &&
                currentSession.uri != lastPausedWebForLocalUri

        if (localTrackStarted) {
            WebPlayback.pause(webPlaybackView)
            lastPausedWebForLocalUri = currentSession.uri
        }
    }

    if (showNowPlaying) {
        BackHandler {
            showNowPlaying = false
            suppressAutoExpand = true
        }
    }

    Scaffold(
        topBar = {
            if (!showNowPlaying) {
                // The app draws edge to edge (enforced from targetSdk 35), so the
                // header must inset itself past the status bar. Without this the
                // header sits underneath it and the status bar swallows taps —
                // which made the Settings button unreachable on-device.
                Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    AppHeader(
                        mode = mode,
                        localTab = localTab,
                        localSearchQuery = localSearchQuery,
                        webSearchQuery = webSearchQuery,
                        onLocalSearchChange = { localSearchQuery = it },
                        onWebSearchChange = { webSearchQuery = it },
                        showSortMenu = showSortMenu,
                        onShowSortMenuChange = { showSortMenu = it },
                        onOpenSettings = { showSettingsSheet = true },
                        songSortOption = songSortOption,
                        albumSortOption = albumSortOption,
                        onSongSortChange = {
                            songSortOption = it
                            showSortMenu = false
                        },
                        onAlbumSortChange = {
                            albumSortOption = it
                            showSortMenu = false
                        },
                        collectionSortOption = collectionSortOption,
                        onCollectionSortChange = {
                            collectionSortOption = it
                            showSortMenu = false
                        }
                    )
                }
            }
        },
        bottomBar = {
            // Likewise for the gesture bar, which otherwise overlaps the mini player.
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                if (!showNowPlaying) {
                    MiniPlayerBar(
                        session = session,
                        onExpand = { showNowPlaying = true },
                        onOpenQueue = { showQueueSheet = true }
                    )
                    NavigationBar {
                        NavigationBarItem(
                            selected = mode == RootMode.LocalDevice,
                            onClick = { mode = RootMode.LocalDevice },
                            label = { Text("Local/Device") },
                            icon = {}
                        )
                        if (WebPlayback.IS_AVAILABLE) {
                            NavigationBarItem(
                                selected = mode == RootMode.WebPlayback,
                                onClick = { mode = RootMode.WebPlayback },
                                label = { Text("Web") },
                                icon = {}
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (showNowPlaying) {
            ExpandedNowPlayingScreen(
                session = session,
                onMinimize = {
                    showNowPlaying = false
                    suppressAutoExpand = true
                },
                onOpenQueue = { showQueueSheet = true },
                onGoToArtist = { artistName ->
                    mode = RootMode.LocalDevice
                    localTab = LocalCategoryTab.Songs
                    localSearchQuery = artistName
                    appliedLocalSearchQuery = artistName
                    showNowPlaying = false
                    suppressAutoExpand = true
                },
                onViewAlbum = { albumName ->
                    mode = RootMode.LocalDevice
                    localTab = LocalCategoryTab.Albums
                    localSearchQuery = albumName
                    appliedLocalSearchQuery = albumName
                    showNowPlaying = false
                    suppressAutoExpand = true
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (WebPlayback.IS_AVAILABLE) {
                    WebPlayback.Screen(
                        searchQuery = webSearchQuery,
                        isVisible = mode == RootMode.WebPlayback,
                        onWebViewReady = { webPlaybackView = it }
                    )
                }

                if (mode == RootMode.LocalDevice) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LocalCategoryTabs(
                            selected = localTab,
                            onSelect = { localTab = it }
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            when (localTab) {
                                LocalCategoryTab.Songs -> LibraryScreen(
                                    searchQuery = appliedLocalSearchQuery,
                                    sortOption = songSortOption,
                                    listState = songsListState
                                )
                                LocalCategoryTab.Albums -> AlbumsScreen(
                                    searchQuery = appliedLocalSearchQuery,
                                    sortOption = albumSortOption,
                                    listState = albumsListState
                                )
                                LocalCategoryTab.Playlists -> PlaylistsScreen(
                                    searchQuery = appliedLocalSearchQuery,
                                    sortOption = collectionSortOption,
                                    listState = playlistsListState
                                )
                                LocalCategoryTab.Folders -> FoldersScreen(
                                    searchQuery = appliedLocalSearchQuery,
                                    sortOption = collectionSortOption,
                                    listState = foldersListState
                                )
                                LocalCategoryTab.Genres -> GenresScreen(
                                    searchQuery = appliedLocalSearchQuery,
                                    sortOption = collectionSortOption,
                                    listState = genresListState
                                )
                                LocalCategoryTab.Suggested -> SuggestedScreen(
                                    listState = suggestedListState
                                )
                                LocalCategoryTab.Favourites -> FavouritesScreen(
                                    searchQuery = appliedLocalSearchQuery,
                                    sortOption = songSortOption,
                                    listState = favouritesListState
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showQueueSheet) {
            QueueSidebar(
                queue = session?.queue.orEmpty(),
                currentIndex = session?.currentIndex ?: -1,
                onDismiss = { showQueueSheet = false }
            )
        }

        if (showSettingsSheet) {
            SettingsSheet(
                onDismiss = { showSettingsSheet = false },
                showPerfOverlay = showPerfOverlay,
                onShowPerfOverlayChange = { showPerfOverlay = it }
            )
        }

        if (isDebugBuild(context) && showPerfOverlay) {
            DebugPerformanceOverlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = 8.dp)
            )
        }
    }
}

@Composable
private fun AppHeader(
    mode: RootMode,
    localTab: LocalCategoryTab,
    localSearchQuery: String,
    webSearchQuery: String,
    onLocalSearchChange: (String) -> Unit,
    onWebSearchChange: (String) -> Unit,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    songSortOption: SongSortOption,
    albumSortOption: AlbumSortOption,
    collectionSortOption: CollectionSortOption,
    onSongSortChange: (SongSortOption) -> Unit,
    onAlbumSortChange: (AlbumSortOption) -> Unit,
    onCollectionSortChange: (CollectionSortOption) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Text(
                text = "Music Player",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { onShowSortMenuChange(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Sort and filter"
                )
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { onShowSortMenuChange(false) }
            ) {
                if (mode == RootMode.LocalDevice && localTab == LocalCategoryTab.Songs) {
                    SongSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (songSortOption == option) "✓ ${option.label()}" else option.label()
                                )
                            },
                            onClick = { onSongSortChange(option) }
                        )
                    }
                } else if (mode == RootMode.LocalDevice && localTab == LocalCategoryTab.Albums) {
                    AlbumSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (albumSortOption == option) "✓ ${option.label()}" else option.label()
                                )
                            },
                            onClick = { onAlbumSortChange(option) }
                        )
                    }
                } else if (
                    mode == RootMode.LocalDevice &&
                    (localTab == LocalCategoryTab.Playlists ||
                        localTab == LocalCategoryTab.Folders ||
                        localTab == LocalCategoryTab.Genres)
                ) {
                    CollectionSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (collectionSortOption == option) "✓ ${option.label()}" else option.label()
                                )
                            },
                            onClick = { onCollectionSortChange(option) }
                        )
                    }
                } else if (mode == RootMode.LocalDevice && localTab == LocalCategoryTab.Favourites) {
                    SongSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (songSortOption == option) "✓ ${option.label()}" else option.label()
                                )
                            },
                            onClick = { onSongSortChange(option) }
                        )
                    }
                } else {
                    DropdownMenuItem(
                        text = { Text("Sort options are not available for this tab yet") },
                        onClick = { onShowSortMenuChange(false) }
                    )
                }
            }

            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        }

        OutlinedTextField(
            value = if (mode == RootMode.LocalDevice) localSearchQuery else webSearchQuery,
            onValueChange = {
                if (mode == RootMode.LocalDevice) {
                    onLocalSearchChange(it)
                } else {
                    onWebSearchChange(it)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            singleLine = true,
            label = {
                Text(
                    if (mode == RootMode.LocalDevice) {
                        "Search ${localTab.label()}"
                    } else {
                        "Search Web"
                    }
                )
            }
        )

        HorizontalDivider()
    }
}

@Composable
private fun DebugPerformanceOverlay(modifier: Modifier = Modifier) {
    var fps by remember { mutableStateOf(0) }
    var jankFrames by remember { mutableStateOf(0) }
    var totalFrames by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val choreographer = Choreographer.getInstance()
        var lastFrameNs = 0L
        var windowStartNs = 0L
        var frameCountInWindow = 0
        var jankInWindow = 0

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (windowStartNs == 0L) {
                    windowStartNs = frameTimeNanos
                }

                if (lastFrameNs != 0L) {
                    val deltaMs = (frameTimeNanos - lastFrameNs) / 1_000_000.0
                    if (deltaMs > 24.0) {
                        jankInWindow += 1
                    }
                }

                lastFrameNs = frameTimeNanos
                frameCountInWindow += 1

                val windowDurationSec = (frameTimeNanos - windowStartNs) / 1_000_000_000.0
                if (windowDurationSec >= 1.0) {
                    fps = (frameCountInWindow / windowDurationSec).toInt()
                    jankFrames = jankInWindow
                    totalFrames = frameCountInWindow
                    windowStartNs = frameTimeNanos
                    frameCountInWindow = 0
                    jankInWindow = 0
                }

                choreographer.postFrameCallback(this)
            }
        }

        choreographer.postFrameCallback(callback)
        onDispose {
            choreographer.removeFrameCallback(callback)
        }
    }

    Box(modifier = modifier) {
        Text(
            text = "FPS: $fps  Jank: $jankFrames/$totalFrames",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun LocalCategoryTabs(
    selected: LocalCategoryTab,
    onSelect: (LocalCategoryTab) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(LocalCategoryTab.entries) { tab ->
            OutlinedButton(
                onClick = { onSelect(tab) }
            ) {
                Text(
                    text = if (selected == tab) {
                        "• ${tab.label()}"
                    } else {
                        tab.label()
                    }
                )
            }
        }
    }
    HorizontalDivider()
}

private fun LocalCategoryTab.label(): String = when (this) {
    LocalCategoryTab.Songs -> "Songs"
    LocalCategoryTab.Albums -> "Albums"
    LocalCategoryTab.Playlists -> "Playlists"
    LocalCategoryTab.Folders -> "Folders"
    LocalCategoryTab.Genres -> "Genres"
    LocalCategoryTab.Suggested -> "Suggested"
    LocalCategoryTab.Favourites -> "Favourites"
}
