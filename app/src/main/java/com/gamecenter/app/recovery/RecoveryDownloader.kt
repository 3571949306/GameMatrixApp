package com.gamecenter.app.recovery

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.gamecenter.app.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL


object RecoveryDownloader {

    private const val TAG = "RecoveryDownloader"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 300_000
    private const val BUFFER_SIZE = 8192

    /**
     * 慢速切换阈值：当下载速率持续低于 5 MB/s 时，自动切换到下一个下载源。
     */
    private const val SLOW_SPEED_KBPS = 5 * 1024 // 5 MB/s = 5120 KB/s

    /**
     * 连续慢速采样达到该次数即触发切换（每个采样窗口约 1 秒）。
     * 3 秒的持续慢速可避免瞬时抖动与启动期误判。
     */
    private const val SLOW_STREAK_THRESHOLD = 3

    /** 进入速度判定前需已下载的最小字节数，避免启动初期速度为 0 的误判 */
    private const val MIN_BYTES_FOR_SPEED_CHECK = 512L * 1024 // 512 KB

    /** 慢速采样窗口（毫秒） */
    private const val SLOW_CHECK_INTERVAL_MS = 1000L

    /** 单源最大尝试次数（含首次），用于瞬时失败重试 */
    private const val MAX_ATTEMPTS_PER_SOURCE = 2

    /** 下载所需额外预留空间（16MB），防止下载到一半因空间耗尽失败 */
    private const val EXTRA_SPACE_BYTES = 16L * 1024 * 1024

    /**
     * 可选：稳定版 APK 的预期 SHA-256。
     * 留空表示不做强校验（仅依赖 HTTPS 传输安全 + ZIP 魔数检查）。
     * 团队应在发布流水线中将真实哈希写入此处，或从 recovery_meta.json 注入。
     *
     * [internal] 以便同模块的 [RecoveryInstaller] 在安装前复用同一完整性开关，
     * 决定是否启用签名/证书强校验（见 R3）。
     */
    internal const val EXPECTED_STABLE_SHA256 = ""

    /**
     * 恢复模式 APK 下载源列表。
     *
     * 2026-06-19: 已移除美国 VPS 备用源，仅保留 HK VPS + GitHub 两级分发。
     * 顺序即优先级：香港 VPS（主源）→ GitHub Releases（备用源）。
     * 下载时若 VPS 速率过低或失败，将自动切换至 GitHub。
     */
    private val DOWNLOAD_SOURCES: List<String> get() {
        return listOf(
            BuildConfig.SERVER_URL + "/app-stable.apk",
            BuildConfig.GITHUB_RELEASES_URL + "/latest/download/app-release.apk"
        )
    }

    interface Callback {
        fun onProgress(downloaded: Long, total: Long, speedKbps: Long)
        fun onComplete(file: File)
        fun onError(message: String)
        fun onSourceSwitch(sourceIndex: Int, url: String)

