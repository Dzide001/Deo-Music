package com.deox9.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deox9.musicplayer.library.LocalMusicRepository
import com.deox9.musicplayer.library.LocalTrack
import com.deox9.musicplayer.player.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

private enum class RootTab { Library, WebPlayback }

@Composable
private fun AppRoot() {
    var tab by remember { mutableStateOf(RootTab.Library) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == RootTab.Library,
                    onClick = { tab = RootTab.Library },
                    label = { Text("Library") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = tab == RootTab.WebPlayback,
                    onClick = { tab = RootTab.WebPlayback },
                    label = { Text("Web") },
                    icon = {}
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tab) {
                RootTab.Library -> LibraryScreen()

                RootTab.WebPlayback -> WebPlaybackScreen()
            }
        }
    }
}

@Composable
private fun LibraryScreen() {
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

    if (tracks.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text("No local tracks found.")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(tracks, key = { it.id }) { track ->
            LocalTrackRow(
                track = track,
                onClick = {
                    val playIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PLAY_URI
                        putExtra(PlaybackService.EXTRA_URI, track.contentUri)
                        putExtra(PlaybackService.EXTRA_TITLE, track.title)
                        putExtra(PlaybackService.EXTRA_ARTIST, track.artist)
                    }
                    ContextCompat.startForegroundService(context, playIntent)
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun LocalTrackRow(
    track: LocalTrack,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun WebPlaybackScreen() {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            settings.javaScriptEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            loadUrl("https://m.youtube.com")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webView }
    )
}
