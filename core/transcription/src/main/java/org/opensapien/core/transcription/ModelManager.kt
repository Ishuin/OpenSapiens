package org.opensapien.core.transcription

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads, stores and switches on-device ASR models.
 *
 * Models are plain `.onnx` files fetched individually (no archive), which keeps the
 * decoder dependency-free and lets every file resume independently. A model directory
 * only counts as installed once [COMPLETE_MARKER] is written, so a half-finished
 * download can never be handed to the engine.
 */
class ModelManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val modelsRoot = File(appContext.filesDir, "models").apply { mkdirs() }

    var lastError: String? = null
        private set

    // ---------------------------------------------------------------- catalog

    /**
     * A downloadable speech model.
     *
     * @param files remote filenames, saved under [dirOf] with the same name.
     * @param mirrors base URLs tried fastest-first; each must serve every entry in [files].
     */
    data class AsrModel(
        val id: String,
        val displayName: String,
        val tagline: String,
        val languages: String,
        val sizeBytes: Long,
        val multilingual: Boolean,
        val demanding: Boolean,
        val files: List<String>,
        val mirrors: List<String>,
    ) {
        val sizeMb: Int get() = (sizeBytes / (1024 * 1024)).toInt()
    }

    // -------------------------------------------------------------- state

    var activeModelId: String
        get() = prefs.getString(KEY_ACTIVE, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
        set(value) = prefs.edit().putString(KEY_ACTIVE, value).apply()

    val activeModel: AsrModel? get() = CATALOG.firstOrNull { it.id == activeModelId }

    fun dirOf(model: AsrModel): File = File(modelsRoot, model.id)

    fun isInstalled(model: AsrModel): Boolean =
        File(dirOf(model), COMPLETE_MARKER).exists()

    fun installedModels(): List<AsrModel> = CATALOG.filter { isInstalled(it) }

    /** Directory for the selected model, or `null` when it still needs downloading. */
    fun activeModelDir(): File? = activeModel
        ?.takeIf { isInstalled(it) }
        ?.let { dirOf(it) }

    /**
     * True when this device is comfortable running [model]. The 0.6B model needs
     * roughly a gigabyte of headroom; running it on a low-RAM phone will thrash.
     */
    fun isDeviceCapable(model: AsrModel): Boolean {
        if (!model.demanding) return true
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return true
        if (am.isLowRamDevice) return false
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return info.totalMem >= DEMANDING_MIN_TOTAL_RAM
    }

    // ------------------------------------------------------------- install

    fun delete(model: AsrModel) {
        dirOf(model).deleteRecursively()
    }

    /**
     * Downloads every file of [model], reporting overall progress in `0f..1f`.
     * Safe to re-run: completed files are skipped and partial ones resume.
     */
    suspend fun install(
        model: AsrModel,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        lastError = null
        val dest = dirOf(model)

        try {
            if (isInstalled(model)) return@withContext Result.success(dest)
            dest.mkdirs()

            val mirrors = rankMirrors(model.mirrors, model.files.first())
            Log.i(TAG, "installing ${model.id} via ${mirrors.firstOrNull()}")

            var downloadedSoFar = 0L
            for (file in model.files) {
                val target = File(dest, file)
                val alreadyHere = downloadedSoFar

                downloadWithMirrors(mirrors, file, target) { bytesForThisFile ->
                    val total = (alreadyHere + bytesForThisFile).toFloat()
                    onProgress((total / model.sizeBytes).coerceIn(0f, 0.999f))
                }
                downloadedSoFar += target.length()
            }

            File(dest, COMPLETE_MARKER).createNewFile()
            onProgress(1f)
            Result.success(dest)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            Log.w(TAG, "install failed for ${model.id}", t)
            Result.failure(t)
        }
    }

    // ------------------------------------------------------------ transport

    /** Probes each mirror with a tiny ranged GET and orders them fastest-first. */
    private suspend fun rankMirrors(urls: List<String>, probeFile: String): List<String> {
        if (urls.size == 1) return urls
        return coroutineScope {
            urls.map { base ->
                async {
                    val start = System.nanoTime()
                    val ok = runCatching {
                        val conn = (URL("$base/$probeFile").openConnection() as HttpURLConnection)
                        conn.connectTimeout = PROBE_TIMEOUT_MS
                        conn.readTimeout = PROBE_TIMEOUT_MS
                        conn.setRequestProperty("Range", "bytes=0-${PROBE_BYTES - 1}")
                        conn.inputStream.use { it.read(ByteArray(PROBE_BYTES)) }
                        conn.responseCode in 200..299
                    }.getOrDefault(false)
                    base to if (ok) System.nanoTime() - start else Long.MAX_VALUE
                }
            }.awaitAll()
                .sortedBy { it.second }
                .also { ranked ->
                    if (ranked.all { it.second == Long.MAX_VALUE }) {
                        Log.w(TAG, "all mirrors unreachable; will still attempt in listed order")
                    }
                }
                .map { it.first }
        }
    }

    /** Tries each mirror in turn; a failure falls through to the next one. */
    private suspend fun downloadWithMirrors(
        mirrors: List<String>,
        file: String,
        dest: File,
        onBytes: (Long) -> Unit,
    ) {
        var last: Throwable? = null
        for (base in mirrors) {
            try {
                downloadResumable("$base/$file", dest, onBytes)
                return
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                last = t
                Log.w(TAG, "mirror failed for $file: $base (${t.message})")
            }
        }
        throw last ?: IOException("no mirror produced $file")
    }

    /** HTTP download that resumes from whatever is already on disk via `Range`. */
    private fun downloadResumable(url: String, dest: File, onBytes: (Long) -> Unit) {
        dest.parentFile?.mkdirs()
        var already = if (dest.exists()) dest.length() else 0L

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            if (already > 0) setRequestProperty("Range", "bytes=$already-")
        }

        try {
            val resumed = conn.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (already > 0 && !resumed) {
                // Server ignored the range request, so start over rather than corrupt.
                already = 0
                dest.delete()
            }
            if (conn.responseCode !in 200..299) {
                throw IOException("HTTP ${conn.responseCode} for $url")
            }

            val remaining = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            val total = if (remaining > 0) already + remaining else -1L

            RandomAccessFile(dest, "rw").use { out ->
                out.seek(already)
                conn.inputStream.use { input ->
                    val buf = ByteArray(BUFFER_BYTES)
                    var written = already
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        written += n
                        onBytes(written)
                    }
                    if (total > 0 && written < total) {
                        throw IOException("truncated $url at $written of $total")
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "ModelManager"
        private const val PREFS = "asr_models"
        private const val KEY_ACTIVE = "active_model"
        private const val COMPLETE_MARKER = ".complete"

        private const val PROBE_BYTES = 256 * 1024
        private const val PROBE_TIMEOUT_MS = 8_000
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_BYTES = 128 * 1024

        /** Nemotron needs ~1 GB of working set; require a 6 GB-class device. */
        private const val DEMANDING_MIN_TOTAL_RAM = 5_500_000_000L

        const val DEFAULT_MODEL_ID = "zipformer-en-20m"

        private fun hf(repo: String) = "https://huggingface.co/$repo/resolve/main"
        private fun hfMirror(repo: String) = "https://hf-mirror.com/$repo/resolve/main"

        private const val REPO_20M = "csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17"
        private const val REPO_EN = "csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26"
        private const val REPO_NEMOTRON =
            "csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-320ms-int8-2026-06-11"

        val CATALOG: List<AsrModel> = listOf(
            AsrModel(
                id = DEFAULT_MODEL_ID,
                displayName = "Compact",
                tagline = "Fastest. Best on older phones and for all-day recording.",
                languages = "English",
                sizeBytes = 43_649_301L,
                multilingual = false,
                demanding = false,
                files = listOf(
                    "encoder-epoch-99-avg-1.int8.onnx",
                    "decoder-epoch-99-avg-1.int8.onnx",
                    "joiner-epoch-99-avg-1.int8.onnx",
                    "tokens.txt",
                ),
                mirrors = listOf(hf(REPO_20M), hfMirror(REPO_20M)),
            ),
            AsrModel(
                id = "zipformer-en",
                displayName = "Balanced",
                tagline = "Noticeably more accurate. Still keeps up with live speech.",
                languages = "English",
                sizeBytes = 72_654_256L,
                multilingual = false,
                demanding = false,
                files = listOf(
                    "encoder-epoch-99-avg-1-chunk-16-left-64.int8.onnx",
                    "decoder-epoch-99-avg-1-chunk-16-left-64.int8.onnx",
                    "joiner-epoch-99-avg-1-chunk-16-left-64.int8.onnx",
                    "tokens.txt",
                ),
                mirrors = listOf(hf(REPO_EN), hfMirror(REPO_EN)),
            ),
            AsrModel(
                id = "nemotron-3.5-0.6b",
                displayName = "Nemotron 3.5",
                tagline = "Highest accuracy and the only multilingual option. " +
                    "Large download; needs a recent phone.",
                languages = "33 languages, including Hindi",
                sizeBytes = 682_215_471L,
                multilingual = true,
                demanding = true,
                files = listOf(
                    "encoder.int8.onnx",
                    "decoder.int8.onnx",
                    "joiner.int8.onnx",
                    "tokens.txt",
                ),
                mirrors = listOf(hf(REPO_NEMOTRON), hfMirror(REPO_NEMOTRON)),
            ),
        )

        /** Language hints offered when a multilingual model is active. */
        val LANGUAGES: List<Pair<String, String>> = listOf(
            SherpaEngine.LANG_AUTO to "Detect automatically",
            "en" to "English",
            "hi" to "Hindi",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "zh" to "Chinese",
            "ja" to "Japanese",
            "ko" to "Korean",
            "ar" to "Arabic",
            "ru" to "Russian",
            "pt" to "Portuguese",
        )
    }
}
