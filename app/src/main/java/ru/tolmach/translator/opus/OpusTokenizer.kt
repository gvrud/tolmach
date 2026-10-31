package ru.tolmach.translator.opus

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Токенизатор OPUS-MT (SentencePiece Unigram).
 *
 * Читает компактный двоичный словарь, подготовленный из tokenizer.json:
 * без JSON-парсинга на телефоне это в разы быстрее и не требует лишней памяти.
 */
class OpusTokenizer private constructor(
    private val pieces: Array<String>,
    private val scores: FloatArray,
    private val ids: HashMap<String, Int>,
    private val unkId: Int,
    private val eosId: Int,
    private val maxPieceLen: Int,
    private val unkScore: Float
) {

    fun encode(text: String): LongArray {
        val out = ArrayList<Long>(64)
        val n = text.length
        var i = 0
        while (i < n) {
            while (i < n && text[i].isWhitespace()) i++
            val start = i
            while (i < n && !text[i].isWhitespace()) i++
            if (i > start) segment(METASPACE + text.substring(start, i), out)
        }
        out.add(eosId.toLong())
        return out.toLongArray()
    }

    fun decode(idsIn: LongArray): String {
        val sb = StringBuilder(idsIn.size * 4)
        for (raw in idsIn) {
            val id = raw.toInt()
            if (id == eosId || id == unkId || id == PAD_ID || id < 0 || id >= pieces.size) continue
            sb.append(pieces[id])
        }
        return sb.toString().replace(METASPACE, ' ').removePrefix(" ")
    }

    /** Алгоритм Витерби для Unigram: ищем разбиение с максимальной суммой весов. */
    private fun segment(token: String, out: MutableList<Long>) {
        val n = token.length
        if (n == 0) return

        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val prevLen = IntArray(n + 1)
        val prevId = IntArray(n + 1) { -1 }
        best[0] = 0f

        for (pos in 0 until n) {
            if (best[pos] == Float.NEGATIVE_INFINITY) continue

            val limit = minOf(maxPieceLen, n - pos)
            for (len in 1..limit) {
                val piece = token.substring(pos, pos + len)
                val id = ids[piece] ?: continue
                val candidate = best[pos] + scores[id]
                if (candidate > best[pos + len]) {
                    best[pos + len] = candidate
                    prevLen[pos + len] = len
                    prevId[pos + len] = id
                }
            }

            val unknown = best[pos] + unkScore
            if (unknown > best[pos + 1]) {
                best[pos + 1] = unknown
                prevLen[pos + 1] = 1
                prevId[pos + 1] = unkId
            }
        }

        val rev = ArrayList<Long>(n)
        var pos = n
        while (pos > 0) {
            val len = prevLen[pos]
            if (len <= 0) break
            rev.add(prevId[pos].toLong())
            pos -= len
        }
        for (index in rev.indices.reversed()) out.add(rev[index])
    }

    companion object {
        private const val MAGIC = "TLMT"
        private const val METASPACE = '\u2581'
        private const val PAD_ID = 62517

        fun load(file: File): OpusTokenizer = file.inputStream().use { load(it) }

        fun load(stream: InputStream): OpusTokenizer {
            val bytes = stream.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            val magic = ByteArray(4)
            buffer.get(magic)
            require(String(magic) == MAGIC) { "Неизвестный формат токенизатора" }

            val version = buffer.int
            require(version == 1) { "Неподдерживаемая версия токенизатора: $version" }

            val count = buffer.int
            val pieces = Array(count) { "" }
            val scores = FloatArray(count)
            val ids = HashMap<String, Int>(count * 2)

            for (id in 0 until count) {
                val length = buffer.short.toInt()
                val raw = ByteArray(length)
                buffer.get(raw)
                val piece = String(raw, Charsets.UTF_8)
                pieces[id] = piece
                scores[id] = buffer.float
                ids[piece] = id
            }

            val unkId = buffer.int
            val eosId = buffer.int
            val maxPieceLen = buffer.int

            var minScore = Float.MAX_VALUE
            for (score in scores) if (score < minScore) minScore = score

            return OpusTokenizer(
                pieces = pieces,
                scores = scores,
                ids = ids,
                unkId = unkId,
                eosId = eosId,
                maxPieceLen = maxPieceLen,
                unkScore = minScore - 10f
            )
        }
    }
}
