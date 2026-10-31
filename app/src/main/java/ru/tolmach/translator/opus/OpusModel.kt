package ru.tolmach.translator.opus

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Инференс OPUS-MT (Marian) поверх ONNX Runtime — без Android API, чтобы
 * тот же код можно было прогнать обычным JVM-тестом.
 *
 * Декодирование жадное: на каждом шаге берётся самый вероятный токен.
 * Кэш прошлых состояний не используется — для коротких фраз это дешевле
 * по коду и памяти, чем ручное управление present/past тензорами.
 */
class OpusModel(
    dir: File,
    val tokenizer: OpusTokenizer,
    private val startId: Int = 62517,
    private val eosId: Int = 0,
    threads: Int = 4
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads.coerceAtLeast(1))
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }

    private val encoder: OrtSession = env.createSession(File(dir, "encoder.onnx").absolutePath, options)
    private val decoder: OrtSession = env.createSession(File(dir, "decoder.onnx").absolutePath, options)

    fun translate(text: String, maxNewTokens: Int = 256): String {
        val ids = tokenizer.encode(text)
        if (ids.isEmpty()) return ""

        val sourceLength = ids.size
        val mask = LongArray(sourceLength) { 1L }

        val hidden = encode(ids, mask)
        val dim = hidden[0].size

        val flat = FloatArray(sourceLength * dim)
        for (i in 0 until sourceLength) {
            System.arraycopy(hidden[i], 0, flat, i * dim, dim)
        }

        val generated = ArrayList<Long>(64)
        generated.add(startId.toLong())

        val limit = minOf(maxNewTokens, sourceLength * 4 + 24)

        OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(flat),
            longArrayOf(1, sourceLength.toLong(), dim.toLong())
        ).use { hiddenTensor ->
            OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(mask),
                longArrayOf(1, sourceLength.toLong())
            ).use { maskTensor ->
                for (step in 0 until limit) {
                    val decIds = generated.toLongArray()
                    var next = eosId
                    OnnxTensor.createTensor(
                        env,
                        LongBuffer.wrap(decIds),
                        longArrayOf(1, decIds.size.toLong())
                    ).use { inputTensor ->
                        decoder.run(
                            mapOf(
                                "input_ids" to inputTensor,
                                "encoder_attention_mask" to maskTensor,
                                "encoder_hidden_states" to hiddenTensor
                            )
                        ).use { result ->
                            @Suppress("UNCHECKED_CAST")
                            val logits = (result.get(0) as OnnxTensor).value as Array<Array<FloatArray>>
                            val last = logits[0][logits[0].size - 1]
                            next = argmax(last)
                        }
                    }
                    generated.add(next.toLong())
                    if (next == eosId) break
                }
            }
        }

        return tokenizer.decode(generated.subList(1, generated.size).toLongArray())
    }

    private fun encode(ids: LongArray, mask: LongArray): Array<FloatArray> {
        OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(ids),
            longArrayOf(1, ids.size.toLong())
        ).use { inputTensor ->
            OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(mask),
                longArrayOf(1, mask.size.toLong())
            ).use { maskTensor ->
                encoder.run(mapOf("input_ids" to inputTensor, "attention_mask" to maskTensor)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val tensor = (result.get(0) as OnnxTensor).value as Array<Array<FloatArray>>
                    return tensor[0]
                }
            }
        }
    }

    private fun argmax(values: FloatArray): Int {
        var bestIndex = 0
        var bestValue = values[0]
        for (i in 1 until values.size) {
            if (values[i] > bestValue) {
                bestValue = values[i]
                bestIndex = i
            }
        }
        return bestIndex
    }

    override fun close() {
        runCatching { encoder.close() }
        runCatching { decoder.close() }
        runCatching { options.close() }
    }
}
