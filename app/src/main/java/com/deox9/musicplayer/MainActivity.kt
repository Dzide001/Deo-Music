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
import androidx.compose.foundation.layout.PaddingValues
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
import com.deox9.musicplayer.feature.library.ListeningStatsScreen
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
import com.deox9.musicplayer.player.PlaybackState
import com.deox9.musicplayer.scanner.LibraryScanWorker
import com.deox9.musicplayer.ui.AlbumSortOption
import com.deox9.musicplayer.ui.CollectionSortOption
import com.deox9.musicplayer.ui.LibraryTab
import com.deox9.musicplayer.ui.NavigationStyle
import com.deox9.musicplayer.ui.QueueExpansion
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
    var localTab by rememberSaveable { mutableStateOf(LibraryTab.Songs) }
    val search = rememberSearchFields()
    val sortMenu = rememberSortMenu()
    var fullScreenRoute by rememberSaveable { mutableStateOf<FullScreenRoute?>(null) }
    val sheets = rememberAppSheets(onOpenRoute = { fullScreenRoute = it })
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    // Not rememberSaveable: the tracker's whole job is telling a queue the listener
    // just chose from one restored on launch, and restoring its state across process
    // death would make every cold start look like a deliberate change.
    val queueExpansion = remember { QueueExpansion() }
    var webPlaybackView by remember { mutableStateOf<WebView?>(null) }
    var lastPausedWebForLocalUri by rememberSaveable { mutableStateOf("") }

    val listStates = rememberLibraryListStates()

    val playback = viewModel.playback
    val playbackState by playback.state.collectAsState()
    // Null means "nothing loaded", which is what the rest of the UI already
    // branches on. Everything else reads straight off the bound controller.
    val session = playbackState.takeIf { it.hasTrack }
    val rootSettings by viewModel.settings.collectAsState()
    val visibleTabs = rootSettings.tabs.visible

    // A tab that has just been hidden must not stay selected, or the library shows
    // content for something no longer in the strip and nothing looks selected.
    LaunchedEffect(visibleTabs) {
        if (localTab !in visibleTabs) localTab = visibleTabs.first()
    }
    val snackbarHostState = remember { SnackbarHostState() }

    RequestNotificationPermission()

    RescanWhenMediaChanges()

    // Opens the player when the queue is replaced, not when it is added to. The
    // rule itself lives in QueueExpansion, where it is tested.
    LaunchedEffect(session?.updatedAtMs) {
        val uris = session?.queue?.map { it.uri }.orEmpty()
        if (queueExpansion.shouldExpand(uris)) showNowPlaying = true
    }

    // Keep web playback active across mode switches, but pause it once local playback starts.
    lastPausedWebForLocalUri = pauseWebWhenLocalPlaybackStarts(
        session = session,
        webView = webPlaybackView,
        alreadyPausedFor = lastPausedWebForLocalUri,
    )

    // A track can fail while the user is anywhere in the app — browsing the library,
    // on another destination, or with the screen off and the notification driving
    // playback. The Now Playing screen carries the same failure inline, but only the
    // snackbar reaches them wherever they actually are, so it names the track.
    //
    AnnouncePlaybackErrors(playbackState, snackbarHostState)

    if (showNowPlaying) {
        BackHandler {
            showNowPlaying = false
            queueExpansion.suppress(true)
        }
    }

    fullScreenRoute?.let { route ->
        FullScreenDestination(route, onBack = { fullScreenRoute = null })
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
                    AppTopBar(
                        destination = destination,
                        localTab = localTab,
                        search = search,
                        sortMenu = sortMenu,
                        onRescan = { LibraryScanWorker.enqueue(context, thorough = true) },
                        onOpenSettings = sheets.onOpenSettings,
                    )
                }
            },
            bottomBar = {
                if (!showNowPlaying) {
                    AppBottomBar(
                        session = session,
                        destination = destination,
                        // The mini player stays along the bottom even with a rail: it
                        // is the width of it that makes the artwork and title
                        // readable, and a rail-width version would be a column of
                        // icons.
                        showNavigationBar = !useRail,
                        onSelectDestination = { destination = it },
                        onExpandPlayer = { showNowPlaying = true },
                        onOpenQueue = sheets.onOpenQueue,
                        onTogglePlayPause = playback::togglePlayPause,
                        onSkipNext = playback::skipNext,
                    )
                }
            }
        ) { innerPadding ->
            AppContent(
                innerPadding = innerPadding,
                session = session,
                showNowPlaying = showNowPlaying,
                onMinimizeNowPlaying = {
                    showNowPlaying = false
                    queueExpansion.suppress(true)
                },
                navigation = LibraryNavigation(
                    destination = destination,
                    tab = localTab,
                    visibleTabs = visibleTabs,
                    searchQuery = search.appliedLocalQuery,
                    webSearchQuery = search.webQuery,
                    sorts = sortMenu.options,
                    onSelectTab = { localTab = it },
                    onShowInLibrary = { tab, query ->
                        destination = RootDestination.Library
                        localTab = tab
                        search.onApply(query)
                        showNowPlaying = false
                        queueExpansion.suppress(true)
                    },
                ),
                sheets = sheets,
                listStates = listStates,
                onWebViewReady = { webPlaybackView = it },
            )
        }
    }
}

