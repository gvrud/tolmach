package ru.tolmach.translator

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class TolmachApp : Application() {

    lateinit var prefs: Prefs
        private set

    val engine: TranslationEngine by lazy { OpusEngine(this) }

    val history: HistoryStore by lazy { HistoryStore(this) }

    var modelsChecked = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        // Тема не должна стать причиной падения запуска: что угодно — только не краш.
        runCatching { AppCompatDelegate.setDefaultNightMode(prefs.nightMode) }
    }
}
