package com.ck.haloring

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = Prefs.sp(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(20), dp(20), dp(40))

        val title = TextView(this)
        title.text = "Halo Ring 0.3"
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

        root.addView(button("Preview ring") {
            val s = RingService.instance
            if (s == null) toast("Turn on step 2 first") else s.preview()
        })
        root.addView(button("Send test notification in 8 seconds") { runTest() })

        // ---- which notifications ----
        root.addView(header("Which notifications"))

        root.addView(switch("All apps", sp.getBoolean(Prefs.ALL_APPS, true)) { on ->
            sp.edit().putBoolean(Prefs.ALL_APPS, on).apply()
        })
        root.addView(note("Off = only the apps you tick in the list below."))
        root.addView(button("Choose apps and app colours") {
            startActivity(Intent(this, AppsActivity::class.java))
        })

        root.addView(switch("Missed calls", sp.getBoolean(Prefs.MISSED, true)) { on ->
            sp.edit().putBoolean(Prefs.MISSED, on).apply()
        })
        root.addView(button("Missed call colour") {
            ColorPicker.show(
                this, "Missed call colour",
                sp.getInt(Prefs.MISSED_COLOR, Prefs.DEF_MISSED_COLOR), false
            ) { picked ->
                if (picked != null) {
                    sp.edit().putInt(Prefs.MISSED_COLOR, picked).apply()
                    RingService.instance?.preview(picked)
                }
            }
        })

        // ---- look ----
        root.addView(header("Ring look"))

        root.addView(label("Animation"))
        val spinner = Spinner(this)
        val styles = ArrayAdapter(this, android.R.layout.simple_spinner_item, RingView.STYLE_NAMES)
        styles.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = styles
        spinner.setSelection(sp.getInt(Prefs.STYLE, RingView.SWIRL).coerceIn(0, RingView.STYLE_NAMES.size - 1))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (sp.getInt(Prefs.STYLE, RingView.SWIRL) == position) return
                sp.edit().putInt(Prefs.STYLE, position).apply()
                RingService.instance?.preview()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        root.addView(spinner)

        root.addView(button("Default ring colour") {
            ColorPicker.show(
                this, "Default ring colour",
                sp.getInt(Prefs.COLOR, Prefs.DEF_COLOR), false
            ) { picked ->
                if (picked != null) {
                    sp.edit().putInt(Prefs.COLOR, picked).apply()
                    RingService.instance?.preview()
                }
            }
        })
        root.addView(note("Used for every app that has no colour of its own."))

        addSlider(root, "Speed", Prefs.SPEED, Prefs.DEF_SPEED)
        addSlider(root, "Ring size", Prefs.SIZE, Prefs.DEF_SIZE)
        addSlider(root, "Thickness", Prefs.THICK, Prefs.DEF_THICK)
        addSlider(root, "Move left / right", Prefs.DX, Prefs.DEF_DX)
        addSlider(root, "Move up / down", Prefs.DY, Prefs.DEF_DY)

        // ---- mode ----
        root.addView(header("Display mode"))
        root.addView(note(
            "Ring-only (default): a black screen shows just the ring for 10 seconds, turns off, " +
                "then reminds you every 2 minutes for an hour. Keep Samsung Always On Display off."
        ))
        root.addView(switch("Use Samsung AOD instead (experimental)", sp.getBoolean(Prefs.AOD_MODE, false)) { on ->
            sp.edit().putBoolean(Prefs.AOD_MODE, on).apply()
        })

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
            "\nAccessibility (ring): " + (if (accOk) "ON" else "OFF")
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

    private fun switch(text: String, checked: Boolean, onChange: (Boolean) -> Unit): Switch {
        val s = Switch(this)
        s.text = text
        s.textSize = 16f
        s.isChecked = checked
        s.setPadding(0, dp(10), 0, dp(10))
        s.setOnCheckedChangeListener { _, on -> onChange(on) }
        return s
    }

    private fun header(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 19f
        t.setPadding(0, dp(28), 0, dp(6))
        return t
    }

    private fun label(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.setPadding(0, dp(14), 0, dp(4))
        return t
    }

    private fun note(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.alpha = 0.7f
        t.setPadding(0, 0, 0, dp(8))
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
