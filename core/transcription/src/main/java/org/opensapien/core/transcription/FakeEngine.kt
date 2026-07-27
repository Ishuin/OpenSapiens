package org.opensapien.core.transcription

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Development stand-in until the whisper.cpp native build lands.
 * Emits placeholder text so the full capture→file→sync pipeline is testable.
 */
class FakeEngine : TranscriptionEngine {
    override var isReady: Boolean = false
        private set

    override suspend fun initialize(modelFile: File) {
        isReady = true
    }

    override fun transcribeStream(pcmChunks: Flow<ShortArray>): Flow<TranscriptionEngine.Segment> =
        flow {
            var i = 0
            pcmChunks.collect {
                if (++i % 50 == 0) {
                    emit(TranscriptionEngine.Segment("[fake segment $i] ", 0, 0, isFinal = true))
                }
            }
        }

    override suspend fun transcribeFile(wav: File): String {
        delay(300)
        return "[fake transcription of ${wav.name}]"
    }

    override fun release() {
        isReady = false
    }
}
