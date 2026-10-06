package com.example.carbrowser

import android.app.Presentation
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.Display
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ProgressBar

/**
 * A Presentation is a Dialog bound to a secondary Display. Bound to our private
 * VirtualDisplay, its window content ends up on the car screen.
 */
class BrowserPresentation(
    context: Context,
    display: Display,
    private val homeUrl: String,
) : Presentation(context, display, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen) {

    private var root: View? = null
    private var webView: WebView? = null
    private var progress: ProgressBar? = null
    private var contentInsets = Rect()

    val touch = TouchForwarder(rootView = { window?.decorView }, webView = { webView })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_webview)

        root = findViewById(R.id.root)
        progress = findViewById(R.id.progress)
        webView = findViewById<WebView>(R.id.webview).also {
            configure(it)
            it.loadUrl(homeUrl)
        }
        applyInsets()
    }

    override fun onStop() {
        // Called when the Presentation is dismissed (screen destroyed or display removed).
        touch.release()
        webView?.destroy()
        webView = null
        super.onStop()
    }

    /**
     * Keeps page content out of the strips Android Auto draws over the Surface
     * (action strip, map buttons, and on wide screens the side rail).
     */
    fun setContentInsets(insets: Rect) {
        contentInsets = Rect(insets)
        applyInsets()
    }

    private fun applyInsets() {
        root?.setPadding(contentInsets.left, contentInsets.top, contentInsets.right, contentInsets.bottom)
    }

    private fun configure(view: WebView) {
        WebViewSetup.configure(view)

        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress?.apply {
                    progress = newProgress
                    visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }
            }
        }
    }

    // ---- Navigation ---------------------------------------------------------------------

    fun goBack() {
        webView?.let { if (it.canGoBack()) it.goBack() }
    }

    fun reload() {
        webView?.reload()
    }

    fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    fun zoomIn() {
        webView?.zoomIn()
    }

    fun zoomOut() {
        webView?.zoomOut()
    }

    /** Pinch from the host. zoomBy() only accepts (0.01, 100) and zooms around the centre. */
    fun zoomBy(scaleFactor: Float) {
        webView?.zoomBy(scaleFactor.coerceIn(0.02f, 50f))
    }
}
