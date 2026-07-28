package org.opensapien.app.wear

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.opensapien.core.data.OpenSapienDb
import org.opensapien.core.data.Transcript
import org.opensapien.core.data.TranscriptFileStore
import org.opensapien.core.transcription.ModelManager
import org.opensapien.core.transcription.SherpaEngine
import java.io.File

/**
 * Batch-transcribes every WAV in `wear_inbox/` with the phone-side ASR engine,
 * persists transcript file + Room row (source WEAR), then deletes the audio —
 * per spec, audio never outlives its transcription.
 */
class WearTranscribeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val inbox = File(applicationContext.filesDir, WearAudioListenerService.INBOX_DIR)
        val files = inbox.listFiles { f -> f.extension == "wav" && f.length() > 44 }
            ?.sortedBy { it.name } ?: return Result.success()
        if (files.isEmpty()) return Result.success()

        val modelManager = ModelManager(applicationContext)
        // Model setup still pending — retry once the user has downloaded one.
        val modelDir = modelManager.activeModelDir() ?: return Result.retry()

        val engine = SherpaEngine()
        return try {
            engine.initialize(modelDir)
            val store = TranscriptFileStore(applicationContext)
            val dao = OpenSapienDb.get(applicationContext).transcripts()
            for (wav in files) {
                val text = engine.transcribeFile(wav)
                val createdAt = wav.nameWithoutExtension
                    .substringAfter("wear_").toLongOrNull() ?: System.currentTimeMillis()
                val fileName = store.newFileName(createdAt)
                store.write(fileName, text)
                dao.insert(
                    Transcript(
                        fileName = fileName,
                        title = text.take(48).ifBlank { fileName },
                        createdAt = createdAt,
                        // PCM16 mono @16 kHz → 32 bytes per ms.
                        durationMs = (wav.length() - 44) / 32,
                        preview = text.take(200),
                        source = Transcript.Source.WEAR,
                    ),
                )
                wav.delete()
            }
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        } finally {
            engine.release()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "wear_transcribe",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<WearTranscribeWorker>().build(),
            )
        }
    }
}
