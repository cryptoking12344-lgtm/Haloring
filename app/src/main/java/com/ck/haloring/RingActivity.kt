package com.ck.haloring

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout

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

        val geo = RingGeometry.compute(this)
        val v = RingView(this)
        v.configure(geo.color, geo.radius, geo.stroke, geo.periodMs)
        val lp = FrameLayout.LayoutParams(geo.box, geo.box)
        lp.leftMargin = geo.x
        lp.topMargin = geo.y
        root.addView(v, lp)
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
        ring?.setAnimating(true)
        arm(intent)
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
