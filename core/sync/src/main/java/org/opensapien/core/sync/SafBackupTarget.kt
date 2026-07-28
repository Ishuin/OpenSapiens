package org.opensapien.core.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Backup via the Storage Access Framework: the user picks one folder with the
 * system document picker and the app mirrors transcripts into it. The folder
 * can live in Google Drive, OneDrive, Dropbox, an SD card, or local storage —
 * whatever document providers are installed.
 *
 * No account login, no OAuth client id, no extra permission at install time,
 * and the app can only touch the single folder the user pointed at.
 */
class SafBackupTarget(context: Context) : BackupTarget {

    private val appContext = context.applicationContext
    private val prefs = BackupPrefs(appContext)

    private fun root(): DocumentFile? {
        val uri = prefs.treeUri ?: return null
        // The grant survives reboots only while it stays in the persisted list.
        val held = appContext.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isWritePermission }
        if (!held) return null
        return DocumentFile.fromTreeUri(appContext, uri)?.takeIf { it.isDirectory && it.canWrite() }
    }

    override fun isLinked(): Boolean = root() != null

    override fun describe(): String? = root()?.name ?: prefs.treeUri?.let { lastSegmentOf(it) }

    override suspend fun upload(
        fileName: String,
        text: String,
        existingId: String?,
    ): String = withContext(Dispatchers.IO) {
        val dir = root() ?: throw IOException("Backup folder not linked or permission lost")

        // Reuse the previously written document when it is still there, so we
        // update in place rather than piling up "file (1).md" copies.
        val existing = existingId
            ?.let { runCatching { DocumentFile.fromSingleUri(appContext, Uri.parse(it)) }.getOrNull() }
            ?.takeIf { it.exists() && it.canWrite() }
            ?: dir.findFile(fileName)?.takeIf { it.canWrite() }

        val doc = existing
            ?: dir.createFile(MIME, fileName)
            ?: throw IOException("Could not create $fileName in backup folder")

        // "wt" truncates; without it a shorter transcript would leave trailing
        // bytes from the previous, longer version.
        appContext.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IOException("Could not open $fileName for writing")

        doc.uri.toString()
    }

    private fun lastSegmentOf(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: uri.toString()

    companion object {
        private const val MIME = "text/plain"
    }
}
