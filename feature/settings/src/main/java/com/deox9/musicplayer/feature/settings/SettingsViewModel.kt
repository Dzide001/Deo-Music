// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deox9.musicplayer.designsystem.ThemeMode
import com.deox9.musicplayer.designsystem.storedValue
import com.deox9.musicplayer.settings.AppSettings
import com.deox9.musicplayer.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings state and edits.
 *
 * Survives configuration changes, so toggling a setting no longer re-reads
 * DataStore on every rotation, and the repository is injected rather than
 * constructed from a Context inside the composable.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppSettings(),
        )

    fun setWebHomeUrl(url: String) = edit { repository.setWebHomeUrl(url) }

    fun setCrossfadeEnabled(enabled: Boolean) = edit { repository.setCrossfadeEnabled(enabled) }

    fun setGaplessEnabled(enabled: Boolean) = edit { repository.setGaplessEnabled(enabled) }

    fun setThemeMode(mode: ThemeMode) = edit { repository.setThemeMode(mode.storedValue()) }

    fun setDynamicColorEnabled(enabled: Boolean) = edit { repository.setDynamicColorEnabled(enabled) }

    fun setAmoledEnabled(enabled: Boolean) = edit { repository.setAmoledEnabled(enabled) }

    fun setSuggestionsEnabled(enabled: Boolean) = edit { repository.setSuggestionsEnabled(enabled) }

    fun setThoroughScanEnabled(enabled: Boolean) = edit { repository.setThoroughScanEnabled(enabled) }

    fun setEqEnabled(enabled: Boolean) = edit { repository.setEqEnabled(enabled) }

    fun setReplayGainEnabled(enabled: Boolean) = edit { repository.setReplayGainEnabled(enabled) }

    fun setReplayGainDb(db: Float) = edit { repository.setReplayGainDb(db) }

    fun setEqBandLevels(levels: List<Int>) = edit { repository.setEqBandLevels(levels) }

    fun resetDefaults() = edit { repository.resetDefaults() }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
