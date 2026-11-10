package com.ck.haloring

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

object ColorPicker {

    val PALETTE = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF00E5FF.toInt(), 0xFF2979FF.toInt(), 0xFF7C4DFF.toInt(),
        0xFFFF4081.toInt(), 0xFFFF1744.toInt(), 0xFFFF6D00.toInt(), 0xFFFFC400.toInt(),
        0xFFFFEA00.toInt(), 0xFF76FF03.toInt(), 0xFF00E676.toInt(), 0xFF1DE9B6.toInt()
    )

    fun dot(color: Int, strokePx: Int): GradientDrawable {
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(color)
        bg.setStroke(strokePx, 0xFF888888.toInt())
        return bg
    }

    /** A hollow grey circle, meaning "uses the default colour". */
    fun emptyDot(strokePx: Int): GradientDrawable {
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.TRANSPARENT)
        bg.setStroke(strokePx, 0xFF888888.toInt())
        return bg
    }

    /**
     * Shows the picker. onPick receives the chosen colour, or null when the
     * user taps "Default" (only offered when allowDefault is true).
     */
    fun show(a: Activity, title: String, current: Int, allowDefault: Boolean, onPick: (Int?) -> Unit) {
        val density = a.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(12), dp(20), dp(4))

        var dialog: AlertDialog? = null

        for (r in 0 until 2) {
            val row = LinearLayout(a)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_HORIZONTAL
            for (i in 0 until 6) {
                val c = PALETTE[r * 6 + i]
                val swatch = View(a)
                swatch.background = dot(c, dp(1))
                val lp = LinearLayout.LayoutParams(dp(38), dp(38))
                lp.setMargins(dp(4), dp(6), dp(4), dp(6))
                swatch.layoutParams = lp
                swatch.setOnClickListener {
                    onPick(c)
                    dialog?.dismiss()
                }
                row.addView(swatch)
            }
            box.addView(row)
        }

        val label = TextView(a)
        label.text = "Or slide for any colour"
        label.setPadding(0, dp(14), 0, dp(4))
        box.addView(label)

        val hsv = FloatArray(3)
        Color.colorToHSV(current, hsv)
        var custom = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))

        val customRow = LinearLayout(a)
        customRow.orientation = LinearLayout.HORIZONTAL
        customRow.gravity = Gravity.CENTER_VERTICAL
        val customSwatch = View(a)
        customSwatch.background = dot(custom, dp(1))
        customRow.addView(customSwatch, LinearLayout.LayoutParams(dp(34), dp(34)))
        val bar = SeekBar(a)
        bar.max = 359
        bar.progress = hsv[0].toInt().coerceIn(0, 359)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                custom = Color.HSVToColor(floatArrayOf(progress.toFloat(), 1f, 1f))
                customSwatch.background = dot(custom, dp(1))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        customRow.addView(bar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(customRow)

        val builder = AlertDialog.Builder(a)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Use slider colour") { _, _ -> onPick(custom) }
            .setNegativeButton("Cancel", null)
        if (allowDefault) {
            builder.setNeutralButton("Default") { _, _ -> onPick(null) }
        }
        dialog = builder.show()
    }

    fun toast(c: Context, msg: String) {
        android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
