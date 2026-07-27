package org.opensapien.core.transcription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Real on-device ASR backed by Vosk (bundled native lib, fully offline).
 * [initialize] takes the *model directory* (see [ModelManager.modelDir]).
 */
class VoskEngine : TranscriptionEngine {

    override var isReady: Boolean = false
        private set

    private var model: Model? = null

    override suspend fun initialize(modelFile: File) = withContext(Dispatchers.IO) {
        check(modelFile.isDirectory) { "vosk model dir missing: $modelFile" }
        model = Model(modelFile.absolutePath)
        isReady = true
    }

    override fun transcribeStream(pcmChunks: Flow<ShortArray>): Flow<TranscriptionEngine.Segment> =
        flow {
            val m = checkNotNull(model) { "engine not initialized" }
            val rec = Recognizer(m, SAMPLE_RATE.toFloat())
            val startedAt = System.currentTimeMillis()
            try {
                pcmChunks.collect { chunk ->
                    val elapsed = System.currentTimeMillis() - startedAt
                    if (rec.acceptWaveForm(chunk, chunk.size)) {
                        val text = JSONObject(rec.result).optString("text")
                        if (text.isNotBlank()) {
                            emit(TranscriptionEngine.Segment("$text ", 0, elapsed, isFinal = true))
                        }
                    } else {
                        val partial = JSONObject(rec.partialResult).optString("partial")
                        if (partial.isNotBlank()) {
                            emit(TranscriptionEngine.Segment(partial, 0, elapsed, isFinal = false))
                        }
                    }
                }
                val text = JSONObject(rec.finalResult).optString("text")
                if (text.isNotBlank()) {
                    emit(
                        TranscriptionEngine.Segment(
                            "$text ", 0, System.currentTimeMillis() - startedAt, isFinal = true,
                        ),
                    )
                }
            } finally {
                rec.close()
            }
        }.flowOn(Dispatchers.Default)

    override suspend fun transcribeFile(wav: File): String = withContext(Dispatchers.Default) {
        val m = checkNotNull(model) { "engine not initialized" }
        val pcm = WavIo.readPcm16Mono(wav)
        val rec = Recognizer(m, SAMPLE_RATE.toFloat())
        try {
            val sb = StringBuilder()
            var i = 0
            val step = SAMPLE_RATE / 5 // 200 ms
            while (i < pcm.size) {
                val len = minOf(step, pcm.size - i)
                val chunk = pcm.copyOfRange(i, i + len)
                if (rec.acceptWaveForm(chunk, chunk.size)) {
                    val text = JSONObject(rec.result).optString("text")
                    if (text.isNotBlank()) sb.append(text).append(' ')
                }
                i += len
            }
            val tail = JSONObject(rec.finalResult).optString("text")
            if (tail.isNotBlank()) sb.append(tail)
            sb.toString().trim()
        } finally {
            rec.close()
        }
    }

    override fun release() {
        model?.close()
        model = null
        isReady = false
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
