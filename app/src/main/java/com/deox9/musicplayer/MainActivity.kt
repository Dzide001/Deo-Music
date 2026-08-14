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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.designsystem.DeoTheme
import com.deox9.musicplayer.designsystem.ThemeConfig
import com.deox9.musicplayer.designsystem.themeModeFrom
import com.deox9.musicplayer.feature.library.AlbumsScreen
import com.deox9.musicplayer.feature.library.ArtistsScreen
import com.deox9.musicplayer.feature.library.FavouritesScreen
import com.deox9.musicplayer.feature.library.FoldersScreen
import com.deox9.musicplayer.feature.library.GenresScreen
import com.deox9.musicplayer.feature.library.LibraryScreen
import com.deox9.musicplayer.feature.library.PlaylistsScreen
import com.deox9.musicplayer.feature.library.SearchScreen
import com.deox9.musicplayer.feature.library.SuggestedScreen
import com.deox9.musicplayer.feature.player.ExpandedNowPlayingScreen
import com.deox9.musicplayer.feature.player.MiniPlayerBar
import com.deox9.musicplayer.feature.player.PlayerViewModel
import com.deox9.musicplayer.feature.player.QueueSidebar
import com.deox9.musicplayer.feature.settings.OpenSourceLicensesScreen
import com.deox9.musicplayer.feature.settings.SettingsSheet
import com.deox9.musicplayer.feature.settings.SettingsViewModel
import com.deox9.musicplayer.library.LocalMusicRepository
import com.deox9.musicplayer.scanner.LibraryScanWorker
import com.deox9.musicplayer.ui.AlbumSortOption
import com.deox9.musicplayer.ui.CollectionSortOption
import com.deox9.musicplayer.ui.NavigationStyle
import com.deox9.musicplayer.ui.SongSortOption
import com.deox9.musicplayer.ui.isDebugBuild
import com.deox9.musicplayer.ui.label
import com.deox9.musicplayer.ui.rememberWindowLayout
import com.deox9.musicplayer.web.WebPlayback
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableStrictModeInDebug()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val appSettings by settingsViewModel.settings.collectAsState()

            DeoTheme(
                config = ThemeConfig(
                    mode = themeModeFrom(appSettings.themeMode, appSettings.darkThemeEnabled),
                    dynamicColor = appSettings.dynamicColorEnabled,
                    amoled = appSettings.amoledEnabled,
                    // Artwork-derived colour is wired in with the Now Playing
                    // rebuild, which is where the current cover actually lives.
                    seedFromArtwork = null,
                ),
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

/**
 * Top-level destinations.
 *
 * Replaces the Local/Web footer, which was a second navigation layer competing with
 * the category chips. Web exists only in the full flavour — [available] is what keeps
 * the FOSS build honest about having two destinations rather than three.
 */
private enum class RootDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Library("Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    Search("Search", Icons.Filled.Search, Icons.Outlined.Search),
    Web("Web", Icons.Filled.Language, Icons.Outlined.Language),
    ;

    val available: Boolean get() = this != Web || WebPlayback.IS_AVAILABLE
}

private enum class LocalCategoryTab {
    Songs,
    Albums,
    Artists,
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
    var destination by rememberSaveable { mutableStateOf(RootDestination.Library) }
    var localTab by rememberSaveable { mutableStateOf(LocalCategoryTab.Songs) }
    var localSearchQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var appliedLocalSearchQuery by rememberSaveable { mutableStateOf("") }
    var webSearchQuery by rememberSaveable { mutableStateOf("") }
    var showSortMenu by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showLicenses by rememberSaveable { mutableStateOf(false) }
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

    val listStates = rememberLibraryListStates()

    val playback = viewModel.playback
    val playbackState by playback.state.collectAsState()
    // Null means "nothing loaded", which is what the rest of the UI already
    // branches on. Everything else reads straight off the bound controller.
    val session = playbackState.takeIf { it.hasTrack }
    val snackbarHostState = remember { SnackbarHostState() }

    RequestNotificationPermission()

    LaunchedEffect(localSearchQuery) {
        delay(180)
        appliedLocalSearchQuery = localSearchQuery
    }

    DisposableEffect(Unit) {
        // Restoring the last session is the service's job now: it happens in
        // PlaybackService.onCreate, which runs when the MediaController binds.

        // Index on launch. Cheap when nothing changed, and it is what populates the
        // library the first time the app runs.
        LibraryScanWorker.enqueue(context)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                LocalMusicRepository.invalidateCaches()
                // A change means files were added, removed or retagged, so re-index.
                // The worker uses KEEP, so a burst during a large copy coalesces
                // into one scan rather than restarting it repeatedly.
                LibraryScanWorker.enqueue(context)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                LocalMusicRepository.invalidateCaches()
                LibraryScanWorker.enqueue(context)
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

    // A track can fail while the user is anywhere in the app — browsing the library,
    // on another destination, or with the screen off and the notification driving
    // playback. The Now Playing screen carries the same failure inline, but only the
    // snackbar reaches them wherever they actually are, so it names the track.
    //
    // Keyed on Unit rather than on the error, so that the service withdrawing it —
    // which happens the moment the auto-advance lands on a track that plays, often
    // well inside the snackbar's own duration — cancels this coroutine and cuts the
    // message off mid-display. Each failure is collected once and then shown for as
    // long as it takes to read, whatever the player does next.
    LaunchedEffect(Unit) {
        // Reads the delegated state inside the lambda, which is what makes this a
        // tracked snapshot read; a captured local would be frozen at first composition.
        snapshotFlow { playbackState.error }
            .filterNotNull()
            .distinctUntilChangedBy { it.id }
            .collect { error ->
                val track = error.trackTitle.ifBlank { "this track" }
                snackbarHostState.showSnackbar(
                    message = "Can't play $track — ${error.message}",
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
            }
    }

    if (showNowPlaying) {
        BackHandler {
            showNowPlaying = false
            suppressAutoExpand = true
        }
    }

    // A separate full-screen route rather than another branch inside the Scaffold
    // below: the licenses list needs its own scrolling app bar and has nothing in
    // common with the mini player / now-playing layout the Scaffold already juggles.
    if (showLicenses) {
        BackHandler { showLicenses = false }
        OpenSourceLicensesScreen(
            onBack = { showLicenses = false },
            aboutLibrariesRawResId = R.raw.aboutlibraries
        )
        return
    }

    // Reads the window the app was given, not the display: a phone-width split
    // window on a tablet has to be laid out like a phone.
    val windowLayout = rememberWindowLayout()
    val useRail = windowLayout.navigationStyle == NavigationStyle.Rail && !showNowPlaying

    Row(modifier = Modifier.fillMaxSize()) {
        if (useRail) {
            AppNavigationRail(
                selected = destination,
                onSelect = { destination = it },
            )
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!showNowPlaying) {
                    // The app draws edge to edge (enforced from targetSdk 35), so the
                    // header must inset itself past the status bar. Without this the
                    // header sits underneath it and the status bar swallows taps —
                    // which made the Settings button unreachable on-device.
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        AppHeader(
                            destination = destination,
                            localTab = localTab,
                            searchActive = searchActive,
                            onSearchActiveChange = { searchActive = it },
                            localSearchQuery = localSearchQuery,
                            webSearchQuery = webSearchQuery,
                            onLocalSearchChange = { localSearchQuery = it },
                            onWebSearchChange = { webSearchQuery = it },
                            onRescan = {
                                LibraryScanWorker.enqueue(context, thorough = true)
                            },
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
                        // The mini player stays along the bottom even with a rail: it is
                        // the width of it that makes the artwork and title readable, and
                        // a rail-width version would be a column of icons.
                        if (!useRail) {
                            AppNavigationBar(
                                selected = destination,
                                onSelect = { destination = it },
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
                        destination = RootDestination.Library
                        // The Artists tab exists now, so "go to artist" lands on the
                        // artist rather than on a song list filtered by their name.
                        localTab = LocalCategoryTab.Artists
                        localSearchQuery = artistName
                        appliedLocalSearchQuery = artistName
                        searchActive = true
                        showNowPlaying = false
                        suppressAutoExpand = true
                    },
                    onViewAlbum = { albumName ->
                        destination = RootDestination.Library
                        localTab = LocalCategoryTab.Albums
                        localSearchQuery = albumName
                        appliedLocalSearchQuery = albumName
                        searchActive = true
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
                        // Kept composed across destinations so the page and any playing
                        // media survive switching away and back.
                        WebPlayback.Screen(
                            searchQuery = webSearchQuery,
                            isVisible = destination == RootDestination.Web,
                            onWebViewReady = { webPlaybackView = it }
                        )
                    }

                    if (destination == RootDestination.Search) {
                        SearchScreen(listState = listStates.search)
                    }

                    if (destination == RootDestination.Library) {
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
                                        listState = listStates.songs
                                    )
                                    LocalCategoryTab.Albums -> AlbumsScreen(
                                        searchQuery = appliedLocalSearchQuery,
                                        sortOption = albumSortOption,
                                        listState = listStates.albums
                                    )
                                    LocalCategoryTab.Artists -> ArtistsScreen(
                                        searchQuery = appliedLocalSearchQuery,
                                        sortOption = collectionSortOption,
                                        listState = listStates.artists
                                    )
                                    LocalCategoryTab.Playlists -> PlaylistsScreen(
                                        searchQuery = appliedLocalSearchQuery,
                                        sortOption = collectionSortOption,
                                        listState = listStates.playlists
                                    )
                                    LocalCategoryTab.Folders -> FoldersScreen(
                                        searchQuery = appliedLocalSearchQuery,
                                        sortOption = collectionSortOption,
                                        listState = listStates.folders
                                    )
                                    LocalCategoryTab.Genres -> GenresScreen(
                                        searchQuery = appliedLocalSearchQuery,
                                        sortOption = collectionSortOption,
                                        listState = listStates.genres
                                    )
                                    LocalCategoryTab.Suggested -> SuggestedScreen(
                                        listState = listStates.suggested
                                    )
                                    LocalCategoryTab.Favourites -> FavouritesScreen(
                                        searchQuery = appliedLocalSearchQuery,
                                        sortOption = songSortOption,
                                        listState = listStates.favourites
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
                    onShowLicenses = {
                        showSettingsSheet = false
                        showLicenses = true
                    },
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
}

/**
 * Top-level navigation along the bottom, for a window a thumb can reach across.
 */
@Composable
private fun AppNavigationBar(
    selected: RootDestination,
    onSelect: (RootDestination) -> Unit,
) {
    NavigationBar {
        RootDestination.entries.filter { it.available }.forEach { item ->
            NavigationBarItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                label = { Text(item.label) },
                icon = { DestinationIcon(item, selected == item) },
            )
        }
    }
}

/**
 * The same destinations down the leading edge, from Medium width up.
 *
 * A bottom bar on a tablet spends the scarcest dimension — vertical space — on
 * controls that are nowhere near where the hands are. The rail insets itself because
 * it sits outside the Scaffold, so nothing else is padding it past the status bar or
 * a display cutout on the left edge.
 */
@Composable
private fun AppNavigationRail(
    selected: RootDestination,
    onSelect: (RootDestination) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
        ),
    ) {
        RootDestination.entries.filter { it.available }.forEach { item ->
            NavigationRailItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                label = { Text(item.label) },
                icon = { DestinationIcon(item, selected == item) },
            )
        }
    }
}

/** Filled when selected, outlined otherwise — a second cue beyond colour. */
@Composable
private fun DestinationIcon(destination: RootDestination, selected: Boolean) {
    Icon(
        imageVector = if (selected) destination.selectedIcon else destination.icon,
        contentDescription = null,
    )
}

@Composable
private fun AppHeader(
    destination: RootDestination,
    localTab: LocalCategoryTab,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    localSearchQuery: String,
    webSearchQuery: String,
    onLocalSearchChange: (String) -> Unit,
    onWebSearchChange: (String) -> Unit,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onRescan: () -> Unit,
    songSortOption: SongSortOption,
    albumSortOption: AlbumSortOption,
    collectionSortOption: CollectionSortOption,
    onSongSortChange: (SongSortOption) -> Unit,
    onAlbumSortChange: (AlbumSortOption) -> Unit,
    onCollectionSortChange: (CollectionSortOption) -> Unit
) {
    if (searchActive) {
        HeaderSearchField(
            destination = destination,
            localTab = localTab,
            localSearchQuery = localSearchQuery,
            webSearchQuery = webSearchQuery,
            onLocalSearchChange = onLocalSearchChange,
            onWebSearchChange = onWebSearchChange,
            onClose = { onSearchActiveChange(false) }
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The app's own name and logo used to sit here. On the one screen the user
        // is already looking at, in an app they chose to open, neither told them
        // anything — and together they cost a whole bar above the one that does.
        Text(
            text = destination.label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.weight(1f))

        // The filter is a button rather than a permanently open text field. The
        // field was ~90dp of chrome on every screen for something used
        // occasionally, and it pushed the list down by more than a row.
        if (destination != RootDestination.Search) {
            IconButton(onClick = { onSearchActiveChange(true) }) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Filter ${destination.label.lowercase()}"
                )
            }
        }

        if (destination == RootDestination.Library) {
            Box {
                IconButton(onClick = { onShowSortMenuChange(true) }) {
                    // Tune, not MoreVert: this opens sort options, and MoreVert
                    // already means "more options" two buttons along.
                    Icon(imageVector = Icons.Filled.Tune, contentDescription = "Sort")
                }
                SortMenu(
                    expanded = showSortMenu,
                    onDismiss = { onShowSortMenuChange(false) },
                    family = localTab.sortFamily(),
                    songSortOption = songSortOption,
                    albumSortOption = albumSortOption,
                    collectionSortOption = collectionSortOption,
                    onSongSortChange = onSongSortChange,
                    onAlbumSortChange = onAlbumSortChange,
                    onCollectionSortChange = onCollectionSortChange,
                )
            }
        }

        OverflowMenuButton(onOpenSettings = onOpenSettings, onRescan = onRescan)
    }
}

/**
 * The filter, shown only while it is being used.
 *
 * Replaces the whole app-bar row rather than appearing under it, so opening the
 * filter costs no height — which is the point of hiding it in the first place.
 */
@Composable
private fun HeaderSearchField(
    destination: RootDestination,
    localTab: LocalCategoryTab,
    localSearchQuery: String,
    webSearchQuery: String,
    onLocalSearchChange: (String) -> Unit,
    onWebSearchChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val isLibrary = destination == RootDestination.Library
    val value = if (isLibrary) localSearchQuery else webSearchQuery
    val focusRequester = remember { FocusRequester() }

    // Opened by a tap, so the keyboard should already be up; otherwise it takes a
    // second tap on the field that just appeared under the finger.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (isLibrary) onLocalSearchChange("") else onWebSearchChange("")
                onClose()
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close filter"
            )
        }
        TextField(
            value = value,
            onValueChange = { if (isLibrary) onLocalSearchChange(it) else onWebSearchChange(it) },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            singleLine = true,
            placeholder = {
                Text(
                    if (isLibrary) "Filter ${localTab.label().lowercase()}" else "Search the web"
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { if (isLibrary) onLocalSearchChange("") else onWebSearchChange("") }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            }
        )
    }
}

@Composable
private fun OverflowMenuButton(onOpenSettings: () -> Unit, onRescan: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Rescan library") },
                onClick = {
                    expanded = false
                    onRescan()
                }
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    expanded = false
                    onOpenSettings()
                }
            )
        }
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

