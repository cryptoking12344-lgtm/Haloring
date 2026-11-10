package com.ck.haloring

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Shared look: the same deep navy, violet and cyan palette as Orbit EQ. */
object Ui {
    const val BG = 0xFF0A0A0F.toInt()
    const val SURFACE = 0xFF15151D.toInt()
    const val SURFACE2 = 0xFF222230.toInt()
    const val ACCENT = 0xFFA78BFA.toInt()
    const val ACCENT2 = 0xFF22D3EE.toInt()
    const val ON_ACCENT = 0xFF14101F.toInt()
    const val TEXT = 0xFFF4F4F8.toInt()
    const val DIM = 0xFF9A9AAE.toInt()
    const val GOOD = 0xFF5EEAD4.toInt()
    const val BAD = 0xFFFB7185.toInt()

    private val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density).toInt()

    fun shape(color: Int, radiusPx: Float): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = radiusPx
        g.setColor(color)
        return g
    }

    /** A rounded background that shows a touch ripple. */
    fun pressable(c: Context, color: Int, radiusDp: Float): Drawable {
        val r = dp(c, radiusDp).toFloat()
        return RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape(color, r), shape(0xFFFFFFFF.toInt(), r))
    }

    fun text(c: Context, s: String, sizeSp: Float, color: Int, medium: Boolean = false): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = sizeSp
        t.setTextColor(color)
        if (medium) t.typeface = MEDIUM
        return t
    }

    fun card(c: Context): LinearLayout {
        val card = LinearLayout(c)
        card.orientation = LinearLayout.VERTICAL
        card.background = shape(SURFACE, dp(c, 24f).toFloat())
        val p = dp(c, 18f)
        card.setPadding(p, p, p, p)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = dp(c, 14f)
        card.layoutParams = lp
        return card
    }

    fun cardTitle(c: Context, s: String): TextView {
        val t = text(c, s.uppercase(), 12f, DIM, true)
        t.letterSpacing = 0.12f
        t.setPadding(0, 0, 0, dp(c, 10f))
        return t
    }

    /** A wide rounded button. Primary = violet fill; otherwise a quiet grey one. */
    fun pill(c: Context, label: String, primary: Boolean, onClick: () -> Unit): TextView {
        val t = text(c, label, 15f, if (primary) ON_ACCENT else TEXT, true)
        t.gravity = Gravity.CENTER
        t.background = pressable(c, if (primary) ACCENT else SURFACE2, 100f)
        t.setPadding(dp(c, 18f), dp(c, 13f), dp(c, 18f), dp(c, 13f))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(c, 10f)
        t.layoutParams = lp
        t.setOnClickListener { onClick() }
        return t
    }

    fun styleChip(c: Context, chip: TextView, selected: Boolean) {
        chip.background = pressable(c, if (selected) ACCENT else SURFACE2, 100f)
        chip.setTextColor(if (selected) ON_ACCENT else TEXT)
    }

    fun styleSwitch(sw: Switch) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(0xFFFFFFFF.toInt(), DIM))
        sw.trackTintList = ColorStateList(states, intArrayOf(ACCENT, 0xFF3A3A4A.toInt()))
    }

    /**
     * A settings row: title and optional subtitle on the left, an optional
     * control (switch, colour dot, arrow) on the right.
     */
    fun row(c: Context, title: String, subtitle: String?, trailing: View?, onClick: (() -> Unit)?): LinearLayout {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(c, 10f), 0, dp(c, 10f))

        val texts = LinearLayout(c)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(text(c, title, 16f, TEXT))
        if (subtitle != null) {
            val sub = text(c, subtitle, 13f, DIM)
            sub.setPadding(0, dp(c, 2f), 0, 0)
            texts.addView(sub)
        }
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        if (trailing != null) {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = dp(c, 12f)
            row.addView(trailing, lp)
        }
        if (onClick != null) row.setOnClickListener { onClick() }
        return row
    }

    fun colorDot(c: Context, color: Int, sizeDp: Float): View {
        val v = View(c)
        v.background = ColorPicker.dot(color, dp(c, 1f))
        v.layoutParams = LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp))
        return v
    }
}
