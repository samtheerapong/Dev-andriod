package com.example.carbrowser

import android.util.DisplayMetrics
import kotlin.math.min

/**
 * Tuning for the car head unit. Defaults target large, high-resolution screens such as
 * Ford SYNC 4/4A (Ranger/Everest Next-Gen 10"/12" portrait and wide landscape units),
 * where Android Auto may project at 720p/1080p with a host DPI that makes web pages either
 * tiny or cramped. Check `adb logcat -s CarBrowser` for the real surface size/DPI and tweak.
 */
object CarDisplayProfile {

    /** Multiplier on the host-reported DPI. > 1 = bigger text/touch targets, < 1 = more content. */
    const val DENSITY_SCALE = 1.0f

    /**
     * Smallest layout width/height (in dp) the page gets. Below ~360 dp m.youtube.com
     * collapses its layout; the DPI is lowered to guarantee at least this much space.
     */
    const val MIN_SHORT_SIDE_DP = 360

    /** Never go below this DPI, or text becomes unreadable at arm's length. */
    const val MIN_DPI = 120

    /** Pad the page so the host's action strip / map buttons / rail never cover it. */
    const val AVOID_HOST_OVERLAYS = true

    fun chooseDpi(width: Int, height: Int, hostDpi: Int): Int {
        val base = (if (hostDpi > 0) hostDpi else DisplayMetrics.DENSITY_DEFAULT) * DENSITY_SCALE
        val maxDpiForLayout = min(width, height) * DisplayMetrics.DENSITY_DEFAULT / MIN_SHORT_SIDE_DP
        return base.toInt().coerceAtMost(maxDpiForLayout).coerceAtLeast(MIN_DPI)
    }
}
