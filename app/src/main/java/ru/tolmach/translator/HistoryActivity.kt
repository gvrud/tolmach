package ru.tolmach.translator

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<BreadcrumbsView>(R.id.crumbs).setItems(
            listOf(
                BreadcrumbsView.Item(getString(R.string.crumb_home)) { finish() },
                BreadcrumbsView.Item(getString(R.string.crumb_history), current = true)
            )
        )

        val entries = (application as TolmachApp).history.all()
        val list = findViewById<RecyclerView>(R.id.list)
        val empty = findViewById<TextView>(R.id.empty)

        empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = HistoryAdapter(entries) { entry -> returnEntry(entry) }
    }

    private fun returnEntry(entry: HistoryEntry) {
        val data = Intent()
            .putExtra(EXTRA_FROM, entry.from.name)
            .putExtra(EXTRA_SOURCE, entry.source)
            .putExtra(EXTRA_RESULT, entry.result)
            .putExtra(EXTRA_TIME, entry.time)
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {
        const val EXTRA_FROM = "history_from"
        const val EXTRA_SOURCE = "history_source"
        const val EXTRA_RESULT = "history_result"
        const val EXTRA_TIME = "history_time"
    }
}
