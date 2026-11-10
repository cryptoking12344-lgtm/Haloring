package com.ck.haloring

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var sp: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusPill: TextView
    private lateinit var setupCard: LinearLayout
    private lateinit var setupRows: LinearLayout
    private lateinit var hero: RingView
    private lateinit var missedDot: View
    private val chips = ArrayList<TextView>()
    private val swatches = ArrayList<View>()

    private val cameraPreview = Runnable { RingService.instance?.preview() }

    private fun dp(v: Float): Int = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sp = Prefs.sp(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16f), dp(18f), dp(16f), dp(40f))

        root.addView(buildHeader())
        root.addView(buildHero())
        root.addView(buildSetup())
        root.addView(buildNotifications())
        root.addView(buildAnimation())
        root.addView(buildColour())
        root.addView(buildShape())
        root.addView(buildTest())
        root.addView(buildMode())

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Ui.BG)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshSetup()
        updateHero()
        handler.post { paintSwatches() }
    }

    // ---------------------------------------------------------------- header

    private fun buildHeader(): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(4f), 0, dp(4f), dp(16f))

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(Ui.text(this, "Halo Ring", 28f, Ui.TEXT, true))
        texts.addView(Ui.text(this, "Notification light for your camera ring", 13f, Ui.DIM))
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        statusPill = Ui.text(this, "", 12f, Ui.GOOD, true)
        statusPill.setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
        row.addView(statusPill)
        return row
    }

    // ------------------------------------------------------------------ hero

    private fun buildHero(): View {
        val card = Ui.card(this)
        card.setPadding(dp(10f), dp(10f), dp(10f), dp(10f))

        val stage = FrameLayout(this)
        stage.background = Ui.shape(0xFF000000.toInt(), dp(18f).toFloat())

        hero = RingView(this)
        stage.addView(hero, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(150f)))

        // The camera hole the ring wraps around.
        val lens = View(this)
        val lensBg = GradientDrawable()
        lensBg.shape = GradientDrawable.OVAL
        lensBg.setColor(0xFF16161C.toInt())
        lens.background = lensBg
        val lensLp = FrameLayout.LayoutParams(dp(26f), dp(26f))
        lensLp.gravity = Gravity.CENTER
        stage.addView(lens, lensLp)

        card.addView(stage)
        return card
    }

    private fun updateHero() {
        val thick = sp.getInt(Prefs.THICK, Prefs.DEF_THICK)
        val speed = sp.getInt(Prefs.SPEED, Prefs.DEF_SPEED)
        hero.configure(
            sp.getInt(Prefs.COLOR, Prefs.DEF_COLOR),
            dp(24f).toFloat(),
            dp(2f) + thick * 0.09f * resources.displayMetrics.density,
            4000L - speed * 35L,
            sp.getInt(Prefs.STYLE, RingView.SWIRL),
            dp(18f).toFloat()
        )
        hero.setAnimating(true)
    }

    // ----------------------------------------------------------------- setup

    private fun buildSetup(): View {
        setupCard = Ui.card(this)
        setupCard.addView(Ui.cardTitle(this, "Finish setup"))
        setupRows = LinearLayout(this)
        setupRows.orientation = LinearLayout.VERTICAL
        setupCard.addView(setupRows)
        return setupCard
    }

    private fun refreshSetup() {
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val notifOk = listeners.contains(packageName)
        val accOk = RingService.instance != null

        setupRows.removeAllViews()
        if (!notifOk) {
            setupRows.addView(Ui.row(
                this, "Notification access", "Lets Halo Ring know when something arrives",
                Ui.text(this, "Turn on", 14f, Ui.ACCENT, true)
            ) { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) })
        }
        if (!accOk) {
            setupRows.addView(Ui.row(
                this, "Accessibility", "Lets Halo Ring draw the ring and turn the screen off",
                Ui.text(this, "Turn on", 14f, Ui.ACCENT, true)
            ) { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
        }

        val ready = notifOk && accOk
        setupCard.visibility = if (ready) View.GONE else View.VISIBLE
        statusPill.text = if (ready) "Active" else "Setup needed"
        statusPill.setTextColor(if (ready) Ui.GOOD else Ui.BAD)
        statusPill.background = Ui.shape(if (ready) 0x225EEAD4 else 0x22FB7185, dp(100f).toFloat())
    }

    // --------------------------------------------------------- notifications

    private fun buildNotifications(): View {
        val card = Ui.card(this)
        card.addView(Ui.cardTitle(this, "Notifications"))

        card.addView(switchRow(
            "All apps", "Off = only the apps you tick",
            sp.getBoolean(Prefs.ALL_APPS, true)
        ) { on -> sp.edit().putBoolean(Prefs.ALL_APPS, on).apply() })

        card.addView(Ui.row(
            this, "Apps and colours", "Pick apps and give each its own colour",
            Ui.text(this, "›", 24f, Ui.DIM)
        ) { startActivity(Intent(this, AppsActivity::class.java)) })

        card.addView(switchRow(
            "Missed calls", "Always first, with a phone icon beside the ring",
            sp.getBoolean(Prefs.MISSED, true)
        ) { on -> sp.edit().putBoolean(Prefs.MISSED, on).apply() })

        missedDot = Ui.colorDot(this, sp.getInt(Prefs.MISSED_COLOR, Prefs.DEF_MISSED_COLOR), 28f)
        card.addView(Ui.row(this, "Missed call colour", null, missedDot) {
            ColorPicker.show(
                this, "Missed call colour",
                sp.getInt(Prefs.MISSED_COLOR, Prefs.DEF_MISSED_COLOR), false
            ) { picked ->
                if (picked != null) {
                    sp.edit().putInt(Prefs.MISSED_COLOR, picked).apply()
                    missedDot.background = ColorPicker.dot(picked, dp(1f))
                    RingService.instance?.preview(picked)
                }
            }
        })
        return card
    }

    // ------------------------------------------------------------- animation

    private fun buildAnimation(): View {
        val card = Ui.card(this)
        card.addView(Ui.cardTitle(this, "Animation"))

        val strip = LinearLayout(this)
        strip.orientation = LinearLayout.HORIZONTAL
        val current = sp.getInt(Prefs.STYLE, RingView.SWIRL)
        chips.clear()
        for (i in RingView.STYLE_NAMES.indices) {
            val chip = Ui.text(this, RingView.STYLE_NAMES[i], 14f, Ui.TEXT, true)
            chip.setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
            Ui.styleChip(this, chip, i == current)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.rightMargin = dp(8f)
            chip.layoutParams = lp
            chip.setOnClickListener {
                sp.edit().putInt(Prefs.STYLE, i).apply()
                for (j in chips.indices) Ui.styleChip(this, chips[j], j == i)
                updateHero()
            }
            chips.add(chip)
            strip.addView(chip)
        }
        val scroller = HorizontalScrollView(this)
        scroller.isHorizontalScrollBarEnabled = false
        scroller.addView(strip)
        card.addView(scroller)

        addSlider(card, "Speed", Prefs.SPEED, Prefs.DEF_SPEED, false, false)
        return card
    }

    // ---------------------------------------------------------------- colour

    private fun buildColour(): View {
        val card = Ui.card(this)
        card.addView(Ui.cardTitle(this, "Default colour"))

        swatches.clear()
        for (r in 0 until 2) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for (i in 0 until 6) {
                val color = ColorPicker.PALETTE[r * 6 + i]
                val cell = FrameLayout(this)
                val swatch = View(this)
                swatch.tag = color
                val lp = FrameLayout.LayoutParams(dp(36f), dp(36f))
                lp.gravity = Gravity.CENTER
                cell.addView(swatch, lp)
                cell.setOnClickListener { setDefaultColour(color) }
                swatches.add(swatch)
                line.addView(cell, LinearLayout.LayoutParams(0, dp(48f), 1f))
            }
            card.addView(line)
        }
        paintSwatches()

        card.addView(Ui.pill(this, "Custom colour", false) {
            ColorPicker.show(this, "Default colour", sp.getInt(Prefs.COLOR, Prefs.DEF_COLOR), false) { picked ->
                if (picked != null) setDefaultColour(picked)
            }
        })
        val note = Ui.text(this, "Used by every app that has no colour of its own.", 13f, Ui.DIM)
        note.setPadding(dp(2f), dp(10f), 0, 0)
        card.addView(note)
        return card
    }

    private fun setDefaultColour(color: Int) {
        sp.edit().putInt(Prefs.COLOR, color).apply()
        paintSwatches()
        updateHero()
    }

    private fun paintSwatches() {
        val current = sp.getInt(Prefs.COLOR, Prefs.DEF_COLOR)
        for (s in swatches) {
            val color = s.tag as Int
            val selected = color == current
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(color)
            // The chosen colour is larger with a dark gap and an outer line.
            if (selected) g.setStroke(dp(3f), Ui.SURFACE)
            s.background = g
            s.scaleX = if (selected) 1.15f else 0.8f
            s.scaleY = if (selected) 1.15f else 0.8f
            val cell = s.parent as? FrameLayout
            if (cell != null) {
                if (selected) {
                    val halo = GradientDrawable()
                    halo.shape = GradientDrawable.OVAL
                    halo.setColor(0x00000000)
                    halo.setStroke(dp(2f), color)
                    val inset = (cell.width - dp(46f)) / 2
                    cell.foreground = if (cell.width > 0) {
                        android.graphics.drawable.InsetDrawable(halo, inset, dp(1f), inset, dp(1f))
                    } else {
                        null
                    }
                } else {
                    cell.foreground = null
                }
            }
        }
    }

    // ----------------------------------------------------- size and position

    private fun buildShape(): View {
        val card = Ui.card(this)
        card.addView(Ui.cardTitle(this, "Size and position"))
        val note = Ui.text(this, "The ring shows on your camera while you adjust.", 13f, Ui.DIM)
        note.setPadding(dp(2f), 0, 0, dp(2f))
        card.addView(note)

        addSlider(card, "Ring size", Prefs.SIZE, Prefs.DEF_SIZE, false, true)
        addSlider(card, "Thickness", Prefs.THICK, Prefs.DEF_THICK, false, true)
        addSlider(card, "Left / right", Prefs.DX, Prefs.DEF_DX, true, true)
        addSlider(card, "Up / down", Prefs.DY, Prefs.DEF_DY, true, true)

        card.addView(Ui.pill(this, "Show ring on camera", false) {
            val s = RingService.instance
            if (s == null) toast("Finish setup first") else s.preview()
        })
        return card
    }

    // ------------------------------------------------------------------ test

    private fun buildTest(): View {
        val card = Ui.card(this)
        card.addView(Ui.cardTitle(this, "Try it"))
        card.addView(Ui.text(this, "Sends a test notification in 8 seconds. Turn the screen off and watch the camera.", 14f, Ui.DIM))
        card.addView(Ui.pill(this, "Send test notification", true) { runTest() })
        return card
    }

    // ------------------------------------------------------------------ mode

    private fun buildMode(): View {
        val card = Ui.card(this)
        card.addView(Ui.cardTitle(this, "Display mode"))
        card.addView(Ui.text(
            this,
            "Ring only: a black screen shows just the ring for 10 seconds, turns off, " +
                "then reminds you every 2 minutes for an hour.",
            14f, Ui.DIM
        ))
        card.addView(switchRow(
            "Use Samsung AOD instead", "Experimental. Shows the AOD clock and the ring does not move",
            sp.getBoolean(Prefs.AOD_MODE, false)
        ) { on -> sp.edit().putBoolean(Prefs.AOD_MODE, on).apply() })
        return card
    }

    // --------------------------------------------------------------- helpers

    private fun switchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val sw = Switch(this)
        Ui.styleSwitch(sw)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, on -> onChange(on) }
        return Ui.row(this, title, subtitle, sw) { sw.isChecked = !sw.isChecked }
    }

    /**
     * A labelled slider with its number and minus / plus buttons.
     * centred: shows -50..+50 so 0 means "not moved".
     * onCamera: also shows the ring on the real camera hole after a change.
     */
    private fun addSlider(card: LinearLayout, name: String, key: String, def: Int, centred: Boolean, onCamera: Boolean) {
        fun shown(v: Int): String {
            if (!centred) return v.toString()
            val d = v - 50
            return if (d > 0) "+$d" else d.toString()
        }

        val start = sp.getInt(key, def)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(2f), dp(16f), dp(2f), dp(4f))
        head.addView(
            Ui.text(this, name, 15f, Ui.TEXT),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val number = Ui.text(this, shown(start), 15f, Ui.ACCENT, true)
        head.addView(number)
        card.addView(head)

        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL

        val minus = StepButton(this, false)
        val slider = HaloSlider(this)
        val plus = StepButton(this, true)
        slider.value = start

        fun commit(v: Int) {
            sp.edit().putInt(key, v).apply()
            number.text = shown(v)
            updateHero()
        }

        fun released() {
            if (!onCamera) return
            handler.removeCallbacks(cameraPreview)
            handler.postDelayed(cameraPreview, 150L)
        }

        slider.onChange = { v -> commit(v) }
        slider.onRelease = { released() }
        minus.onStep = {
            if (slider.value > 0) {
                slider.value = slider.value - 1
                commit(slider.value)
            }
        }
        plus.onStep = {
            if (slider.value < 100) {
                slider.value = slider.value + 1
                commit(slider.value)
            }
        }
        minus.onRelease = { released() }
        plus.onRelease = { released() }

        line.addView(minus)
        line.addView(slider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(plus)
        card.addView(line)
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
            toast("Finish setup first")
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
}
