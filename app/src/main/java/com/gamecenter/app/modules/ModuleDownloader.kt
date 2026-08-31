package com.gamecenter.app.modules

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.core.security.SecureOkHttpFactory
import com.gamecenter.app.core.security.ModuleSignatureVerifier
import com.gamecenter.app.modules.catalog.CatalogPackageTrustRegistry
import com.gamecenter.app.modules.store.DownloadSourceSelector
import java.io.File
import java.io.FileOutputStream
import okhttp3.Request

object ModuleDownloader {

    private const val TAG = "ModuleDownloader"
    private const val BUFFER_SIZE = 8192
    private const val BYTES_PER_KB = 1024
    private const val HTTP_OK = 200
    private const val HTTP_PARTIAL_CONTENT = 206

    /** Batch 21: 每个 URL 的最大重试次数（不含首次尝试） */
    private const val MAX_RETRIES_PER_URL = 2
    /** Batch 21: 重试线性退避基准（毫秒） */
    private const val RETRY_BASE_DELAY_MS = 1000L

    private val activeDownloads = mutableMapOf<String, Boolean>()
    private val activeCallbacks = mutableMapOf<String, Callback>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun cancel(moduleId: String) {
        Log.d(TAG, "cancel() called for $moduleId")
        activeDownloads[moduleId] = false
        val cb = activeCallbacks.remove(moduleId)
        if (cb != null) {
            mainHandler.post { cb.onError(moduleId, ErrorCodes.ERROR_CANCELED, "下载已取消") }
        }
    }

    fun cancelAll() {
        Log.d(TAG, "cancelAll() called")
        for ((moduleId, cb) in activeCallbacks) {
            mainHandler.post { cb.onError(moduleId, ErrorCodes.ERROR_CANCELED, "下载已取消") }
        }
        activeDownloads.clear()
        activeCallbacks.clear()
    }

    interface Callback {
        fun onProgress(moduleId: String, downloaded: Long, total: Long, speedKbps: Long)
        fun onComplete(moduleId: String, file: File)
        fun onError(moduleId: String, message: String)
        fun onStateChanged(moduleId: String, state: String) = Unit
        fun onError(moduleId: String, errorCode: Int, message: String) {
            // Default implementation delegates to old signature for backwards compatibility
            onError(moduleId, message)
        }
        fun onSourceSwitch(moduleId: String, sourceIndex: Int, url: String)
    }

    object ErrorCodes {
        const val ERROR_CANCELED = 1001
        const val ERROR_NO_URL = 1002
        const val ERROR_CHECKSUM_FAILED = 1003
        const val ERROR_NETWORK = 1004
        const val ERROR_CONFIG = 1005
        const val ERROR_UNKNOWN = 1099
    }

    fun downloadModule(
        context: Context,
        manifest: ModuleManifest,
        callback: Callback?
    ) {
        val moduleId = manifest.id
        Log.d(TAG, "downloadModule() called for $moduleId, callback=$callback")

        if (activeDownloads[moduleId] == true) {
            Log.w(TAG, "模块 $moduleId 已在下载中，跳过")
            callback?.onError(moduleId, ErrorCodes.ERROR_UNKNOWN, "该模块正在下载中")
            return
        }

        activeDownloads[moduleId] = true
        if (callback != null) {
            activeCallbacks[moduleId] = callback
            callback.onStateChanged(moduleId, "queued")
        }
        val appContext = context.applicationContext

        Thread({
            try {
                doDownload(appContext, manifest, moduleId)
            } catch (e: Exception) {
                Log.e(TAG, "模块 $moduleId 下载线程异常: ${e.message}", e)
                notifyError(moduleId, ErrorCodes.ERROR_UNKNOWN, "下载异常: ${e.message}")
                cleanup(moduleId)
            } catch (e: Error) {
                Log.e(TAG, "模块 $moduleId 下载线程严重错误: ${e.message}", e)
                notifyError(moduleId, ErrorCodes.ERROR_UNKNOWN, "下载错误: ${e.message}")
                cleanup(moduleId)
            }
        }, "ModuleDL-$moduleId").start()
    }

