// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.web

import android.webkit.WebView
import androidx.compose.runtime.Composable

/**
 * Web playback mode — the "foss" flavour stub.
 *
 * The real implementation embeds a browser pointed at YouTube and filters ad and
 * tracker requests. That is incompatible with F-Droid inclusion and with Google
 * Play's policy on ad circumvention, so this flavour ships without it.
 *
 * Callers gate on [IS_AVAILABLE]; when it is false the Web entry is not shown and
 * [Screen] is never composed.
 */
object WebPlayback {
    const val IS_AVAILABLE = false

    @Composable
    fun Screen(
        @Suppress("UNUSED_PARAMETER") searchQuery: String,
        @Suppress("UNUSED_PARAMETER") isVisible: Boolean,
        @Suppress("UNUSED_PARAMETER") onWebViewReady: (WebView) -> Unit,
    ) {
        // No web playback in the FOSS build.
    }

    fun pause(@Suppress("UNUSED_PARAMETER") webView: WebView?) = Unit
}
