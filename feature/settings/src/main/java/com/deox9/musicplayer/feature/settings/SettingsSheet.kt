// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.designsystem.ThemeMode
import com.deox9.musicplayer.designsystem.themeModeFrom
import com.deox9.musicplayer.settings.AppSettings
import com.deox9.musicplayer.ui.isDebugBuild
import com.deox9.musicplayer.ui.label
import com.deox9.musicplayer.web.normalizeWebUrl

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
                title = "Gapless playback",
                checked = settings.gaplessEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setGaplessEnabled(enabled)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingToggleRow(
                title = "Crossfade (preview)",
                checked = settings.crossfadeEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setCrossfadeEnabled(enabled)
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
