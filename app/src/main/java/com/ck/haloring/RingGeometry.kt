package com.ck.haloring

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/** Ring position and look, in screen pixels, shared by the overlay and the black screen. */
class RingGeometry(
    val x: Int,
    val y: Int,
    val box: Int,
    val radius: Float,
    val stroke: Float,
    val periodMs: Long,
    val color: Int
) {
    companion object {
        fun compute(c: Context): RingGeometry {
            val density = c.resources.displayMetrics.density
            fun dp(v: Float): Float = v * density

            val sp = Prefs.sp(c)
            val size = sp.getInt(Prefs.SIZE, Prefs.DEF_SIZE)
            val thick = sp.getInt(Prefs.THICK, Prefs.DEF_THICK)
            val dx = sp.getInt(Prefs.DX, Prefs.DEF_DX)
            val dy = sp.getInt(Prefs.DY, Prefs.DEF_DY)
            val speed = sp.getInt(Prefs.SPEED, Prefs.DEF_SPEED)
            val color = sp.getInt(Prefs.COLOR, Prefs.DEF_COLOR)

            // Find the camera hole from the display cutout; fall back to top centre.
            var cx = c.resources.displayMetrics.widthPixels / 2f
            var cy = dp(22f)
            var baseRadius = dp(10f)
            try {
                val dm = c.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val rect = dm.getDisplay(Display.DEFAULT_DISPLAY)?.cutout?.boundingRectTop
                if (rect != null && !rect.isEmpty) {
                    cx = rect.exactCenterX()
                    cy = rect.exactCenterY()
                    baseRadius = minOf(rect.width(), rect.height()) / 2f
                }
            } catch (e: Exception) {
            }

            cx += dp((dx - 50) * 0.5f)
            cy += dp((dy - 50) * 0.5f)
            val stroke = dp((thick + 2) / 2f)
            var radius = baseRadius + dp((size - 20) / 2f)
            if (radius < dp(4f)) radius = dp(4f)
            val periodMs = 4000L - speed * 35L
            val box = (2f * (radius + stroke) + dp(4f)).toInt()

            return RingGeometry(
                (cx - box / 2f).toInt(),
                (cy - box / 2f).toInt(),
                box, radius, stroke, periodMs, color
            )
        }
    }
}
