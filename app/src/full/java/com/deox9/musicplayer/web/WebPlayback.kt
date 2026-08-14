// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.web

import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.feature.settings.SettingsViewModel
import com.deox9.musicplayer.settings.AppSettingsRepository
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream
import java.net.URLEncoder

/**
 * Web playback mode — the "full" flavour implementation.
 *
 * This mode is deliberately excluded from the `foss` flavour: it embeds a browser
 * pointed at YouTube and filters ad and tracker requests, which is incompatible
 * with F-Droid inclusion and with Google Play's policy on ad circumvention.
 */
object WebPlayback {
    const val IS_AVAILABLE = true

    @Composable
    fun Screen(
        searchQuery: String,
        isVisible: Boolean,
        onWebViewReady: (WebView) -> Unit,
    ) {
        WebPlaybackScreen(
            searchQuery = searchQuery,
            isVisible = isVisible,
            onWebViewReady = onWebViewReady,
        )
    }

    fun pause(webView: WebView?) = pauseWebPlayback(webView)
}

@Composable
private fun WebPlaybackScreen(
    searchQuery: String = "",
    isVisible: Boolean,
    onWebViewReady: (WebView) -> Unit
) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val appSettings by settingsViewModel.settings.collectAsState()
    var blockedRequestCount by rememberSaveable { mutableStateOf(0) }
    var lastBlockedHost by rememberSaveable { mutableStateOf<String?>(null) }
    var lastLoadError by rememberSaveable { mutableStateOf<String?>(null) }
    var fallbackTriggered by rememberSaveable { mutableStateOf(false) }
    var webViewSavedState by rememberSaveable { mutableStateOf<Bundle?>(null) }
    var restoredFromSavedState by rememberSaveable { mutableStateOf(false) }
    var currentWebUrl by rememberSaveable { mutableStateOf("") }

    val fallbackHomeUrl = remember(appSettings.webHomeUrl) {
        normalizeWebUrl(appSettings.webHomeUrl) ?: AppSettingsRepository.DEFAULT_WEB_HOME
    }

    val webView = remember {
        WebView(context).apply {
            onWebViewReady(this)
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            setOnTouchListener { view, _ ->
                view.requestFocus()
                false
            }
            webViewClient = HardenedWebViewClient(
                onBlocked = { blockedUrl ->
                    blockedRequestCount += 1
                    lastBlockedHost = Uri.parse(blockedUrl).host ?: blockedUrl
                },
                onMainFrameError = { code, description ->
                    lastLoadError = "Web load failed ($code): $description"
                    if (!fallbackTriggered) {
                        fallbackTriggered = true
                        loadUrl(fallbackHomeUrl)
                    }
                },
                onPageSuccess = { pageUrl ->
                    lastLoadError = null
                    if (!pageUrl.isNullOrBlank()) {
                        currentWebUrl = pageUrl
                    }
                    injectYouTubeAdSkipper(webView = this)
                }
            )
            webChromeClient = WebChromeClient()
            settings.javaScriptEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            val restored = webViewSavedState?.let { state ->
                restoreState(state)
            }
            if (restored == null) {
                val bootUrl = when {
                    currentWebUrl.isNotBlank() -> currentWebUrl
                    else -> AppSettingsRepository.DEFAULT_WEB_HOME
                }
                loadUrl(bootUrl)
            } else {
                restoredFromSavedState = true
            }
        }
    }

    LaunchedEffect(appSettings.webHomeUrl) {
        val homeUrl = normalizeWebUrl(appSettings.webHomeUrl) ?: AppSettingsRepository.DEFAULT_WEB_HOME
        val currentUrl = webView.url.orEmpty()
        // Nothing loaded yet, and nothing the user has asked for. Both halves matter:
        // loading the home page over a restored session throws away where they were,
        // and loading it over a search discards what they typed.
        val showingNothing = currentUrl.isBlank() || currentUrl == "about:blank"
        val userHasNotAskedForAnything = searchQuery.trim().length < 2 && !restoredFromSavedState
        if (showingNothing && userHasNotAskedForAnything) {
            fallbackTriggered = false
            webView.loadUrl(homeUrl)
        }
    }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.length >= 2) {
            delay(350)
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val target = "https://m.youtube.com/results?search_query=$encoded"
            val current = webView.url.orEmpty()
            if (current.contains("m.youtube.com/results") && current.contains("search_query=$encoded")) {
                return@LaunchedEffect
            }
            fallbackTriggered = false
            webView.loadUrl(target)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val state = Bundle()
            webView.saveState(state)
            webViewSavedState = state
            webView.stopLoading()
            webView.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = if (isVisible) Modifier.fillMaxSize() else Modifier.size(1.dp),
            factory = { webView }
        )

        if (isVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Ad-filter blocks: $blockedRequestCount",
                    style = MaterialTheme.typography.labelMedium
                )
                if (!lastBlockedHost.isNullOrBlank()) {
                    Text(
                        text = "Last blocked: $lastBlockedHost",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!lastLoadError.isNullOrBlank()) {
                    Text(
                        text = lastLoadError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(
                        onClick = {
                            fallbackTriggered = false
                            webView.reload()
                        }
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

private class HardenedWebViewClient(
    private val onBlocked: (String) -> Unit,
    private val onMainFrameError: (Int, String) -> Unit,
    private val onPageSuccess: (String?) -> Unit
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return super.shouldInterceptRequest(view, request)
        return if (shouldBlockWebResource(url.toString())) {
            onBlocked(url.toString())
            emptyBlockedResponse()
        } else {
            super.shouldInterceptRequest(view, request)
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: android.webkit.WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            onMainFrameError(error?.errorCode ?: -1, error?.description?.toString().orEmpty())
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageSuccess(url)
    }
}

private fun injectYouTubeAdSkipper(webView: WebView) {
    val script = """
            (function() {
                if (window.__deoAdSkipInstalled) return;
                window.__deoAdSkipInstalled = true;

                function clickIfVisible(el) {
                    if (!el) return false;
                    const style = window.getComputedStyle(el);
                    if (style && style.display !== 'none' && style.visibility !== 'hidden') {
                        try { el.click(); return true; } catch (e) { return false; }
                    }
                    return false;
                }

                function skipAds() {
                        // Desktop YouTube controls
                    clickIfVisible(document.querySelector('.ytp-ad-skip-button'));
                    clickIfVisible(document.querySelector('.ytp-ad-skip-button-modern'));
                    clickIfVisible(document.querySelector('.ytp-ad-overlay-close-button'));

                        // Mobile YouTube controls
                    clickIfVisible(document.querySelector('.ytmAdSkipButton'));
                    clickIfVisible(document.querySelector('button[aria-label*="Skip" i]'));
                    clickIfVisible(document.querySelector('button[aria-label*="Close" i]'));

                    const video = document.querySelector('video');
                    if (video) {
                        try { video.muted = false; } catch (e) {}
                    }
                }
                window.__deoAdSkipTimer = setInterval(skipAds, 400);
                document.addEventListener('visibilitychange', skipAds, { passive: true });
                skipAds();
            })();
    """.trimIndent()

    webView.evaluateJavascript(script, null)
}

private fun pauseWebPlayback(webView: WebView?) {
    if (webView == null) return
    val script = """
        (function() {
            const mediaNodes = document.querySelectorAll('video, audio');
            mediaNodes.forEach(function(node) {
                try {
                    node.pause();
                    node.muted = true;
                } catch (e) {}
            });
        })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
}

/**
 * Whether a request is an ad, a tracker or telemetry rather than the page itself.
 *
 * The lists are the whole substance and they are checked against four different
 * parts of the URL, which is why this reads as four passes rather than one
 * expression. Each returns early on a hit; the alternative — one long boolean — was
 * how it started and made it impossible to see which list a block came from.
 */
private fun shouldBlockWebResource(rawUrl: String): Boolean {
    val lower = rawUrl.lowercase()
    val uri = runCatching { android.net.Uri.parse(rawUrl) }.getOrNull()
    val host = uri?.host?.lowercase() ?: return false
    val query = uri.query?.lowercase().orEmpty()

    return AD_HOSTS.any { host.contains(it) } ||
        AD_PATHS.any { lower.contains(it) } ||
        AD_QUERY_PARAMS.any { query.contains(it) } ||
        AD_FILE_SUFFIXES.any { lower.endsWith(it) }
}

/** Ad, tracker and telemetry hosts. */
private val AD_HOSTS = listOf(
    // Google ad infrastructure
    "doubleclick.net", "pagead2.googlesyndication.com", "adservice.google",
    "googlesyndication.com", "googletagservices.com", "googletagmanager.com",

    // YouTube ad delivery
    "ads.youtube.com", "yt.be", "adx.g.doubleclick.net",

    // Third-party ad networks
    "ad.doubleclick.net", "ads4.google.com", "mads.google.com",
    "csi.gstatic.com", // Google client error/CSI tracking

    // Analytics and telemetry
    "google-analytics.com", "analytics.google.com", "www.googletagmanager.com",
    "stats.g.doubleclick.net",

    // Additional tracking services
    "tpc.googlesyndication.com", "www.gstatic.com/generate_204",
    "bat.bing.com", "c.bing.com",

    // YouTube specific tracking
    "yt-video-upload",
)

/** Paths that serve ads or carry logging, whatever host they are on. */
private val AD_PATHS = listOf(
    // YouTube ad delivery endpoints
    "/api/stats/ads", "/get_ads", "/api/ads", "/js/ads/",
    "/pagead/", "/gvt1/ads", "/ads?", "/ad_break", "ad_break=",

    // YouTube logging and telemetry
    "/log_event", "/api/stats", "/youtubei/v1/log_event",
    "/youtubei/v1/log", "/api/v1/log", "/reporting/", "tracking=",

    // Ad format and unit detection
    "adformat=", "adunit=", "instream_ad", "yt_ad", "ad_request",

    // Engagement metrics for ads
    "/api/v1/survey", "/ptracking", "pcs/active", "ping?",

    // Beacon tracking
    "beacon.scorecardresearch.com", "sb.scorecardresearch.com",

    // Redirect and measurement
    "/r/", "/t/", "doubleclick_tracking",
)

/** Query parameters that only appear on ad or tracking requests. */
private val AD_QUERY_PARAMS = listOf(
    "ad_", "ads_", "adunit", "adformat", "ad_type", "ad_client",
    "google_afc", "google_ad", "google_gd", "tracking", "utm_",
    "fbclid", "gclid", "msclkid", "igshid",
)

/** Video-ad and banner formats: VAST/VMAP and friends. */
private val AD_FILE_SUFFIXES = listOf("vmap.xml", ".vpaid", ".vast", "ads.js")

private fun emptyBlockedResponse(): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        "utf-8",
        204,
        "No Content",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0))
    )
}
