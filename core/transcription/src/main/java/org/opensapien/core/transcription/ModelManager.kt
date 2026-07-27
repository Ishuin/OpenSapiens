package org.opensapien.core.transcription

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * One-time download + install of the Vosk model (~40 MB). After install the
 * app never needs the network for transcription. Files land under
 * `<filesDir>/models/vosk-model-small-en-us-0.15/` with a `.complete` marker.
 */
class ModelManager(context: Context) {

    private val modelsRoot = File(context.filesDir, "models")
    private val cacheDir: File = context.cacheDir

    /** Directory to pass to [VoskEngine.initialize]. */
    val modelDir = File(modelsRoot, MODEL_NAME)

    private val marker = File(modelDir, ".complete")

    val isInstalled: Boolean get() = marker.exists()

    /** Download + unzip. Safe to call repeatedly; no-op once installed. */
    suspend fun install(onProgress: (percent: Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        if (isInstalled) return@withContext
        modelsRoot.mkdirs()
        val zip = File(cacheDir, "$MODEL_NAME.zip")
        try {
            download(zip, onProgress)
            val staging = File(modelsRoot, "$MODEL_NAME.staging")
            staging.deleteRecursively()
            staging.mkdirs()
            unzip(zip, staging)
            // Zip contains a single root folder named after the model.
            val extractedRoot = File(staging, MODEL_NAME).takeIf { it.isDirectory } ?: staging
            modelDir.deleteRecursively()
            check(extractedRoot.renameTo(modelDir)) { "model install failed (rename)" }
            staging.deleteRecursively()
            check(marker.createNewFile() || marker.exists())
        } finally {
            zip.delete()
        }
    }

    private fun download(dest: File, onProgress: (Int) -> Unit) {
        val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode !in 200..299) {
                throw IOException("model download failed: HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().buffered().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(((read * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun unzip(zip: File, destDir: File) {
        val destCanonical = destDir.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val out = File(destDir, entry.name)
                if (!out.canonicalPath.startsWith(destCanonical)) {
                    throw IOException("zip-slip blocked: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().buffered().use { zin.copyTo(it) }
                }
                zin.closeEntry()
            }
        }
    }

    companion object {
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        const val MODEL_SIZE_MB = 40
    }
}
