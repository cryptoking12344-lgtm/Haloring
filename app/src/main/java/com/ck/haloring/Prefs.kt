package com.ck.haloring

import android.content.Context
import android.content.SharedPreferences

/** Slider values are stored as 0..100 ints; RingGeometry converts them to pixels. */
object Prefs {
    const val SIZE = "size"
    const val THICK = "thick"
    const val DX = "dx"
    const val DY = "dy"
    const val SPEED = "speed"
    const val COLOR = "color"
    const val AOD_MODE = "aod_mode"
    const val STYLE = "style"
    const val ALL_APPS = "all_apps"
    const val APPS = "apps"
    const val MISSED = "missed"
    const val MISSED_COLOR = "missed_color"

    const val DEF_SIZE = 28
    const val DEF_THICK = 6
    const val DEF_DX = 50
    const val DEF_DY = 50
    const val DEF_SPEED = 50
    const val DEF_COLOR = 0xFFFFFFFF.toInt()
    const val DEF_MISSED_COLOR = 0xFFFF1744.toInt()

    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("halo", Context.MODE_PRIVATE)

    fun selectedApps(c: Context): HashSet<String> {
        val stored = sp(c).getStringSet(APPS, null)
        return if (stored == null) HashSet() else HashSet(stored)
    }

    fun saveSelectedApps(c: Context, apps: Set<String>) {
        sp(c).edit().putStringSet(APPS, HashSet(apps)).apply()
    }

    /** The app's own colour, or null when it uses the default colour. */
    fun appColor(c: Context, pkg: String): Int? {
        val p = sp(c)
        val key = "c_$pkg"
        return if (p.contains(key)) p.getInt(key, DEF_COLOR) else null
    }

    fun setAppColor(c: Context, pkg: String, color: Int?) {
        val e = sp(c).edit()
        if (color == null) e.remove("c_$pkg") else e.putInt("c_$pkg", color)
        e.apply()
    }
}

/** Notifications that arrived unseen, with the ring colour each one should use. */
object Pending {
    private class Item(val color: Int, val missedCall: Boolean)

    private val map = LinkedHashMap<String, Item>()

    fun add(key: String, color: Int, missedCall: Boolean) {
        map.remove(key)
        map[key] = Item(color, missedCall)
    }

    fun remove(key: String): Boolean = map.remove(key) != null

    fun clear() = map.clear()

    fun isEmpty(): Boolean = map.isEmpty()

    /** True while a missed call is still unseen. */
    fun hasMissedCall(): Boolean {
        for (v in map.values) if (v.missedCall) return true
        return false
    }

    /** A missed call wins; otherwise the most recent unseen notification. */
    fun color(): Int? {
        var last: Int? = null
        var missed: Int? = null
        for (v in map.values) {
            last = v.color
            if (v.missedCall) missed = v.color
        }
        return missed ?: last
    }
}
