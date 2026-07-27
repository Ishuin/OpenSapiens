package org.opensapien.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.opensapien.core.data.OpenSapienDb
import org.opensapien.core.data.Transcript
import org.opensapien.core.data.TranscriptFileStore
import java.util.concurrent.TimeUnit

/**
 * One-way local→Drive mirror of open_sapien/. Runs only when a network
 * constraint is met and only if the user has linked an account
 * ([DriveClient.isLinked]); otherwise it no-ops — the app is fully
 * functional offline.
 */
class DriveSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val drive = DriveClient(applicationContext)
        if (!drive.isLinked()) return Result.success()

        val dao = OpenSapienDb.get(applicationContext).transcripts()
        val store = TranscriptFileStore(applicationContext)
        var anyFailed = false

        for (t in dao.pendingSync()) {
            val text = store.read(t.fileName) ?: continue
            val result = runCatching { drive.upload(t.fileName, text, t.driveFileId) }
            result.fold(
                onSuccess = { fileId ->
                    dao.update(t.copy(syncState = Transcript.SyncState.SYNCED, driveFileId = fileId))
                },
                onFailure = {
                    anyFailed = true
                    dao.update(t.copy(syncState = Transcript.SyncState.FAILED))
                },
            )
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "drive_sync"

        /** Call from Application.onCreate. Wi-Fi-only is a user setting (default). */
        fun schedule(context: Context, unmeteredOnly: Boolean = true) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DriveSyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build(),
            )
        }
    }
}
