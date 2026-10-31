package ru.tolmach.translator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class HistoryEntry(
    val time: Long,
    val from: Lang,
    val to: Lang,
    val source: String,
    val result: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("t", time)
        .put("f", from.name)
        .put("o", to.name)
        .put("s", source)
        .put("r", result)

    companion object {
        fun fromJson(obj: JSONObject): HistoryEntry? = runCatching {
            HistoryEntry(
                time = obj.getLong("t"),
                from = Lang.valueOf(obj.getString("f")),
                to = Lang.valueOf(obj.getString("o")),
                source = obj.getString("s"),
                result = obj.getString("r")
            )
        }.getOrNull()
    }
}

/**
 * Хранит 50 последних переводов в файле history.json во внутренней папке приложения.
 * Пока пользователь дописывает фразу, последняя запись обновляется на месте,
 * а не плодит по строке на каждую букву.
 */
class HistoryStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "history.json")
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tolmach-history").apply { isDaemon = true }
    }
    private val entries = ArrayList<HistoryEntry>()
    private var loaded = false

    @Synchronized
    fun all(): List<HistoryEntry> {
        ensureLoaded()
        return entries.toList()
    }

    @Synchronized
    fun record(from: Lang, to: Lang, source: String, result: String) {
        ensureLoaded()
        if (source.isBlank() || result.isBlank()) return

        val now = System.currentTimeMillis()
        val last = entries.firstOrNull()
        val sameSession = last != null &&
            last.from == from &&
            last.to == to &&
            now - last.time < SESSION_MS &&
            isContinuation(last.source, source)

        if (sameSession) {
            entries[0] = last!!.copy(time = now, source = source, result = result)
        } else {
            entries.add(0, HistoryEntry(now, from, to, source, result))
            while (entries.size > MAX) entries.removeAt(entries.lastIndex)
        }
        persist()
    }

    private fun isContinuation(old: String, new: String): Boolean {
        val a = old.trimEnd()
        val b = new.trimEnd()
        if (a.isEmpty() || b.isEmpty()) return false
        return b.startsWith(a) || a.startsWith(b)
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            if (!file.exists()) return
            val array = JSONArray(file.readText())
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                HistoryEntry.fromJson(obj)?.let { entries.add(it) }
            }
        }
    }

    private fun persist() {
        val snapshot = JSONArray().apply { entries.forEach { put(it.toJson()) } }.toString()
        io.execute { runCatching { file.writeText(snapshot) } }
    }

    companion object {
        const val MAX = 50
        private const val SESSION_MS = 10 * 60 * 1000L
    }
}
