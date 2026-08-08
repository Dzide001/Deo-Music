// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.deox9.musicplayer.feature.player.ExpandedNowPlayingScreen
import com.deox9.musicplayer.feature.player.MiniPlayerBar
import com.deox9.musicplayer.feature.player.QueueSidebar
import com.deox9.musicplayer.library.Album
import com.deox9.musicplayer.library.FavouritesRepository
import com.deox9.musicplayer.library.FolderInfo
import com.deox9.musicplayer.library.GenreInfo
import com.deox9.musicplayer.library.LocalMusicRepository
import com.deox9.musicplayer.library.LocalTrack
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.library.RecommendationSignals
import com.deox9.musicplayer.library.RecommendationSignalsRepository
import com.deox9.musicplayer.player.LocalPlaybackConnection
import com.deox9.musicplayer.player.ProvidePlaybackConnection
import com.deox9.musicplayer.player.rememberPlaybackConnection
import com.deox9.musicplayer.settings.AppSettings
import com.deox9.musicplayer.settings.AppSettingsRepository
import com.deox9.musicplayer.ui.formatDuration
import com.deox9.musicplayer.web.WebPlayback
import com.deox9.musicplayer.web.normalizeWebUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableStrictModeInDebug()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsRepository = remember { AppSettingsRepository(this@MainActivity) }
            val appSettings by settingsRepository.observe().collectAsState(initial = AppSettings())

            MaterialTheme(
                colorScheme = if (appSettings.darkThemeEnabled) darkColorScheme() else lightColorScheme()
            ) {
                ProvidePlaybackConnection(rememberPlaybackConnection()) {
                    AppRoot()
                }
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
private enum class SongSortOption { Title, Artist, Album, Duration }
private enum class AlbumSortOption { Name, Artist, TrackCount }
private enum class CollectionSortOption { Name, TrackCount }

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
private fun AppRoot() {
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

    val playback = LocalPlaybackConnection.current
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
        },
        bottomBar = {
            Column {
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

private fun SongSortOption.label(): String = when (this) {
    SongSortOption.Title -> "Title"
    SongSortOption.Artist -> "Artist"
    SongSortOption.Album -> "Album"
    SongSortOption.Duration -> "Duration"
}

private fun AlbumSortOption.label(): String = when (this) {
    AlbumSortOption.Name -> "Name"
    AlbumSortOption.Artist -> "Artist"
    AlbumSortOption.TrackCount -> "Track count"
}

private fun CollectionSortOption.label(): String = when (this) {
    CollectionSortOption.Name -> "Name"
    CollectionSortOption.TrackCount -> "Track count"
}

private fun isDebugBuild(context: Context): Boolean {
    return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsSheet(
    onDismiss: () -> Unit,
    showPerfOverlay: Boolean,
    onShowPerfOverlayChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { AppSettingsRepository(context) }
    val settings by settingsRepository.observe().collectAsState(initial = AppSettings())
    var webHomeInput by remember(settings.webHomeUrl) { mutableStateOf(settings.webHomeUrl) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Web home URL",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = webHomeInput,
                onValueChange = { webHomeInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("https://...") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val normalized = normalizeWebUrl(webHomeInput)
                        if (normalized != null) {
                            scope.launch { settingsRepository.setWebHomeUrl(normalized) }
                            webHomeInput = normalized
                        }
                    }
                ) {
                    Text("Save URL")
                }
                OutlinedButton(onClick = { webHomeInput = settings.webHomeUrl }) {
                    Text("Revert")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Playback",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingToggleRow(
                title = "Gapless playback",
                checked = settings.gaplessEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setGaplessEnabled(enabled) }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingToggleRow(
                title = "Crossfade (preview)",
                checked = settings.crossfadeEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setCrossfadeEnabled(enabled) }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Library and appearance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingToggleRow(
                title = "Enable suggestions tab content",
                checked = settings.suggestionsEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setSuggestionsEnabled(enabled) }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingToggleRow(
                title = "Prefer dark theme (saved)",
                checked = settings.darkThemeEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setDarkThemeEnabled(enabled) }
                }
            )

            if (isDebugBuild(context)) {
                Spacer(modifier = Modifier.height(8.dp))
                SettingToggleRow(
                    title = "Show performance overlay (debug)",
                    checked = showPerfOverlay,
                    onCheckedChange = onShowPerfOverlayChange
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch { settingsRepository.resetDefaults() }
                }
            ) {
                Text("Reset defaults")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDismiss) {
                Text("Close")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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

@Composable
private fun PlaylistsScreen(
    searchQuery: String = "",
    sortOption: CollectionSortOption = CollectionSortOption.Name,
    listState: LazyListState
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<PlaylistInfo?>(null) }
    val playlists by produceState<List<PlaylistInfo>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            LocalMusicRepository(context).getPlaylists()
        }
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
private fun FoldersScreen(
    searchQuery: String = "",
    sortOption: CollectionSortOption = CollectionSortOption.Name,
    listState: LazyListState
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<FolderInfo?>(null) }
    val folders by produceState<List<FolderInfo>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            LocalMusicRepository(context).getFolders()
        }
    }

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
private fun GenresScreen(
    searchQuery: String = "",
    sortOption: CollectionSortOption = CollectionSortOption.Name,
    listState: LazyListState
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<GenreInfo?>(null) }
    val genres by produceState<List<GenreInfo>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            LocalMusicRepository(context).getGenres()
        }
    }

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
private fun SuggestedScreen(
    listState: LazyListState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { AppSettingsRepository(context) }
    val appSettings by settingsRepository.observe().collectAsState(initial = AppSettings())
    val playback = LocalPlaybackConnection.current
    val playbackState by playback.state.collectAsState()
    val session = playbackState.takeIf { it.hasTrack }
    val favRepo = remember { FavouritesRepository(context) }
    val favourites by favRepo.observe().collectAsState(initial = emptySet())
    val recommendationSignalsRepository = remember { RecommendationSignalsRepository(context) }
    val recommendationSignals by recommendationSignalsRepository.observe().collectAsState(
        initial = RecommendationSignals()
    )
    var refreshNonce by remember { mutableStateOf(0) }

    if (!appSettings.suggestionsEnabled) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text("Suggestions are disabled in settings.")
        }
        return
    }

    val tracks by produceState<List<LocalTrack>>(initialValue = emptyList(), key1 = appSettings.suggestionsEnabled) {
        value = withContext(Dispatchers.IO) {
            LocalMusicRepository(context).getTracks(limit = 1500)
        }
    }

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
                            scope.launch { favRepo.toggle(rec.track.contentUri) }
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
                                    recommendationSignalsRepository.setLiked(rec.track.contentUri, liked = true)
                                }
                            }
                        ) {
                            Text("Like")
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    recommendationSignalsRepository.setHidden(rec.track.contentUri, hidden = true)
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
private fun FavouritesScreen(
    searchQuery: String = "",
    sortOption: SongSortOption = SongSortOption.Title,
    listState: LazyListState
) {
    val context = LocalContext.current
    val playback = LocalPlaybackConnection.current
    val favRepo = remember { FavouritesRepository(context) }
    val favourites by favRepo.observe().collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    val tracks by produceState<List<LocalTrack>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            LocalMusicRepository(context).getTracks(limit = 3000)
        }
    }

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
                    scope.launch { favRepo.toggle(track.contentUri) }
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
    playlist: PlaylistInfo,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tracks by produceState<List<LocalTrack>>(initialValue = emptyList(), key1 = playlist.id) {
        value = withContext(Dispatchers.IO) {
            LocalMusicRepository(context).getTracksByPlaylist(playlist.id)
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
    genre: GenreInfo,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tracks by produceState<List<LocalTrack>>(initialValue = emptyList(), key1 = genre.id) {
        value = withContext(Dispatchers.IO) {
            LocalMusicRepository(context).getTracksByGenre(genre.id)
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
    folder: FolderInfo,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tracks by produceState<List<LocalTrack>>(initialValue = emptyList(), key1 = folder.path) {
        value = withContext(Dispatchers.IO) {
            LocalMusicRepository(context).getTracksByFolder(folder.path)
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
    title: String,
    tracks: List<LocalTrack>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playback = LocalPlaybackConnection.current
    val favRepo = remember { FavouritesRepository(context) }
    val favourites by favRepo.observe().collectAsState(initial = emptySet())
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
                        scope.launch { favRepo.toggle(track.contentUri) }
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
private fun LibraryScreen(
    searchQuery: String = "",
    sortOption: SongSortOption = SongSortOption.Title,
    listState: LazyListState
) {
    val context = LocalContext.current
    val playback = LocalPlaybackConnection.current
    val favRepo = remember { FavouritesRepository(context) }
    val favourites by favRepo.observe().collectAsState(initial = emptySet())
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

    val tracks by produceState<List<LocalTrack>>(
        initialValue = emptyList(),
        key1 = hasPermission
    ) {
        value = if (!hasPermission) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                LocalMusicRepository(context).getTracks(limit = 1500)
            }
        }
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
                    scope.launch { favRepo.toggle(track.contentUri) }
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
private fun AlbumsScreen(
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

    val albums by produceState<List<Album>>(
        initialValue = emptyList(),
        key1 = hasPermission
    ) {
        value = if (!hasPermission) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                LocalMusicRepository(context).getAlbums()
            }
        }
    }

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
private fun AlbumDetailScreen(album: Album, onBack: () -> Unit) {
    val context = LocalContext.current
    val playback = LocalPlaybackConnection.current
    val favRepo = remember { FavouritesRepository(context) }
    val favourites by favRepo.observe().collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    val tracks by produceState<List<LocalTrack>>(
        initialValue = emptyList(),
        key1 = album.id
    ) {
        value = withContext(Dispatchers.IO) {
            LocalMusicRepository(context).getTracksByAlbum(album.id)
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
                        scope.launch { favRepo.toggle(track.contentUri) }
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

