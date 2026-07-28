package org.opensapien.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.opensapien.core.data.OpenSapienDb
import org.opensapien.core.data.Transcript
import org.opensapien.core.data.TranscriptFileStore
import java.util.concurrent.TimeUnit

/**
 * One-way local -> backup-folder mirror of `open_sapien/`. Runs only when the
 * user has linked a destination; otherwise it no-ops, so the app stays fully
 * functional offline with nothing leaving the device.
 */
class BackupSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val target = resolveTarget(applicationContext)
        if (target == null || !target.isLinked()) return Result.success()

        val dao = OpenSapienDb.get(applicationContext).transcripts()
        val store = TranscriptFileStore(applicationContext)
        var anyFailed = false

        for (t in dao.pendingSync()) {
            val text = store.read(t.fileName) ?: continue
            runCatching { target.upload(t.fileName, text, t.driveFileId) }.fold(
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
        private const val WORK_NAME = "backup_sync"
        private const val WORK_NAME_NOW = "backup_sync_now"

        /**
         * Currently the SAF folder; native Drive (OAuth) slots in here once
         * [DriveClient] is wired.
         */
        fun resolveTarget(context: Context): BackupTarget? =
            SafBackupTarget(context).takeIf { it.isLinked() }

        /** Periodic mirror. Call from Application.onCreate and after linking. */
        fun schedule(context: Context) {
            val unmeteredOnly = BackupPrefs(context).unmeteredOnly
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BackupSyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build(),
            )
        }

        /** Fire an immediate pass, e.g. right after the user picks a folder. */
        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BackupSyncWorker>().build(),
            )
        }

        /** Stop the periodic pass when the user unlinks the destination. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
