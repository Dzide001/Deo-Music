// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.designsystem

/**
 * Maps a stored theme-mode string onto [ThemeMode].
 *
 * The mode is persisted as a string rather than an ordinal so adding a case later
 * cannot silently reinterpret everyone's saved preference.
 *
 * @param stored the persisted value, or [UNSET] if the user has never chosen one.
 * @param legacyDarkEnabled the superseded `darkThemeEnabled` boolean, consulted only
 *   while [stored] is unset. This is what stops an upgrade quietly moving someone who
 *   had chosen dark back to following the system.
 */
fun themeModeFrom(stored: String, legacyDarkEnabled: Boolean): ThemeMode = when (stored) {
    LIGHT -> ThemeMode.Light
    DARK -> ThemeMode.Dark
    SYSTEM -> ThemeMode.System
    // Unset, or a value written by a newer build we do not understand: fall back to
    // the legacy flag rather than guessing.
    else -> if (legacyDarkEnabled) ThemeMode.Dark else ThemeMode.System
}

/** The persisted form of [ThemeMode]. */
fun ThemeMode.storedValue(): String = when (this) {
    ThemeMode.System -> SYSTEM
    ThemeMode.Light -> LIGHT
    ThemeMode.Dark -> DARK
}

const val UNSET = ""
private const val SYSTEM = "system"
private const val LIGHT = "light"
private const val DARK = "dark"
