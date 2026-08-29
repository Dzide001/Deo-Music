// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Records which code paths to compile ahead of time.
 *
 * A baseline profile is a list of the classes and methods used early in a session,
 * shipped with the app so the runtime compiles them before they are needed rather
 * than interpreting them on first use. The plan calls it the single highest-value
 * performance item, and it is: it costs nothing at runtime and typically takes
 * 20–30% off a cold start.
 *
 * What is exercised here is what gets compiled, so this walks the paths a session
 * actually begins with — the library list and its scrolling — rather than only
 * launching and stopping at the first frame.
 */
class StartupBaselineProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndLibraryScroll() = rule.collect(
        packageName = PACKAGE,
        // The default is enough for the shape of a startup profile; more iterations
        // mostly re-record the same classes.
        maxIterations = 5,
        stableIterations = 3,
    ) {
        pressHome()
        startActivityAndWait()

        // Waits for content rather than a fixed sleep: an emulator under load takes
        // far longer than a phone, and a sleep that is long enough there wastes the
        // same time on every run.
        device.wait(Until.hasObject(By.scrollable(true)), CONTENT_TIMEOUT_MS)

        // Scrolling is included because a music library is a long list, and the
        // classes behind the first fling are exactly the ones worth compiling.
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
            repeat(SCROLL_PASSES) {
                list.fling(androidx.test.uiautomator.Direction.DOWN)
                device.waitForIdle()
            }
        }
    }

    private companion object {
        const val PACKAGE = "com.deox9.musicplayer"
        const val CONTENT_TIMEOUT_MS = 10_000L

        /** Keeps the fling clear of the gesture-navigation strip at the edges. */
        const val GESTURE_MARGIN_DIVISOR = 5
        const val SCROLL_PASSES = 3
    }
}
