package com.gamecenter.app.modules

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gamecenter.app.core.security.SecureOkHttpFactory
import java.io.File
import java.io.FileOutputStream
import okhttp3.Request

object ModuleDownloader {

    private const val TAG = "ModuleDownloader"
    private const val BUFFER_SIZE = 8192

    private val activeDownloads = mutableMapOf<String, Boolean>()
    private val activeCallbacks = mutableMapOf<String, Callback>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun cancel(moduleId: String) {
        Log.d(TAG, "cancel() called for $moduleId")
        activeDownloads[moduleId] = false
        val cb = activeCallbacks.remove(moduleId)
        if (cb != null) {
            mainHandler.post { cb.onError(moduleId, "下载已取消") }
        }
    }

    fun cancelAll() {
        Log.d(TAG, "cancelAll() called")
        for ((moduleId, cb) in activeCallbacks) {
            mainHandler.post { cb.onError(moduleId, "下载已取消") }
        }
        activeDownloads.clear()
        activeCallbacks.clear()
    }

    interface Callback {
        fun onProgress(moduleId: String, downloaded: Long, total: Long, speedKbps: Long)
        fun onComplete(moduleId: String, file: File)
        fun onError(moduleId: String, message: String)
        fun onSourceSwitch(moduleId: String, sourceIndex: Int, url: String)
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
            callback?.onError(moduleId, "该模块正在下载中")
            return
        }

        activeDownloads[moduleId] = true
        if (callback != null) {
            activeCallbacks[moduleId] = callback
        }
        val appContext = context.applicationContext

        Thread({
            try {
                doDownload(appContext, manifest, moduleId)
            } catch (e: Exception) {
                Log.e(TAG, "模块 $moduleId 下载线程异常: ${e.message}", e)
                notifyError(moduleId, "下载异常: ${e.message}")
                cleanup(moduleId)
            } catch (e: Error) {
                Log.e(TAG, "模块 $moduleId 下载线程严重错误: ${e.message}", e)
                notifyError(moduleId, "下载错误: ${e.message}")
                cleanup(moduleId)
            }
        }, "ModuleDL-$moduleId").start()
    }

    private fun doDownload(appContext: Context, manifest: ModuleManifest, moduleId: String) {
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

        val urls = manifest.getAllDownloadUrls()

        Log.d(TAG, "模块 $moduleId 开始下载, ${urls.size} 个源, 目标: ${targetFile.absolutePath}")

        if (urls.isEmpty() || urls.all { it.isEmpty() }) {
            Log.e(TAG, "模块 $moduleId 没有有效的下载URL")
            notifyError(moduleId, "没有有效的下载地址")
            cleanup(moduleId)
            return
        }

        for ((index, url) in urls.withIndex()) {
            if (url.isEmpty()) {
                Log.w(TAG, "模块 $moduleId 源 ${index + 1} URL为空，跳过")
                continue
            }

            if (activeDownloads[moduleId] != true) {
                Log.d(TAG, "模块 $moduleId 下载已取消(进入循环检查)")
                notifyError(moduleId, "下载已取消")
                cleanup(moduleId)
                return
            }

            Log.d(TAG, "模块 $moduleId 尝试源 ${index + 1}/${urls.size}: $url")
            notifySourceSwitch(moduleId, index, url)

            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                    Log.d(TAG, "切换源前删除临时文件: ${tempFile.name}")
                }

                val file = downloadFromUrl(url, targetFile, tempFile, moduleId)
                if (file != null) {
                    Log.d(TAG, "模块 $moduleId 下载完成，开始SHA-256校验")
                    // 安全加固：sha256 为空时直接拒绝，不允许绕过校验
                    if (manifest.sha256.isEmpty()) {
                        Log.e(TAG, "模块 $moduleId 安全校验配置错误：manifest 中 sha256 为空，拒绝安装")
                        file.delete()
                        notifyError(moduleId, "模块安全配置错误：sha256 不能为空")
                        cleanup(moduleId)
                        return
                    }
                    val actualSha256 = ModuleVerifier.computeSha256(file)
                    if (!actualSha256.equals(manifest.sha256, ignoreCase = true)) {
                        Log.w(TAG, "模块 $moduleId SHA-256 校验失败: expected=${manifest.sha256}, actual=$actualSha256")
                        file.delete()
                        notifyError(moduleId, "SHA-256 校验失败，尝试下一个源")
                        continue
                    }
                    Log.d(TAG, "模块 $moduleId 下载完成: ${file.absolutePath}")
                    notifyComplete(moduleId, file)
                    cleanup(moduleId)
                    return
                } else {
                    Log.w(TAG, "模块 $moduleId 源 ${index + 1} 返回null(可能被取消)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "模块 $moduleId 源 ${index + 1} 失败: ${e.message}", e)
                if (index >= urls.size - 1) {
                    notifyError(moduleId, "所有下载源均失败: ${e.message}")
                }
            }
        }

        if (activeCallbacks.containsKey(moduleId)) {
            notifyError(moduleId, "所有下载源均失败")
        }
        cleanup(moduleId)
    }

    private fun cleanup(moduleId: String) {
        activeDownloads.remove(moduleId)
        activeCallbacks.remove(moduleId)
        Log.d(TAG, "cleanup() for $moduleId, remaining active: ${activeDownloads.keys}")
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

    private fun notifyError(moduleId: String, message: String) {
        val cb = activeCallbacks[moduleId]
        if (cb == null) {
            Log.w(TAG, "notifyError: callback for $moduleId is null, message=$message")
            return
        }
        mainHandler.post { cb.onError(moduleId, message) }
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

        if (responseCode != 200 && responseCode != 206) {
            response.close()
            throw Exception("HTTP $responseCode")
        }

        val appendMode = responseCode == 206 && existingBytes > 0
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
                val speedKbps = if (elapsed > 0) (bytesDiff * 1000 / elapsed) / 1024 else 0
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
        val dir = File(context.filesDir, "modules")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, manifest.fileName)
    }

    fun getModuleDir(context: Context): File {
        val dir = File(context.filesDir, "modules")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

}
