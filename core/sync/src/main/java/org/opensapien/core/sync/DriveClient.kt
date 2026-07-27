package org.opensapien.core.sync

import android.content.Context

/**
 * Google Drive REST wrapper, `drive.file` scope only (least privilege — the app
 * can see only files it created). Ensures an `open_sapien` folder exists at
 * Drive root and uploads/updates one file per transcription.
 *
 * TODO(auth): wire Google Sign-In / AuthorizationClient to obtain credentials,
 * store the linked account name in DataStore. Until then [isLinked] is false
 * and sync no-ops, keeping the app 100% offline-functional.
 */
class DriveClient(private val context: Context) {

    fun isLinked(): Boolean = false // TODO: read linked account from DataStore

    /**
     * Create or update [fileName] inside the Drive `open_sapien` folder.
     * @return the Drive file id.
     */
    suspend fun upload(fileName: String, text: String, existingFileId: String?): String {
        // TODO: Drive v3 files.create / files.update with parent = ensureFolder()
        throw NotImplementedError("Drive auth not wired yet")
    }

    /** Find-or-create the open_sapien folder at Drive root. */
    private suspend fun ensureFolder(): String {
        throw NotImplementedError()
    }

    companion object {
        const val FOLDER_NAME = "open_sapien"
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
