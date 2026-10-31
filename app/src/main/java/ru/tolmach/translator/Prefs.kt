package ru.tolmach.translator

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var nightMode: Int
        get() = sp.getInt(KEY_NIGHT, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) {
            sp.edit().putInt(KEY_NIGHT, value).apply()
        }

    var fontSize: Float
        get() = sp.getFloat(KEY_FONT, DEFAULT_FONT)
        set(value) {
            sp.edit().putFloat(KEY_FONT, value.coerceIn(MIN_FONT, MAX_FONT)).apply()
        }

    companion object {
        private const val FILE = "tolmach"
        private const val KEY_NIGHT = "night_mode"
        private const val KEY_FONT = "font_size"

        const val MIN_FONT = 12f
        const val MAX_FONT = 32f
        const val STEP_FONT = 2f
        const val DEFAULT_FONT = 18f
    }
}
