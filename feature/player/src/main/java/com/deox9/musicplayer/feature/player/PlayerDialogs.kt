// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.lyrics.LyricsData
import com.deox9.musicplayer.player.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

@Composable
internal fun PlayerDialogs(
    dialog: PlayerDialog,
    onDismiss: () -> Unit,
    onShowDialog: (PlayerDialog) -> Unit,
    session: PlaybackState?,
    onMinimize: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    when (dialog) {
        PlayerDialog.None -> Unit
        PlayerDialog.AddToPlaylist -> AddToPlaylistDialog(
            trackUri = session?.uri.orEmpty(),
            onDismiss = onDismiss,
            onCreateNew = { onShowDialog(PlayerDialog.CreatePlaylist) },
            viewModel = viewModel,
        )
        PlayerDialog.CreatePlaylist -> CreatePlaylistDialog(
            trackUri = session?.uri.orEmpty(),
            onDismiss = onDismiss,
            viewModel = viewModel,
        )
        PlayerDialog.ConfirmDelete -> ConfirmDeleteDialog(
            trackUri = session?.uri.orEmpty(),
            onDismiss = onDismiss,
            onDeleted = onMinimize,
            viewModel = viewModel,
        )
        PlayerDialog.Lyrics -> LyricsDialog(
            session = session,
            onDismiss = onDismiss,
            viewModel = viewModel,
        )
        PlayerDialog.AudioSettings -> AudioSettingsDialog(
            onDismiss = onDismiss,
            onOpenEqualizer = { onShowDialog(PlayerDialog.Equalizer) },
            viewModel = viewModel,
        )
        PlayerDialog.Equalizer -> EqualizerDialog(onDismiss = onDismiss, viewModel = viewModel)
    }
}

@Composable
internal fun NowPlayingMenu(
    expanded: Boolean,
    session: PlaybackState?,
    onDismiss: () -> Unit,
    onOpenQueue: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onViewAlbum: (String) -> Unit,
    onRequestDialog: (PlayerDialog) -> Unit,
) {
    val context = LocalContext.current

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Show queue") },
            onClick = {
                onDismiss()
                onOpenQueue()
            },
        )
        DropdownMenuItem(
            text = { Text("Add to playlist") },
            enabled = !session?.uri.isNullOrBlank(),
            onClick = {
                onDismiss()
                onRequestDialog(PlayerDialog.AddToPlaylist)
            },
        )
        DropdownMenuItem(
            text = { Text("Go to artist") },
            enabled = session?.artist?.isNotBlank() == true,
            onClick = {
                onDismiss()
                onGoToArtist(session?.artist.orEmpty().trim())
            },
        )
        DropdownMenuItem(
            text = { Text("View album") },
            enabled = session?.album?.isNotBlank() == true,
            onClick = {
                onDismiss()
                onViewAlbum(session?.album.orEmpty().trim())
            },
        )
        DropdownMenuItem(
            text = { Text("Share") },
            enabled = session != null,
            onClick = {
                onDismiss()
                session?.let { shareTrack(context, it) }
            },
        )
        if (session?.uri?.startsWith("content://") == true) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    onDismiss()
                    onRequestDialog(PlayerDialog.ConfirmDelete)
                },
            )
        }
    }
}

private fun shareTrack(context: Context, session: PlaybackState) {
    val text = buildString {
        append("Now playing: ${session.title}")
        if (session.artist.isNotBlank()) append(" by ${session.artist}")
        if (session.uri.isNotBlank()) append("\n${session.uri}")
    }
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share track").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    trackUri: String,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    viewModel: PlayerViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Loaded here rather than by whatever opened the dialog. The old flow fetched
    // playlists in the menu item's onClick and then flipped a boolean, so the dialog
    // could not be opened from anywhere else without repeating the fetch.
    val playlists by produceState<List<PlaylistInfo>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { viewModel.playlists() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val loaded = playlists
                when {
                    loaded == null -> Text("Loading playlists…")
                    loaded.isEmpty() -> Text("No playlists yet. Create one first.")
                    else -> loaded.forEach { playlist ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (trackUri.isBlank()) return@OutlinedButton
                                scope.launch {
                                    val added = withContext(Dispatchers.IO) {
                                        viewModel.addTrackToPlaylist(playlist.id, trackUri)
                                    }
                                    context.toast(
                                        if (added) "Added to ${playlist.name}" else "Could not add to playlist",
                                    )
                                    onDismiss()
                                }
                            },
                        ) {
                            Text(playlist.name)
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCreateNew) { Text("Create new") }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun CreatePlaylistDialog(
    trackUri: String,
    onDismiss: () -> Unit,
    viewModel: PlayerViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    if (trackUri.isBlank()) return@TextButton
                    scope.launch {
                        val playlistId = withContext(Dispatchers.IO) {
                            viewModel.createPlaylist(name.trim())
                        }
                        val added = playlistId != null && withContext(Dispatchers.IO) {
                            viewModel.addTrackToPlaylist(playlistId, trackUri)
                        }
                        context.toast(
                            if (added) "Playlist created and track added" else "Could not create playlist",
                        )
                        onDismiss()
                    }
                },
            ) {
                Text("Create")
            }
        },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    trackUri: String,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: PlayerViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete track") },
        text = { Text("This deletes the audio file from your device. It cannot be undone.") },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (trackUri.isBlank()) return@TextButton
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) { viewModel.deleteTrack(trackUri) }
                        context.toast(if (deleted) "Track deleted" else "Could not delete track")
                        onDismiss()
                        if (deleted) onDeleted()
                    }
                },
            ) {
                Text("Delete")
            }
        },
    )
}

