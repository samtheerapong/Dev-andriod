package com.example.carbrowser

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar

/**
 * The browser itself. The same activity runs on the phone and, through the CAR_LAUNCHER
 * intent filter, directly on the Android Auto screen as a "parked app" (Android 15+).
 * On the car it is a normal Activity: real touch, real scrolling, no Surface tricks.
 */
class BrowserActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webview)
        urlBar = findViewById(R.id.url_bar)
        progress = findViewById(R.id.progress)

        WebViewSetup.configure(webView) { url -> if (!urlBar.hasFocus()) urlBar.setText(url) }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        findViewById<Button>(R.id.btn_back).setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<Button>(R.id.btn_home).setOnClickListener { load(WebViewSetup.YOUTUBE_HOME) }
        findViewById<Button>(R.id.btn_reload).setOnClickListener { webView.reload() }
        findViewById<Button>(R.id.btn_sign_in).setOnClickListener { load(WebViewSetup.YOUTUBE_LOGIN) }

        urlBar.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || isEnter) {
                val text = urlBar.text.toString()
                if (text.isNotBlank()) load(WebViewSetup.urlFromInput(text))
                true
            } else {
                false
            }
        }

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            load(WebViewSetup.YOUTUBE_HOME)
        }
    }

    private fun load(url: String) {
        urlBar.clearFocus()
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(urlBar.windowToken, 0)
        webView.requestFocus()
        webView.loadUrl(url)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        // Persist login cookies right away; the phone and car instances share them.
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
