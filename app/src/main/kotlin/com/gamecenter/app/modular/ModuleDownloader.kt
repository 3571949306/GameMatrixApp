package com.gamecenter.app.modular

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

data class DownloadProgress(
    val moduleId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val percent: Int
)

data class DownloadResult(
    val moduleId: String,
    val success: Boolean,
    val filePath: String? = null,
    val error: String? = null
)

class ModuleDownloader(
    private val okHttpClient: OkHttpClient,
    private val cacheDir: File
) {
    companion object {
        private const val TAG = "ModuleDownloader"
        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_INTERVAL_MS = 200L
        private const val TEMP_SUFFIX = ".tmp"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private val activeDownloads = mutableMapOf<String, Boolean>()

    suspend fun download(
        moduleInfo: ModuleInfo,
        existingDownloadedSize: Long = 0L,
        onProgress: suspend (DownloadProgress) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        val moduleId = moduleInfo.moduleId
        activeDownloads[moduleId] = true

        try {
            downloadWithRetry(moduleInfo, existingDownloadedSize, onProgress)
        } catch (e: CancellationException) {
            Log.d(TAG, "Download cancelled: $moduleId")
            DownloadResult(moduleId, false, error = "已取消")
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: $moduleId", e)
            DownloadResult(moduleId, false, error = e.message ?: "下载失败")
        } finally {
            activeDownloads.remove(moduleId)
        }
    }

    private suspend fun downloadWithRetry(
        moduleInfo: ModuleInfo,
        existingDownloadedSize: Long,
        onProgress: suspend (DownloadProgress) -> Unit
    ): DownloadResult {
        var lastError: Exception? = null

        for (attempt in 0..MAX_RETRIES) {
            if (!isActive(moduleInfo.moduleId)) {
                return DownloadResult(moduleInfo.moduleId, false, error = "已取消")
            }

            try {
                return performDownload(moduleInfo, existingDownloadedSize, onProgress)
            } catch (e: IOException) {
                lastError = e
                Log.w(TAG, "Download attempt ${attempt + 1} failed for ${moduleInfo.moduleId}: ${e.message}")
                if (attempt < MAX_RETRIES) {
                    kotlinx.coroutines.delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }

        return DownloadResult(
            moduleInfo.moduleId,
            false,
            error = lastError?.message ?: "重试耗尽"
        )
    }

    private suspend fun performDownload(
        moduleInfo: ModuleInfo,
        existingDownloadedSize: Long,
        onProgress: suspend (DownloadProgress) -> Unit
    ): DownloadResult {
        val moduleDir = File(cacheDir, moduleInfo.moduleId)
        if (!moduleDir.exists()) moduleDir.mkdirs()

        val targetFile = File(moduleDir, "${moduleInfo.moduleId}-${moduleInfo.versionCode}.apk")
        val tempFile = File(moduleDir, "${targetFile.name}$TEMP_SUFFIX")

        val currentSize = if (existingDownloadedSize > 0 && tempFile.exists()) {
            tempFile.length()
        } else {
            0L
        }

        val requestBuilder = Request.Builder()
            .url(moduleInfo.downloadUrl)
            .header("User-Agent", "GameMatrixApp/ModuleLoader")

        if (currentSize > 0) {
            requestBuilder.header("Range", "bytes=$currentSize-")
            Log.d(TAG, "Resuming download from byte $currentSize for ${moduleInfo.moduleId}")
        }

        val request = requestBuilder.build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful && response.code != 206) {
            response.close()
            throw IOException("服务器返回错误: ${response.code}")
        }

        val isPartial = response.code == 206
        val contentLength = response.body?.contentLength() ?: 0L
        val totalSize = if (isPartial) currentSize + contentLength else contentLength

        if (!isPartial && tempFile.exists()) {
            tempFile.delete()
        }

        val body = response.body ?: throw IOException("响应体为空")
        val inputStream = body.byteStream()

        val outputStream = if (isPartial) {
            FileOutputStream(tempFile, true)
        } else {
            FileOutputStream(tempFile)
        }

        var downloadedSize = if (isPartial) currentSize else 0L
        val buffer = ByteArray(BUFFER_SIZE)
        var lastProgressTime = 0L
        var bytesRead: Int

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (!isActive(moduleInfo.moduleId)) {
                    inputStream.close()
                    outputStream.close()
                    response.close()
                    return DownloadResult(moduleInfo.moduleId, false, error = "已取消")
                }

                outputStream.write(buffer, 0, bytesRead)
                downloadedSize += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastProgressTime >= PROGRESS_INTERVAL_MS) {
                    val percent = if (totalSize > 0) (downloadedSize * 100 / totalSize).toInt() else 0
                    onProgress(
                        DownloadProgress(
                            moduleId = moduleInfo.moduleId,
                            downloadedBytes = downloadedSize,
                            totalBytes = totalSize,
                            percent = percent
                        )
                    )
                    lastProgressTime = now
                }
            }
        } catch (e: Exception) {
            inputStream.close()
            outputStream.close()
            response.close()
            throw e
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()
        response.close()

        onProgress(
            DownloadProgress(
                moduleId = moduleInfo.moduleId,
                downloadedBytes = downloadedSize,
                totalBytes = totalSize,
                percent = 100
            )
        )

        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        Log.d(TAG, "Download complete: ${moduleInfo.moduleId} -> ${targetFile.absolutePath}")
        return DownloadResult(
            moduleId = moduleInfo.moduleId,
            success = true,
            filePath = targetFile.absolutePath
        )
    }

    fun cancel(moduleId: String) {
        activeDownloads[moduleId] = false
    }

    fun isActive(moduleId: String): Boolean {
        return activeDownloads[moduleId] == true
    }

    fun getTempFile(moduleId: String, versionCode: Int): File {
        return File(File(cacheDir, moduleId), "${moduleId}-${versionCode}.apk$TEMP_SUFFIX")
    }

    fun getExistingDownloadedSize(moduleId: String, versionCode: Int): Long {
        val tempFile = getTempFile(moduleId, versionCode)
        return if (tempFile.exists()) tempFile.length() else 0L
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
