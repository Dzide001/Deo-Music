package com.deox9.musicplayer

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.view.Choreographer
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.provider.MediaStore
import java.io.ByteArrayInputStream
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.deox9.musicplayer.library.LocalMusicRepository
import com.deox9.musicplayer.library.LocalTrack
import com.deox9.musicplayer.library.Album
import com.deox9.musicplayer.library.FavouritesRepository
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.library.GenreInfo
import com.deox9.musicplayer.library.FolderInfo
import com.deox9.musicplayer.library.RecommendationSignals
import com.deox9.musicplayer.library.RecommendationSignalsRepository
import com.deox9.musicplayer.lyrics.LyricsData
import com.deox9.musicplayer.lyrics.LyricsRepository
import com.deox9.musicplayer.player.PlaybackService
import com.deox9.musicplayer.player.storage.PlaybackSessionEntity
import com.deox9.musicplayer.player.storage.PlaybackSessionRepository
import com.deox9.musicplayer.player.storage.QueueItem
import com.deox9.musicplayer.settings.AppSettings
import com.deox9.musicplayer.settings.AppSettingsRepository
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsRepository = remember { AppSettingsRepository(this@MainActivity) }
            val appSettings by settingsRepository.observe().collectAsState(initial = AppSettings())

            MaterialTheme(
                colorScheme = if (appSettings.darkThemeEnabled) darkColorScheme() else lightColorScheme()
            ) {
                AppRoot()
            }
        }
    }
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
    var didSendRestoreIntent by rememberSaveable { mutableStateOf(false) }
    var showPerfOverlay by rememberSaveable { mutableStateOf(false) }

    val songsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val albumsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val playlistsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val foldersListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val genresListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val suggestedListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val favouritesListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val playbackSessionRepository = remember {
        PlaybackSessionRepository(context)
    }

    val session by playbackSessionRepository.observe().collectAsState(initial = null)

    LaunchedEffect(localSearchQuery) {
        delay(180)
        appliedLocalSearchQuery = localSearchQuery
    }

    DisposableEffect(Unit) {
        if (!didSendRestoreIntent) {
            val restoreIntent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_RESTORE_LAST
            }
            sendPlaybackIntent(context, restoreIntent)
            didSendRestoreIntent = true
        }

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
                when (mode) {
                    RootMode.LocalDevice -> {
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

                    RootMode.WebPlayback -> WebPlaybackScreen(searchQuery = webSearchQuery)
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
    val playbackSessionRepository = remember { PlaybackSessionRepository(context) }
    val session by playbackSessionRepository.observe().collectAsState(initial = null)
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
                            val intent = Intent(context, PlaybackService::class.java).apply {
                                action = PlaybackService.ACTION_PLAY_URI
                                putExtra(PlaybackService.EXTRA_URI, rec.track.contentUri)
                                putExtra(PlaybackService.EXTRA_TITLE, rec.track.title)
                                putExtra(PlaybackService.EXTRA_ARTIST, rec.track.artist)
                            }
                            sendPlaybackIntent(context, intent)
                        },
                        onPlayNext = {
                            val intent = Intent(context, PlaybackService::class.java).apply {
                                action = PlaybackService.ACTION_PLAY_NEXT
                                putExtra(PlaybackService.EXTRA_URI, rec.track.contentUri)
                                putExtra(PlaybackService.EXTRA_TITLE, rec.track.title)
                                putExtra(PlaybackService.EXTRA_ARTIST, rec.track.artist)
                            }
                            sendPlaybackIntent(context, intent)
                        },
                        onAddToQueue = {
                            val intent = Intent(context, PlaybackService::class.java).apply {
                                action = PlaybackService.ACTION_ADD_TO_QUEUE
                                putExtra(PlaybackService.EXTRA_URI, rec.track.contentUri)
                                putExtra(PlaybackService.EXTRA_TITLE, rec.track.title)
                                putExtra(PlaybackService.EXTRA_ARTIST, rec.track.artist)
                            }
                            sendPlaybackIntent(context, intent)
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
                    val playIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PLAY_URI
                        putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                        putExtra(PlaybackService.EXTRA_TITLE, track.title)
                        putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                    }
                    sendPlaybackIntent(context, playIntent)
                },
                onPlayNext = {
                    val nextIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PLAY_NEXT
                        putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                        putExtra(PlaybackService.EXTRA_TITLE, track.title)
                        putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                    }
                    sendPlaybackIntent(context, nextIntent)
                },
                onAddToQueue = {
                    val queueIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_ADD_TO_QUEUE
                        putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                        putExtra(PlaybackService.EXTRA_TITLE, track.title)
                        putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                    }
                    sendPlaybackIntent(context, queueIntent)
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
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_PLAY_URI
                            putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                            putExtra(PlaybackService.EXTRA_TITLE, track.title)
                            putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                        }
                        sendPlaybackIntent(context, intent)
                    },
                    onPlayNext = {
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_PLAY_NEXT
                            putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                            putExtra(PlaybackService.EXTRA_TITLE, track.title)
                            putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                        }
                        sendPlaybackIntent(context, intent)
                    },
                    onAddToQueue = {
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_ADD_TO_QUEUE
                            putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                            putExtra(PlaybackService.EXTRA_TITLE, track.title)
                            putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                        }
                        sendPlaybackIntent(context, intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    session: PlaybackSessionEntity?,
    onExpand: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val context = LocalContext.current
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
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_SKIP_PREV
                    }
                    sendPlaybackIntent(context, intent)
                }
            ) {
                Text("Prev")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                enabled = session != null,
                onClick = {
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_TOGGLE_PLAY_PAUSE
                    }
                    sendPlaybackIntent(context, intent)
                }
            ) {
                Text(if (isPlaying) "Pause" else "Play")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                enabled = session != null,
                onClick = {
                    val seekIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_SEEK_TO
                        putExtra(
                            PlaybackService.EXTRA_SEEK_TO_MS,
                            ((session?.positionMs ?: 0L) + 10_000L).coerceAtLeast(0L)
                        )
                    }
                    sendPlaybackIntent(context, seekIntent)
                }
            ) {
                Text("+10s")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                enabled = session != null,
                onClick = {
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_SKIP_NEXT
                    }
                    sendPlaybackIntent(context, intent)
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

@Composable
private fun ExpandedNowPlayingScreen(
    session: PlaybackSessionEntity?,
    onMinimize: () -> Unit,
    onOpenQueue: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onViewAlbum: (String) -> Unit
) {
    val context = LocalContext.current
    val favRepo = remember { FavouritesRepository(context) }
    val localRepo = remember { LocalMusicRepository(context) }
    val lyricsRepository = remember { LyricsRepository(context) }
    val settingsRepository = remember { AppSettingsRepository(context) }
    val appSettings by settingsRepository.observe().collectAsState(initial = AppSettings())
    val currentTrackUri = session?.uri ?: ""
    val isFavourite by favRepo.observe().collectAsState(initial = emptySet())
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

    LaunchedEffect(showLyricsPanel, currentTrackUri, session?.title, session?.artist, session?.album, session?.durationMs) {
        if (!showLyricsPanel || currentTrackUri.isBlank()) {
            return@LaunchedEffect
        }
        lyricsLoading = true
        fetchedLyrics = withContext(Dispatchers.IO) {
            lyricsRepository.getLyrics(
                trackKey = currentTrackUri,
                title = session?.title.orEmpty(),
                artist = session?.artist.orEmpty(),
                album = session?.album.orEmpty(),
                durationMs = session?.durationMs ?: 0L
            )
        }
        lyricsLoading = false
    }

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
                    DropdownMenuItem(
                        text = { Text("Add to playlist") },
                        onClick = {
                            showMoreMenu = false
                            if (session?.uri.isNullOrBlank()) return@DropdownMenuItem
                            scope.launch {
                                availablePlaylists = withContext(Dispatchers.IO) {
                                    localRepo.getPlaylists()
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (session?.albumArtUri?.isNotBlank() == true) {
                AsyncImage(
                    model = session.albumArtUri,
                    contentDescription = "Album art for ${session.title}",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("♪", style = MaterialTheme.typography.displayLarge)
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
                val seekIntent = Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_SEEK_TO
                    putExtra(PlaybackService.EXTRA_SEEK_TO_MS, sliderPosition.toLong())
                }
                sendPlaybackIntent(context, seekIntent)
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
                    val volumeIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_SET_PLAYER_VOLUME
                        putExtra(PlaybackService.EXTRA_PLAYER_VOLUME, newVolume)
                    }
                    sendPlaybackIntent(context, volumeIntent)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                enabled = session != null,
                onClick = {
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_SKIP_PREV
                    }
                    sendPlaybackIntent(context, intent)
                },
                modifier = Modifier.weight(1f)
            ) { Text("Prev") }
            OutlinedButton(
                enabled = session != null,
                onClick = {
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_TOGGLE_SHUFFLE
                    }
                    sendPlaybackIntent(context, intent)
                },
                modifier = Modifier.weight(1f)
            ) { Text(if (shuffleEnabled) "🔀 ON" else "🔀") }
            OutlinedButton(
                enabled = session != null,
                onClick = {
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_TOGGLE_PLAY_PAUSE
                    }
                    sendPlaybackIntent(context, intent)
                },
                modifier = Modifier.weight(1f)
            ) { Text(if (isPlaying) "Pause" else "Play") }
            OutlinedButton(
                enabled = session != null,
                onClick = {
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_CYCLE_REPEAT
                    }
                    sendPlaybackIntent(context, intent)
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
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_SKIP_NEXT
                    }
                    sendPlaybackIntent(context, intent)
                },
                modifier = Modifier.weight(1f)
            ) { Text("Next") }
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
                        favRepo.toggle(currentTrackUri)
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
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onMinimize
        ) {
            Text("Minimize player (or swipe down)")
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
                                                localRepo.addTrackToPlaylist(playlist.id, trackUri)
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
                                    localRepo.createPlaylist(newPlaylistName.trim())
                                }
                                val added = if (playlistId != null) {
                                    withContext(Dispatchers.IO) {
                                        localRepo.addTrackToPlaylist(playlistId, trackUri)
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
                                    localRepo.deleteTrack(trackUri)
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
                                        settingsRepository.setCrossfadeEnabled(enabled)
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
                                        settingsRepository.setGaplessEnabled(enabled)
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
                                        settingsRepository.setReplayGainEnabled(enabled)
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
                                        settingsRepository.setReplayGainDb(value)
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
                                        settingsRepository.setEqEnabled(enabled)
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
                                        settingsRepository.setEqEnabled(true)
                                        settingsRepository.setEqBandLevels(List(10) { 0 })
                                    }
                                }
                            ) { Text("Flat") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setEqEnabled(true)
                                        settingsRepository.setEqBandLevels(
                                            listOf(350, 300, 220, 120, 40, -40, -100, -180, -220, -260)
                                        )
                                    }
                                }
                            ) { Text("Bass") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setEqEnabled(true)
                                        settingsRepository.setEqBandLevels(
                                            listOf(-200, -120, -40, 140, 260, 260, 140, -20, -120, -200)
                                        )
                                    }
                                }
                            ) { Text("Vocal") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        settingsRepository.setEqEnabled(true)
                                        settingsRepository.setEqBandLevels(
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
                                        settingsRepository.setEqEnabled(true)
                                        settingsRepository.setEqBandLevels(updated)
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

private fun currentSyncedLyricLine(lines: List<com.deox9.musicplayer.lyrics.SyncedLyricLine>, positionMs: Long): String? {
    if (lines.isEmpty()) return null
    return lines.lastOrNull { it.timeMs <= positionMs }?.text ?: lines.firstOrNull()?.text
}

@Composable
private fun QueueSidebar(
    queue: List<QueueItem>,
    currentIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
        val swapIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SWAP_QUEUE_ITEMS
            putExtra(PlaybackService.EXTRA_FROM_INDEX, fromIndex)
            putExtra(PlaybackService.EXTRA_TO_INDEX, toIndex)
        }
        sendPlaybackIntent(context, swapIntent)
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
                                    val playNowIntent = Intent(context, PlaybackService::class.java).apply {
                                        action = PlaybackService.ACTION_PLAY_QUEUE_INDEX
                                        putExtra(PlaybackService.EXTRA_QUEUE_INDEX, index)
                                    }
                                    sendPlaybackIntent(context, playNowIntent)
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
                                        val playNowIntent = Intent(context, PlaybackService::class.java).apply {
                                            action = PlaybackService.ACTION_PLAY_QUEUE_INDEX
                                            putExtra(PlaybackService.EXTRA_QUEUE_INDEX, index)
                                        }
                                        sendPlaybackIntent(context, playNowIntent)
                                    }
                                ) {
                                    Text("Now")
                                }
                                if (index > 0) {
                                    TextButton(
                                        onClick = {
                                            val moveIntent = Intent(context, PlaybackService::class.java).apply {
                                                action = PlaybackService.ACTION_MOVE_QUEUE_ITEM
                                                putExtra(PlaybackService.EXTRA_FROM_INDEX, index)
                                                putExtra(PlaybackService.EXTRA_TO_INDEX, 0)
                                            }
                                            sendPlaybackIntent(context, moveIntent)
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
                                            val moveIntent = Intent(context, PlaybackService::class.java).apply {
                                                action = PlaybackService.ACTION_MOVE_QUEUE_ITEM
                                                putExtra(PlaybackService.EXTRA_FROM_INDEX, index)
                                                putExtra(
                                                    PlaybackService.EXTRA_TO_INDEX,
                                                    reorderableQueue.lastIndex
                                                )
                                            }
                                            sendPlaybackIntent(context, moveIntent)
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
                                        val removeIntent = Intent(context, PlaybackService::class.java).apply {
                                            action = PlaybackService.ACTION_REMOVE_QUEUE_INDEX
                                            putExtra(PlaybackService.EXTRA_QUEUE_INDEX, index)
                                        }
                                        sendPlaybackIntent(context, removeIntent)
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
                        val clearIntent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_CLEAR_QUEUE
                        }
                        sendPlaybackIntent(context, clearIntent)
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

@Composable
private fun LibraryScreen(
    searchQuery: String = "",
    sortOption: SongSortOption = SongSortOption.Title,
    listState: LazyListState
) {
    val context = LocalContext.current
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
                    val playIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PLAY_URI
                        putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                        putExtra(PlaybackService.EXTRA_TITLE, track.title)
                        putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                    }
                    sendPlaybackIntent(context, playIntent)
                },
                onPlayNext = {
                    val nextIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PLAY_NEXT
                        putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                        putExtra(PlaybackService.EXTRA_TITLE, track.title)
                        putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                    }
                    sendPlaybackIntent(context, nextIntent)
                },
                onAddToQueue = {
                    val queueIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_ADD_TO_QUEUE
                        putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                        putExtra(PlaybackService.EXTRA_TITLE, track.title)
                        putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                    }
                    sendPlaybackIntent(context, queueIntent)
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

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
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
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_PLAY_URI
                            putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                            putExtra(PlaybackService.EXTRA_TITLE, track.title)
                            putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                        }
                        sendPlaybackIntent(context, intent)
                    },
                    onPlayNext = {
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_PLAY_NEXT
                            putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                            putExtra(PlaybackService.EXTRA_TITLE, track.title)
                            putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                        }
                        sendPlaybackIntent(context, intent)
                    },
                    onAddToQueue = {
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_ADD_TO_QUEUE
                            putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                            putExtra(PlaybackService.EXTRA_TITLE, track.title)
                            putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                        }
                        sendPlaybackIntent(context, intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun WebPlaybackScreen(searchQuery: String = "") {
    val context = LocalContext.current
    val settingsRepository = remember { AppSettingsRepository(context) }
    val appSettings by settingsRepository.observe().collectAsState(initial = AppSettings())
    var blockedRequestCount by rememberSaveable { mutableStateOf(0) }
    var lastBlockedHost by rememberSaveable { mutableStateOf<String?>(null) }
    var lastLoadError by rememberSaveable { mutableStateOf<String?>(null) }
    var fallbackTriggered by rememberSaveable { mutableStateOf(false) }
    var webViewSavedState by rememberSaveable { mutableStateOf<Bundle?>(null) }
    var restoredFromSavedState by rememberSaveable { mutableStateOf(false) }
    var currentWebUrl by rememberSaveable { mutableStateOf("") }

    val fallbackHomeUrl = remember(appSettings.webHomeUrl) {
        normalizeWebUrl(appSettings.webHomeUrl) ?: AppSettingsRepository.DEFAULT_WEB_HOME
    }

    val webView = remember {
        WebView(context).apply {
            webViewClient = HardenedWebViewClient(
                onBlocked = { blockedUrl ->
                    blockedRequestCount += 1
                    lastBlockedHost = Uri.parse(blockedUrl).host ?: blockedUrl
                },
                onMainFrameError = { code, description ->
                    lastLoadError = "Web load failed ($code): $description"
                    if (!fallbackTriggered) {
                        fallbackTriggered = true
                        loadUrl(fallbackHomeUrl)
                    }
                },
                onPageSuccess = { pageUrl ->
                    lastLoadError = null
                    if (!pageUrl.isNullOrBlank()) {
                        currentWebUrl = pageUrl
                    }
                    injectYouTubeAdSkipper(webView = this)
                }
            )
            webChromeClient = WebChromeClient()
            settings.javaScriptEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            val restored = webViewSavedState?.let { state ->
                restoreState(state)
            }
            if (restored == null) {
                val bootUrl = when {
                    currentWebUrl.isNotBlank() -> currentWebUrl
                    else -> AppSettingsRepository.DEFAULT_WEB_HOME
                }
                loadUrl(bootUrl)
            } else {
                restoredFromSavedState = true
            }
        }
    }

    LaunchedEffect(appSettings.webHomeUrl) {
        val homeUrl = normalizeWebUrl(appSettings.webHomeUrl) ?: AppSettingsRepository.DEFAULT_WEB_HOME
        val currentUrl = webView.url.orEmpty()
        if (
            searchQuery.trim().length < 2 &&
            !restoredFromSavedState &&
            (currentUrl.isBlank() || currentUrl == "about:blank")
        ) {
            fallbackTriggered = false
            webView.loadUrl(homeUrl)
        }
    }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.length >= 2) {
            delay(350)
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val target = "https://m.youtube.com/results?search_query=$encoded"
            val current = webView.url.orEmpty()
            if (current.contains("m.youtube.com/results") && current.contains("search_query=$encoded")) {
                return@LaunchedEffect
            }
            fallbackTriggered = false
            webView.loadUrl(target)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val state = Bundle()
            webView.saveState(state)
            webViewSavedState = state
            webView.stopLoading()
            webView.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { webView }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Ad-filter blocks: $blockedRequestCount",
                style = MaterialTheme.typography.labelMedium
            )
            if (!lastBlockedHost.isNullOrBlank()) {
                Text(
                    text = "Last blocked: $lastBlockedHost",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!lastLoadError.isNullOrBlank()) {
                Text(
                    text = lastLoadError.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(
                    onClick = {
                        fallbackTriggered = false
                        webView.reload()
                    }
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

private fun sendPlaybackIntent(context: Context, intent: Intent) {
    context.startService(intent)
}

private class HardenedWebViewClient(
    private val onBlocked: (String) -> Unit,
    private val onMainFrameError: (Int, String) -> Unit,
    private val onPageSuccess: (String?) -> Unit
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return super.shouldInterceptRequest(view, request)
        return if (shouldBlockWebResource(url.toString())) {
            onBlocked(url.toString())
            emptyBlockedResponse()
        } else {
            super.shouldInterceptRequest(view, request)
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: android.webkit.WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            onMainFrameError(error?.errorCode ?: -1, error?.description?.toString().orEmpty())
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageSuccess(url)
    }
}

private fun injectYouTubeAdSkipper(webView: WebView) {
        val script = """
                (function() {
                    if (window.__deoAdSkipInstalled) return;
                    window.__deoAdSkipInstalled = true;

                    function clickIfVisible(el) {
                        if (!el) return false;
                        const style = window.getComputedStyle(el);
                        if (style && style.display !== 'none' && style.visibility !== 'hidden') {
                            try { el.click(); return true; } catch (e) { return false; }
                        }
                        return false;
                    }

                    function skipAds() {
                        // Desktop YouTube controls
                        clickIfVisible(document.querySelector('.ytp-ad-skip-button'));
                        clickIfVisible(document.querySelector('.ytp-ad-skip-button-modern'));
                        clickIfVisible(document.querySelector('.ytp-ad-overlay-close-button'));

                        // Mobile YouTube controls
                        clickIfVisible(document.querySelector('.ytmAdSkipButton'));
                        clickIfVisible(document.querySelector('button[aria-label*="Skip" i]'));
                        clickIfVisible(document.querySelector('button[aria-label*="Close" i]'));

                        // If ad markers are active, jump ad segment and mute quickly
                        const isAd = !!document.querySelector('.ad-showing, .ytp-ad-player-overlay, .video-ads, .ytm-ad-interrupting');
                        const video = document.querySelector('video');
                        if (isAd && video) {
                            try {
                                video.muted = true;
                                if (Number.isFinite(video.duration) && video.duration > 0) {
                                    video.currentTime = Math.max(video.duration - 0.25, 0);
                                } else {
                                    video.playbackRate = 16;
                                }
                            } catch (e) {}
                        } else if (video) {
                            try { video.playbackRate = 1; } catch (e) {}
                        }
                    }

                    window.__deoAdSkipTimer = setInterval(skipAds, 400);
                    document.addEventListener('visibilitychange', skipAds, { passive: true });
                    skipAds();
                })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
}

private fun shouldBlockWebResource(rawUrl: String): Boolean {
    val lower = rawUrl.lowercase()
    val uri = try { android.net.Uri.parse(rawUrl) } catch (_: Exception) { null }
    val host = uri?.host?.lowercase() ?: return false

    // ========== PRIMARY AD & TRACKER NETWORKS ==========
    val adNetworks = listOf(
        // Google Ad Infrastructure
        "doubleclick.net", "pagead2.googlesyndication.com", "adservice.google",
        "googlesyndication.com", "googletagservices.com", "googletagmanager.com",
        
        // YouTube Ad Delivery
        "ads.youtube.com", "yt.be", "adx.g.doubleclick.net",
        
        // Third-party ad networks
        "ad.doubleclick.net", "ads4.google.com", "mads.google.com",
        "csi.gstatic.com", // Google client error/CSI tracking
        
        // Analytics & Telemetry
        "google-analytics.com", "analytics.google.com", "www.googletagmanager.com",
        "stats.g.doubleclick.net", "analytics.google.com",
        
        // Additional Tracking Services
        "tpc.googlesyndication.com", "www.gstatic.com/generate_204",
        "bat.bing.com", "c.bing.com",
        
        // YouTube specific tracking
        "youtube.com/api", "yt-video-upload", "youtubei.googleapis.com"
    )

    if (adNetworks.any { host.contains(it) }) return true

    // ========== PATH PATTERNS (YouTube-focused routes) ==========
    val blockedPaths = listOf(
        // YouTube ad delivery endpoints
        "/api/stats/ads", "/get_ads", "/api/ads", "/js/ads/",
        "/pagead/", "/gvt1/ads", "/ads?", "/ad_break", "ad_break=",
        
        // YouTube logging & telemetry
        "/log_event", "/api/stats", "/youtubei/v1/log_event",
        "/youtubei/v1/log", "/api/v1/log", "/reporting/", "tracking=",
        
        // Ad format & unit detection
        "adformat=", "adunit=", "instream_ad", "yt_ad", "ad_request",
        
        // Engagement metrics for ads
        "/api/v1/survey", "/ptracking", "pcs/active", "ping?",
        
        // Beacon tracking
        "beacon.scorecardresearch.com", "sb.scorecardresearch.com",
        
        // Redirect & measurement
        "/r/", "/t/", "doubleclick_tracking"
    )

    if (blockedPaths.any { lower.contains(it) }) return true

    // ========== QUERY PARAMETER PATTERNS ==========
    val query = uri?.query?.lowercase() ?: ""
    val adQueryParams = listOf(
        "ad_", "ads_", "adunit", "adformat", "ad_type", "ad_client",
        "google_afc", "google_ad", "google_gd", "tracking", "utm_",
        "fbclid", "gclid", "msclkid", "igshid"
    )

    if (adQueryParams.any { query.contains(it) }) return true

    // ========== FILE TYPE BLOCKING (video ads, banners) ==========
    val blockedExtensions = listOf(
        // Video ads formats
        "vmap.xml", // VAST/VMAP (video ad XML)
        ".vpaid", ".vast", "ads.js"
    )

    if (blockedExtensions.any { lower.endsWith(it) }) return true

    return false
}

private fun emptyBlockedResponse(): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        "utf-8",
        204,
        "No Content",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0))
    )
}

private fun normalizeWebUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return try {
        android.net.Uri.parse(withScheme)
        withScheme
    } catch (_: Exception) {
        null
    }
}