@Composable
private fun LyricsDialog(
    session: PlaybackState?,
    onDismiss: () -> Unit,
    viewModel: PlayerViewModel,
) {
    val context = LocalContext.current
    var lyrics by remember { mutableStateOf<LyricsData?>(null) }
    var loading by remember { mutableStateOf(session != null) }

    LaunchedEffect(session?.uri) {
        val uri = session?.uri
        if (uri.isNullOrBlank()) {
            lyrics = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        lyrics = viewModel.lyricsFor(
            trackKey = uri,
            title = session.title,
            artist = session.artist,
            album = session.album,
            durationMs = session.durationMs,
        )
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics") },
        text = {
            if (session == null) {
                Text("Start playback to open lyrics.")
            } else {
                LyricsBody(session = session, lyrics = lyrics, loading = loading)
            }
        },
        dismissButton = {
            if (session != null) {
                TextButton(onClick = { searchLyricsOnTheWeb(context, session) }) {
                    Text("Search web")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun LyricsBody(session: PlaybackState, lyrics: LyricsData?, loading: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = LYRICS_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = session.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = session.artist, style = MaterialTheme.typography.labelMedium)
        HorizontalDivider()

        when {
            loading -> Text("Fetching lyrics…")
            lyrics != null && lyrics.syncedLines.isNotEmpty() -> {
                val active = currentSyncedLyricLine(lyrics.syncedLines, session.positionMs)
                Text(
                    text = "Synced lyrics${if (lyrics.cached) " (cached)" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                lyrics.syncedLines.forEach { line ->
                    val isActive = line.text == active
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            lyrics != null && lyrics.plainLyrics.isNotBlank() -> {
                Text(
                    text = "Lyrics${if (lyrics.cached) " (cached)" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = lyrics.plainLyrics, style = MaterialTheme.typography.bodyMedium)
            }
            else -> Text(
                text = "No lyrics found for this track yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun searchLyricsOnTheWeb(context: Context, session: PlaybackState) {
    val query = "${session.title} ${session.artist} lyrics"
    val url = "https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
private fun AudioSettingsDialog(
    onDismiss: () -> Unit,
    onOpenEqualizer: () -> Unit,
    viewModel: PlayerViewModel,
) {
    val settings by viewModel.settings.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingSwitch("Crossfade", settings.crossfadeEnabled, viewModel::setCrossfadeEnabled)
                SettingSwitch("Gapless", settings.gaplessEnabled, viewModel::setGaplessEnabled)
                SettingSwitch("Replay gain", settings.replayGainEnabled, viewModel::setReplayGainEnabled)

                if (settings.replayGainEnabled) {
                    Text(
                        text = "Pre-amp: ${settings.replayGainDb.toInt()} dB",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = settings.replayGainDb,
                        onValueChange = viewModel::setReplayGainDb,
                        valueRange = REPLAY_GAIN_MIN_DB..0f,
                    )
                }

                SettingSwitch("Equalizer", settings.eqEnabled, viewModel::setEqEnabled)
                TextButton(onClick = onOpenEqualizer) { Text("Open equalizer") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun EqualizerDialog(onDismiss: () -> Unit, viewModel: PlayerViewModel) {
    val settings by viewModel.settings.collectAsState()
    val levels = settings.eqBandLevels

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Equalizer") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqPreset.entries.forEach { preset ->
                        TextButton(
                            onClick = {
                                viewModel.setEqEnabled(true)
                                viewModel.setEqBandLevels(preset.levels)
                            },
                        ) {
                            Text(preset.label)
                        }
                    }
                }

                repeat(EQ_BANDS) { index ->
                    val value = levels.getOrElse(index) { 0 }
                    Text(
                        text = "Band ${index + 1}: ${"%.1f".format(value / EQ_MILLIBELS_PER_DB)} dB",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { newValue ->
                            val updated = List(EQ_BANDS) { band ->
                                if (band == index) {
                                    newValue.toInt().coerceIn(-EQ_RANGE, EQ_RANGE)
                                } else {
                                    levels.getOrElse(band) { 0 }
                                }
                            }
                            viewModel.setEqEnabled(true)
                            viewModel.setEqBandLevels(updated)
                        },
                        valueRange = -EQ_RANGE.toFloat()..EQ_RANGE.toFloat(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

/**
 * The four presets the dialog offered as hand-written lists inside four onClick
 * bodies. Levels are in millibels, matching what the settings store holds.
 */
private enum class EqPreset(val label: String, val levels: List<Int>) {
    Flat("Flat", List(EQ_BANDS) { 0 }),
    Bass("Bass", listOf(350, 300, 220, 120, 40, -40, -100, -180, -220, -260)),
    Vocal("Vocal", listOf(-200, -120, -40, 140, 260, 260, 140, -20, -120, -200)),
    Treble("Treble", listOf(-260, -220, -160, -80, 40, 140, 240, 320, 380, 430)),
}

private fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

private const val EQ_BANDS = 10
private const val EQ_RANGE = 1500
private const val EQ_MILLIBELS_PER_DB = 100f
private const val REPLAY_GAIN_MIN_DB = -18f
private val LYRICS_MAX_HEIGHT = 320.dp