/**
 * A screen that takes over the whole window rather than sitting inside the Scaffold.
 *
 * These need their own scrolling app bar and have nothing in common with the mini
 * player / now-playing layout the Scaffold already juggles, so the screen returns
 * early for them instead of adding another branch to its content.
 */
private enum class FullScreenRoute { Licenses, ListeningStats }

@Composable
private fun FullScreenDestination(route: FullScreenRoute, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    when (route) {
        FullScreenRoute.Licenses -> OpenSourceLicensesScreen(
            onBack = onBack,
            aboutLibrariesRawResId = R.raw.aboutlibraries,
        )
        FullScreenRoute.ListeningStats -> ListeningStatsScreen(onBack = onBack)
    }
}

/** How the library is sorted, per kind of list. */
@Stable
private class LibrarySortOptions(
    val song: SongSortOption,
    val album: AlbumSortOption,
    val collection: CollectionSortOption,
)

/**
 * The sort menu, and what it does when something in it is picked.
 *
 * Each callback closes the menu as well as applying the choice. That belongs here
 * rather than in the menu itself because it is a decision about behaviour — a sort
 * order is a single choice, so the menu has said everything it has to say once one
 * is made.
 */
@Stable
private class SortMenu(
    val options: LibrarySortOptions,
    val expanded: Boolean,
    val onExpandedChange: (Boolean) -> Unit,
    onSong: (SongSortOption) -> Unit,
    onAlbum: (AlbumSortOption) -> Unit,
    onCollection: (CollectionSortOption) -> Unit,
) {
    val onSong: (SongSortOption) -> Unit = { song ->
        onSong(song)
        onExpandedChange(false)
    }
    val onAlbum: (AlbumSortOption) -> Unit = { album ->
        onAlbum(album)
        onExpandedChange(false)
    }
    val onCollection: (CollectionSortOption) -> Unit = { collection ->
        onCollection(collection)
        onExpandedChange(false)
    }
}

/**
 * The sort state, owned here rather than by the screen.
 *
 * Four pieces of state that are only ever read together, so they are remembered
 * together — and AppRoot, which does not care how anything is sorted, no longer
 * declares any of it.
 */
