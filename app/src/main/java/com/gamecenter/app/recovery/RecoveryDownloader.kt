package com.gamecenter.app.recovery

import android.content.Context
import android.os.Environment
import android.util.Log
import com.gamecenter.app.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object RecoveryDownloader {

    private const val TAG = "RecoveryDownloader"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 300_000
    private const val BUFFER_SIZE = 8192

    private val DOWNLOAD_SOURCES: List<String> get() {
        val sources = mutableListOf(
            BuildConfig.SERVER_URL + "/app-stable.apk",
            BuildConfig.GITHUB_RELEASES_URL + "/latest/download/app-release.apk"
        )
        val fallback = BuildConfig.SERVER_URL_FALLBACK
        if (fallback.isNotEmpty()) {
            sources.add(1, "$fallback/app-stable.apk")
        }
        return sources
    }

    interface Callback {
        fun onProgress(downloaded: Long, total: Long, speedKbps: Long)
        fun onComplete(file: File)
        fun onError(message: String)
        fun onSourceSwitch(sourceIndex: Int, url: String)
    }

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun downloadStableApk(context: Context, expectedSha256: String?, callback: Callback?) {
        cancelled = false
        Thread {
            val targetFile = getRecoveryApkFile(context)
            val tempFile = File(targetFile.parent, targetFile.name + ".tmp")

            for ((index, url) in DOWNLOAD_SOURCES.withIndex()) {
                if (cancelled) {
                    callback?.onError("下载已取消")
                    return@Thread
                }

                Log.d(TAG, "尝试下载源 ${index + 1}/${DOWNLOAD_SOURCES.size}: $url")
                callback?.onSourceSwitch(index, url)

                try {
                    val file = downloadFromUrl(url, targetFile, tempFile, callback)
                    if (file != null) {
                        if (expectedSha256 != null) {
                            callback?.onProgress(0, 0, 0)
                            val actualSha256 = RecoveryVerifier.computeSha256(file)
                            if (actualSha256.equals(expectedSha256, ignoreCase = true)) {
                                Log.d(TAG, "SHA-256 校验通过")
                                callback?.onComplete(file)
                                return@Thread
                            } else {
                                Log.w(TAG, "SHA-256 校验失败: expected=$expectedSha256 actual=$actualSha256")
                                file.delete()
                                callback?.onError("SHA-256 校验失败，尝试下一个源")
                                continue
                            }
                        } else {
                            callback?.onComplete(file)
                            return@Thread
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "源 ${index + 1} 下载失败: ${e.message}")
                    if (index < DOWNLOAD_SOURCES.size - 1) {
                        callback?.onError("源 ${index + 1} 失败: ${e.message}，切换下一个源...")
                    } else {
                        callback?.onError("所有下载源均失败: ${e.message}")
                    }
                }
            }
        }.start()
    }

    private fun downloadFromUrl(
        urlStr: String,
        targetFile: File,
        tempFile: File,
        callback: Callback?
    ): File? {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("User-Agent", "GameMatrixApp-Recovery/1.0")
        }

        if (conn is HttpsURLConnection) {
            try {
                val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf(TrustAllX509TrustManager), java.security.SecureRandom())
                conn.sslSocketFactory = sslContext.socketFactory
                conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                Log.w(TAG, "SSL setup failed: ${e.message}")
            }
        }

        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
        if (existingBytes > 0) {
            conn.setRequestProperty("Range", "bytes=$existingBytes-")
            Log.d(TAG, "断点续传: 从 $existingBytes 字节开始")
        }

        conn.connect()
        val responseCode = conn.responseCode

        val appendMode = responseCode == 206 && existingBytes > 0
        val totalFromServer = if (appendMode) {
            existingBytes + (conn.contentLengthLong)
        } else {
            conn.contentLengthLong
        }

        if (!appendMode && tempFile.exists()) {
            tempFile.delete()
        }

        val input = BufferedInputStream(conn.inputStream)
        val output = FileOutputStream(tempFile, appendMode)
        val buffer = ByteArray(BUFFER_SIZE)

        var downloaded = if (appendMode) existingBytes else 0L
        var lastReportTime = System.currentTimeMillis()
        var lastReportBytes = downloaded

        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            if (cancelled) {
                input.close()
                output.close()
                conn.disconnect()
                return null
            }
            output.write(buffer, 0, read)
            downloaded += read

            val now = System.currentTimeMillis()
            if (now - lastReportTime >= 200) {
                val elapsed = now - lastReportTime
                val bytesDiff = downloaded - lastReportBytes
                val speedKbps = if (elapsed > 0) (bytesDiff * 1000 / elapsed) / 1024 else 0
                callback?.onProgress(downloaded, totalFromServer, speedKbps)
                lastReportTime = now
                lastReportBytes = downloaded
            }
        }

        output.flush()
        output.close()
        input.close()
        conn.disconnect()

        if (tempFile.exists()) {
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)
        }

        callback?.onProgress(downloaded, downloaded, 0)
        return targetFile
    }

    fun getRecoveryApkFile(context: Context): File {
        val dir = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "recovery")
        } else {
            File(context.filesDir, "recovery")
        }
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "GameCenter_stable_recovery.apk")
    }

    private object TrustAllX509TrustManager : javax.net.ssl.X509TrustManager {
        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    }
}
