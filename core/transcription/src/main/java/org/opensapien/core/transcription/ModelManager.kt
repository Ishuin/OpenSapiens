package org.opensapien.core.transcription

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Offline Vosk ASR model metadata. [urls] lists the primary source first followed by
 * mirrors; downloads race a small probe against every mirror and pick the fastest.
 */
data class VoskModel(
    val id: String,
    val urls: List<String>,
    val displayName: String,
    val sizeMb: Int,
    val quality: String,
)

/**
 * Downloads / installs Vosk models under filesDir with a `.complete` marker.
 * The active model id is persisted in SharedPreferences; [modelDir] and [isInstalled]
 * always refer to the active model so existing callers keep working.
 */
class ModelManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
    private val modelsRoot: File = File(appContext.filesDir, "models")
    private val cacheDir: File = File(appContext.cacheDir, "model_staging")

    var activeModel: VoskModel
        get() = CATALOG.firstOrNull { it.id == prefs.getString(KEY_ACTIVE, DEFAULT_MODEL_ID) }
            ?: CATALOG.first()
        set(value) {
            prefs.edit().putString(KEY_ACTIVE, value.id).apply()
        }

    /** Directory to pass to [VoskEngine.initialize] — the active model. */
    val modelDir: File get() = dirOf(activeModel)

    /** True when the active model is fully installed. */
    val isInstalled: Boolean get() = isInstalled(activeModel)

    fun isInstalled(model: VoskModel): Boolean =
        File(dirOf(model), COMPLETE_MARKER).exists()

    fun installedModels(): List<VoskModel> = CATALOG.filter { isInstalled(it) }

    fun dirOf(model: VoskModel): File = File(modelsRoot, model.id)

    fun delete(model: VoskModel) {
        dirOf(model).deleteRecursively()
        stagingZip(model).delete()
    }

    /**
     * Download (from the fastest reachable mirror, resuming partial data) and unzip.
     * Safe to call repeatedly; no-op once installed. [onProgress] gets 0..100.
     */
    suspend fun install(model: VoskModel, onProgress: (Int) -> Unit = {}) {
        if (isInstalled(model)) return
        withContext(Dispatchers.IO) {
            cacheDir.mkdirs()
            val zip = stagingZip(model)
            downloadWithMirrors(model, zip, onProgress)
            val destDir = dirOf(model)
            destDir.deleteRecursively()
            val staging = File(cacheDir, "${model.id}.staging")
            staging.deleteRecursively()
            unzip(zip, staging)
            // Zip contains a single root folder named after the model; flatten it.
            val extractedRoot = staging.listFiles()?.singleOrNull { it.isDirectory } ?: staging
            modelsRoot.mkdirs()
            if (!extractedRoot.renameTo(destDir)) {
                extractedRoot.copyRecursively(destDir, overwrite = true)
                extractedRoot.deleteRecursively()
            }
            staging.deleteRecursively()
            zip.delete()
            check(File(destDir, "conf").exists() || destDir.listFiles()?.isNotEmpty() == true) {
                "Model install failed: ${model.id}"
            }
            File(destDir, COMPLETE_MARKER).createNewFile()
        }
    }

    // --- download internals -------------------------------------------------

    private fun stagingZip(model: VoskModel) = File(cacheDir, "${model.id}.zip")

    /**
     * Races a [PROBE_BYTES] ranged read against every mirror, then downloads from the
     * fastest (falling back to the others in probe order), resuming partial files.
     */
    private suspend fun downloadWithMirrors(model: VoskModel, dest: File, onProgress: (Int) -> Unit) {
        val ordered = rankMirrors(model.urls)
        var lastError: Exception? = null
        for (url in ordered) {
            try {
                downloadResumable(url, dest, model.sizeMb.toLong() * 1024 * 1024, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("All mirrors failed for ${model.id}", lastError)
    }

    /** Probe all mirrors in parallel; fastest first. Unreachable mirrors go last. */
    private suspend fun rankMirrors(urls: List<String>): List<String> = coroutineScope {
        if (urls.size <= 1) return@coroutineScope urls
        val probes = urls.map { url ->
            async(Dispatchers.IO) {
                val start = System.nanoTime()
                try {
                    val conn = open(url)
                    conn.setRequestProperty("Range", "bytes=0-${PROBE_BYTES - 1}")
                    conn.connectTimeout = PROBE_TIMEOUT_MS
                    conn.readTimeout = PROBE_TIMEOUT_MS
                    try {
                        if (conn.responseCode !in 200..299) return@async url to Long.MAX_VALUE
                        val buf = ByteArray(16 * 1024)
                        var total = 0
                        conn.inputStream.use { input ->
                            while (total < PROBE_BYTES) {
                                val n = input.read(buf)
                                if (n < 0) break
                                total += n
                            }
                        }
                        url to (System.nanoTime() - start)
                    } finally {
                        conn.disconnect()
                    }
                } catch (_: Exception) {
                    url to Long.MAX_VALUE
                }
            }
        }
        probes.map { it.await() }.sortedBy { it.second }.map { it.first }
    }

    /** HTTP download with Range resume into [dest]. Throws on any failure. */
    private fun downloadResumable(url: String, dest: File, expectedTotal: Long, onProgress: (Int) -> Unit) {
        var already = if (dest.exists()) dest.length() else 0L
        val conn = open(url)
        if (already > 0) conn.setRequestProperty("Range", "bytes=$already-")
        try {
            val code = conn.responseCode
            when {
                code == HttpURLConnection.HTTP_PARTIAL -> Unit // resume honored
                code in 200..299 -> {
                    // Server ignored Range; restart from zero.
                    dest.delete()
                    already = 0
                }
                else -> throw IOException("HTTP $code from $url")
            }
            val remaining = conn.contentLengthLong
            val total = if (remaining > 0) already + remaining else expectedTotal
            RandomAccessFile(dest, "rw").use { out ->
                out.seek(already)
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var written = already
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                        val pct = ((written * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                    if (remaining > 0 && written - already < remaining) {
                        throw IOException("Truncated download from $url")
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }

    private fun unzip(zip: File, destDir: File) {
        destDir.mkdirs()
        val destCanonical = destDir.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val out = File(destDir, entry.name)
                if (!out.canonicalPath.startsWith(destCanonical)) {
                    throw IOException("Zip-slip blocked: ${entry.name}")
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
        private const val KEY_ACTIVE = "active_model_id"
        private const val COMPLETE_MARKER = ".complete"
        private const val PROBE_BYTES = 256 * 1024
        private const val PROBE_TIMEOUT_MS = 8_000
        const val DEFAULT_MODEL_ID = "vosk-model-small-en-us-0.15"

        // Kept for backward compatibility with earlier callers.
        const val MODEL_NAME = DEFAULT_MODEL_ID
        const val MODEL_SIZE_MB = 40

        private const val ALPHACEPHEI = "https://alphacephei.com/vosk/models"
        private const val HF_GRIMSO = "https://huggingface.co/grimso/vosk-models/resolve/main"
        private const val HF_RHASSPY = "https://huggingface.co/rhasspy/vosk-models/resolve/main/en"

        val CATALOG: List<VoskModel> = listOf(
            VoskModel(
                id = "vosk-model-small-en-us-0.15",
                urls = listOf(
                    "$ALPHACEPHEI/vosk-model-small-en-us-0.15.zip",
                    "$HF_GRIMSO/vosk-model-small-en-us-0.15.zip",
                    "$HF_RHASSPY/vosk-model-small-en-us-0.15.zip",
                ),
                displayName = "Small (fast)",
                sizeMb = 40,
                quality = "Basic accuracy. Fastest, lowest battery use. Fine for close-range dictation.",
            ),
        )
    }
}
