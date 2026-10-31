package ru.tolmach.translator

/**
 * Языки, между которыми работает Толмач.
 * [label] выводится в заголовке половины экрана.
 */
enum class Lang(val code: String, val label: String) {
    RU("ru", "Russian"),
    EN("en", "English");

    fun other(): Lang = if (this == RU) EN else RU
}

interface TranslationEngine {

    /** Готовит модель к работе: при первом вызове распаковывает её из APK. */
    suspend fun prepare(from: Lang, to: Lang)

    /** Переводит текст. Сеть не используется ни на одном шаге. */
    suspend fun translate(from: Lang, to: Lang, text: String): String
}
