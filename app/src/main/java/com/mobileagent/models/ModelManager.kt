package com.mobileagent.models

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val runtime: String,
    val downloadUrl: String? = null,
    val expectedBytes: Long = 0,
    val sha256: String? = null,
    val companionFiles: List<String> = emptyList(),
)

enum class ModelStatus {
    READY,
    MISSING,
    DOWNLOADING,
    MANUAL,
    FAILED,
}

data class ModelInfo(
    val spec: ModelSpec,
    val status: ModelStatus,
    val file: File,
    val bytes: Long = 0,
    val totalBytes: Long = spec.expectedBytes,
    val error: String? = null,
)

class ModelManager(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    val directory: File = File(context.filesDir, "models").also { it.mkdirs() }
    private val downloadMutex = Mutex()

    fun fileFor(spec: ModelSpec): File = File(directory, spec.fileName)

    suspend fun inspect(spec: ModelSpec): ModelInfo = withContext(io) {
        val file = fileFor(spec)
        when {
            spec.downloadUrl == null && isReady(file, spec) && companionsReady(spec) -> {
                ModelInfo(spec, ModelStatus.READY, file, file.length(), spec.expectedBytes)
            }
            spec.downloadUrl == null -> ModelInfo(spec, ModelStatus.MANUAL, file)
            isReady(file, spec) -> ModelInfo(spec, ModelStatus.READY, file, file.length(), spec.expectedBytes)
            else -> ModelInfo(spec, ModelStatus.MISSING, file)
        }
    }

    suspend fun download(
        spec: ModelSpec,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(io) {
        downloadMutex.withLock {
        val url = spec.downloadUrl
            ?: return@withContext Result.failure(IllegalStateException("Kein Download für ${spec.id} hinterlegt"))
        val target = fileFor(spec)
        if (isReady(target, spec)) return@withContext Result.success(target)

        val temporary = File(directory, "${spec.fileName}.part")
        temporary.delete()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MobileAgent/0.1")
        }
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) connection.disconnect()
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: spec.expectedBytes
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                }
            }
            if (spec.expectedBytes > 0 && downloaded != spec.expectedBytes) {
                throw IOException("Unvollständiger Download: $downloaded/${spec.expectedBytes} Bytes")
            }
            val expectedHash = spec.sha256
            if (!expectedHash.isNullOrBlank()) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedHash, ignoreCase = true)) {
                    throw IOException("SHA-256-Prüfung fehlgeschlagen")
                }
            }
            if (!temporary.renameTo(target)) {
                throw IOException("Atomares Umbenennen der Modelldatei fehlgeschlagen")
            }
            Result.success(target)
        } catch (error: CancellationException) {
            temporary.delete()
            throw error
        } catch (error: Throwable) {
            temporary.delete()
            Result.failure(error)
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
        }
    }

    suspend fun delete(spec: ModelSpec): Boolean = withContext(io) {
        fileFor(spec).delete()
    }

    private fun companionsReady(spec: ModelSpec): Boolean =
        spec.companionFiles.all { File(directory, it).isFile }

    private fun isReady(file: File, spec: ModelSpec): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (spec.expectedBytes > 0 && file.length() != spec.expectedBytes) return false
        val expectedHash = spec.sha256 ?: return true
        return sha256(file).equals(expectedHash, ignoreCase = true)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

object DefaultModels {
    val whisper = ModelSpec(
        id = "whisper-tiny",
        displayName = "Whisper tiny",
        fileName = "ggml-tiny.bin",
        runtime = "whisper.cpp",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
        expectedBytes = 77_691_713,
        sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
    )
    val bonsai = ModelSpec(
        id = "bonsai-1.7b",
        displayName = "Ternary Bonsai 1.7B",
        fileName = "Ternary-Bonsai-1.7B-Q2_0.gguf",
        runtime = "llama.cpp / Prism Q2_0",
        downloadUrl = "https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf/resolve/main/Ternary-Bonsai-1.7B-Q2_0.gguf",
        expectedBytes = 463_290_464,
        sha256 = "d97d94eb564590c9f0300e54d3f87bbbb25a78693d0ade9f6e177973dcb8228a",
    )
    val laya = ModelSpec(
        id = "laya-multilingual",
        displayName = "Laya multilingual",
        fileName = "laya-multilingual.onnx",
        runtime = "ONNX Runtime Mobile",
        downloadUrl = null,
        companionFiles = listOf("laya-tokenizer.json"),
    )

    val all = listOf(whisper, bonsai, laya)
}
