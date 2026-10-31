package ru.tolmach.translator

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class TolmachApp : Application() {

    lateinit var prefs: Prefs
        private set

    val engine: TranslationEngine by lazy { MlKitEngine() }

    val history: HistoryStore by lazy { HistoryStore(this) }

    var modelsChecked = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.nightMode)
    }
}
