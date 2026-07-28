package org.opensapien.core.sync

/**
 * Destination for the one-way local -> cloud mirror of `open_sapien/`.
 *
 * Two implementations exist:
 *  - [SafBackupTarget]: user picks any folder via the system document picker
 *    (Drive, OneDrive, SD card, ...). No account login, no OAuth client.
 *  - [DriveClient]: native Google Drive REST, not wired yet.
 */
interface BackupTarget {

    /** True when the user has configured this target and it is writable. */
    fun isLinked(): Boolean

    /** Human-readable destination for the settings screen, or null when unlinked. */
    fun describe(): String?

    /**
     * Create or overwrite [fileName] in the backup destination.
     *
     * @param existingId opaque id returned by a previous [upload] of the same
     *   transcript, so implementations can update in place instead of
     *   duplicating. Null on first upload.
     * @return opaque id to persist on the transcript row.
     */
    suspend fun upload(fileName: String, text: String, existingId: String?): String
}
