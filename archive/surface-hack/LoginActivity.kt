package com.example.carbrowser

import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView

/**
 * Phone-side screen for signing in to YouTube. The car display has no keyboard while
 * driving, so log in here once; the session cookie is shared with the car WebView.
 */
class LoginActivity : Activity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        WebViewSetup.configure(webView)
        if (savedInstanceState == null) {
            webView.loadUrl(WebViewSetup.YOUTUBE_LOGIN)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        // Persist login cookies to disk right away so the car session sees them.
        CookieManager.getInstance().flush()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
