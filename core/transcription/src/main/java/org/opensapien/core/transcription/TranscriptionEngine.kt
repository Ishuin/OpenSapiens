package org.opensapien.core.transcription

import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * On-device ASR abstraction. Implementations must never touch the network.
 * Engines are swappable (whisper.cpp primary; Vosk possible alternative).
 */
interface TranscriptionEngine {

    /** True once the model is loaded and [transcribeStream]/[transcribeFile] may be called. */
    val isReady: Boolean

    /** Load model from disk. Heavy; call off the main thread. */
    suspend fun initialize(modelFile: File)

    /**
     * Streaming mode: feed 16 kHz mono PCM16 chunks, collect partial/final segments.
     * Emitted [Segment]s with [Segment.isFinal] = true will not change again.
     */
    fun transcribeStream(pcmChunks: Flow<ShortArray>): Flow<Segment>

    /** Batch mode: transcribe a whole WAV file (16 kHz mono PCM16). */
    suspend fun transcribeFile(wav: File): String

    fun release()

    data class Segment(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val isFinal: Boolean,
    )
}