    /**
     * 下载源列表构造接缝（自 doDownload 提取，§六 契约测试对象）。
     * - 镜像插队：mirrorBases 语义为"胜者首"（见 DownloadSourceSelector.preferredMirrorBases KDoc）；
     *   反序遍历收集后 addAll(0, reversed) 使最终镜像顺序 = mirrorBases 原顺序置于列表头部；
     *   仅当主 URL 命中 downloadBase 时改写；对既有源与已插源双重去重。
     *   （BL-007 教训：曾用 inserted.reversed().forEach { add(0) }——add(0) 逐次插入自带反转，
     *   与前置双重反转叠加后净效果变成镜像顺序反转，胜者沉底。）
     * - CDN fallback：fallbackBase 非空且主 URL 命中 downloadBase 时，替换构造备用源追加末尾（去重）。
     */
    internal fun buildDownloadUrlList(
        primaryUrl: String,
        baseUrls: List<String>,
        mirrorBases: List<String>,
        downloadBase: String,
        fallbackBase: String,
    ): MutableList<String> {
        val urls = baseUrls.toMutableList()
        val inserted = mutableListOf<String>()
        for (mirrorBase in mirrorBases.asReversed()) {
            if (!primaryUrl.startsWith(downloadBase)) break
            val mirrorUrl = primaryUrl.replace(downloadBase, "$mirrorBase/modules/")
            if (mirrorUrl != primaryUrl && urls.none { it == mirrorUrl } && inserted.none { it == mirrorUrl }) {
                inserted.add(mirrorUrl)
            }
        }
        urls.addAll(0, inserted.reversed())
        if (fallbackBase.isNotEmpty() && primaryUrl.startsWith(downloadBase)) {
            val autoFallbackUrl = primaryUrl.replace(downloadBase, fallbackBase)
            if (autoFallbackUrl != primaryUrl && urls.none { it == autoFallbackUrl }) {
                urls.add(autoFallbackUrl)
            }
        }
        return urls
    }

