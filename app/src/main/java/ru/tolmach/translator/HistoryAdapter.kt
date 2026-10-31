package ru.tolmach.translator

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val items: List<HistoryEntry>,
    private val onClick: (HistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.time.text = formatter.format(Date(item.time))
        holder.source.text = item.source
        holder.result.text = item.result
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.time)
        val source: TextView = view.findViewById(R.id.source)
        val result: TextView = view.findViewById(R.id.result)
    }

    private companion object {
        val formatter = SimpleDateFormat("dd.MM.yyyy  HH:mm:ss", Locale.getDefault())
    }
}
