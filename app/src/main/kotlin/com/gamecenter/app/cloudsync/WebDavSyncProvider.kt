package com.gamecenter.app.cloudsync

import android.content.Context
import android.util.Base64
import android.util.Log
import com.gamecenter.app.core.security.SecureOkHttpFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * P3-13 (CLOUD_SAVE_SYNC): WebDAV 云存档同步实现。
 *
 * 使用标准 WebDAV 协议（PUT/GET/PROPFIND）与 Nextcloud/ownCloud/Nutstore 等兼容。
 *
 * 配置存储在 `cloud_sync_prefs` SharedPreferences：
 * - `webdav_url`：服务器根 URL（如 https://dav.example.com/remote.php/dav/files/user/）
 * - `webdav_user`：用户名
 * - `webdav_pass`：密码（Base64 存储，非加密，仅做简单混淆；敏感场景应使用 EncryptedSharedPreferences）
 * - `webdav_subdir`：子目录（默认 GameMatrixApp/）
 *
 * 冲突检测：通过远程文件的 `Last-Modified` 头与本地时间戳比较。
 */
class WebDavSyncProvider(private val context: Context) : CloudSyncProvider {

    companion object {
        private const val TAG = "WebDavSync"
        private const val PREFS_NAME = "cloud_sync_prefs"
        private const val KEY_URL = "webdav_url"
        private const val KEY_USER = "webdav_user"
        private const val KEY_PASS = "webdav_pass"
        private const val KEY_SUBDIR = "webdav_subdir"
        private const val DEFAULT_SUBDIR = "GameMatrixApp/"

        private val RFC1123_FORMAT = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client by lazy { SecureOkHttpFactory.buildGeneralClient() }

    override val providerId: String = "webdav"
    override val displayName: String = "WebDAV"

    // ============ 配置读写 ============

    fun setConfig(url: String, user: String, pass: String, subdir: String = DEFAULT_SUBDIR) {
        prefs.edit()
            .putString(KEY_URL, url.trimEnd('/'))
            .putString(KEY_USER, user)
            .putString(KEY_PASS, Base64.encodeToString(pass.toByteArray(), Base64.NO_WRAP))
            .putString(KEY_SUBDIR, subdir.trimEnd('/'))
            .apply()
    }

    fun getUrl(): String = prefs.getString(KEY_URL, "") ?: ""
    fun getUser(): String = prefs.getString(KEY_USER, "") ?: ""
    fun getSubdir(): String = prefs.getString(KEY_SUBDIR, DEFAULT_SUBDIR) ?: DEFAULT_SUBDIR

    fun getPass(): String {
        val encoded = prefs.getString(KEY_PASS, "") ?: ""
        return if (encoded.isEmpty()) ""
        else String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
    }

    override fun isConfigured(): Boolean {
        return getUrl().isNotEmpty() && getUser().isNotEmpty() && getPass().isNotEmpty()
    }

    override fun testConnection(): String? {
        if (!isConfigured()) return "WebDAV 未配置"
        return try {
            val url = buildRemoteUrl("")
            // PROPFIND 根目录，深度 0
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", "".toRequestBody())
                .header("Authorization", buildAuthHeader())
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { resp ->
                when {
                    resp.code in 200..299 -> null
                    resp.code == 401 -> "认证失败：用户名或密码错误"
                    resp.code == 404 -> "路径不存在：$url"
                    else -> "服务器返回 ${resp.code}"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "testConnection 失败: ${e.message}")
            "连接失败：${e.message}"
        }
    }

    // ============ 上传/下载 ============

    override fun upload(remotePath: String, data: ByteArray, localTimestamp: Long): CloudSyncProvider.UploadResult {
        if (!isConfigured()) return CloudSyncProvider.UploadResult.Failure("WebDAV 未配置")
        return try {
            val url = buildRemoteUrl(remotePath)
            // 先确保父目录存在（MKCOL，忽略已存在错误）
            ensureParentDir(remotePath)

            val body = data.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .put(body)
                .header("Authorization", buildAuthHeader())
                .build()
            client.newCall(request).execute().use { resp ->
                when {
                    resp.code in 200..299 -> CloudSyncProvider.UploadResult.Success
                    resp.code == 401 -> CloudSyncProvider.UploadResult.Failure("认证失败")
                    resp.code == 409 -> CloudSyncProvider.UploadResult.Failure("父目录不存在")
                    else -> CloudSyncProvider.UploadResult.Failure("上传失败：HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "upload 失败: ${e.message}")
            CloudSyncProvider.UploadResult.Failure("上传异常：${e.message}")
        }
    }

    override fun download(remotePath: String): CloudSyncProvider.DownloadResult {
        if (!isConfigured()) return CloudSyncProvider.DownloadResult.Failure("WebDAV 未配置")
        return try {
            val url = buildRemoteUrl(remotePath)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", buildAuthHeader())
                .build()
            client.newCall(request).execute().use { resp ->
                when {
                    resp.code == 200 -> {
                        val body = resp.body?.bytes() ?: ByteArray(0)
                        val ts = parseLastModified(resp)
                        CloudSyncProvider.DownloadResult.Success(body, ts)
                    }
                    resp.code == 404 -> CloudSyncProvider.DownloadResult.NotFound
                    resp.code == 401 -> CloudSyncProvider.DownloadResult.Failure("认证失败")
                    else -> CloudSyncProvider.DownloadResult.Failure("下载失败：HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "download 失败: ${e.message}")
            CloudSyncProvider.DownloadResult.Failure("下载异常：${e.message}")
        }
    }

    override fun getRemoteMeta(remotePath: String): CloudSyncProvider.RemoteMeta? {
        if (!isConfigured()) return null
        return try {
            val url = buildRemoteUrl(remotePath)
            // PROPFIND 单个资源
            val propfindBody = """<?xml version="1.0" encoding="utf-8"?>
                |<propfind xmlns="DAV:">
                |  <prop><getlastmodified/><getcontentlength/></prop>
                |</propfind>""".trimMargin()
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
                .header("Authorization", buildAuthHeader())
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.code !in 200..299) return null
                val xml = resp.body?.string() ?: return null
                val lastMod = parsePropfindLastModified(xml) ?: parseLastModified(resp)
                val size = parsePropfindSize(xml)
                CloudSyncProvider.RemoteMeta(lastMod, size)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getRemoteMeta 失败: ${e.message}")
            null
        }
    }

    // ============ 内部工具 ============

    private fun buildRemoteUrl(remotePath: String): String {
        val base = getUrl().trimEnd('/')
        val subdir = getSubdir().trim('/')
        val path = remotePath.trim('/')
        return buildString {
            append(base)
            if (subdir.isNotEmpty()) {
                append('/').append(subdir)
            }
            if (path.isNotEmpty()) {
                append('/').append(path)
            }
            append('/')
        }.let { if (remotePath.isEmpty() || remotePath.endsWith("/")) it else it.trimEnd('/') }
    }

    private fun buildAuthHeader(): String {
        val credential = okhttp3.Credentials.basic(getUser(), getPass())
        return credential
    }

    private fun ensureParentDir(remotePath: String) {
        val parts = remotePath.trim('/').split('/').dropLast(1) // 去掉文件名
        if (parts.isEmpty()) return
        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current/$part"
            try {
                val url = buildRemoteUrl("$current/")
                val request = Request.Builder()
                    .url(url)
                    .method("MKCOL", null)
                    .header("Authorization", buildAuthHeader())
                    .build()
                client.newCall(request).execute().use { resp ->
                    // 201 = created, 405 = already exists，都视为成功
                    if (resp.code !in 200..299 && resp.code != 405) {
                        Log.d(TAG, "MKCOL $current -> ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ensureParentDir MKCOL 失败: $current, ${e.message}")
            }
        }
    }

    private fun parseLastModified(resp: Response): Long {
        return try {
            val lm = resp.header("Last-Modified") ?: return System.currentTimeMillis()
            RFC1123_FORMAT.parse(lm)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun parsePropfindLastModified(xml: String): Long? {
        // 简化解析：提取 <getlastmodified>...</getlastmodified> 中的日期文本
        val regex = "<getlastmodified>([^<]+)</getlastmodified>".toRegex()
        val match = regex.find(xml) ?: return null
        return try {
            RFC1123_FORMAT.parse(match.groupValues[1])?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePropfindSize(xml: String): Long {
        val regex = "<getcontentlength>([^<]+)</getcontentlength>".toRegex()
        val match = regex.find(xml) ?: return 0
        return match.groupValues[1].toLongOrNull() ?: 0
    }
}
