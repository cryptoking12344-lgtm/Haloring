package com.ck.haloring

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.View

class RingView(context: Context) : View(context) {

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var color: Int = Prefs.DEF_COLOR
    private var radius: Float = 0f
    private var stroke: Float = 0f
    private var periodMs: Long = 2000L

    private var animating = false
    private var startMs = 0L
    private var shader: Shader? = null

    fun configure(color: Int, radius: Float, stroke: Float, periodMs: Long) {
        this.color = color
        this.radius = radius
        this.stroke = stroke
        this.periodMs = if (periodMs < 300L) 300L else periodMs
        shader = null
        invalidate()
    }

    fun setAnimating(on: Boolean) {
        animating = on
        startMs = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shader = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        basePaint.strokeWidth = stroke
        basePaint.color = color

        if (!animating) {
            // Static ring: costs no CPU once drawn.
            basePaint.alpha = 220
            canvas.drawCircle(cx, cy, radius, basePaint)
            return
        }

        basePaint.alpha = 50
        canvas.drawCircle(cx, cy, radius, basePaint)

        var s = shader
        if (s == null) {
            s = SweepGradient(
                cx, cy,
                intArrayOf(color and 0x00FFFFFF, color, color),
                floatArrayOf(0f, 0.75f, 1f)
            )
            shader = s
        }
        sweepPaint.shader = s
        sweepPaint.strokeWidth = stroke

        val elapsed = SystemClock.uptimeMillis() - startMs
        val angle = (elapsed % periodMs).toFloat() / periodMs.toFloat() * 360f
        canvas.save()
        canvas.rotate(angle, cx, cy)
        canvas.drawCircle(cx, cy, radius, sweepPaint)
        canvas.restore()

        postInvalidateDelayed(33L)
    }
}
