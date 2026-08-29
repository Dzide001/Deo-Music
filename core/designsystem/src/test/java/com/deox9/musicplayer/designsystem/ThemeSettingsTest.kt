// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSettingsTest {

    @Test
    fun `stored values map to their modes`() {
        assertEquals(ThemeMode.System, themeModeFrom("system", legacyDarkEnabled = false))
        assertEquals(ThemeMode.Light, themeModeFrom("light", legacyDarkEnabled = true))
        assertEquals(ThemeMode.Dark, themeModeFrom("dark", legacyDarkEnabled = false))
    }

    /**
     * The upgrade path. Someone who had turned dark on must stay on dark, not be
     * quietly moved to following the system.
     */
    @Test
    fun `an unset mode inherits the superseded dark flag`() {
        assertEquals(ThemeMode.Dark, themeModeFrom(UNSET, legacyDarkEnabled = true))
        assertEquals(ThemeMode.System, themeModeFrom(UNSET, legacyDarkEnabled = false))
    }

    /** A value from a newer build must fall back, not crash or pick arbitrarily. */
    @Test
    fun `an unrecognised mode falls back to the legacy flag`() {
        assertEquals(ThemeMode.Dark, themeModeFrom("solarized", legacyDarkEnabled = true))
        assertEquals(ThemeMode.System, themeModeFrom("solarized", legacyDarkEnabled = false))
    }

    @Test
    fun `every mode round-trips through its stored value`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, themeModeFrom(mode.storedValue(), legacyDarkEnabled = false))
        }
    }

    /**
     * Once the user has chosen explicitly, the legacy flag must never override it —
     * otherwise picking "system" on an upgraded install would keep snapping to dark.
     */
    @Test
    fun `an explicit choice wins over the legacy flag`() {
        assertEquals(ThemeMode.System, themeModeFrom("system", legacyDarkEnabled = true))
        assertEquals(ThemeMode.Light, themeModeFrom("light", legacyDarkEnabled = true))
    }
}
