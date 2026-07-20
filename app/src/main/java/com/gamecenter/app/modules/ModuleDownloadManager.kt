package com.gamecenter.app.modules

import android.content.Context
import android.util.Log
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.core.security.SecureOkHttpFactory
import okhttp3.Request
import java.io.File

/**
 * 模块下载管理器（T03 新增）。
 *
 * 负责模块 APK 的下载、暂停、恢复、取消。
 * 支持断点续传、SHA-256 校验、多源下载。
 *
 * @author 高见远 (Gao)
 * @version 1.0
 * @since 2026-05-25
 */
object ModuleDownloadManager {

    private const val TAG = "ModuleDownloadManager"

    private val DOWNLOAD_URL: String get() = BuildConfig.DOWNLOAD_BASE_URL

    private val downloadQueue = mutableMapOf<String, ModuleDownloader.Callback>()

    /**
     * 下载模块 APK。
     *
     * @param context  Context 对象
     * @param manifest 模块清单对象
     * @param callback 下载回调接口
     */
    fun downloadModule(
        context: Context,
        manifest: ModuleManifest,
        callback: ModuleDownloader.Callback?
    ) {
        Log.d(TAG, "downloadModule() called for ${manifest.id}, callback=$callback")

        if (manifest.downloadUrl.isEmpty() && manifest.fileName.isEmpty()) {
            Log.e(TAG, "downloadModule: manifest 缺少下载地址: ${manifest.id}")
            callback?.onError(manifest.id, "模块缺少下载地址")
            return
        }

        val url = if (manifest.downloadUrl.isNotEmpty()) {
            manifest.downloadUrl
        } else {
            DOWNLOAD_URL + manifest.fileName
        }

        // P3: 使用兼容方法获取模块文件路径（优先 current/，兼容旧 modules/）
        val outputFile = ModuleDownloader.getModuleFileCompat(context, manifest)

        // 检查是否已下载且校验通过
        if (outputFile.exists() && ModuleVerifier.verifySha256(outputFile, manifest.sha256, allowEmpty = manifest.builtIn)) {
            Log.d(TAG, "downloadModule: ${manifest.id} 已下载且校验通过")
            callback?.onComplete(manifest.id, outputFile)
            return
        }

        // 删除损坏的文件
        if (outputFile.exists()) {
            Log.w(TAG, "downloadModule: ${manifest.id} 文件损坏，删除后重新下载")
            outputFile.delete()
        }

        // 保存回调
        if (callback != null) {
            downloadQueue[manifest.id] = callback
        }

        // 开始下载（使用 ModuleDownloader.downloadModule()）
        Log.d(TAG, "downloadModule: start ${manifest.id}, url=$url")
        ModuleDownloader.downloadModule(context, manifest, object : ModuleDownloader.Callback {
            override fun onProgress(moduleId: String, downloaded: Long, total: Long, speedKbps: Long) {
                Log.d(TAG, "onProgress: $moduleId downloaded=$downloaded total=$total speed=$speedKbps")
                downloadQueue[moduleId]?.onProgress(moduleId, downloaded, total, speedKbps)
            }

            override fun onComplete(moduleId: String, file: File) {
                Log.d(TAG, "onComplete: $moduleId file=${file.absolutePath}")

                // 校验 SHA-256
                if (!ModuleVerifier.verifySha256(file, manifest.sha256, allowEmpty = manifest.builtIn)) {
                    Log.e(TAG, "onComplete: ${manifest.id} SHA-256 校验失败")
                    file.delete()
                    downloadQueue[moduleId]?.onError(moduleId, "SHA-256 校验失败")
                    downloadQueue.remove(moduleId)
                    return
                }

                Log.d(TAG, "onComplete: ${manifest.id} 下载完成且校验通过")
                downloadQueue[moduleId]?.onComplete(moduleId, file)
                downloadQueue.remove(moduleId)
            }

            override fun onError(moduleId: String, message: String) {
                Log.e(TAG, "onError: $moduleId message=$message")
                downloadQueue[moduleId]?.onError(moduleId, message)
                downloadQueue.remove(moduleId)
            }

            override fun onSourceSwitch(moduleId: String, sourceIndex: Int, url: String) {
                Log.d(TAG, "onSourceSwitch: $moduleId sourceIndex=$sourceIndex url=$url")
                downloadQueue[moduleId]?.onSourceSwitch(moduleId, sourceIndex, url)
            }
        })
    }

    /**
     * 取消下载。
     *
     * @param moduleId 模块 ID
     */
    fun cancelDownload(moduleId: String) {
        Log.d(TAG, "cancelDownload() called for $moduleId")
        ModuleDownloader.cancel(moduleId)
        downloadQueue.remove(moduleId)
    }

    /**
     * 取消所有下载。
     */
    fun cancelAllDownloads() {
        Log.d(TAG, "cancelAllDownloads() called")
        ModuleDownloader.cancelAll()
        downloadQueue.clear()
    }

    /**
     * 获取下载进度。
     *
     * @param moduleId 模块 ID
     * @return 下载进度（0-100），如果未下载则返回 -1
     */
    fun getDownloadProgress(moduleId: String): Int {
        // 当前架构: ModuleDownloader 内部持有进度但未暴露回调。
        // 后续若需要 UI 实时进度，可让 ModuleDownloader 持有 SharedFlow<DownloadProgress>，
        // 在此处 collectLatest 转发到 LiveData。短期返回 -1（未知）。
        return -1
    }

    /**
     * 检查模块是否已下载。
     *
     * @param context  Context 对象
     * @param manifest 模块清单对象
     * @return 是否已下载且校验通过
     */
    fun isModuleDownloaded(context: Context, manifest: ModuleManifest): Boolean {
        // P3: 使用兼容方法检查模块文件
        val file = ModuleDownloader.getModuleFileCompat(context, manifest)
        return file.exists() && ModuleVerifier.verifySha256(file, manifest.sha256, allowEmpty = manifest.builtIn)
    }

    /**
     * 删除已下载的模块文件。
     *
     * @param context  Context 对象
     * @param manifest 模块清单对象
     * @return 是否删除成功
     */
    fun deleteDownloadedModule(context: Context, manifest: ModuleManifest): Boolean {
        // P3: 使用兼容方法获取模块文件路径
        val file = ModuleDownloader.getModuleFileCompat(context, manifest)
        return if (file.exists()) {
            val result = file.delete()
            Log.d(TAG, "deleteDownloadedModule: ${manifest.id} result=$result")
            result
        } else {
            Log.w(TAG, "deleteDownloadedModule: ${manifest.id} 文件不存在")
            false
        }
    }
}
