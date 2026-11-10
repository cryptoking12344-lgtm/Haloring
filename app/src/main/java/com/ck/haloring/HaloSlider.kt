package com.ck.haloring

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/** A slim 0..100 slider: dim track, violet fill, white bead. */
class HaloSlider(context: Context) : View(context) {

    var value: Int = 50
        set(v) {
            field = v.coerceIn(0, 100)
            invalidate()
        }

    /** Called with the new value while the user drags. */
    var onChange: ((Int) -> Unit)? = null

    /** Called when the user lets go. */
    var onRelease: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val density = resources.displayMetrics.density
    private var held = false

    private fun dp(v: Float): Float = v * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            dp(40f).toInt()
        )
    }

    private fun setFromX(x: Float) {
        val pad = dp(12f)
        val w = width - 2f * pad
        if (w <= 0f) return
        val f = ((x - pad) / w).coerceIn(0f, 1f)
        val nv = Math.round(f * 100f)
        if (nv != value) {
            value = nv
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onChange?.invoke(nv)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                held = true
                setFromX(event.x)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                setFromX(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                held = false
                invalidate()
                onRelease?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = dp(12f)
        val w = width - 2f * pad
        val cy = height / 2f
        val x = pad + w * (value / 100f)
        val th = dp(if (held) 7f else 5f)

        paint.style = Paint.Style.FILL
        paint.color = 0x26F4F4F8
        rect.set(pad, cy - th / 2f, pad + w, cy + th / 2f)
        canvas.drawRoundRect(rect, th / 2f, th / 2f, paint)

        paint.color = Ui.ACCENT
        rect.set(pad, cy - th / 2f, x, cy + th / 2f)
        canvas.drawRoundRect(rect, th / 2f, th / 2f, paint)

        // Bead: soft violet halo while held, white centre.
        if (held) {
            paint.color = 0x44A78BFA
            canvas.drawCircle(x, cy, dp(15f), paint)
        }
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, cy, dp(if (held) 10f else 9f), paint)
        paint.color = Ui.ACCENT
        canvas.drawCircle(x, cy, dp(4f), paint)
    }
}

/** A round minus or plus button. Hold it to keep stepping. */
class StepButton(context: Context, private val plus: Boolean) : View(context) {

    var onStep: (() -> Unit)? = null
    var onRelease: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val stepHandler = Handler(Looper.getMainLooper())
    private var pressedDown = false

    private val repeater = object : Runnable {
        override fun run() {
            if (!pressedDown) return
            onStep?.invoke()
            stepHandler.postDelayed(this, 70L)
        }
    }

    init {
        contentDescription = if (plus) "Increase" else "Decrease"
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = (38f * density).toInt()
        setMeasuredDimension(s, s)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pressedDown = true
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onStep?.invoke()
                stepHandler.postDelayed(repeater, 380L)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressedDown = false
                stepHandler.removeCallbacks(repeater)
                invalidate()
                onRelease?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        pressedDown = false
        stepHandler.removeCallbacks(repeater)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        paint.style = Paint.Style.FILL
        paint.color = if (pressedDown) Ui.ACCENT else Ui.SURFACE2
        canvas.drawCircle(cx, cy, width / 2f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 2.2f * density
        paint.color = if (pressedDown) Ui.ON_ACCENT else Ui.TEXT
        val arm = 6.5f * density
        canvas.drawLine(cx - arm, cy, cx + arm, cy, paint)
        if (plus) canvas.drawLine(cx, cy - arm, cx, cy + arm, paint)
    }
}
