// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.web

import android.net.Uri

/**
 * Normalises user-entered URLs, defaulting a missing scheme to https.
 *
 * Lives in the shared source set rather than a flavour: it is a plain URL helper
 * used by settings, with no dependency on web playback being available.
 */
fun normalizeWebUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null

    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }

    return runCatching {
        Uri.parse(withScheme)
        withScheme
    }.getOrNull()
}
