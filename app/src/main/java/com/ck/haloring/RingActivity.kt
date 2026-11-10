package com.ck.haloring

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Ring-only mode: a pure black screen over the lock screen with just the ring.
 * On an OLED panel the black pixels are off, so only the ring is lit.
 * It turns the screen back off by itself when the burst ends.
 */
class RingActivity : Activity() {

    companion object {
        @Volatile
        var current: RingActivity? = null
        const val EXTRA_MS = "ms"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ring: RingView? = null
    private var missedIcon: ImageView? = null
    private val endRun = Runnable { end() }
    private val finishRun = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        val attrs = window.attributes
        attrs.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        window.attributes = attrs
        window.setDecorFitsSystemWindows(false)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        root.setOnClickListener { finish() }

        val geo = RingGeometry.compute(this, Pending.color())
        val v = RingView(this)
        v.configure(geo.color, geo.radius, geo.stroke, geo.periodMs, geo.style, geo.ripple)
        val lp = FrameLayout.LayoutParams(geo.box, geo.box)
        lp.leftMargin = geo.x
        lp.topMargin = geo.y
        root.addView(v, lp)

        val icon = ImageView(this)
        icon.setImageResource(R.drawable.ic_missed_call)
        root.addView(icon, FrameLayout.LayoutParams(0, 0))
        missedIcon = icon
        placeMissedIcon(geo)
        setContentView(root)
        ring = v
        v.setAnimating(true)

        val controller = window.insetsController
        if (controller != null) {
            controller.hide(WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        arm(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // A newer notification arrived: switch to its colour.
        val geo = RingGeometry.compute(this, Pending.color())
        ring?.configure(geo.color, geo.radius, geo.stroke, geo.periodMs, geo.style, geo.ripple)
        ring?.setAnimating(true)
        placeMissedIcon(geo)
        arm(intent)
    }

    /** The missed-call icon sits just to the right of the ring, in the ring's colour. */
    private fun placeMissedIcon(geo: RingGeometry) {
        val icon = missedIcon ?: return
        if (!Pending.hasMissedCall()) {
            icon.visibility = View.GONE
            return
        }
        val density = resources.displayMetrics.density
        var size = (geo.radius * 1.7f).toInt()
        val minSize = (18f * density).toInt()
        if (size < minSize) size = minSize
        val cx = geo.x + geo.box / 2f
        val cy = geo.y + geo.box / 2f
        val lp = FrameLayout.LayoutParams(size, size)
        lp.leftMargin = (cx + geo.radius + geo.stroke + 8f * density).toInt()
        lp.topMargin = (cy - size / 2f).toInt()
        icon.layoutParams = lp
        icon.setColorFilter(geo.color)
        icon.visibility = View.VISIBLE
    }

    private fun arm(intent: Intent?) {
        handler.removeCallbacks(endRun)
        handler.removeCallbacks(finishRun)
        val ms = intent?.getLongExtra(EXTRA_MS, 8_000L) ?: 8_000L
        handler.postDelayed(endRun, ms)
    }

    /** Turn the screen off again, then close. */
    private fun end() {
        val service = RingService.instance
        val locked = service != null && service.lockScreen()
        handler.postDelayed(finishRun, if (locked) 1_500L else 0L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (current === this) current = null
        super.onDestroy()
    }
}
