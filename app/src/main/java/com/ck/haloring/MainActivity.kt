package com.ck.haloring

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    private val colors = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFF00E5FF.toInt(), 0xFF00E676.toInt(), 0xFFFFC400.toInt(),
        0xFFFF1744.toInt(), 0xFFFF4081.toInt(), 0xFF7C4DFF.toInt()
    )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(20), dp(20), dp(40))

        val title = TextView(this)
        title.text = "Halo Ring (test build 2)"
        title.textSize = 22f
        root.addView(title)

        status = TextView(this)
        status.textSize = 15f
        status.setPadding(0, dp(12), 0, dp(12))
        root.addView(status)

        root.addView(button("1. Allow notification access") {
            open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        })
        root.addView(button("2. Turn on Halo Ring in Accessibility") {
            open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        })

        val hint = TextView(this)
        hint.text = "Ring-only mode (default): the screen stays black and shows just the ring " +
            "for 10 seconds, turns off, then reminds you every 2 minutes for an hour. " +
            "Set Samsung Always On Display back to off so no clock or message appears."
        hint.setPadding(0, dp(10), 0, dp(10))
        root.addView(hint)

        val aod = Switch(this)
        aod.text = "Use Samsung AOD instead (experimental)"
        aod.isChecked = Prefs.sp(this).getBoolean(Prefs.AOD_MODE, false)
        aod.setPadding(0, dp(6), 0, dp(10))
        aod.setOnCheckedChangeListener { _, checked ->
            Prefs.sp(this).edit().putBoolean(Prefs.AOD_MODE, checked).apply()
        }
        root.addView(aod)

        root.addView(button("Preview ring (screen on)") {
            val s = RingService.instance
            if (s == null) toast("Turn on step 2 first") else s.preview()
        })
        root.addView(button("Send test notification in 8 seconds") { runTest() })

        root.addView(label("Colour"))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for (c in colors) {
            val swatch = View(this)
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(c)
            bg.setStroke(dp(1), 0xFF888888.toInt())
            swatch.background = bg
            val lp = LinearLayout.LayoutParams(dp(38), dp(38))
            lp.setMargins(0, dp(6), dp(10), dp(6))
            swatch.layoutParams = lp
            swatch.setOnClickListener {
                Prefs.sp(this).edit().putInt(Prefs.COLOR, c).apply()
                RingService.instance?.preview()
            }
            row.addView(swatch)
        }
        root.addView(row)

        addSlider(root, "Speed", Prefs.SPEED, Prefs.DEF_SPEED)
        addSlider(root, "Ring size", Prefs.SIZE, Prefs.DEF_SIZE)
        addSlider(root, "Thickness", Prefs.THICK, Prefs.DEF_THICK)
        addSlider(root, "Move left / right", Prefs.DX, Prefs.DEF_DX)
        addSlider(root, "Move up / down", Prefs.DY, Prefs.DEF_DY)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val notifOk = listeners.contains(packageName)
        val accOk = RingService.instance != null
        status.text = "Notification access: " + (if (notifOk) "ON" else "OFF") +
            "\nAccessibility (ring overlay): " + (if (accOk) "ON" else "OFF")
    }

    private fun runTest() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            toast("Allow notifications, then tap again")
            return
        }
        val s = RingService.instance
        if (s == null) {
            toast("Turn on step 2 first")
            return
        }
        s.testSoon()
        toast("Turn the screen off now")
    }

    private fun open(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            toast("Could not open settings")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun label(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.setPadding(0, dp(18), 0, dp(4))
        return t
    }

    private fun addSlider(root: LinearLayout, name: String, key: String, def: Int) {
        root.addView(label(name))
        val bar = SeekBar(this)
        bar.max = 100
        bar.progress = Prefs.sp(this).getInt(key, def)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                Prefs.sp(this@MainActivity).edit().putInt(key, progress).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                RingService.instance?.preview()
            }
        })
        root.addView(bar)
    }
}
