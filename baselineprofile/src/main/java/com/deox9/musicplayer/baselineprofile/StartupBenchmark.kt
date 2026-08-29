// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * What the baseline profile is actually worth on this app.
 *
 * The plan quotes 20–30% off a cold start, and that is the plan's number, not a
 * measurement of this code. Shipping a profile and repeating someone else's figure
 * is the same mistake as claiming a feature works because it compiles.
 *
 * Two runs of the same journey, differing only in how the app was compiled:
 *
 * - [startupNoCompilation] runs with nothing compiled ahead of time, which is the
 *   worst case and the baseline to improve on.
 * - [startupWithBaselineProfile] runs with the shipped profile applied, which is
 *   what a real first launch from the Play Store gets.
 *
 * The difference between the two medians is the profile's contribution. Anything
 * else — a faster emulator, a warmer cache — moves both.
 */
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = measure(CompilationMode.None())

    @Test
    fun startupWithBaselineProfile() =
        measure(CompilationMode.Partial(baselineProfileMode = androidx.benchmark.macro.BaselineProfileMode.Require))

    /**
     * Measures cold start to first frame.
     *
     * COLD rather than WARM: the profile exists to help the run where nothing is
     * resident, and a warm start would mostly measure the process already being
     * alive.
     */
    private fun measure(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        // Ten, because startup timing is noisy on an emulator and a median over a
        // handful of runs still moves several percent between passes.
        iterations = 10,
        setupBlock = {
            pressHome()
            // Killed explicitly, or the second measurement is not a cold start at
            // all: this app runs a media session in a foreground service, so the
            // process outlives the activity and the framework's own teardown leaves
            // it alive. Macrobenchmark refuses to measure that, correctly.
            killProcess()
        },
    ) {
        startActivityAndWait()
        // Waits for real content, so the measurement ends when the library is on
        // screen rather than when a blank frame is drawn.
        device.wait(Until.hasObject(By.scrollable(true)), CONTENT_TIMEOUT_MS)
    }

    private companion object {
        const val PACKAGE = "com.deox9.musicplayer"
        const val CONTENT_TIMEOUT_MS = 10_000L
    }
}
