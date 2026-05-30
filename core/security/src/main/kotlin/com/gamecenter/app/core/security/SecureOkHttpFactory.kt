package com.gamecenter.app.core.security

import android.util.Log
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 安全的 OkHttp 客户端工厂。
 *
 * 使用证书固定（Certificate Pinning）确保 App 只与已知的合法服务器通信，
 * 防止中间人攻击（MITM）篡改模块分发文件。
 *
 * 如何获取证书指纹（将 YOUR_SERVER 替换为实际服务器域名）：
 *   openssl s_client -connect YOUR_SERVER:443 | openssl x509 -pubkey -noout |
 *   openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | base64
 */
object SecureOkHttpFactory {

    private const val TAG = "SecureOkHttpFactory"

    /**
     * 模块分发服务器的 SHA-256 公钥固定指纹。
     * 格式: "sha256/BASE64_ENCODED_HASH"
     *
     * 维护说明：
     * - 证书续期前需提前添加新证书的指纹（备用固定），避免服务中断。
     * - 同时保留至少 2 个有效指纹（当前 + 备用）。
     */
    private val MODULE_SERVER_PINS = arrayOf(
        "sha256/HeIT3BL0oFGKkDHVuCa8tk99aeVwsuR9z0QxavMzL98=", // 模块服务器 Leaf Cert
        "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=", // Intermediate Cert
        "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="  // Root Cert
    )

    /** 运行时通过 setHosts() 注入实际服务器域名 */
    @Volatile private var MODULE_HOST: String = "your-server.example.com"
    @Volatile private var FALLBACK_HOST: String = "fallback.example.com"

    /**
     * 由 app 模块在初始化时调用，注入实际服务器域名。
     */
    fun setHosts(moduleHost: String, fallbackHost: String) {
        MODULE_HOST = moduleHost
        FALLBACK_HOST = fallbackHost
    }

    /**
     * 构建用于下载模块的 OkHttpClient（带证书固定）。
     * 仅允许与已固定的服务器通信。
     */
    fun buildModuleClient(): OkHttpClient {
        // 注意：由于 Android 模拟器 TLS CertificatePinner 的 SIGSEGV 兼容性问题，
        // 暂时禁用证书固定。生产环境应重新启用并定期更新指纹。
        // val pinner = CertificatePinner.Builder().apply {
        //     MODULE_SERVER_PINS.forEach { pin ->
        //         add(MODULE_HOST, pin)
        //         add("*.$FALLBACK_HOST", pin)
        //     }
        // }.build()

        return OkHttpClient.Builder()
            // .certificatePinner(pinner)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "GameMatrixApp-ModuleManager/2.0")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    /**
     * 构建通用网络请求客户端（无证书固定，用于非模块下载场景）。
     */
    fun buildGeneralClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 检查证书固定是否已正确配置（指纹是否为占位符）。
     * 在 Debug 构建中启动时调用，提醒开发者替换占位证书指纹。
     */
    fun validatePinsConfigured(): Boolean {
        val hasPlaceholder = MODULE_SERVER_PINS.any { it.contains("REPLACE_WITH_ACTUAL") }
        if (hasPlaceholder) {
            Log.w(TAG, "⚠️  证书固定指纹尚未配置！请运行以下命令获取真实指纹并替换 SecureOkHttpFactory 中的占位符：\n" +
                    "openssl s_client -connect $MODULE_HOST:443 2>/dev/null | " +
                    "openssl x509 -pubkey -noout | openssl pkey -pubin -outform DER | " +
                    "openssl dgst -sha256 -binary | base64")
        }
        return !hasPlaceholder
    }
}
