package com.ck.haloring

import android.content.Context
import android.content.SharedPreferences

/** Slider values are stored as 0..100 ints; RingService converts them to pixels. */
object Prefs {
    const val SIZE = "size"
    const val THICK = "thick"
    const val DX = "dx"
    const val DY = "dy"
    const val SPEED = "speed"
    const val COLOR = "color"

    const val DEF_SIZE = 28
    const val DEF_THICK = 6
    const val DEF_DX = 50
    const val DEF_DY = 50
    const val DEF_SPEED = 50
    const val DEF_COLOR = 0xFFFFFFFF.toInt()

    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("halo", Context.MODE_PRIVATE)
}

/** Keys of notifications that arrived while the screen was off and are still unseen. */
object Pending {
    val keys = HashSet<String>()
}
