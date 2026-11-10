package com.ck.haloring

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.View
import kotlin.math.cos

class RingView(context: Context) : View(context) {

    companion object {
        const val SWIRL = 0
        const val PULSE = 1
        const val BLINK = 2
        const val DOUBLE = 3
        const val RIPPLE = 4
        const val RAINBOW = 5
        val STYLE_NAMES = arrayOf("Swirl", "Pulse", "Blink", "Double spin", "Ripple", "Rainbow")
    }

    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val plainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private var color: Int = Prefs.DEF_COLOR
    private var radius: Float = 0f
    private var stroke: Float = 0f
    private var periodMs: Long = 2000L
    private var animStyle: Int = SWIRL
    private var ripple: Float = 0f

    private var animating = false
    private var startMs = 0L
    private var shader: Shader? = null

    fun configure(color: Int, radius: Float, stroke: Float, periodMs: Long, style: Int, ripple: Float) {
        this.color = color
        this.radius = radius
        this.stroke = stroke
        this.periodMs = if (periodMs < 300L) 300L else periodMs
        this.animStyle = style
        this.ripple = ripple
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

    private fun plain(canvas: Canvas, cx: Float, cy: Float, r: Float, width: Float, alpha: Int) {
        plainPaint.color = color
        plainPaint.alpha = alpha.coerceIn(0, 255)
        plainPaint.strokeWidth = width
        canvas.drawCircle(cx, cy, r, plainPaint)
    }

    private fun spin(canvas: Canvas, cx: Float, cy: Float, t: Float, s: Shader) {
        shaderPaint.shader = s
        shaderPaint.strokeWidth = stroke
        canvas.save()
        canvas.rotate(t * 360f, cx, cy)
        canvas.drawCircle(cx, cy, radius, shaderPaint)
        canvas.restore()
    }

    private fun buildShader(cx: Float, cy: Float): Shader {
        val clear = color and 0x00FFFFFF
        return when (animStyle) {
            DOUBLE -> SweepGradient(
                cx, cy,
                intArrayOf(clear, color, clear, color),
                floatArrayOf(0f, 0.5f, 0.501f, 1f)
            )
            RAINBOW -> SweepGradient(
                cx, cy,
                intArrayOf(
                    0xFFFF1744.toInt(), 0xFFFFC400.toInt(), 0xFF00E676.toInt(),
                    0xFF00E5FF.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF4081.toInt(),
                    0xFFFF1744.toInt()
                ),
                null
            )
            else -> SweepGradient(
                cx, cy,
                intArrayOf(clear, color, color),
                floatArrayOf(0f, 0.75f, 1f)
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        if (!animating) {
            // Static ring: costs no CPU once drawn.
            plain(canvas, cx, cy, radius, stroke, 220)
            return
        }

        val elapsed = SystemClock.uptimeMillis() - startMs
        val t = (elapsed % periodMs).toFloat() / periodMs.toFloat()

        when (animStyle) {
            PULSE -> {
                val a = 0.5f - 0.5f * cos(t * 2f * Math.PI.toFloat())
                plain(canvas, cx, cy, radius, stroke, (30 + 225 * a).toInt())
            }
            BLINK -> {
                if (t < 0.4f) plain(canvas, cx, cy, radius, stroke, 255)
            }
            RIPPLE -> {
                plain(canvas, cx, cy, radius, stroke, 255)
                for (k in 0..1) {
                    val p = (t + k * 0.5f) % 1f
                    plain(canvas, cx, cy, radius + p * ripple, stroke * (1f - 0.6f * p), ((1f - p) * 200).toInt())
                }
            }
            RAINBOW -> {
                val s = shader ?: buildShader(cx, cy).also { shader = it }
                spin(canvas, cx, cy, t, s)
            }
            else -> {
                plain(canvas, cx, cy, radius, stroke, 50)
                val s = shader ?: buildShader(cx, cy).also { shader = it }
                spin(canvas, cx, cy, t, s)
            }
        }

        postInvalidateDelayed(33L)
    }
}
