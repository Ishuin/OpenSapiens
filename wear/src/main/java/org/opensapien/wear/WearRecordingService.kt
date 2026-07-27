package org.opensapien.wear

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.IBinder
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Watch-side foreground recorder. Watches run no ASR — audio is recorded
 * locally as 16 kHz mono PCM16 WAV, then streamed to the phone over the
 * Wearable Data Layer channel `/open_sapien/audio` (immediately if reachable,
 * else it stays queued and is flushed on the next record/stop/app-open).
 * The phone transcribes; both sides delete the audio.
 */
class WearRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recJob: Job? = null

    @Volatile
    private var stopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (recJob != null) stopRec() else startRec()
            ACTION_FLUSH -> if (recJob == null) {
                scope.launch {
                    flushQueue()
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startRec() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        stopRequested = false
        _isRecording.value = true
        recJob = scope.launch {
            val wav = File(queueDir(), "wear_${System.currentTimeMillis()}.wav")
            try {
                recordWav(wav)
            } catch (t: Throwable) {
                wav.delete()
            } finally {
                _isRecording.value = false
                recJob = null
                runCatching { flushQueue() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopRec() {
        stopRequested = true // recJob finalizes the WAV, flushes, and exits
    }

    @SuppressLint("MissingPermission")
    private fun recordWav(dest: File) {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 4,
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
        val agc = if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
            android.media.audiofx.AutomaticGainControl.create(record.audioSessionId)
                ?.apply { enabled = true }
        } else null
        val ns = if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
            android.media.audiofx.NoiseSuppressor.create(record.audioSessionId)
                ?.apply { enabled = true }
        } else null
        record.startRecording()

        var dataLen = 0
        try {
            RandomAccessFile(dest, "rw").use { raf ->
                raf.write(ByteArray(44)) // header placeholder
                val buf = ByteArray(SAMPLE_RATE / 5 * 2) // 200 ms
                while (!stopRequested && dataLen < MAX_DATA_BYTES &&
                    record.recordingState == AudioRecord.RECORDSTATE_RECORDING
                ) {
                    val n = record.read(buf, 0, buf.size)
                    if (n > 0) {
                        raf.write(buf, 0, n)
                        dataLen += n
                    }
                }
                raf.seek(0)
                raf.write(wavHeader(dataLen))
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            runCatching { agc?.release() }
            runCatching { ns?.release() }
        }
        if (dataLen == 0) dest.delete()
    }

    private fun wavHeader(dataLen: Int): ByteArray =
        ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataLen); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataLen)
        }.array()

    /** Send every queued WAV to the phone; delete local copy on success. */
    private suspend fun flushQueue() {
        val files = queueDir().listFiles { f -> f.extension == "wav" && f.length() > 44 }
            ?.sortedBy { it.name } ?: return
        if (files.isEmpty()) return
        val node = runCatching {
            Wearable.getNodeClient(this).connectedNodes.await()
                .firstOrNull { it.isNearby } ?: Wearable.getNodeClient(this).connectedNodes.await().firstOrNull()
        }.getOrNull() ?: return // phone unreachable — keep queued

        val channelClient = Wearable.getChannelClient(this)
        for (wav in files) {
            runCatching {
                val channel = channelClient.openChannel(node.id, AUDIO_CHANNEL_PATH).await()
                // sendFile streams the file and closes the output side when done.
                channelClient.sendFile(channel, Uri.fromFile(wav)).await()
                wav.delete()
            }
        }
    }

    private fun queueDir(): File = File(filesDir, "queue").apply { mkdirs() }

    override fun onDestroy() {
        stopRequested = true
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "org.opensapien.wear.TOGGLE"
        const val ACTION_FLUSH = "org.opensapien.wear.FLUSH"
        const val AUDIO_CHANNEL_PATH = "/open_sapien/audio"
        private const val CHANNEL_ID = "recording"
        private const val SAMPLE_RATE = 16_000

        /** 1 h cap: 16 kHz × 2 B × 3600 s. */
        private const val MAX_DATA_BYTES = 16_000 * 2 * 3600

        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording
    }
}