/**
 * Category filter inside Library.
 *
 * A real tab row rather than a row of outlined buttons: the buttons gave no indicator
 * beyond a bullet glyph prefixed to the label, and a scrolling row of them read as
 * primary navigation when it is a filter. The scrollable variant keeps every category
 * reachable without the seven-chip horizontal scroller hiding half of them.
 */
@Composable
private fun LocalCategoryTabs(
    selected: LocalCategoryTab,
    onSelect: (LocalCategoryTab) -> Unit
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = LocalCategoryTab.entries.indexOf(selected),
        edgePadding = 12.dp,
        divider = { HorizontalDivider() },
    ) {
        LocalCategoryTab.entries.forEach { tab ->
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                text = { Text(tab.label()) },
            )
        }
    }
}

private fun LocalCategoryTab.label(): String = when (this) {
    LocalCategoryTab.Songs -> "Songs"
    LocalCategoryTab.Albums -> "Albums"
    LocalCategoryTab.Artists -> "Artists"
    LocalCategoryTab.Playlists -> "Playlists"
    LocalCategoryTab.Folders -> "Folders"
    LocalCategoryTab.Genres -> "Genres"
    LocalCategoryTab.Suggested -> "Suggested"
    LocalCategoryTab.Favourites -> "Favourites"
}