        /** 因下载速率过低，从 fromIndex 切换到 toIndex 下载源 */
        fun onSlowSpeedSwitch(fromIndex: Int, toIndex: Int) {}
    }

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    fun downloadStableApk(context: Context, expectedSha256: String?, callback: Callback?) {
        cancelled = false
        val expected = expectedSha256?.takeIf { it.isNotEmpty() }
            ?: EXPECTED_STABLE_SHA256.takeIf { it.isNotEmpty() }

        Thread {
            val targetFile = getRecoveryApkFile(context)
            val tempFile = File(targetFile.parent, targetFile.name + ".tmp")
            val sources = DOWNLOAD_SOURCES
            var resultError: String? = null

            for (index in sources.indices) {
                if (cancelled) {
                    callback?.onError("下载已取消")
                    return@Thread
                }

                // 每个源均从头下载，避免跨源断点续传导致文件损坏
                if (tempFile.exists()) tempFile.delete()
                val url = sources[index]
                Log.d(TAG, "尝试下载源 ${index + 1}/${sources.size}: $url")
                callback?.onSourceSwitch(index, url)

                // R9: 单源瞬时失败重试（慢速切换异常不重试，直接切下一源）
                var file: File? = null
                var downloadErr: Exception? = null
                for (attempt in 1..MAX_ATTEMPTS_PER_SOURCE) {
                    try {
                        file = downloadFromUrl(url, targetFile, tempFile, callback)
                        downloadErr = null
                        break
                    } catch (e: SlowSpeedSwitchException) {
                        throw e
                    } catch (e: Exception) {
                        downloadErr = e
                        if (attempt < MAX_ATTEMPTS_PER_SOURCE) {
                            Log.w(TAG, "源 ${index + 1} 第 $attempt 次下载失败，重试: ${e.message}")
                        }
                    }
                }

                if (file == null) {
                    val e = downloadErr ?: Exception("未知下载错误")
                    if (index < sources.size - 1) {
                        callback?.onError("源 ${index + 1} 失败: ${e.message}，正在切换下一个源…")
                        continue
                    } else {
                        resultError = "所有下载源均失败: ${e.message}"
                        break
                    }
                }

                if (cancelled) {
                    callback?.onError("下载已取消")
                    return@Thread
                }
                if (file != null) {
                    if (expected != null) {
                        callback?.onProgress(0, 0, 0)
                        val actualSha256 = RecoveryVerifier.computeSha256(file)
                        if (actualSha256.equals(expected, ignoreCase = true)) {
                            Log.d(TAG, "SHA-256 校验通过")
                            callback?.onComplete(file)
                            return@Thread
                        } else {
                            Log.w(TAG, "SHA-256 校验失败: expected=$expected actual=$actualSha256")
                            file.delete()
                            resultError = "稳定版校验失败，请稍后重试"
                            continue
                        }
                    } else {
                        callback?.onComplete(file)
                        return@Thread
                    }
                }
            }

            if (resultError != null) {
                callback?.onError(resultError)
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

        // 使用系统默认 SSL 证书验证（已移除 TrustAll 绕过以防止 MITM 攻击）
        // 不启用断点续传：跨源续传会导致文件损坏，每次均从头开始
        if (tempFile.exists()) tempFile.delete()

        conn.connect()
        val responseCode = conn.responseCode

        // 仅处理 200；206（断点续传）在此场景下不使用
        if (responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IllegalStateException("HTTP $responseCode")
        }

        val totalFromServer = conn.contentLengthLong

        // R6: 磁盘空间预检（仅在服务器返回 Content-Length 时）
        if (totalFromServer > 0) {
            val dir = targetFile.parentFile ?: File("/data/local/tmp")
            val available = try {
                StatFs(dir.absolutePath).availableBytes
            } catch (e: Exception) {
                Log.w(TAG, "磁盘空间检查失败", e)
                Long.MAX_VALUE // 无法统计时放行，交由后续写入失败处理
            }
            if (available < totalFromServer + EXTRA_SPACE_BYTES) {
                conn.disconnect()
                throw IOException("存储空间不足，无法保存恢复包")
            }
        }

        val input = BufferedInputStream(conn.inputStream)
        val output = FileOutputStream(tempFile, false)
        val buffer = ByteArray(BUFFER_SIZE)

        var downloaded = 0L
        var lastReportTime = System.currentTimeMillis()
        var lastReportBytes = 0L
        var lastSlowCheckTime = lastReportTime
        var lastSlowCheckBytes = 0L
        var slowStreak = 0

        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            if (cancelled) {
                input.close()
                output.close()
                conn.disconnect()
                tempFile.delete()
                return null
            }
            output.write(buffer, 0, read)
            downloaded += read

            val now = System.currentTimeMillis()

            // UI 进度上报（每 200ms 一次）
            if (now - lastReportTime >= 200) {
                val elapsed = now - lastReportTime
                val speedKbps = if (elapsed > 0) (downloaded - lastReportBytes) * 1000 / elapsed / 1024 else 0
                callback?.onProgress(downloaded, totalFromServer, speedKbps)
                lastReportTime = now
                lastReportBytes = downloaded
            }

            // 慢速检测（每约 1 秒采样一次，统计窗口平均速度）
            if (now - lastSlowCheckTime >= SLOW_CHECK_INTERVAL_MS) {
                val elapsed = now - lastSlowCheckTime
                val windowKbps = if (elapsed > 0) (downloaded - lastSlowCheckBytes) * 1000 / elapsed / 1024 else 0
                slowStreak = if (downloaded >= MIN_BYTES_FOR_SPEED_CHECK && windowKbps < SLOW_SPEED_KBPS) {
                    slowStreak + 1
                } else {
                    0
                }
                lastSlowCheckTime = now
                lastSlowCheckBytes = downloaded

                if (slowStreak >= SLOW_STREAK_THRESHOLD) {
                    Log.w(TAG, "下载速率持续过低 (${windowKbps} KB/s < ${SLOW_SPEED_KBPS} KB/s)，切换下载源")
                    input.close()
                    output.close()
                    conn.disconnect()
                    tempFile.delete()
                    throw SlowSpeedSwitchException()
                }
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

    // TrustAllX509TrustManager 已移除 — 使用系统默认 SSL 验证链防止 MITM 攻击

    /** 下载速率持续过慢时抛出，触发下载源切换 */
    private class SlowSpeedSwitchException : Exception()
}