    private fun doDownload(appContext: Context, manifest: ModuleManifest, moduleId: String) {
        notifyStateChanged(moduleId, "downloading")
        val downloadStartTime = System.currentTimeMillis()
        val targetFile = getModuleFile(appContext, manifest)
        val tempFile = File(targetFile.parent, targetFile.name + ".tmp")

        if (targetFile.exists()) {
            targetFile.delete()
            Log.d(TAG, "删除旧模块文件: ${targetFile.name}")
        }
        if (tempFile.exists()) {
            tempFile.delete()
            Log.d(TAG, "删除残留临时文件: ${tempFile.name}")
        }

        val baseUrls = manifest.getAllDownloadUrls()
        val urls = buildDownloadUrlList(
            primaryUrl = manifest.downloadUrl,
            baseUrls = baseUrls,
            mirrorBases = DownloadSourceSelector.preferredMirrorBases(appContext),
            downloadBase = BuildConfig.DOWNLOAD_BASE_URL,
            fallbackBase = BuildConfig.DOWNLOAD_FALLBACK_BASE_URL,
        )
        if (urls.size > baseUrls.size) {
            Log.d(TAG, "模块 $moduleId 源扩展至 ${urls.size} 个: $urls")
        }

        Log.d(TAG, "模块 $moduleId 开始下载, ${urls.size} 个源, 目标: ${targetFile.absolutePath}")

        if (urls.isEmpty() || urls.all { it.isEmpty() }) {
            Log.e(TAG, "模块 $moduleId 没有有效的下载URL")
            notifyError(moduleId, ErrorCodes.ERROR_NO_URL, "没有有效的下载地址")
            cleanup(moduleId)
            return
        }

        for ((index, url) in urls.withIndex()) {
            if (url.isEmpty()) {
                Log.w(TAG, "模块 $moduleId 源 ${index + 1} URL为空，跳过")
                continue
            }

            // 安全加固：强制要求使用 HTTPS 协议，防止中间人拦截和篡改下载链路
            if (!url.startsWith("https://", ignoreCase = true)) {
                Log.e(TAG, "安全警告：模块 $moduleId 尝试使用不安全的 HTTP 连接下载，已强制拒绝: $url")
                continue
            }

            if (activeDownloads[moduleId] != true) {
                Log.d(TAG, "模块 $moduleId 下载已取消(进入循环检查)")
                notifyError(moduleId, ErrorCodes.ERROR_CANCELED, "下载已取消")
                cleanup(moduleId)
                return
            }

            Log.d(TAG, "模块 $moduleId 尝试源 ${index + 1}/${urls.size}: $url")
            notifySourceSwitch(moduleId, index, url)

            // Batch 21: 在同一 URL 内进行重试 + 线性退避
            var lastError: Exception? = null
            var successInThisUrl = false
            for (attempt in 0..MAX_RETRIES_PER_URL) {
                if (activeDownloads[moduleId] != true) {
                    Log.d(TAG, "模块 $moduleId 下载已取消(重试检查)")
                    notifyError(moduleId, ErrorCodes.ERROR_CANCELED, "下载已取消")
                    cleanup(moduleId)
                    return
                }

                if (attempt > 0) {
                    val delay = RETRY_BASE_DELAY_MS * attempt
                    Log.d(TAG, "模块 $moduleId 源 ${index + 1} 第 $attempt 次重试，等待 ${delay}ms")
                    try {
                        Thread.sleep(delay)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        notifyError(moduleId, ErrorCodes.ERROR_CANCELED, "下载已取消")
                        cleanup(moduleId)
                        return
                    }
                }

                try {
                    if (tempFile.exists()) {
                        tempFile.delete()
                        Log.d(TAG, "尝试下载前删除临时文件: ${tempFile.name}")
                    }

                    val file = downloadFromUrl(url, targetFile, tempFile, moduleId)
                    if (file != null) {
                        notifyStateChanged(moduleId, "verifying")
                        Log.d(TAG, "模块 $moduleId 下载完成，开始SHA-256校验")
                        // 安全加固：sha256 为空时直接拒绝，不允许绕过校验
                        if (manifest.sha256.isEmpty()) {
                            Log.e(TAG, "模块 $moduleId 安全校验配置错误：manifest 中 sha256 为空，拒绝安装")
                            file.delete()
                            notifyError(moduleId, ErrorCodes.ERROR_CONFIG, "模块安全配置错误：sha256 不能为空")
                            cleanup(moduleId)
                            return
                        }
                        val actualSha256 = ModuleVerifier.computeSha256(file)
                        if (!actualSha256.equals(manifest.sha256, ignoreCase = true)) {
                            Log.w(TAG, "模块 $moduleId SHA-256 校验失败: expected=${manifest.sha256}, actual=$actualSha256")
                            file.delete()
                            notifyError(moduleId, ErrorCodes.ERROR_CHECKSUM_FAILED, "SHA-256 校验失败，尝试下一个源")
                            // SHA 不匹配说明文件有问题，重试无意义，直接切换 URL
                            break
                        }
                        val packageTrustFailure = when {
                            file.name.endsWith(".apk", ignoreCase = true) -> null
                            !file.name.endsWith(".zip", ignoreCase = true) -> "不支持的模块包格式"
                            !CatalogPackageTrustRegistry.isTrusted(manifest) -> "归档包未绑定到已验签 Catalog V2"
                            else -> null
                        }
                        if (packageTrustFailure != null) {
                            Log.e(TAG, "模块 $moduleId 安全校验失败: $packageTrustFailure")
                            file.delete()
                            notifyError(moduleId, ErrorCodes.ERROR_CONFIG, packageTrustFailure)
                            cleanup(moduleId)
                            return
                        }
                        if (!file.name.endsWith(".apk", ignoreCase = true)) {
                            Log.d(TAG, "模块 $moduleId 归档包已通过 Catalog V2 绑定和 SHA-256 校验")
                        } else when (val signature = ModuleSignatureVerifier.verify(file, appContext)) {
                            ModuleSignatureVerifier.Result.Success -> Unit
                            is ModuleSignatureVerifier.Result.Failure -> {
                                Log.e(TAG, "模块 $moduleId 签名校验失败: ${signature.reason}")
                                file.delete()
                                // Batch 21 改进：签名校验失败也记录指标（之前遗漏）
                                DownloadMetricsCollector.record(DownloadMetric(
                                    moduleId = moduleId,
                                    success = false,
                                    durationMs = System.currentTimeMillis() - downloadStartTime,
                                    errorCode = ErrorCodes.ERROR_CONFIG,
                                    urlIndex = index,
                                    attemptCount = attempt + 1,
                                    timestamp = System.currentTimeMillis()
                                ))
                                notifyError(moduleId, ErrorCodes.ERROR_CONFIG, "模块签名验证失败")
                                cleanup(moduleId)
                                return
                            }
                            is ModuleSignatureVerifier.Result.Warning -> {
                                Log.e(TAG, "模块 $moduleId 签名校验未通过: ${signature.reason}")
                                file.delete()
                                // Batch 21 改进：签名校验警告也记录指标
                                DownloadMetricsCollector.record(DownloadMetric(
                                    moduleId = moduleId,
                                    success = false,
                                    durationMs = System.currentTimeMillis() - downloadStartTime,
                                    errorCode = ErrorCodes.ERROR_CONFIG,
                                    urlIndex = index,
                                    attemptCount = attempt + 1,
                                    timestamp = System.currentTimeMillis()
                                ))
                                notifyError(moduleId, ErrorCodes.ERROR_CONFIG, "模块签名验证失败")
                                cleanup(moduleId)
                                return
                            }
                        }
                        // 下载成功：记录指标 + 通知完成
                        DownloadMetricsCollector.record(DownloadMetric(
                            moduleId = moduleId,
                            success = true,
                            durationMs = System.currentTimeMillis() - downloadStartTime,
                            errorCode = 0,
                            urlIndex = index,
                            attemptCount = attempt,
                            timestamp = System.currentTimeMillis()
                        ))
                        Log.d(TAG, "模块 $moduleId 下载完成: ${file.absolutePath}")
                        notifyComplete(moduleId, file)
                        cleanup(moduleId)
                        return
                    } else {
                        Log.w(TAG, "模块 $moduleId 源 ${index + 1} attempt=$attempt 返回null(可能被取消)")
                    }
                    successInThisUrl = true
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "模块 $moduleId 源 ${index + 1} attempt=$attempt 失败: ${e.message}", e)
                    lastError = e
                    // 分发 v2：边缘源(:2088)连接级失败（不通/超时/DNS）立即级联下一源，
                    // 不空耗重试——符合"链接超时则选择其他服务器"的要求
                    val isConnectLevel = e is java.net.ConnectException ||
                        e is java.net.SocketTimeoutException ||
                        e is java.net.UnknownHostException
                    if (url.contains(":2088") && isConnectLevel && attempt == 0) {
                        Log.w(TAG, "模块 $moduleId 边缘源连接失败，快速级联下一源: $url")
                        break
                    }
                    // 仅在网络/IO 异常时重试；其他异常直接切换 URL
                    val isRetryable = e is java.io.IOException ||
                        e is java.net.SocketTimeoutException ||
                        e is java.net.UnknownHostException ||
                        e is javax.net.ssl.SSLException
                    if (!isRetryable) break
                }
            }

            if (!successInThisUrl && lastError != null) {
                Log.w(TAG, "模块 $moduleId 源 ${index + 1} 所有重试均失败: ${lastError.message}")
                if (index >= urls.size - 1) {
                    // 记录失败指标
                    DownloadMetricsCollector.record(DownloadMetric(
                        moduleId = moduleId,
                        success = false,
                        durationMs = System.currentTimeMillis() - downloadStartTime,
                        errorCode = ErrorCodes.ERROR_NETWORK,
                        urlIndex = index,
                        attemptCount = MAX_RETRIES_PER_URL + 1,
                        timestamp = System.currentTimeMillis()
                    ))
                    notifyError(moduleId, ErrorCodes.ERROR_NETWORK, "所有下载源均失败: ${lastError.message}")
                }
            }
        }