/** Which family of sort options a library tab offers, if any. */
private enum class SortFamily { Song, Album, Collection, None }

/**
 * Maps a tab to its sort options.
 *
 * Replaces a chain of `destination == Library && tab == X` conditions in the header,
 * one of which had grown complex enough for detekt to flag it. Exhaustive on the enum,
 * so adding a tab is a compile error here rather than a silent "no sort options".
 */
private fun LocalCategoryTab.sortFamily(): SortFamily = when (this) {
    LocalCategoryTab.Songs, LocalCategoryTab.Favourites -> SortFamily.Song
    LocalCategoryTab.Albums -> SortFamily.Album
    LocalCategoryTab.Artists,
    LocalCategoryTab.Playlists,
    LocalCategoryTab.Folders,
    LocalCategoryTab.Genres -> SortFamily.Collection
    LocalCategoryTab.Suggested -> SortFamily.None
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    family: SortFamily,
    songSortOption: SongSortOption,
    albumSortOption: AlbumSortOption,
    collectionSortOption: CollectionSortOption,
    onSongSortChange: (SongSortOption) -> Unit,
    onAlbumSortChange: (AlbumSortOption) -> Unit,
    onCollectionSortChange: (CollectionSortOption) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (family) {
            SortFamily.Song -> SortOptions(SongSortOption.entries, songSortOption, onSongSortChange) { it.label() }
            SortFamily.Album -> SortOptions(AlbumSortOption.entries, albumSortOption, onAlbumSortChange) { it.label() }
            SortFamily.Collection ->
                SortOptions(CollectionSortOption.entries, collectionSortOption, onCollectionSortChange) { it.label() }
            SortFamily.None -> DropdownMenuItem(
                text = { Text("This tab has no sort options") },
                onClick = onDismiss,
            )
        }
    }
}

/** One checkable row per option, so every sort family renders identically. */
@Composable
private fun <T> SortOptions(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
) {
    options.forEach { option ->
        DropdownMenuItem(
            text = { Text(if (option == selected) "\u2713 ${label(option)}" else label(option)) },
            onClick = { onSelect(option) },
        )
    }
}

/**
 * One scroll position per library list, kept while the app is open.
 *
 * Nine of them, which is why they are a holder rather than nine locals in the root
 * composable: they are one idea — "where each tab was left" — and spelling that out
 * as nine near-identical lines made the root longer without making it clearer.
 *
 * Saveable, so the positions survive rotation and process death rather than
 * snapping every list back to the top.
 */
@Stable
private class LibraryListStates(
    val songs: LazyListState,
    val albums: LazyListState,
    val artists: LazyListState,
    val playlists: LazyListState,
    val folders: LazyListState,
    val genres: LazyListState,
    val suggested: LazyListState,
    val favourites: LazyListState,
    val search: LazyListState,
)

@Composable
private fun rememberLibraryListStates(): LibraryListStates = LibraryListStates(
    songs = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    albums = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    artists = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    playlists = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    folders = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    genres = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    suggested = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    favourites = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    search = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
)
