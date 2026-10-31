package ru.tolmach.translator

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * Языки, между которыми работает Толмач.
 * [label] выводится в заголовке половины экрана.
 */
enum class Lang(val code: String, val label: String) {
    RU(TranslateLanguage.RUSSIAN, "Russian"),
    EN(TranslateLanguage.ENGLISH, "English");

    fun other(): Lang = if (this == RU) EN else RU
}

interface TranslationEngine {

    /** Готовит модель перевода к работе: скачивает её один раз, если её ещё нет на устройстве. */
    suspend fun prepare(from: Lang, to: Lang)

    /** Переводит текст. После [prepare] сеть не используется. */
    suspend fun translate(from: Lang, to: Lang, text: String): String
}

/**
 * Офлайн-движок на ML Kit Translation (бесплатный, работает на устройстве).
 *
 * Модели хранятся в памяти телефона: после однократной загрузки перевод идёт
 * полностью локально, без интернета и без обращения к серверам.
 */
class MlKitEngine : TranslationEngine {

    private val clients = HashMap<String, Translator>()

    private fun client(from: Lang, to: Lang): Translator = clients.getOrPut(key(from, to)) {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(from.code)
                .setTargetLanguage(to.code)
                .build()
        )
    }

    private fun key(from: Lang, to: Lang) = "${from.name}>${to.name}"

    override suspend fun prepare(from: Lang, to: Lang) {
        client(from, to).downloadModelIfNeeded().await()
    }

    override suspend fun translate(from: Lang, to: Lang, text: String): String {
        val translator = client(from, to)
        translator.downloadModelIfNeeded().await()

        val parts = chunk(text)
        val out = ArrayList<String>(parts.size)
        for (part in parts) {
            out.add(if (part.isBlank()) "" else translator.translate(part).await())
        }
        return out.joinToString("\n")
    }

    /**
     * Разбивает текст на куски по строкам и по границам слов,
     * чтобы длинный текст не упирался в ограничения движка.
     */
    private fun chunk(text: String, limit: Int = 400): List<String> {
        val out = ArrayList<String>()
        for (line in text.split("\n")) {
            if (line.isBlank()) {
                out.add("")
                continue
            }
            var rest = line
            while (rest.length > limit) {
                var cut = rest.lastIndexOf(' ', limit)
                if (cut <= 0) cut = limit
                out.add(rest.substring(0, cut))
                rest = rest.substring(cut).trimStart()
            }
            if (rest.isNotEmpty()) out.add(rest)
        }
        return out
    }
}
