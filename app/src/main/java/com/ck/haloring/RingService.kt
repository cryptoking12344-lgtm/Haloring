package com.ck.haloring

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Decides when the ring shows.
 *
 * Ring-only mode (default): opens RingActivity, a black screen with just the ring,
 * for a short burst, turns the screen off again, and repeats every few minutes
 * until the phone is woken.
 *
 * AOD mode (experimental): draws the ring as an overlay on Samsung's Always On Display.
 *
 * The service asks for no accessibility events, so it is idle between notifications.
 */
class RingService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: RingService? = null

        const val REPEAT_BURST_MS = 4_000L
        const val REPEAT_EVERY_MS = 120_000L
        const val REPEAT_WINDOW_MS = 60L * 60L * 1000L

        const val AOD_ANIM_MS = 20_000L
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

    private var lastNewAt = 0L
    private var selfWakeUntil = 0L

    /** True when something arrived while the screen was lit but locked (e.g. a missed call). */
    private var deferred = false

    /** True when the screen is going off because we asked it to. */
    private var selfLock = false

    private fun untilSeen(): Boolean = Prefs.durationMs(this) <= 0L

    private val stopAnim = Runnable {
        view?.setAnimating(false)
        releaseAnimLock()
    }
    private val hideRun = Runnable { hideOverlay() }
    private val testRun = Runnable { postTestNotification() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (aodMode()) {
                        updateAod(true)
                    } else {
                        val act = RingActivity.current
                        val ours = selfLock
                        selfLock = false
                        if (act != null) {
                            act.finish()
                            // Power button pressed while the ring was showing: treat as dismissed.
                            if (!ours && untilSeen()) seen()
                        } else if (!Pending.isEmpty() && (deferred || untilSeen())) {
                            handler.postDelayed({
                                if (!Pending.isEmpty() && !pm.isInteractive && RingActivity.current == null) {
                                    lastNewAt = SystemClock.elapsedRealtime()
                                    burst(Prefs.durationMs(this@RingService))
                                }
                            }, 1_200L)
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (aodMode()) {
                        hideOverlay()
                    } else if (SystemClock.elapsedRealtime() > selfWakeUntil &&
                        RingActivity.current == null
                    ) {
                        // The user woke the phone themselves, so they have seen it.
                        seen()
                    }
                }
                Intent.ACTION_USER_PRESENT -> seen()
            }
        }
    }

    private fun aodMode(): Boolean = Prefs.sp(this).getBoolean(Prefs.AOD_MODE, false)

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
        hideOverlay()
        cancelRepeat()
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

    // ---- events from the notification listener and the alarm ----

    fun onNewNotification() {
        handler.post {
            lastNewAt = SystemClock.elapsedRealtime()
            if (aodMode()) updateAod(true) else burst(Prefs.durationMs(this))
        }
    }

    /** Arrived while the screen was lit but locked: show the ring once the screen goes off. */
    fun onDeferred() {
        handler.post { deferred = true }
    }

    fun onRemoved() {
        handler.post {
            if (Pending.isEmpty()) {
                cancelRepeat()
                hideOverlay()
                RingActivity.current?.endNow()
            } else {
                // Something is still unseen: switch to its colour and icon.
                RingActivity.current?.refresh()
            }
        }
    }

    fun onRepeat() {
        handler.post {
            if (Pending.isEmpty() || aodMode()) return@post
            if (pm.isInteractive) return@post
            burst(REPEAT_BURST_MS)
        }
    }

    private fun seen() {
        deferred = false
        Pending.clear()
        cancelRepeat()
        hideOverlay()
    }

    // ---- ring-only mode ----

    private fun burst(ms: Long) {
        if (Pending.isEmpty()) return
        if (pm.isInteractive && RingActivity.current == null) return
        deferred = false
        // ms <= 0 means "keep going until seen".
        selfWakeUntil = SystemClock.elapsedRealtime() + (if (ms <= 0L) 5_000L else ms + 2_500L)
        try {
            val i = Intent(this, RingActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.putExtra(RingActivity.EXTRA_MS, ms)
            startActivity(i)
        } catch (e: Exception) {
        }
        if (ms > 0L) scheduleRepeat() else cancelRepeat()
    }

    /** Same effect as pressing the power button; fingerprint unlock still works. */
    fun lockScreen(): Boolean {
        selfLock = true
        val ok = try {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } catch (e: Exception) {
            false
        }
        if (!ok) selfLock = false
        return ok
    }

    private fun repeatIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            this, 0, Intent(this, RepeatReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleRepeat() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNewAt > REPEAT_WINDOW_MS) return
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = now + REPEAT_EVERY_MS
        val pi = repeatIntent()
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            }
        } catch (e: Exception) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            } catch (e2: Exception) {
            }
        }
    }

    private fun cancelRepeat() {
        try {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(repeatIntent())
        } catch (e: Exception) {
        }
    }

    // ---- AOD mode and preview: overlay window ----

    private fun updateAod(restartAnimation: Boolean) {
        val shouldShow = !Pending.isEmpty() && !pm.isInteractive
        if (!shouldShow) {
            hideOverlay()
        } else if (view == null) {
            showOverlay(false, null)
        } else if (restartAnimation) {
            startOverlayAnimation()
        }
    }

    /** Shows the ring for a few seconds with the screen on, for adjusting. */
    fun preview(color: Int? = null) {
        handler.post {
            hideOverlay()
            showOverlay(true, color)
        }
    }

    private fun showOverlay(isPreview: Boolean, previewColor: Int? = null) {
        val geo = RingGeometry.compute(this, if (isPreview) previewColor else Pending.color())

        val lp = WindowManager.LayoutParams(
            geo.box, geo.box,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = geo.x
        lp.y = geo.y
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        lp.fitInsetsTypes = 0
        lp.title = "HaloRing"

        val v = RingView(this)
        v.configure(geo.color, geo.radius, geo.stroke, geo.periodMs, geo.style, geo.ripple)
        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            return
        }
        view = v
        startOverlayAnimation()

        if (isPreview) {
            handler.postDelayed(hideRun, PREVIEW_MS)
        }
    }

    private fun startOverlayAnimation() {
        val v = view ?: return
        handler.removeCallbacks(stopAnim)
        v.setAnimating(true)
        // The CPU sleeps with the screen off, so hold it awake only while animating.
        try {
            val lock = animLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "haloring:anim").also {
                it.setReferenceCounted(false)
                animLock = it
            }
            lock.acquire(AOD_ANIM_MS + 1_000L)
        } catch (e: Exception) {
        }
        handler.postDelayed(stopAnim, AOD_ANIM_MS)
    }

    private fun releaseAnimLock() {
        try {
            animLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
        }
    }

    private fun hideOverlay() {
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

    // ---- self test ----

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
                .setContentText("If the ring showed around the camera, it works.")
                .setAutoCancel(true)
                .build()
            nm.notify(1, n)
        } catch (e: Exception) {
        }
    }
}
