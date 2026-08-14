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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.audio.CrossfadeCurve
import com.deox9.musicplayer.audio.CrossfadeSettings
import com.deox9.musicplayer.audio.EqBand
import com.deox9.musicplayer.audio.EqBandType
import com.deox9.musicplayer.audio.OutputProfiles
import com.deox9.musicplayer.audio.OutputRoute
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
        PlayerDialog.SignalChain -> SignalChainDialog(onDismiss = onDismiss, viewModel = viewModel)
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
            text = { Text("Signal chain") },
            onClick = {
                onDismiss()
                onRequestDialog(PlayerDialog.SignalChain)
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
                FadeSettings(settings.crossfade, viewModel)
                HorizontalDivider()
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
private fun SignalChainDialog(onDismiss: () -> Unit, viewModel: PlayerViewModel) {
    val chain by viewModel.signalChain.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signal chain") },
        text = { SignalChainView(chain) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
internal fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // On the Row, not the Switch. With the toggle on the Switch alone the
            // label and the control are two separate stops, and the control has no
            // name — a screen reader announces "switch, off" without ever saying
            // what it switches. toggleable merges the row into one node, which also
            // makes the whole width the target rather than the switch alone.
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        // null, so the Switch does not also claim the toggle for itself and undo
        // the merge by becoming independently focusable.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun EqualizerDialog(onDismiss: () -> Unit, viewModel: PlayerViewModel) {
    val settings by viewModel.settings.collectAsState()
    val route by viewModel.outputRoute.collectAsState()
    val bands = settings.eqBands

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Equaliser") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = EQ_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The curve first, because it is the only thing that shows what the
                // bands add up to.
                EqCurve(bands = bands, sampleRate = CURVE_SAMPLE_RATE)

                OutputProfileControls(
                    route = route,
                    profiles = settings.outputProfiles,
                    bands = bands,
                    viewModel = viewModel,
                )
                HorizontalDivider()

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EqPreset.entries.forEach { preset ->
                        TextButton(
                            onClick = {
                                viewModel.setEqEnabled(true)
                                viewModel.setEqBands(preset.bands)
                            },
                        ) {
                            Text(preset.label)
                        }
                    }
                }

                if (bands.isEmpty()) {
                    Text(
                        text = "No bands. Add one, or pick a preset.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                bands.forEachIndexed { index, band ->
                    EqBandEditor(
                        band = band,
                        index = index,
                        onChange = { updated ->
                            viewModel.setEqEnabled(true)
                            viewModel.setEqBands(bands.toMutableList().also { it[index] = updated })
                        },
                        onRemove = {
                            viewModel.setEqBands(bands.toMutableList().also { it.removeAt(index) })
                        },
                    )
                }

                TextButton(
                    enabled = bands.size < MAX_BANDS,
                    onClick = {
                        viewModel.setEqEnabled(true)
                        viewModel.setEqBands(bands + defaultNewBand(bands))
                    },
                ) {
                    Text(if (bands.size < MAX_BANDS) "Add band" else "Band limit reached")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

/**
 * Where a new band starts.
 *
 * Placed an octave above the highest existing band rather than always at 1 kHz, so
 * adding several in a row does not stack them all on the same frequency where their
 * effects compound invisibly.
 */
private fun defaultNewBand(existing: List<EqBand>): EqBand {
    val highest = existing.maxOfOrNull { it.frequencyHz } ?: DEFAULT_NEW_BAND_HZ
    return EqBand(
        type = EqBandType.Peaking,
        frequencyHz = (highest * 2).coerceAtMost(MAX_NEW_BAND_HZ),
        gainDb = 0.0,
    )
}

/**
 * The presets, as parametric bands.
 *
 * Previously ten hard-coded millibel values per preset, which only meant anything
 * against the fixed ISO frequencies of the graphic equaliser. Expressed as bands they
 * say what they are doing: a bass preset is a low shelf, not a pattern of sliders.
 */
private enum class EqPreset(val label: String, val bands: List<EqBand>) {
    Flat("Flat", emptyList()),
    Bass(
        "Bass",
        listOf(
            EqBand(EqBandType.LowShelf, frequencyHz = 120.0, gainDb = 6.0, q = 0.7),
            EqBand(EqBandType.Peaking, frequencyHz = 60.0, gainDb = 3.0, q = 1.0),
        ),
    ),
    Vocal(
        "Vocal",
        listOf(
            EqBand(EqBandType.Peaking, frequencyHz = 300.0, gainDb = -3.0, q = 1.2),
            EqBand(EqBandType.Peaking, frequencyHz = 2500.0, gainDb = 4.0, q = 1.0),
            EqBand(EqBandType.HighShelf, frequencyHz = 8000.0, gainDb = 2.0, q = 0.7),
        ),
    ),
    Treble(
        "Treble",
        listOf(EqBand(EqBandType.HighShelf, frequencyHz = 6000.0, gainDb = 6.0, q = 0.7)),
    ),
}

private fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

private const val MAX_BANDS = 12
private const val DEFAULT_NEW_BAND_HZ = 500.0
private const val MAX_NEW_BAND_HZ = 16_000.0

/**
 * The curve is drawn for a nominal rate rather than the file's.
 *
 * Filter shapes barely move between 44.1 and 48 kHz below a few kilohertz, and the
 * dialog can be opened with nothing playing, when there is no real rate to use.
 */
private const val CURVE_SAMPLE_RATE = 48_000
private val EQ_MAX_HEIGHT = 480.dp
private const val REPLAY_GAIN_MIN_DB = -18f
private val LYRICS_MAX_HEIGHT = 320.dp

/**
 * The fade settings.
 *
 * Skipping and finishing are two switches rather than one because they are two
 * different trades. Fading a skip costs nothing — that audio was being abandoned.
 * Fading the end of a track means fading out music the listener wanted, and it puts
 * a hole in a continuous album, so it says so rather than leaving it to be
 * discovered.
 */
@Composable
private fun FadeSettings(crossfade: CrossfadeSettings, viewModel: PlayerViewModel) {
    SettingSwitch("Fade when skipping", crossfade.onSkip, viewModel::setCrossfadeOnSkip)
    SettingSwitch(
        "Fade between tracks",
        crossfade.onAutoAdvance,
        viewModel::setCrossfadeOnAutoAdvance,
    )
    if (crossfade.onAutoAdvance) {
        Text(
            text = "Fades out the end of every track, so albums meant to run together " +
                "get a gap where there was none.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (!crossfade.onSkip && !crossfade.onAutoAdvance) return

    Text(
        text = "Length: ${crossfade.durationMs} ms",
        style = MaterialTheme.typography.labelMedium,
    )
    Slider(
        value = crossfade.durationMs.toFloat(),
        onValueChange = { viewModel.setCrossfadeDurationMs(it.toInt()) },
        valueRange = fadeRange,
        modifier = Modifier.semantics {
            contentDescription = "Fade length, ${crossfade.durationMs} milliseconds"
        },
    )
    if (crossfade.onSkip) {
        Text(
            text = "A skip waits for the fade, so this is also how long the next track " +
                "takes to start.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Text(text = "Curve", style = MaterialTheme.typography.labelMedium)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        CrossfadeCurve.entries.forEachIndexed { index, curve ->
            SegmentedButton(
                selected = crossfade.curve == curve,
                onClick = { viewModel.setCrossfadeCurve(curve) },
                shape = SegmentedButtonDefaults.itemShape(index, CrossfadeCurve.entries.size),
            ) {
                Text(curve.label)
            }
        }
    }
    Text(
        text = crossfade.curve.explanation,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val fadeRange =
    CrossfadeSettings.MIN_DURATION_MS.toFloat()..CrossfadeSettings.MAX_DURATION_MS.toFloat()

private val CrossfadeCurve.label: String
    get() = when (this) {
        CrossfadeCurve.Linear -> "Linear"
        CrossfadeCurve.EqualPower -> "Equal power"
        CrossfadeCurve.Logarithmic -> "Logarithmic"
    }

private val CrossfadeCurve.explanation: String
    get() = when (this) {
        CrossfadeCurve.Linear -> "Straight line in volume. Dips slightly in the middle."
        CrossfadeCurve.EqualPower -> "Holds a steady loudness through the change."
        CrossfadeCurve.Logarithmic -> "Falls steadily to the ear. Suits longer fades."
    }

/**
 * Per-output profile controls, inside the equaliser because that is what they save.
 *
 * The route is named even when the feature is off, since "which output is this
 * curve for" is the question the whole feature answers and the answer is useful
 * before anyone commits to using it.
 */
@Composable
private fun OutputProfileControls(
    route: OutputRoute,
    profiles: OutputProfiles,
    bands: List<EqBand>,
    viewModel: PlayerViewModel,
) {
    val saved = profiles.has(route)

    Text(
        text = "Playing through ${route.label}",
        style = MaterialTheme.typography.labelMedium,
    )

    SettingSwitch(
        label = "Per-output profiles",
        checked = profiles.enabled,
        onChange = viewModel::setOutputProfilesEnabled,
    )

    if (!profiles.enabled) {
        Text(
            text = "Save a different equaliser for each pair of headphones, speaker " +
                "or DAC, and have it load itself when you plug in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { viewModel.saveProfileForCurrentRoute(bands) }) {
            Text(if (saved) "Update this output" else "Save for this output")
        }
        if (saved) {
            TextButton(onClick = viewModel::deleteProfileForCurrentRoute) { Text("Forget") }
        }
    }

    Text(
        // Which curve is actually being heard is not obvious once profiles exist,
        // and guessing wrong means tuning against a correction you cannot see.
        text = if (saved) {
            "${route.label} has its own profile. It is what you are hearing."
        } else {
            "${route.label} has no profile yet, so the settings above apply."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
