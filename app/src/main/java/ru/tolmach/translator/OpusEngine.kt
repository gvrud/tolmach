package ru.tolmach.translator

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.tolmach.translator.opus.OpusModel
import ru.tolmach.translator.opus.OpusTokenizer
import java.io.File

/**
 * Полностью свободный офлайн-движок: OPUS-MT (Helsinki-NLP, CC-BY 4.0)
 * в формате ONNX, счёт — на ONNX Runtime (MIT). Никаких сервисов Google,
 * аккаунтов и загрузок из сети: модели лежат в APK и распаковываются
 * во внутреннюю папку приложения при первом обращении.
 *
 * Держим в памяти только одно направление: модели крупные, а на телефонах
 * с 1–2 ГБ ОЗУ два сразу — верный путь к OOM.
 */
class OpusEngine(private val context: Context) : TranslationEngine {

    private val mutex = Mutex()
    private var model: OpusModel? = null
    private var pair: String? = null

    override suspend fun prepare(from: Lang, to: Lang) {
        ensureModel(pairKey(from, to))
    }

    override suspend fun translate(from: Lang, to: Lang, text: String): String {
        val loaded = ensureModel(pairKey(from, to))
        return withContext(Dispatchers.Default) { loaded.translate(text) }
    }

    private suspend fun ensureModel(key: String): OpusModel = mutex.withLock {
        model?.let { if (pair == key) return@withLock it }
        model?.close()
        model = null
        pair = null

        val dir = withContext(Dispatchers.IO) { extractIfNeeded(key) }
        val tokenizer = withContext(Dispatchers.IO) { OpusTokenizer.load(File(dir, FILE_TOKENIZER)) }
        val loaded = withContext(Dispatchers.Default) {
            OpusModel(dir, tokenizer, threads = threads())
        }
        model = loaded
        pair = key
        loaded
    }

    private fun threads(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    private fun extractIfNeeded(key: String): File {
        val dir = File(context.filesDir, "models/$key")
        dir.mkdirs()
        for (name in FILES) {
            val target = File(dir, name)
            if (target.isFile && target.length() > 0) continue

            val temp = File(dir, "$name.tmp")
            context.assets.open("models/$key/$name").use { input ->
                temp.outputStream().use { output -> input.copyTo(output, BUFFER) }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
        return dir
    }

    private fun pairKey(from: Lang, to: Lang): String =
        "${from.name.lowercase()}-${to.name.lowercase()}"

    private companion object {
        const val FILE_TOKENIZER = "tokenizer.bin"
        val FILES = listOf("encoder.onnx", "decoder.onnx", FILE_TOKENIZER)
        const val BUFFER = 1 shl 16
    }
}
