package com.gamecenter.app.modules.runtime

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream
import java.io.File

class WebModuleActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var moduleRoot: File
    private lateinit var moduleId: String

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moduleId = intent.getStringExtra(EXTRA_MODULE_ID).orEmpty()
        val entry = intent.getStringExtra(EXTRA_ENTRY).orEmpty()
        if (!moduleId.matches(Regex("[A-Za-z0-9_.-]+")) || entry.isBlank()) {
            finish()
            return
        }
        moduleRoot = SecureArchiveInstaller.currentDirectory(this, moduleId).canonicalFile
        webView = WebView(this)
        setContentView(webView)
        webView.settings.apply {
            javaScriptEnabled = intent.getBooleanExtra(EXTRA_JAVASCRIPT, false)
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            databaseEnabled = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(false)
            setAcceptThirdPartyCookies(webView, false)
        }
        webView.webViewClient = IsolatedClient()
        webView.loadUrl("$ORIGIN/$moduleId/${entry.trimStart('/')}")
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    private inner class IsolatedClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return request?.url?.host != HOST
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
            val uri = request?.url ?: return blocked()
            if (uri.scheme != "https" || uri.host != HOST) return blocked()
            val prefix = "/$moduleId/"
            if (!uri.path.orEmpty().startsWith(prefix)) return blocked()

            // ����复路径遍历攻击：先解码，再规范化，最后校验
            // 1. URL 解码（处理双重编码等情况）
            val decodedPath = Uri.decode(uri.path.orEmpty().removePrefix(prefix))
            // 2. ��建目标文件并获取规范路径（解��符号��接、移除 . 和 ..）
            val target = File(moduleRoot, decodedPath).canonicalFile
            // 3. ���取模��根目录的规范路径
            val rootCanonicalPath = moduleRoot.canonicalFile.path + File.separator
            // 4. 双重校验：目标文件必须在模��根目录内，且必须是文件（非目录）
            if (!target.path.startsWith(rootCanonicalPath) || !target.isFile) {
                return blocked()
            }

            val extension = target.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
            return WebResourceResponse(
                mime,
                "utf-8",
                200,
                "OK",
                mapOf(
                    "Content-Security-Policy" to "default-src 'self'; object-src 'none'; frame-src 'none'; base-uri 'none'",
                    "X-Content-Type-Options" to "nosniff",
                    "Cache-Control" to "no-store"
                ),
                target.inputStream()
            )
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
            handler?.cancel()
        }

        private fun blocked() = WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    companion object {
        const val EXTRA_MODULE_ID = "module_id"
        const val EXTRA_ENTRY = "entry"
        const val EXTRA_JAVASCRIPT = "javascript"
        private const val HOST = "module.local"
        private const val ORIGIN = "https://$HOST"
    }
}
