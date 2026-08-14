// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

/**
 * Which of the player's dialogs is open.
 *
 * One enum instead of the six independent booleans the old screen carried. Those could
 * be true at once — the audio-settings dialog and the equalizer it opens were both
 * mounted, stacked on each other — and nothing in the code said they were alternatives.
 */
internal enum class PlayerDialog {
    None,
    AddToPlaylist,
    CreatePlaylist,
    ConfirmDelete,
    Lyrics,
    AudioSettings,
    Equalizer,
    SignalChain,
    SleepTimer,
    SpeedAndPitch,
}
