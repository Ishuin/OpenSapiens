package org.opensapien.core.recording

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlin.concurrent.thread

/**
 * Raw mic capture: 16 kHz mono PCM16, chunked as ShortArray — the exact format
 * whisper.cpp expects. Caller must hold RECORD_AUDIO permission.
 */
class PcmRecorder(private val sampleRate: Int = 16_000) {

    @SuppressLint("MissingPermission")
    fun chunks(): Flow<ShortArray> = callbackFlow {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 4,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
        record.startRecording()

        val reader = thread(name = "pcm-reader") {
            val buf = ShortArray(sampleRate / 10) // 100 ms chunks
            while (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) trySend(buf.copyOf(n))
            }
        }

        awaitClose {
            runCatching {
                record.stop()
                record.release()
            }
            reader.join(500)
        }
    }
}