        if (activeCallbacks.containsKey(moduleId)) {
            notifyError(moduleId, ErrorCodes.ERROR_NETWORK, "所有下载源均失败")
        }
        cleanup(moduleId)
    }

    private fun cleanup(moduleId: String) {
        activeDownloads.remove(moduleId)
        activeCallbacks.remove(moduleId)
        Log.d(TAG, "cleanup() for $moduleId, remaining active: ${activeDownloads.keys}")
        // Batch 21 改进：下载结束后立即 flush 指标，避免应用被系统杀死时丢失数据
        DownloadMetricsCollector.flush()
    }

    private fun notifyProgress(moduleId: String, downloaded: Long, total: Long, speedKbps: Long) {
        val cb = activeCallbacks[moduleId]
        if (cb == null) {
            Log.w(TAG, "notifyProgress: callback for $moduleId is null")
            return
        }
        mainHandler.post { cb.onProgress(moduleId, downloaded, total, speedKbps) }
    }

    private fun notifyComplete(moduleId: String, file: File) {
        val cb = activeCallbacks[moduleId]
        if (cb == null) {
            Log.w(TAG, "notifyComplete: callback for $moduleId is null")
            return
        }
        mainHandler.post { cb.onComplete(moduleId, file) }
    }

    private fun notifyStateChanged(moduleId: String, state: String) {
        val cb = activeCallbacks[moduleId] ?: return
        mainHandler.post { cb.onStateChanged(moduleId, state) }
    }

    private fun notifyError(moduleId: String, errorCode: Int, message: String) {
        val cb = activeCallbacks[moduleId]
        if (cb == null) {
            Log.w(TAG, "notifyError: callback for $moduleId is null, message=$message")
            return
        }
        mainHandler.post { cb.onError(moduleId, errorCode, message) }
    }

    private fun notifySourceSwitch(moduleId: String, sourceIndex: Int, url: String) {
        val cb = activeCallbacks[moduleId]
        if (cb == null) {
            Log.w(TAG, "notifySourceSwitch: callback for $moduleId is null")
            return
        }
        mainHandler.post { cb.onSourceSwitch(moduleId, sourceIndex, url) }
    }

    private fun downloadFromUrl(
        urlStr: String,
        targetFile: File,
        tempFile: File,
        moduleId: String
    ): File? {
        Log.d(TAG, "downloadFromUrl: $urlStr")
        val client = SecureOkHttpFactory.buildModuleClient()

        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val requestBuilder = Request.Builder().url(urlStr)
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
            Log.d(TAG, "断点续传: 从 $existingBytes 字节开始")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val responseCode = response.code
        Log.d(TAG, "连接响应: $responseCode")

        if (responseCode != HTTP_OK && responseCode != HTTP_PARTIAL_CONTENT) {
            response.close()
            throw Exception("HTTP $responseCode")
        }

        val appendMode = responseCode == HTTP_PARTIAL_CONTENT && existingBytes > 0
        val body = response.body ?: run {
            response.close()
            throw Exception("Empty response body")
        }
        val contentLength = body.contentLength()
        val totalFromServer = if (appendMode) {
            existingBytes + contentLength
        } else {
            contentLength
        }

        Log.d(TAG, "内容长度: $contentLength, 总大小: $totalFromServer, 断点续传: $appendMode")

        if (!appendMode && tempFile.exists()) {
            tempFile.delete()
        }

        val input = body.byteStream()
        val output = FileOutputStream(tempFile, appendMode)
        val buffer = ByteArray(BUFFER_SIZE)

        var downloaded = if (appendMode) existingBytes else 0L
        var lastReportTime = System.currentTimeMillis()
        var lastReportBytes = downloaded

        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            if (activeDownloads[moduleId] != true) {
                Log.d(TAG, "下载被取消(读取循环中), moduleId=$moduleId")
                input.close()
                output.close()
                response.close()
                return null
            }
            output.write(buffer, 0, read)
            downloaded += read

            val now = System.currentTimeMillis()
            if (now - lastReportTime >= 200) {
                val elapsed = now - lastReportTime
                val bytesDiff = downloaded - lastReportBytes
                val speedKbps = if (elapsed > 0) (bytesDiff * 1000 / elapsed) / BYTES_PER_KB else 0
                notifyProgress(moduleId, downloaded, totalFromServer, speedKbps)
                lastReportTime = now
                lastReportBytes = downloaded
            }
        }

        output.flush()
        output.close()
        input.close()
        response.close()

        Log.d(TAG, "数据读取完成, downloaded=$downloaded")

        if (tempFile.exists()) {
            if (targetFile.exists()) targetFile.delete()
            val renamed = tempFile.renameTo(targetFile)
            Log.d(TAG, "重命名临时文件: $renamed (${tempFile.name} -> ${targetFile.name})")
        }

        if (targetFile.exists() && targetFile.extension.equals("apk", ignoreCase = true)) {
            targetFile.setWritable(false, false)
            targetFile.setReadOnly()
            Log.d(TAG, "模块 APK 已设置为只读: ${targetFile.name}")
        }

        notifyProgress(moduleId, downloaded, downloaded, 0)
        return targetFile
    }

    fun getModuleFile(context: Context, manifest: ModuleManifest): File {
        // P3: 下载到 staging 目录，由 TransactionInstaller 负责移动到 current
        return com.gamecenter.app.modules.store.TransactionInstaller.getStagingFile(context, manifest)
    }
    
    /**
     * 获取模块在 current 目录的文件（已安装完成的模块）。
     * P3: 用于加载侧读取已安装的模块。
     */
    fun getInstalledModuleFile(context: Context, manifest: ModuleManifest): File {
        return com.gamecenter.app.modules.store.TransactionInstaller.getCurrentFile(context, manifest)
    }
    
    /**
     * 获取模块文件（兼容旧版本）。
     * 优先返回 current/ 路径，若不存在则返回旧 modules/ 路径（兼容迁移）。
     */
    fun getModuleFileCompat(context: Context, manifest: ModuleManifest): File {
        val currentFile = getInstalledModuleFile(context, manifest)
        if (currentFile.exists()) return currentFile
        
        // 兼容旧版本：检查旧 modules/ 目录
        val legacyDir = File(context.filesDir, "modules")
        var safeFileName = File(manifest.fileName).name
        if (safeFileName.isEmpty() || !safeFileName.endsWith(".apk")) {
            safeFileName = "${manifest.id}.apk"
        }
        val legacyFile = File(legacyDir, safeFileName)
        if (legacyFile.exists()) return legacyFile
        
        // 默认返回 current 路径
        return currentFile
    }

    fun getModuleDir(context: Context): File {
        val dir = File(context.filesDir, "modules")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

}
