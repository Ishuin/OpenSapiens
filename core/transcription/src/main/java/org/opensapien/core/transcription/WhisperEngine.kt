package org.opensapien.core.transcription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * whisper.cpp JNI wrapper.
 *
 * TODO(native): add whisper.cpp as a git submodule under /native/whisper.cpp and
 * build libwhisper_jni.so via externalNativeBuild (CMake). Until the native lib
 * is wired, this class throws — [FakeEngine] backs the UI in the interim.
 */
class WhisperEngine : TranscriptionEngine {

    override var isReady: Boolean = false
        private set

    private var ctxPtr: Long = 0

    override suspend fun initialize(modelFile: File) = withContext(Dispatchers.IO) {
        ctxPtr = nativeInit(modelFile.absolutePath)
        check(ctxPtr != 0L) { "whisper init failed for ${modelFile.name}" }
        isReady = true
    }

    override fun transcribeStream(pcmChunks: Flow<ShortArray>): Flow<TranscriptionEngine.Segment> =
        flow<TranscriptionEngine.Segment> {
            val window = ArrayList<Short>(SAMPLE_RATE * 30)
            pcmChunks.collect { chunk ->
                chunk.forEach(window::add)
                // Decode roughly every 3 s of new audio; emit as non-final,
                // finalize the window on silence (simple VAD) or 30 s cap.
                if (window.size >= SAMPLE_RATE * 3) {
                    val text = nativeDecode(ctxPtr, window.toShortArray())
                    emit(TranscriptionEngine.Segment(text, 0, 0, isFinal = false))
                }
            }
            if (window.isNotEmpty()) {
                val text = nativeDecode(ctxPtr, window.toShortArray())
                emit(TranscriptionEngine.Segment(text, 0, 0, isFinal = true))
            }
        }.flowOn(Dispatchers.Default)

    override suspend fun transcribeFile(wav: File): String = withContext(Dispatchers.Default) {
        nativeDecode(ctxPtr, WavIo.readPcm16Mono(wav))
    }

    override fun release() {
        if (ctxPtr != 0L) nativeFree(ctxPtr)
        ctxPtr = 0
        isReady = false
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeDecode(ctx: Long, pcm: ShortArray): String
    private external fun nativeFree(ctx: Long)

    companion object {
        const val SAMPLE_RATE = 16_000

        init {
            runCatching { System.loadLibrary("whisper_jni") }
        }
    }
}
