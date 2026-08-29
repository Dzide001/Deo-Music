// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

import java.util.concurrent.TimeUnit

/**
 * Formats a duration as `m:ss`, or `h:mm:ss` once it passes an hour.
 *
 * Shared because both the library lists and the player show track times, and they
 * live in different modules.
 */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"

    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
