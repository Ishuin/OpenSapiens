package org.opensapien.core.transcription

import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device streaming ASR backed by sherpa-onnx. Never touches the network.
 *
 * [initialize] takes the *model directory* produced by [ModelManager]. The layout is
 * detected from the files present, so one engine covers every model in the catalog:
 *  - transducer: `encoder*.onnx` + `decoder*.onnx` + `joiner*.onnx`
 *  - zipformer2 CTC: a single `model*.onnx`
 */
class SherpaEngine : TranscriptionEngine {

    override var isReady: Boolean = false
        private set

    private var recognizer: OnlineRecognizer? = null

    /**
     * BCP-47-ish language hint for multilingual models (e.g. `en`, `hi`, `auto`).
     * Ignored by monolingual models. Set before [transcribeStream]/[transcribeFile].
     */
    @Volatile
    var language: String = LANG_AUTO

    override suspend fun initialize(modelFile: File): Unit = withContext(Dispatchers.IO) {
        check(modelFile.isDirectory) { "sherpa model dir missing: $modelFile" }
        release()

        val tokens = File(modelFile, "tokens.txt")
        check(tokens.isFile) { "tokens.txt missing in $modelFile" }

        val modelConfig = buildModelConfig(modelFile, tokens)
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = modelConfig,
            endpointConfig = EndpointConfig(),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
        )

        recognizer = OnlineRecognizer(assetManager = null, config = config)
        isReady = true
        Log.i(TAG, "initialized from ${modelFile.name}")
    }

    private fun buildModelConfig(dir: File, tokens: File): OnlineModelConfig {
        val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)

        fun pick(vararg prefixes: String): File? = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".onnx") }
            // Prefer the int8 variant when a model ships both.
            ?.sortedByDescending { it.name.contains("int8") }
            ?.firstOrNull { f -> prefixes.any { f.name.startsWith(it) } }

        val encoder = pick("encoder")
        val decoder = pick("decoder")
        val joiner = pick("joiner")

        return when {
            encoder != null && decoder != null && joiner != null -> OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    joiner = joiner.absolutePath,
                ),
                tokens = tokens.absolutePath,
                numThreads = threads,
                provider = "cpu",
            )

            else -> {
                val single = pick("model")
                    ?: error("no recognizable .onnx layout in $dir")
                OnlineModelConfig(
                    zipformer2Ctc = OnlineZipformer2CtcModelConfig(model = single.absolutePath),
                    tokens = tokens.absolutePath,
                    numThreads = threads,
                    provider = "cpu",
                )
            }
        }
    }

    private fun OnlineRecognizer.newStream(): OnlineStream = createStream().also { stream ->
        // Multilingual builds (Nemotron 3.5) accept a per-stream language hint.
        // Monolingual builds ignore unknown options, so this is always safe.
        runCatching { stream.setOption(OPT_LANGUAGE, language) }
            .onFailure { Log.d(TAG, "language option unsupported by this model") }
    }

    override fun transcribeStream(pcmChunks: Flow<ShortArray>): Flow<TranscriptionEngine.Segment> =
        flow {
            val rec = checkNotNull(recognizer) { "engine not initialized" }
            val stream = rec.newStream()
            val startedAt = System.currentTimeMillis()
            var segmentStart = 0L
            var lastPartial = ""

            try {
                pcmChunks.collect { chunk ->
                    stream.acceptWaveform(chunk.toFloatSamples(), SAMPLE_RATE)
                    while (rec.isReady(stream)) rec.decode(stream)

                    val elapsed = System.currentTimeMillis() - startedAt
                    val text = rec.getResult(stream).text.trim()

                    if (rec.isEndpoint(stream)) {
                        if (text.isNotBlank()) {
                            emit(
                                TranscriptionEngine.Segment(
                                    text = "$text ",
                                    startMs = segmentStart,
                                    endMs = elapsed,
                                    isFinal = true,
                                ),
                            )
                        }
                        rec.reset(stream)
                        segmentStart = elapsed
                        lastPartial = ""
                    } else if (text.isNotBlank() && text != lastPartial) {
                        lastPartial = text
                        emit(
                            TranscriptionEngine.Segment(
                                text = text,
                                startMs = segmentStart,
                                endMs = elapsed,
                                isFinal = false,
                            ),
                        )
                    }
                }

                // Drain whatever is still buffered when the mic stops.
                stream.inputFinished()
                while (rec.isReady(stream)) rec.decode(stream)
                val tail = rec.getResult(stream).text.trim()
                if (tail.isNotBlank()) {
                    emit(
                        TranscriptionEngine.Segment(
                            text = "$tail ",
                            startMs = segmentStart,
                            endMs = System.currentTimeMillis() - startedAt,
                            isFinal = true,
                        ),
                    )
                }
            } finally {
                stream.release()
            }
        }.flowOn(Dispatchers.Default)

    override suspend fun transcribeFile(wav: File): String = withContext(Dispatchers.Default) {
        val rec = checkNotNull(recognizer) { "engine not initialized" }
        val stream = rec.newStream()
        try {
            val pcm = WavIo.readPcm16Mono(wav)
            val sb = StringBuilder()
            var i = 0
            val step = SAMPLE_RATE / 5 // 200 ms

            while (i < pcm.size) {
                val len = minOf(step, pcm.size - i)
                stream.acceptWaveform(pcm.toFloatSamples(i, len), SAMPLE_RATE)
                while (rec.isReady(stream)) rec.decode(stream)
                if (rec.isEndpoint(stream)) {
                    val text = rec.getResult(stream).text.trim()
                    if (text.isNotBlank()) sb.append(text).append(' ')
                    rec.reset(stream)
                }
                i += len
            }

            stream.inputFinished()
            while (rec.isReady(stream)) rec.decode(stream)
            val tail = rec.getResult(stream).text.trim()
            if (tail.isNotBlank()) sb.append(tail)
            sb.toString().trim()
        } finally {
            stream.release()
        }
    }

    override fun release() {
        recognizer?.release()
        recognizer = null
        isReady = false
    }

    companion object {
        private const val TAG = "SherpaEngine"
        const val SAMPLE_RATE = 16_000
        private const val FEATURE_DIM = 80
        private const val OPT_LANGUAGE = "language"
        const val LANG_AUTO = "auto"

        private const val PCM16_FULL_SCALE = 32768f

        /** sherpa consumes normalised float samples in [-1, 1]; the mic gives us PCM16. */
        private fun ShortArray.toFloatSamples(offset: Int = 0, length: Int = size - offset) =
            FloatArray(length) { this[offset + it] / PCM16_FULL_SCALE }
    }
}
