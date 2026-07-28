package org.opensapien.core.sync

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Tiny persistence layer for the chosen backup folder. Deliberately
 * SharedPreferences (not DataStore) so [BackupSyncWorker] can read it
 * synchronously on a background thread without pulling in Flow plumbing.
 */
class BackupPrefs(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Tree uri returned by ACTION_OPEN_DOCUMENT_TREE, or null when unlinked. */
    var treeUri: Uri?
        get() = prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_TREE_URI) else putString(KEY_TREE_URI, value.toString())
        }.apply()

    /** Only back up on unmetered networks. Default true. */
    var unmeteredOnly: Boolean
        get() = prefs.getBoolean(KEY_UNMETERED, true)
        set(value) = prefs.edit().putBoolean(KEY_UNMETERED, value).apply()

    companion object {
        private const val PREFS = "backup_prefs"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_UNMETERED = "unmetered_only"

        /** Flags to pass to takePersistableUriPermission after the picker returns. */
        const val PERSIST_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
