package com.ck.haloring

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Owns the overlay window. An accessibility overlay is used because it is the
 * window type that can draw on top of the lock screen / Always On Display.
 * The service asks for no accessibility events, so it is idle unless a ring is showing.
 */
class RingService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: RingService? = null

        /** Animate this long per notification, then hold a static ring (no CPU). */
        const val ANIM_MS = 20_000L
        const val PREVIEW_MS = 6_000L
        const val TEST_DELAY_MS = 8_000L
        const val TEST_CHANNEL = "halo_test"
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var pm: PowerManager
    private lateinit var wm: WindowManager

    private var view: RingView? = null
    private var animLock: PowerManager.WakeLock? = null
    private var testLock: PowerManager.WakeLock? = null
    private var receiverRegistered = false

    private val stopAnim = Runnable {
        view?.setAnimating(false)
        releaseAnimLock()
    }
    private val hideRun = Runnable { hide() }
    private val testRun = Runnable { postTestNotification() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> update(true)
                Intent.ACTION_SCREEN_ON -> hide()
                Intent.ACTION_USER_PRESENT -> {
                    Pending.keys.clear()
                    hide()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        receiverRegistered = true
        instance = this
        update(false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        hide()
        if (receiverRegistered) {
            receiverRegistered = false
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
            }
        }
        try {
            testLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
        }
    }

    /** Called when the pending set or the screen state changes. */
    fun update(restartAnimation: Boolean) {
        handler.post {
            val shouldShow = Pending.keys.isNotEmpty() && !pm.isInteractive
            if (!shouldShow) {
                hide()
            } else if (view == null) {
                show(false)
            } else if (restartAnimation) {
                startAnimation()
            }
        }
    }

    /** Shows the ring for a few seconds with the screen on, for adjusting. */
    fun preview() {
        handler.post {
            hide()
            show(true)
        }
    }

    /** Posts a real notification after a delay so the ring can be tested alone. */
    fun testSoon() {
        try {
            val lock = testLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "haloring:test").also {
                it.setReferenceCounted(false)
                testLock = it
            }
            lock.acquire(TEST_DELAY_MS + 4_000L)
        } catch (e: Exception) {
        }
        handler.removeCallbacks(testRun)
        handler.postDelayed(testRun, TEST_DELAY_MS)
    }

    private fun postTestNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(TEST_CHANNEL, "Test", NotificationManager.IMPORTANCE_HIGH)
            )
            val n = Notification.Builder(this, TEST_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Halo Ring test")
                .setContentText("If the ring is showing around the camera, it works.")
                .setAutoCancel(true)
                .build()
            nm.notify(1, n)
        } catch (e: Exception) {
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun show(isPreview: Boolean) {
        val sp = Prefs.sp(this)
        val size = sp.getInt(Prefs.SIZE, Prefs.DEF_SIZE)
        val thick = sp.getInt(Prefs.THICK, Prefs.DEF_THICK)
        val dx = sp.getInt(Prefs.DX, Prefs.DEF_DX)
        val dy = sp.getInt(Prefs.DY, Prefs.DEF_DY)
        val speed = sp.getInt(Prefs.SPEED, Prefs.DEF_SPEED)
        val color = sp.getInt(Prefs.COLOR, Prefs.DEF_COLOR)

        // Find the camera hole from the display cutout; fall back to top centre.
        var cx = resources.displayMetrics.widthPixels / 2f
        var cy = dp(22f)
        var baseRadius = dp(10f)
        try {
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
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

        val lp = WindowManager.LayoutParams(
            box, box,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (cx - box / 2f).toInt()
        lp.y = (cy - box / 2f).toInt()
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        lp.fitInsetsTypes = 0
        lp.title = "HaloRing"

        val v = RingView(this)
        v.configure(color, radius, stroke, periodMs)
        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            return
        }
        view = v
        startAnimation()

        if (isPreview) {
            handler.postDelayed(hideRun, PREVIEW_MS)
        }
    }

    private fun startAnimation() {
        val v = view ?: return
        handler.removeCallbacks(stopAnim)
        v.setAnimating(true)
        // The CPU sleeps with the screen off, so hold it awake only while animating.
        try {
            val lock = animLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "haloring:anim").also {
                it.setReferenceCounted(false)
                animLock = it
            }
            lock.acquire(ANIM_MS + 1_000L)
        } catch (e: Exception) {
        }
        handler.postDelayed(stopAnim, ANIM_MS)
    }

    private fun releaseAnimLock() {
        try {
            animLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
        }
    }

    private fun hide() {
        handler.removeCallbacks(stopAnim)
        handler.removeCallbacks(hideRun)
        releaseAnimLock()
        val v = view ?: return
        view = null
        try {
            wm.removeView(v)
        } catch (e: Exception) {
        }
    }
}
