package org.opensapien.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Watch-side foreground recorder. Watches have no ASR engine — audio is
 * recorded locally as 16 kHz WAV, then streamed to the phone over the
 * Wearable Data Layer channel `/open_sapien/audio` (immediately if reachable,
 * else queued and flushed on next connection). The phone transcribes and both
 * sides delete the audio.
 *
 * TODO: AudioRecord capture loop + ChannelClient transfer + local queue.
 */
class WearRecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (isRecording.value) stopRec() else startRec()
        }
        return START_STICKY
    }

    private fun startRec() {
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
        _isRecording.value = true
        // TODO: start AudioRecord → WAV in cacheDir, cap by battery/space.
    }

    private fun stopRec() {
        _isRecording.value = false
        // TODO: finalize WAV, hand off to phone via ChannelClient, delete on ack.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_TOGGLE = "org.opensapien.wear.TOGGLE"
        const val AUDIO_CHANNEL_PATH = "/open_sapien/audio"
        private const val CHANNEL_ID = "recording"

        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording
    }
}
