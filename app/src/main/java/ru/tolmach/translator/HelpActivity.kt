package ru.tolmach.translator

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        findViewById<BreadcrumbsView>(R.id.crumbs).setItems(
            listOf(
                BreadcrumbsView.Item(getString(R.string.crumb_home)) { finish() },
                BreadcrumbsView.Item(getString(R.string.crumb_help), current = true)
            )
        )

        findViewById<TextView>(R.id.helpBody).text = readHelp()
    }

    private fun readHelp(): String = runCatching {
        resources.openRawResource(R.raw.help).bufferedReader().use { it.readText() }
    }.getOrDefault(getString(R.string.help_title))
}