@Composable
private fun rememberSortMenu(): SortMenu {
    var song by rememberSaveable { mutableStateOf(SongSortOption.Title) }
    var album by rememberSaveable { mutableStateOf(AlbumSortOption.Name) }
    var collection by rememberSaveable { mutableStateOf(CollectionSortOption.Name) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    return SortMenu(
        options = LibrarySortOptions(song = song, album = album, collection = collection),
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onSong = { song = it },
        onAlbum = { album = it },
        onCollection = { collection = it },
    )
}

/** The search field, which searches the library or the web depending where you are. */
@Stable
private class SearchFields(
    val active: Boolean,
    val localQuery: String,
    val webQuery: String,
    /** What the library actually filters on: the typed query, once it settles. */
    val appliedLocalQuery: String,
    val onActiveChange: (Boolean) -> Unit,
    val onLocalChange: (String) -> Unit,
    val onWebChange: (String) -> Unit,
    /** Fills the field and applies it at once, for "show me this artist". */
    val onApply: (String) -> Unit,
)

/**
 * The search state, and the debounce between typing and filtering.
 *
 * The delay is why the applied query is separate from the typed one: filtering a
 * large library on every keystroke makes the field feel like it is lagging behind
 * the typist, when what is lagging is the list.
 */
@Composable
private fun rememberSearchFields(): SearchFields {
    var localQuery by rememberSaveable { mutableStateOf("") }
    var webQuery by rememberSaveable { mutableStateOf("") }
    var appliedLocalQuery by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(localQuery) {
        delay(SEARCH_DEBOUNCE_MS)
        appliedLocalQuery = localQuery
    }

    return SearchFields(
        active = active,
        localQuery = localQuery,
        webQuery = webQuery,
        appliedLocalQuery = appliedLocalQuery,
        onActiveChange = { active = it },
        onLocalChange = { localQuery = it },
        onWebChange = { webQuery = it },
        // Applied without waiting for the debounce, because nothing is being typed:
        // the query arrived whole, from a tap on an artist or an album.
        onApply = {
            localQuery = it
            appliedLocalQuery = it
            active = true
        },
    )
}

private const val SEARCH_DEBOUNCE_MS = 180L

/**
 * The header, inset past the status bar.
 *
 * The app draws edge to edge (enforced from targetSdk 35), so the header has to
 * inset itself. Without this it sits underneath the status bar and the status bar
 * swallows taps aimed at it — which is what made the Settings button unreachable
 * on-device rather than merely ugly.
 */
@Composable
private fun AppTopBar(
    destination: RootDestination,
    localTab: LibraryTab,
    search: SearchFields,
    sortMenu: SortMenu,
    onRescan: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        AppHeader(
            destination = destination,
            localTab = localTab,
            searchActive = search.active,
            onSearchActiveChange = search.onActiveChange,
            localSearchQuery = search.localQuery,
            webSearchQuery = search.webQuery,
            onLocalSearchChange = search.onLocalChange,
            onWebSearchChange = search.onWebChange,
            onRescan = onRescan,
            showSortMenu = sortMenu.expanded,
            onShowSortMenuChange = sortMenu.onExpandedChange,
            onOpenSettings = onOpenSettings,
            songSortOption = sortMenu.options.song,
            albumSortOption = sortMenu.options.album,
            onSongSortChange = sortMenu.onSong,
            onAlbumSortChange = sortMenu.onAlbum,
            collectionSortOption = sortMenu.options.collection,
            onCollectionSortChange = sortMenu.onCollection,
        )
    }
}

/**
 * The mini player, and the navigation bar under it.
 *
 * Inset past the navigation bar for the same reason the header is inset past the
 * status bar: the gesture bar would otherwise overlap the mini player's controls.
 */
@Composable
private fun AppBottomBar(
    session: PlaybackState?,
    destination: RootDestination,
    showNavigationBar: Boolean,
    onSelectDestination: (RootDestination) -> Unit,
    onExpandPlayer: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
) {
    Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
        MiniPlayerBar(
            session = session,
            onExpand = onExpandPlayer,
            onOpenQueue = onOpenQueue,
            onTogglePlayPause = onTogglePlayPause,
            onSkipNext = onSkipNext,
        )
        if (showNavigationBar) {
            AppNavigationBar(
                selected = destination,
                onSelect = onSelectDestination,
            )
        }
    }
}

/**
 * Where the library is pointed, and how to point it somewhere else.
 *
 * Grouped into one object rather than passed as a dozen arguments because they are
 * one thing: the selection the screen is showing. The alternative was a content
 * composable with seventeen parameters, at which point the signature stops
 * describing anything.
 */
@Stable
private class LibraryNavigation(
    val destination: RootDestination,
    val tab: LibraryTab,
    val visibleTabs: List<LibraryTab>,
    val searchQuery: String,
    val webSearchQuery: String,
    val sorts: LibrarySortOptions,
    val onSelectTab: (LibraryTab) -> Unit,
    /**
     * Jumps to a tab filtered to one thing — an artist, an album.
     *
     * One callback rather than one per destination, because "show me this artist"
     * and "show me this album" were the same six assignments written out twice, and
     * two copies of a six-step sequence drift.
     */
    val onShowInLibrary: (LibraryTab, String) -> Unit,
)

/**
 * The sheets that open over the top of whatever is showing.
 *
 * Owns its own visibility, so the screen underneath does not declare a boolean for
 * each one. Licenses is the exception and stays with AppRoot: it is a whole separate
 * route rather than a sheet, and the screen returns early for it.
 */
@Composable
private fun rememberAppSheets(onOpenRoute: (FullScreenRoute) -> Unit): AppSheets {
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPerformanceOverlay by rememberSaveable { mutableStateOf(false) }
    return AppSheets(
        showQueue = showQueue,
        showSettings = showSettings,
        showPerfOverlay = showPerformanceOverlay,
        onOpenQueue = { showQueue = true },
        onDismissQueue = { showQueue = false },
        onOpenSettings = { showSettings = true },
        onDismissSettings = { showSettings = false },
        // Dismissing the sheet is part of opening a route, not something every
        // caller has to remember: the sheet is what the route was opened from.
        onOpenRoute = {
            showSettings = false
            onOpenRoute(it)
        },
        onShowPerfOverlayChange = { showPerformanceOverlay = it },
    )
}

