// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.designsystem.ThemeMode
import com.deox9.musicplayer.designsystem.themeModeFrom
import com.deox9.musicplayer.settings.AppSettings
import com.deox9.musicplayer.ui.isDebugBuild
import com.deox9.musicplayer.ui.label
import com.deox9.musicplayer.web.normalizeWebUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsSheet(
    viewModel: SettingsViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onShowLicenses: () -> Unit,
    showPerfOverlay: Boolean,
    onShowPerfOverlayChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
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
                            viewModel.setWebHomeUrl(normalized)
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
                title = "Fade when skipping",
                checked = settings.crossfade.onSkip,
                onCheckedChange = { enabled ->
                    viewModel.setCrossfadeOnSkip(enabled)
                }
            )
            SettingToggleRow(
                title = "Fade between tracks",
                checked = settings.crossfade.onAutoAdvance,
                onCheckedChange = { enabled ->
                    viewModel.setCrossfadeOnAutoAdvance(enabled)
                }
            )

            LibraryAndAppearanceSection(settings = settings, viewModel = viewModel)

            if (isDebugBuild(context)) {
                Spacer(modifier = Modifier.height(8.dp))
                SettingToggleRow(
                    title = "Show performance overlay (debug)",
                    checked = showPerfOverlay,
                    onCheckedChange = onShowPerfOverlayChange
                )
            }

            SettingToggleRow(
                title = "Resume after calls",
                checked = settings.resumeAfterInterruption,
                onCheckedChange = viewModel::setResumeAfterInterruption,
            )
            Text(
                text = "When a call or another app interrupts, pick up where you left " +
                    "off once it finishes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LibraryFilterSection(settings, viewModel)

            BackupSection(viewModel)

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onShowLicenses) {
                Text("Open source licenses")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    viewModel.resetDefaults()
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
internal fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // See SettingSwitch: the toggle belongs on the row so the label and the
            // control are one node with one name, not two stops one of which is
            // anonymous.
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
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
            onCheckedChange = null
        )
    }
}

@Composable
private fun LibraryAndAppearanceSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Library and appearance",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
    SettingToggleRow(
        title = "Thorough scan (reads tags from files)",
        checked = settings.thoroughScanEnabled,
        onCheckedChange = viewModel::setThoroughScanEnabled
    )
    Spacer(modifier = Modifier.height(8.dp))
    SettingToggleRow(
        title = "Enable suggestions tab content",
        checked = settings.suggestionsEnabled,
        onCheckedChange = viewModel::setSuggestionsEnabled
    )
    Spacer(modifier = Modifier.height(8.dp))
    ThemeModeRow(settings = settings, viewModel = viewModel)
    Spacer(modifier = Modifier.height(8.dp))
    SettingToggleRow(
        title = "Colour from wallpaper and artwork",
        checked = settings.dynamicColorEnabled,
        onCheckedChange = viewModel::setDynamicColorEnabled
    )
    Spacer(modifier = Modifier.height(8.dp))
    SettingToggleRow(
        title = "Pure black backgrounds (OLED)",
        checked = settings.amoledEnabled,
        onCheckedChange = viewModel::setAmoledEnabled
    )
}

/**
 * Light / dark / system as a segmented choice.
 *
 * Replaces the old "Prefer dark theme" switch, which could not express "follow the
 * system" — the app stayed on whatever was last chosen regardless of the device.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ThemeModeRow(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val selected = themeModeFrom(settings.themeMode, settings.darkThemeEnabled)
    val options = listOf(ThemeMode.System to "System", ThemeMode.Light to "Light", ThemeMode.Dark to "Dark")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Theme", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { viewModel.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}

/**
 * Backing up and restoring, through the system file picker.
 *
 * The picker rather than a path the app chooses: the file then lives where the user
 * put it — their own Drive folder, an SD card, a cable to a laptop — and the app
 * needs no storage permission to write it. It also means the destination is a
 * document handle, which is why the bytes are handed to a writer here rather than
 * the view model being given somewhere to save.
 */
@Composable
private fun BackupSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val status by viewModel.backupStatus.collectAsState()
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }

    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportBackup(appVersion) { text ->
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // Confirmed before anything is written, because a restore changes the
        // library and there is no undo for it.
        confirmRestore = uri
    }

    Spacer(modifier = Modifier.height(16.dp))
    Text("Backup", style = MaterialTheme.typography.titleSmall)
    Text(
        text = "Playlists, favourites and settings, as a file you keep. Nothing is " +
            "sent anywhere — you choose where it is saved.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { createFile.launch(defaultBackupName()) }) { Text("Back up") }
        OutlinedButton(onClick = { openFile.launch(arrayOf(BACKUP_MIME, "text/plain", "*/*")) }) {
            Text("Restore")
        }
    }

    status?.let { message ->
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = viewModel::clearBackupStatus) { Text("Dismiss") }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore from this file?") },
            text = {
                Text(
                    "Playlists and favourites from the backup are added to what is " +
                        "already here. Nothing is deleted, and settings in the backup " +
                        "replace your current ones.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = null
                        val text = runCatching {
                            context.contentResolver.openInputStream(uri)?.use {
                                it.readBytes().decodeToString()
                            }
                        }.getOrNull()
                        if (text == null) {
                            viewModel.importBackup("")
                        } else {
                            viewModel.importBackup(text)
                        }
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = null }) { Text("Cancel") }
            },
        )
    }
}

/** Dated, so successive backups do not silently overwrite one another. */
private fun defaultBackupName(): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return "music-player-backup-$stamp.json"
}

private const val BACKUP_MIME = "application/json"

/**
 * What the library leaves out.
 *
 * A phone's audio is not all music. Voice notes, ringtones and game sounds land in
 * the same MediaStore, and without this the library is something to scroll past
 * rather than something to use.
 */
@Composable
private fun LibraryFilterSection(settings: AppSettings, viewModel: SettingsViewModel) {
    Spacer(modifier = Modifier.height(16.dp))
    Text("Library", style = MaterialTheme.typography.titleSmall)

    val minutes = settings.minimumTrackDurationMs / 1_000
    Text(
        text = if (minutes <= 0) {
            "Every audio file is indexed, however short."
        } else {
            "Files shorter than $minutes seconds are skipped."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Preset lengths rather than a slider: the useful thresholds are few and
        // well known, and nobody wants to dial in 37 seconds.
        listOf(0L, 30L, 60L, 90L).forEach { seconds ->
            val selected = settings.minimumTrackDurationMs == seconds * 1_000
            OutlinedButton(
                onClick = { viewModel.setMinimumTrackDurationMs(seconds * 1_000) },
                enabled = !selected,
            ) {
                Text(if (seconds == 0L) "All" else "${seconds}s")
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    if (settings.hiddenFolders.isEmpty()) {
        Text(
            text = "No folders hidden. Long-press a folder in the library to hide it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text("Hidden folders", style = MaterialTheme.typography.bodyMedium)
        settings.hiddenFolders.sorted().forEach { folder ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // The tail, because the useful part of a path is its end and the
                    // start is the same for everything on the device.
                    text = folder.substringAfterLast('/').ifBlank { folder },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.unhideFolder(folder) }) { Text("Unhide") }
            }
        }
        Text(
            text = "Rescan the library for changes to take effect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
