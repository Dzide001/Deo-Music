// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deox9.musicplayer.backup.BackupCodec
import com.deox9.musicplayer.backup.BackupException
import com.deox9.musicplayer.backup.BackupRepository
import com.deox9.musicplayer.designsystem.ThemeMode
import com.deox9.musicplayer.designsystem.storedValue
import com.deox9.musicplayer.settings.AppSettings
import com.deox9.musicplayer.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val backupRepository: BackupRepository,
) : ViewModel() {

    /** What the last backup or restore did, for the screen to report. */
    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    fun clearBackupStatus() {
        _backupStatus.value = null
    }

    /**
     * Writes a backup to wherever the picker put it.
     *
     * [write] is handed the bytes rather than a path, because the destination is a
     * document the user chose and only the caller holds the resolver for it.
     */
    fun exportBackup(appVersion: String, write: (String) -> Unit) {
        viewModelScope.launch {
            _backupStatus.value = runCatching {
                val data = backupRepository.export(appVersion, System.currentTimeMillis())
                write(BackupCodec.encode(data))
                val tracks = data.playlists.sumOf { it.tracks.size }
                "Backed up ${data.playlists.size} playlists, ${data.favourites.size} favourites, " +
                    "$tracks playlist entries."
            }.getOrElse { "Could not write the backup: ${it.message}" }
        }
    }

    /**
     * Restores from a file the user picked.
     *
     * The refusal from the codec is passed through rather than flattened into a
     * generic failure — "written by a newer version of the app" tells someone what
     * to do next, and "restore failed" does not.
     */
    fun importBackup(raw: String) {
        viewModelScope.launch {
            _backupStatus.value = BackupCodec.decode(raw).fold(
                onSuccess = { data ->
                    runCatching {
                        backupRepository.restore(data, System.currentTimeMillis()).summary()
                    }.getOrElse { "Could not restore: ${it.message}" }
                },
                onFailure = { (it as? BackupException)?.failure?.message ?: "That file could not be read." },
            )
        }
    }

    val settings: StateFlow<AppSettings> = repository.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppSettings(),
        )

    fun unhideFolder(path: String) = edit { repository.unhideFolder(path) }

    fun setMinimumTrackDurationMs(ms: Long) = edit { repository.setMinimumTrackDurationMs(ms) }

    fun setResumeAfterInterruption(enabled: Boolean) =
        edit { repository.setResumeAfterInterruption(enabled) }

    fun setWebHomeUrl(url: String) = edit { repository.setWebHomeUrl(url) }

    fun setCrossfadeOnSkip(enabled: Boolean) = edit { repository.setCrossfadeOnSkip(enabled) }

    fun setCrossfadeOnAutoAdvance(enabled: Boolean) =
        edit { repository.setCrossfadeOnAutoAdvance(enabled) }

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