@Stable
private class AppSheets(
    val showQueue: Boolean,
    val showSettings: Boolean,
    val showPerfOverlay: Boolean,
    val onOpenQueue: () -> Unit,
    val onDismissQueue: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissSettings: () -> Unit,
    val onOpenRoute: (FullScreenRoute) -> Unit,
    val onShowPerfOverlayChange: (Boolean) -> Unit,
)

/**
 * Everything inside the Scaffold: the player, the browser, and the sheets over them.
 *
 * Now Playing replaces the content rather than covering it, which is why this is one
 * branch and not a stack — the bars above and below already hide themselves for it,
 * and drawing the library underneath a full-screen player only costs a composition.
 */
@Composable
private fun AppContent(
    innerPadding: PaddingValues,
    session: PlaybackState?,
    showNowPlaying: Boolean,
    onMinimizeNowPlaying: () -> Unit,
    navigation: LibraryNavigation,
    sheets: AppSheets,
    listStates: LibraryListStates,
    onWebViewReady: (WebView) -> Unit,
) {
    val context = LocalContext.current

    if (showNowPlaying) {
        ExpandedNowPlayingScreen(
            session = session,
            onMinimize = onMinimizeNowPlaying,
            onOpenQueue = sheets.onOpenQueue,
            // The Artists tab exists now, so "go to artist" lands on the artist
            // rather than on a song list filtered by their name.
            onGoToArtist = { navigation.onShowInLibrary(LibraryTab.Artists, it) },
            onViewAlbum = { navigation.onShowInLibrary(LibraryTab.Albums, it) },
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
                    searchQuery = navigation.webSearchQuery,
                    isVisible = navigation.destination == RootDestination.Web,
                    onWebViewReady = onWebViewReady
                )
            }

            if (navigation.destination == RootDestination.Search) {
                SearchScreen(listState = listStates.search)
            }

            if (navigation.destination == RootDestination.Library) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LibraryTabs(
                        selected = navigation.tab,
                        tabs = navigation.visibleTabs,
                        onSelect = navigation.onSelectTab
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        LibraryTabContent(
                            tab = navigation.tab,
                            searchQuery = navigation.searchQuery,
                            songSortOption = navigation.sorts.song,
                            albumSortOption = navigation.sorts.album,
                            collectionSortOption = navigation.sorts.collection,
                            listStates = listStates,
                        )
                    }
                }
            }
        }
    }

    if (sheets.showQueue) {
        QueueSidebar(
            queue = session?.queue.orEmpty(),
            currentIndex = session?.currentIndex ?: -1,
            onDismiss = sheets.onDismissQueue
        )
    }

    if (sheets.showSettings) {
        SettingsSheet(
            onDismiss = sheets.onDismissSettings,
            onShowLicenses = { sheets.onOpenRoute(FullScreenRoute.Licenses) },
            onShowListeningStats = { sheets.onOpenRoute(FullScreenRoute.ListeningStats) },
            showPerfOverlay = sheets.showPerfOverlay,
            onShowPerfOverlayChange = sheets.onShowPerfOverlayChange
        )
    }

    if (isDebugBuild(context) && sheets.showPerfOverlay) {
        DebugPerformanceOverlay(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, end = 8.dp)
        )
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
    localTab: LibraryTab,
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
    localTab: LibraryTab,
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
                    if (isLibrary) "Filter ${localTab.label.lowercase()}" else "Search the web"
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
private fun LibraryTabs(
    selected: LibraryTab,
    tabs: List<LibraryTab>,
    onSelect: (LibraryTab) -> Unit
) {
    if (tabs.isEmpty()) return
    PrimaryScrollableTabRow(
        // Against the visible list, not the enum: with tabs hidden or reordered the
        // enum's index points at a different tab, and the indicator would sit under
        // the wrong one.
        selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0),
        edgePadding = 12.dp,
        divider = { HorizontalDivider() },
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                text = { Text(tab.label) },
            )
        }
    }
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
private fun LibraryTab.sortFamily(): SortFamily = when (this) {
    LibraryTab.Songs, LibraryTab.Favourites -> SortFamily.Song
    LibraryTab.Albums -> SortFamily.Album
    LibraryTab.Artists,
    LibraryTab.Playlists,
    LibraryTab.Folders,
    LibraryTab.Genres -> SortFamily.Collection
    LibraryTab.Suggested -> SortFamily.None
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

/**
 * Keeps the library in step with the files on the device.
 *
 * Indexes once on launch — cheap when nothing changed, and it is what fills the
 * library the first time — then re-indexes whenever MediaStore reports a change.
 * The worker uses KEEP, so a burst during a large copy coalesces into one scan
 * rather than restarting it repeatedly.
 *
 * Lifted out of the root composable because it is fifty lines of registration that
 * nothing else on that screen interacts with.
 */
@Composable
private fun RescanWhenMediaChanges() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        LibraryScanWorker.enqueue(context)

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = rescan()
            override fun onChange(selfChange: Boolean, uri: Uri?) = rescan()

            private fun rescan() {
                LocalMusicRepository.invalidateCaches()
                LibraryScanWorker.enqueue(context)
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
}

/**
 * Silences the web player once local playback starts, and returns what it silenced for.
 *
 * Both can be playing at once — the WebView keeps running when the mode switches, on
 * purpose, so going back to it resumes where it was. What is not wanted is two things
 * playing over each other, so starting a local track stops the web one.
 *
 * The returned URI is what stops it firing repeatedly for the same track: the effect
 * runs on every session update, which is once a second while playing.
 */
@Composable
private fun pauseWebWhenLocalPlaybackStarts(
    session: PlaybackState?,
    webView: WebView?,
    alreadyPausedFor: String,
): String {
    var pausedFor by remember { mutableStateOf(alreadyPausedFor) }
    LaunchedEffect(session?.updatedAtMs) {
        val current = session ?: return@LaunchedEffect
        val localTrackStarted = current.isPlaying &&
            current.uri.startsWith("content://") &&
            current.uri != pausedFor
        if (localTrackStarted) {
            WebPlayback.pause(webView)
            pausedFor = current.uri
        }
    }
    return pausedFor
}

/**
 * Tells the listener when a track will not play, wherever they are in the app.
 *
 * The Now Playing screen carries the same failure inline, but someone can be
 * browsing the library, on another destination, or have the screen off with the
 * notification driving playback. Only the snackbar reaches them, so it names the
 * track rather than saying something failed.
 *
 * Keyed on Unit, not on the error. Keyed on the error, the service withdrawing it —
 * which happens the moment an auto-advance lands on a track that plays, often well
 * inside the snackbar's own duration — cancels this coroutine and cuts the message
 * off mid-sentence. Each failure is collected once and shown for as long as it takes
 * to read, whatever the player does next.
 */
@Composable
private fun AnnouncePlaybackErrors(
    playbackState: PlaybackState,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(Unit) {
        // Read inside the lambda, which is what makes it a tracked snapshot read; a
        // value captured outside would be frozen at first composition.
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
}

/**
 * The screen behind whichever library tab is selected.
 *
 * Lifted out of AppRoot as one piece rather than split per tab: every branch is the
 * same shape, and what the reader needs to see is that they *are* the same shape —
 * which tabs take which sort option, and that each one keeps its own scroll position.
 * Spread across eight call sites in a 250-line function, an inconsistency here is
 * invisible; gathered, it is a single column to read down.
 */
@Composable
private fun LibraryTabContent(
    tab: LibraryTab,
    searchQuery: String,
    songSortOption: SongSortOption,
    albumSortOption: AlbumSortOption,
    collectionSortOption: CollectionSortOption,
    listStates: LibraryListStates,
) {
    when (tab) {
        LibraryTab.Songs -> LibraryScreen(
            searchQuery = searchQuery,
            sortOption = songSortOption,
            listState = listStates.songs
        )
        LibraryTab.Albums -> AlbumsScreen(
            searchQuery = searchQuery,
            sortOption = albumSortOption,
            listState = listStates.albums
        )
        LibraryTab.Artists -> ArtistsScreen(
            searchQuery = searchQuery,
            sortOption = collectionSortOption,
            listState = listStates.artists
        )
        LibraryTab.Playlists -> PlaylistsScreen(
            searchQuery = searchQuery,
            sortOption = collectionSortOption,
            listState = listStates.playlists
        )
        LibraryTab.Folders -> FoldersScreen(
            searchQuery = searchQuery,
            sortOption = collectionSortOption,
            listState = listStates.folders
        )
        LibraryTab.Genres -> GenresScreen(
            searchQuery = searchQuery,
            sortOption = collectionSortOption,
            listState = listStates.genres
        )
        LibraryTab.Suggested -> SuggestedScreen(
            listState = listStates.suggested
        )
        LibraryTab.Favourites -> FavouritesScreen(
            searchQuery = searchQuery,
            sortOption = songSortOption,
            listState = listStates.favourites
        )
    }
}
