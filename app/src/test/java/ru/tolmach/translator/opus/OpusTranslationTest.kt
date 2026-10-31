package ru.tolmach.translator.opus

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Проверка движка на обычной JVM против эталонных ответов, полученных
 * из той же модели в Python (transformers/onnxruntime). Если тест зелёный,
 * значит токенизатор и сборка тензоров совпадают с эталоном.
 */
class OpusTranslationTest {

    private val modelsDir: File = listOf(
        File("src/main/assets/models"),
        File("app/src/main/assets/models")
    ).first { it.isDirectory }

    private fun open(pair: String): OpusModel {
        val dir = File(modelsDir, pair)
        return OpusModel(dir, OpusTokenizer.load(File(dir, "tokenizer.bin")))
    }

    @Test
    fun tokenizerMatchesReferenceIds() {
        val ru = OpusTokenizer.load(File(modelsDir, "ru-en/tokenizer.bin"))
        assertEquals(listOf(543L, 1877L, 0L), ru.encode("Привет").toList())

        assertEquals(
            listOf(76L, 4196L, 11346L, 12395L, 3L, 0L),
            ru.encode("Я люблю читать книги.").toList()
        )

        val en = OpusTokenizer.load(File(modelsDir, "en-ru/tokenizer.bin"))
        assertEquals(listOf(160L, 5270L, 0L), en.encode("Hello").toList())
        assertEquals(
            listOf(32L, 14194L, 34L, 3703L, 1595L, 2L, 181L, 47L, 34L, 11376L, 200L, 2665L, 3L, 0L),
            en.encode("The weather is nice today, but it is windy outside.").toList()
        )
    }

    @Test
    fun russianToEnglish() {
        open("ru-en").use { model ->
            assertEquals("Hello.", model.translate("Привет"))
            assertEquals("How are you?", model.translate("Как дела?"))
            assertEquals("I love reading books.", model.translate("Я люблю читать книги."))
            assertEquals(
                "Where is the nearest pharmacy?",
                model.translate("Где находится ближайшая аптека?")
            )
            assertEquals(
                "Tolmach translates text without the Internet.",
                model.translate("Толмач переводит текст без интернета.")
            )
        }
    }

    @Test
    fun englishToRussian() {
        open("en-ru").use { model ->
            assertEquals("Привет.", model.translate("Hello"))
            assertEquals("Как ты?", model.translate("How are you?"))
            assertEquals("Я люблю читать книги.", model.translate("I love reading books."))
            assertEquals(
                "Сегодня хорошая погода но снаружи ветер.",
                model.translate("The weather is nice today, but it is windy outside.")
            )
        }
    }
}

