package com.example.carbrowser

import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.annotation.OptIn
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Hosts the browser on the car screen.
 *
 * Pipeline:
 *   Android Auto host --(Surface)--> SurfaceCallback.onSurfaceAvailable
 *     --> DisplayManager.createVirtualDisplay(surface)   // every frame drawn on the VD lands in the car Surface
 *     --> BrowserPresentation(virtualDisplay.display)    // a Dialog-like window living on that VD
 *     --> WebView inside the Presentation
 *
 * Touch input travels the opposite way: SurfaceCallback gestures are forwarded into the
 * Presentation (see the "Touch forwarding" section below).
 */
class BrowserScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private val homeUrl = WebViewSetup.YOUTUBE_HOME

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: BrowserPresentation? = null

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var stableArea: Rect? = null
    private var contentInsets = Rect()

    init {
        // Registering a SurfaceCallback is only honoured for apps holding
        // androidx.car.app.ACCESS_SURFACE that show a map template.
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = releaseDisplay()
        })
    }

    /**
     * MapWithContentTemplate (Car API 7) gives POI-category apps the same raw Surface that
     * NavigationTemplate gives navigation apps. We use POI because Android Auto hides
     * sideloaded NAVIGATION apps from its launcher, while sideloaded POI/IOT apps still show.
     * The host requires a content card on top of the map; it holds the browser shortcuts, and
     * the stable area we pad the page with already excludes it.
     */
    @OptIn(ExperimentalCarApi::class)
    override fun onGetTemplate(): Template {
        val shortcuts = ListTemplate.Builder()
            .setTitle("Car Browser")
            .setSingleList(
                ItemList.Builder()
                    .addItem(row("Search YouTube") { openSearch() })
                    .addItem(row("Home") { presentation?.loadUrl(homeUrl) })
                    .addItem(row("Subscriptions") { presentation?.loadUrl(WebViewSetup.YOUTUBE_SUBSCRIPTIONS) })
                    .addItem(row("Library") { presentation?.loadUrl(WebViewSetup.YOUTUBE_LIBRARY) })
                    .build(),
            )
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(textAction("Back") { presentation?.goBack() })
            .addAction(textAction("Reload") { presentation?.reload() })
            .build()

        // PAN puts the host into pan mode, which is when drag gestures are delivered to onScroll.
        val mapActionStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(iconAction(R.drawable.ic_zoom_in) { presentation?.zoomIn() })
            .addAction(iconAction(R.drawable.ic_zoom_out) { presentation?.zoomOut() })
            .build()

        return MapWithContentTemplate.Builder()
            .setContentTemplate(shortcuts)
            .setActionStrip(actionStrip)
            .setMapController(MapController.Builder().setMapActionStrip(mapActionStrip).build())
            .build()
    }

    /** Host keyboard input (SearchTemplate) -> YouTube results page in the WebView. */
    private fun openSearch() {
        screenManager.pushForResult(SearchScreen(carContext)) { result ->
            (result as? String)?.let { presentation?.loadUrl(WebViewSetup.youtubeSearchUrl(it)) }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Surface -> VirtualDisplay -> Presentation
    // ---------------------------------------------------------------------------------------

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        val width = surfaceContainer.width
        val height = surfaceContainer.height
        val dpi = CarDisplayProfile.chooseDpi(width, height, surfaceContainer.dpi)
        surfaceWidth = width
        surfaceHeight = height

        Log.i(TAG, "Surface ${width}x$height hostDpi=${surfaceContainer.dpi} -> virtual dpi=$dpi")

        val existing = virtualDisplay
        if (existing != null) {
            // Surface was recreated (e.g. the app came back to the foreground) or resized.
            // Re-point the existing display so the WebView keeps its page instead of reloading.
            existing.resize(width, height, dpi)
            existing.surface = surface
            updateContentInsets()
            return
        }

        val displayManager = carContext.getSystemService(DisplayManager::class.java)

        // A *private* virtual display (no VIRTUAL_DISPLAY_FLAG_PUBLIC) owned by our UID.
        // Android lets the owner show a Presentation on it (TYPE_PRIVATE_PRESENTATION window)
        // without any overlay permission, and everything rendered on it is composited
        // directly into the Surface the car host gave us.
        val display = displayManager.createVirtualDisplay(
            "CarBrowserDisplay",
            width,
            height,
            dpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
        virtualDisplay = display

        presentation = BrowserPresentation(carContext, display.display, homeUrl).also { it.show() }
        updateContentInsets()
    }

    /**
     * Area the host guarantees it will not cover with its own UI (action strip, map buttons,
     * side rail on wide/portrait screens such as Ford SYNC 4A). Using the *stable* area rather
     * than the visible area avoids re-laying out the page every time the strips fade in/out.
     */
    override fun onStableAreaChanged(stableArea: Rect) {
        Log.i(TAG, "Stable area $stableArea in ${surfaceWidth}x$surfaceHeight")
        this.stableArea = Rect(stableArea)
        updateContentInsets()
    }

    /** Converts the stable-area rect into padding; re-run whenever surface size or area changes. */
    private fun updateContentInsets() {
        val area = stableArea
        contentInsets = if (!CarDisplayProfile.AVOID_HOST_OVERLAYS || area == null || area.isEmpty || surfaceWidth == 0) {
            Rect()
        } else {
            Rect(
                area.left.coerceAtLeast(0),
                area.top.coerceAtLeast(0),
                (surfaceWidth - area.right).coerceAtLeast(0),
                (surfaceHeight - area.bottom).coerceAtLeast(0),
            )
        }
        presentation?.setContentInsets(contentInsets)
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        Log.d(TAG, "Visible area $visibleArea")
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        // Detach only. The VirtualDisplay and WebView stay alive (frames are dropped)
        // so the page survives the car UI switching away and back.
        presentation?.touch?.release()
        virtualDisplay?.surface = null
    }

    private fun releaseDisplay() {
        presentation?.touch?.release()
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    // ---------------------------------------------------------------------------------------
    // Touch forwarding
    //
    // The host never gives us raw MotionEvents; it pre-digests gestures into these callbacks.
    // The VirtualDisplay has exactly the Surface's size, so host x/y are already valid
    // window coordinates. TouchForwarder rebuilds real finger events from them.
    // ---------------------------------------------------------------------------------------

    /** Single tap (host Car API level 5+). */
    override fun onClick(x: Float, y: Float) {
        presentation?.touch?.tap(x, y)
    }

    /** Drag (pan mode on older hosts). distanceX/Y follow GestureDetector semantics (old - new). */
    override fun onScroll(distanceX: Float, distanceY: Float) {
        presentation?.touch?.scroll(distanceX, distanceY)
    }

    /** Finger released with velocity, in px/s in the finger's direction. */
    override fun onFling(velocityX: Float, velocityY: Float) {
        presentation?.touch?.fling(velocityX, velocityY)
    }

    /** Pinch zoom, or a double tap (reported with a negative scale factor). */
    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        val p = presentation ?: return
        if (scaleFactor < 0f) p.touch.doubleTap(focusX, focusY) else p.zoomBy(scaleFactor)
    }

    // ---------------------------------------------------------------------------------------

    private fun textAction(title: String, onClick: () -> Unit): Action =
        Action.Builder()
            .setTitle(title)
            .setOnClickListener(onClick)
            .build()

    private fun row(title: String, onClick: () -> Unit): Row =
        Row.Builder()
            .setTitle(title)
            .setOnClickListener(onClick)
            .build()

    private fun iconAction(iconRes: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build())
            .setOnClickListener(onClick)
            .build()

    private companion object {
        const val TAG = "CarBrowser"
    }
}
