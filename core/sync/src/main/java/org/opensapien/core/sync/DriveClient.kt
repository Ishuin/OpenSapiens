package org.opensapien.core.sync

import android.content.Context

/**
 * Native Google Drive REST backend, `drive.file` scope only (least privilege —
 * the app can see only files it created). NOT WIRED YET; [SafBackupTarget] is
 * the shipping backup path.
 *
 * Wiring this requires setup outside the codebase:
 *  1. A Google Cloud project with the Drive API enabled.
 *  2. An OAuth client id of type Android, registered against the release
 *     signing SHA-1 (and the debug SHA-1 for local testing).
 *  3. Google's OAuth verification review before a public Play release, since
 *     `drive.file` is a restricted-ish scope for distributed apps.
 *
 * Until then [isLinked] is false and [BackupSyncWorker] ignores this target.
 */
class DriveClient(private val context: Context) : BackupTarget {

    override fun isLinked(): Boolean = false

    override fun describe(): String? = null

    override suspend fun upload(fileName: String, text: String, existingId: String?): String {
        // TODO: Credential Manager sign-in + Drive v3 files.create / files.update
        //  with parent = ensureFolder().
        throw NotImplementedError("Native Drive backend not wired yet; use a backup folder")
    }

    companion object {
        const val FOLDER_NAME = "open_sapien"
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
