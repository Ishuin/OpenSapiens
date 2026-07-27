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
 * A downloadable offline speech model. [sizeMb] is the approximate download
 * size shown to the user before they commit; [quality] is a one-line
 * plain-English description of the accuracy/speed trade-off.
 */
data class VoskModel(
    val id: String,
    val displayName: String,
    val sizeMb: Int,
    val quality: String,
    val url: String,
)

/**
 * Download / install / delete / switch between offline Vosk models. After
 * install the app never needs the network for transcription. Each model lands
 * under `<filesDir>/models/<model-id>/` with a `.complete` marker. The active
 * model id is persisted in SharedPreferences; [modelDir] and [isInstalled]
 * always refer to the active model so existing callers keep working.
 */
class ModelManager(context: Context) {

    private val modelsRoot = File(context.filesDir, "models")
    private val cacheDir: File = context.cacheDir
    private val prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)

    /** Currently selected model (defaults to the small model). */
    var activeModel: VoskModel
        get() {
            val id = prefs.getString(KEY_ACTIVE, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
            return CATALOG.firstOrNull { it.id == id } ?: CATALOG.first()
        }
        set(value) {
            prefs.edit().putString(KEY_ACTIVE, value.id).apply()
        }

    /** Directory to pass to [VoskEngine.initialize] — the active model. */
    val modelDir: File get() = dirOf(activeModel)

    /** True when the *active* model is fully installed. */
    val isInstalled: Boolean get() = isInstalled(activeModel)

    fun dirOf(model: VoskModel) = File(modelsRoot, model.id)

    fun isInstalled(model: VoskModel) = File(dirOf(model), ".complete").exists()

    fun installedModels(): List<VoskModel> = CATALOG.filter { isInstalled(it) }

    /** Delete a downloaded model. If it was active, falls back to the default id. */
    fun delete(model: VoskModel) {
        dirOf(model).deleteRecursively()
        File(modelsRoot, "${model.id}.staging").deleteRecursively()
    }

    /** Download + unzip [model]. Safe to call repeatedly; no-op once installed. */
    suspend fun install(
        model: VoskModel = activeModel,
        onProgress: (percent: Int) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        if (isInstalled(model)) return@withContext
        modelsRoot.mkdirs()
        val zip = File(cacheDir, "${model.id}.zip")
        try {
            download(model.url, zip, onProgress)
            val staging = File(modelsRoot, "${model.id}.staging")
            staging.deleteRecursively()
            staging.mkdirs()
            unzip(zip, staging)
            // Zip contains a single root folder named after the model.
            val extractedRoot = File(staging, model.id).takeIf { it.isDirectory } ?: staging
            val target = dirOf(model)
            target.deleteRecursively()
            check(extractedRoot.renameTo(target)) { "model install failed (rename)" }
            staging.deleteRecursively()
            val marker = File(target, ".complete")
            check(marker.createNewFile() || marker.exists())
        } finally {
            zip.delete()
        }
    }

    private fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
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
        const val KEY_ACTIVE = "active_model_id"
        const val DEFAULT_MODEL_ID = "vosk-model-small-en-us-0.15"

        /** Kept for backward compatibility with earlier callers. */
        const val MODEL_NAME = DEFAULT_MODEL_ID
        const val MODEL_SIZE_MB = 40

        val CATALOG = listOf(
            VoskModel(
                id = "vosk-model-small-en-us-0.15",
                displayName = "Small (fast)",
                sizeMb = 40,
                quality = "Basic accuracy. Fastest, lowest battery use. Fine for close-range dictation.",
                url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            ),
            VoskModel(
                id = "vosk-model-en-us-0.22-lgraph",
                displayName = "Medium (balanced)",
                sizeMb = 128,
                quality = "Noticeably better accuracy, still quick. Recommended for most phones.",
                url = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22-lgraph.zip",
            ),
            VoskModel(
                id = "vosk-model-en-us-0.22",
                displayName = "Large (best accuracy)",
                sizeMb = 1800,
                quality = "Highest accuracy, best for distant/noisy audio. Big download, needs ~4 GB free and a recent phone.",
                url = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip",
            ),
        )
    }
}
