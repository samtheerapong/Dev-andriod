package com.example.carbrowser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLEncoder

/** Shared WebView config for the browser activity and the car Presentation. */
object WebViewSetup {

    const val YOUTUBE_HOME = "https://m.youtube.com/"
    const val YOUTUBE_SUBSCRIPTIONS = "https://m.youtube.com/feed/subscriptions"
    const val YOUTUBE_LIBRARY = "https://m.youtube.com/feed/library"

    const val YOUTUBE_LOGIN =
        "https://accounts.google.com/ServiceLogin?service=youtube&passive=true" +
            "&continue=https%3A%2F%2Fm.youtube.com%2F"

    fun youtubeSearchUrl(query: String): String =
        "https://m.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")

    /** Address-bar input: a URL-looking string is opened, anything else becomes a YouTube search. */
    fun urlFromInput(input: String): String {
        val text = input.trim()
        return when {
            text.startsWith("http://") || text.startsWith("https://") -> text
            ' ' !in text && '.' in text -> "https://$text"
            else -> youtubeSearchUrl(text)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(view: WebView, onPageStarted: (String) -> Unit = {}) {
        with(view.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false

            // Google refuses sign-in from user agents that identify as an embedded WebView
            // ("; wv" token and "Version/4.0"). Present as regular mobile Chrome instead.
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+(\\.\\d+)* "), "")
        }

        // Both WebViews live in the same process, so they share this cookie jar:
        // signing in on the phone keeps the car browser signed in too.
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }

        view.webViewClient = object : WebViewClient() {
            // Keep http(s) in the WebView; drop intent:, market:, vnd.youtube: etc. which would
            // try to hand off to other apps instead of loading here.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.scheme !in setOf("http", "https")

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                onPageStarted(url)
            }
        }
    }
}
