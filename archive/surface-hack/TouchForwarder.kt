package com.example.carbrowser

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns the host's pre-digested gestures (SurfaceCallback) back into a stream of real
 * finger MotionEvents, so Chromium handles them with its own gesture pipeline:
 * native smooth scrolling, inner scroll containers, momentum, and :active/tap states.
 *
 * Scroll strategy: the host sends only deltas, never DOWN/UP. We keep one "virtual finger":
 *   first onScroll        -> ACTION_DOWN at an anchor point
 *   every onScroll        -> ACTION_MOVE by the delta (stamped with real arrival time)
 *   onFling               -> ACTION_UP immediately, so Chromium flings with the velocity it tracked
 *   no onScroll for 100ms -> ACTION_UP stamped >= 40 ms after the last MOVE, so velocity
 *                            trackers see a stopped pointer and do not fling
 *
 * All calls must happen on the main thread (SurfaceCallback already does).
 */
class TouchForwarder(
    private val rootView: () -> View?,
    private val webView: () -> WebView?,
) {
    private val handler = Handler(Looper.getMainLooper())

    private var pendingTapUp: Runnable? = null

    private var dragging = false
    private var dragDownTime = 0L
    private var lastEventTime = 0L
    private var fingerX = 0f
    private var fingerY = 0f
    private val area = Rect()

    private val endDragOnIdle = Runnable { finishDrag(withFling = false) }

    // ---- Taps -------------------------------------------------------------------------------

    fun tap(x: Float, y: Float) {
        finishDrag(withFling = false) // a tap during momentum must not be read as part of a drag
        flushPendingTap()

        val downTime = nextEventTime()
        send(MotionEvent.ACTION_DOWN, downTime, downTime, x, y)

        // UP is posted, not sent inline: Chromium needs a short press to classify it as a tap
        // (and to paint the pressed state); a zero-length press is sometimes dropped.
        val up = Runnable {
            pendingTapUp = null
            send(MotionEvent.ACTION_UP, downTime, nextEventTime(), x, y)
        }
        pendingTapUp = up
        handler.postDelayed(up, TAP_PRESS_MS)
    }

    /** Two real taps: on YouTube this seeks ±10 s, on normal pages it is double-tap zoom. */
    fun doubleTap(x: Float, y: Float) {
        tap(x, y)
        handler.postDelayed({ tap(x, y) }, DOUBLE_TAP_GAP_MS)
    }

    private fun flushPendingTap() {
        pendingTapUp?.let {
            handler.removeCallbacks(it)
            it.run()
        }
    }

    // ---- Scroll / fling ---------------------------------------------------------------------

    /** distanceX/Y use GestureDetector semantics (old - new), so the finger moves by -distance. */
    fun scroll(distanceX: Float, distanceY: Float) {
        flushPendingTap()
        val dx = -distanceX
        val dy = -distanceY

        // Virtual finger would leave the content: lift it and put it down again at a fresh anchor.
        if (dragging && !area.contains((fingerX + dx).roundToInt(), (fingerY + dy).roundToInt())) {
            finishDrag(withFling = false)
        }
        if (!dragging && !startDrag(dx, dy)) return

        fingerX += dx
        fingerY += dy
        send(MotionEvent.ACTION_MOVE, dragDownTime, nextEventTime(), fingerX, fingerY)

        handler.removeCallbacks(endDragOnIdle)
        handler.postDelayed(endDragOnIdle, DRAG_IDLE_TIMEOUT_MS)
    }

    fun fling(velocityX: Float, velocityY: Float) {
        if (dragging) {
            // Chromium already has the MOVE history; releasing now gives a native fling.
            finishDrag(withFling = true)
        } else {
            // Fling without a preceding drag (some hosts): fall back to the root scroller.
            webView()?.flingScroll(-velocityX.roundToInt(), -velocityY.roundToInt())
        }
    }

    /**
     * Puts the finger down on the side opposite to the motion, so a long swipe has the
     * whole content area to travel before it needs re-anchoring.
     */
    private fun startDrag(dx: Float, dy: Float): Boolean {
        val view = webView() ?: return false
        if (!view.getGlobalVisibleRect(area) || area.isEmpty) return false
        area.inset(
            (area.width() * EDGE_MARGIN_FRACTION).toInt(),
            (area.height() * EDGE_MARGIN_FRACTION).toInt(),
        )

        fingerX = when {
            dx < 0 -> area.right.toFloat()
            dx > 0 -> area.left.toFloat()
            else -> area.exactCenterX()
        }
        fingerY = when {
            dy < 0 -> area.bottom.toFloat()
            dy > 0 -> area.top.toFloat()
            else -> area.exactCenterY()
        }

        dragDownTime = nextEventTime()
        send(MotionEvent.ACTION_DOWN, dragDownTime, dragDownTime, fingerX, fingerY)
        dragging = true
        return true
    }

    private fun finishDrag(withFling: Boolean) {
        if (!dragging) return
        handler.removeCallbacks(endDragOnIdle)
        dragging = false

        val lastMoveTime = lastEventTime
        val upTime = if (withFling) {
            nextEventTime()
        } else {
            // Android's and Chromium's velocity trackers treat a pointer silent for 40 ms as
            // stopped; stamping UP past that guarantees zero release velocity.
            max(nextEventTime(), lastMoveTime + POINTER_STOPPED_MS)
        }
        lastEventTime = upTime
        send(MotionEvent.ACTION_UP, dragDownTime, upTime, fingerX, fingerY)
    }

    // ---- Plumbing ---------------------------------------------------------------------------

    /** Cancels anything in flight; call before the window goes away. */
    fun release() {
        flushPendingTap() // never leave the page with a finger stuck down
        handler.removeCallbacksAndMessages(null)
        if (dragging) {
            dragging = false
            send(MotionEvent.ACTION_CANCEL, dragDownTime, nextEventTime(), fingerX, fingerY)
        }
    }

    /** Monotonic timestamps: never earlier than an event we already stamped into the future. */
    private fun nextEventTime(): Long = max(SystemClock.uptimeMillis(), lastEventTime + 1)
        .also { lastEventTime = it }

    private fun send(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val target = rootView() ?: return

        // Full obtain() so the pointer reports TOOL_TYPE_FINGER and real pressure; the simple
        // overload leaves the tool type unknown, which Chromium may treat as a non-touch input.
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        })
        val event = MotionEvent.obtain(
            downTime, eventTime, action, 1, properties, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        // Root view coordinates == VirtualDisplay coordinates == car Surface coordinates,
        // so normal view dispatch delivers this to whatever is under (x, y).
        target.dispatchTouchEvent(event)
        event.recycle()
    }

    private companion object {
        const val TAP_PRESS_MS = 50L
        const val DOUBLE_TAP_GAP_MS = 120L
        const val DRAG_IDLE_TIMEOUT_MS = 100L
        const val POINTER_STOPPED_MS = 50L
        const val EDGE_MARGIN_FRACTION = 0.1f
    }
}
