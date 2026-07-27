package org.opensapien.core.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import org.opensapien.core.data.OpenSapienDb
import org.opensapien.core.data.Transcript
import org.opensapien.core.data.TranscriptFileStore
import org.opensapien.core.transcription.ModelManager
import org.opensapien.core.transcription.TranscriptionEngine
import org.opensapien.core.transcription.VoskEngine

/**
 * Foreground microphone service — the single recorder on the phone.
 * App UI, home/lock widget, and Wear all drive it via [ACTION_START]/[ACTION_STOP]/
 * [ACTION_TOGGLE] intents. Survives screen-off and app swipe.
 *
 * Pipeline: PcmRecorder → TranscriptionEngine (stream) → TranscriptFileStore + Room.
 * Audio is never persisted in streaming mode.
 *
 * Stop is *graceful*: [PcmRecorder.stop] completes the PCM flow normally so the
 * engine finalizes and the transcript row is inserted before the service exits.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var recorder: PcmRecorder? = null
    private var engine: TranscriptionEngine = VoskEngine()
    private var engineModelDir: String? = null
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

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            fail("Microphone permission needed — open the app once to grant it.")
            return
        }
        val modelManager = ModelManager(this)
        if (!modelManager.isInstalled) {
            fail("Speech model not downloaded — open the app to set it up.")
            return
        }

        startedAt = System.currentTimeMillis()
        _state.value = State.Recording(startedAt)
        val rec = PcmRecorder()
        recorder = rec

        job = scope.launch {
            var errored = false
            try {
                val wantedDir = modelManager.modelDir.absolutePath
                if (engine.isReady && engineModelDir != wantedDir) {
                    // User switched models since last recording — reload.
                    engine.release()
                    engine = VoskEngine()
                }
                if (!engine.isReady) {
                    engine.initialize(modelManager.modelDir)
                    engineModelDir = wantedDir
                }
                val store = TranscriptFileStore(this@RecordingService)
                val fileName = store.newFileName(startedAt)
                val sb = StringBuilder()
                store.write(fileName, "")

                engine.transcribeStream(rec.chunks().buffer(capacity = 600)).collect { seg ->
                    if (seg.isFinal) {
                        sb.append(seg.text)
                        store.append(fileName, seg.text)
                        _state.value = State.Recording(startedAt, liveText = sb.toString())
                    } else {
                        _state.value = State.Recording(startedAt, liveText = sb.toString() + seg.text)
                    }
                }

                // Flow completed → recording stopped gracefully; persist the row.
                val text = sb.toString().trim()
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
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                errored = true
                _state.value = State.Error(t.message ?: "recording failed")
            } finally {
                recorder = null
                job = null
                if (!errored) _state.value = State.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stop() {
        val rec = recorder
        if (rec != null) {
            rec.stop() // pipeline finishes + persists, then service exits itself
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun fail(message: String) {
        _state.value = State.Error(message)
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
        data class Error(val message: String) : State
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
