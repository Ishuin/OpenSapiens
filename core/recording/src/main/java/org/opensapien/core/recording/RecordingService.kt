package org.opensapien.core.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.opensapien.core.data.OpenSapienDb
import org.opensapien.core.data.Transcript
import org.opensapien.core.data.TranscriptFileStore
import org.opensapien.core.transcription.FakeEngine
import org.opensapien.core.transcription.TranscriptionEngine
import java.io.File

/**
 * Foreground microphone service — the single recorder on the phone.
 * App UI, home/lock widget, and Wear all drive it via [ACTION_START]/[ACTION_STOP]/
 * [ACTION_TOGGLE] intents. Survives screen-off and app swipe.
 *
 * Pipeline: PcmRecorder → TranscriptionEngine (stream) → TranscriptFileStore + Room.
 * Audio is never persisted in streaming mode; in record-then-transcribe mode the
 * temp WAV is deleted right after successful transcription.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var engine: TranscriptionEngine = FakeEngine() // TODO: WhisperEngine once native lands
    private var startedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getStringExtra(EXTRA_SOURCE) ?: "PHONE")
            ACTION_STOP -> stop()
            ACTION_TOGGLE ->
                if (state.value is State.Recording) stop()
                else start(intent.getStringExtra(EXTRA_SOURCE) ?: "WIDGET")
        }
        return START_STICKY
    }

    private fun start(source: String) {
        if (job != null) return
        startForegroundCompat()
        startedAt = System.currentTimeMillis()
        _state.value = State.Recording(startedAt)

        job = scope.launch {
            if (!engine.isReady) engine.initialize(File(filesDir, "models/model.bin"))
            val store = TranscriptFileStore(this@RecordingService)
            val fileName = store.newFileName(startedAt)
            val sb = StringBuilder()
            store.write(fileName, "")

            engine.transcribeStream(PcmRecorder().chunks()).collect { seg ->
                if (seg.isFinal) {
                    sb.append(seg.text)
                    store.append(fileName, seg.text)
                }
                _state.value = State.Recording(startedAt, liveText = sb.toString() + seg.text)
            }

            // Flow completes when recording stops (chunks flow closed).
            val text = sb.toString()
            OpenSapienDb.get(this@RecordingService).transcripts().insert(
                Transcript(
                    fileName = fileName,
                    title = text.take(48).ifBlank { fileName },
                    createdAt = startedAt,
                    durationMs = System.currentTimeMillis() - startedAt,
                    preview = text.take(200),
                    source = runCatching { Transcript.Source.valueOf(source) }
                        .getOrDefault(Transcript.Source.PHONE),
                ),
            )
            _state.value = State.Idle
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
        _state.value = State.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("open_sapien recording…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        engine.release()
        super.onDestroy()
    }

    sealed interface State {
        data object Idle : State
        data class Recording(val startedAt: Long, val liveText: String = "") : State
    }

    companion object {
        const val ACTION_START = "org.opensapien.action.START_RECORDING"
        const val ACTION_STOP = "org.opensapien.action.STOP_RECORDING"
        const val ACTION_TOGGLE = "org.opensapien.action.TOGGLE_RECORDING"
        const val EXTRA_SOURCE = "source"
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 42

        private val _state = MutableStateFlow<State>(State.Idle)

        /** Observed by app UI + widget for live status. */
        val state: StateFlow<State> = _state

        fun toggle(context: Context, source: String) {
            context.startForegroundService(
                Intent(context, RecordingService::class.java)
                    .setAction(ACTION_TOGGLE)
                    .putExtra(EXTRA_SOURCE, source),
            )
        }
    }
}
