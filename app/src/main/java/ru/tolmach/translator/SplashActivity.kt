package ru.tolmach.translator

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private val openTranslator = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        handler.postDelayed(openTranslator, SPLASH_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openTranslator)
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_MS = 1600L
    }
}
