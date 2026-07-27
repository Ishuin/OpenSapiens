package org.opensapien.core.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Files-as-truth store. One markdown file per transcription under
 * `<filesDir>/open_sapien/`. Drive sync mirrors this directory.
 */
class TranscriptFileStore(context: Context) {

    val root: File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    fun newFileName(createdAt: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(createdAt)) + ".md"

    fun write(fileName: String, text: String) {
        File(root, fileName).writeText(text)
    }

    fun append(fileName: String, text: String) {
        File(root, fileName).appendText(text)
    }

    fun read(fileName: String): String? =
        File(root, fileName).takeIf { it.exists() }?.readText()

    fun delete(fileName: String): Boolean = File(root, fileName).delete()

    fun list(): List<File> =
        root.listFiles { f -> f.isFile }?.sortedByDescending { it.name } ?: emptyList()

    companion object {
        /** Verbatim per spec — also the Drive folder name. */
        const val DIR_NAME = "open_sapien"
    }
}
