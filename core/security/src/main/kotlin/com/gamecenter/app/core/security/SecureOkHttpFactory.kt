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
        // 2026-08-29 域名迁移 hk-update.tcp888.uk（Let's Encrypt 链：Leaf / YE1 / Root YE）
        "sha256/ijBcxbC/7DLkqL9aYpHjoLtOOzkcA7YIE/RbNCLJYRg=", // 模块服务器 Leaf Cert
        "sha256/brzvtCELCIZUo4sD/qPX0ccRtPsd3DY6RfmxpOU9oB4=", // Intermediate Cert (LE YE1)
        "sha256/sCkq5UWXjg+7mKu9lMhhYF5bGLsy7VI/UNW3tccdR7w="  // Root Cert (ISRG Root YE)
    )

    /** 运行时通过 setHosts() 注入实际服务器域名 */
    @Volatile private var MODULE_HOST: String = "your-server.example.com"
    /** host → 是否包含 Leaf pin（主域 true，镜像 false）；LinkedHashMap 保持注入顺序 */
    @Volatile private val MODULE_HOSTS = LinkedHashMap<String, Boolean>()
    
    /** 是否启用证书绑定(Release构建启用,Debug构建禁用以避免模拟器SIGSEGV) */
    @Volatile private var enableCertificatePinning: Boolean = false

    /**
     * 由 app 模块在初始化时调用,注入实际服务器域名列表。
     * 分发架构 v2：主域(hk-update) + 三边缘(jp/hk/us.dl) 共用同一组
     * Let's Encrypt 链 pin（中级 YE1 + 根 ISRG YE 对全部 LE 证书通用），
     * Leaf 指纹仅对主域生效。
     *
     * @param moduleHost 模块服务器主域名（保持兼容）
     * @param mirrorHosts 分发边缘主机名列表（逗号分隔，可为空）
     * @param enablePinning 是否启用证书绑定(Release构建传true,Debug构建传false)
     */
    @JvmStatic
    @JvmOverloads
    fun setHosts(moduleHost: String, mirrorHosts: String = "", enablePinning: Boolean = true) {
        val hosts = linkedMapOf(moduleHost to true)
        mirrorHosts.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { hosts[it] = false }
        MODULE_HOSTS.clear()
        MODULE_HOSTS.putAll(hosts)
        enableCertificatePinning = enablePinning
    }

    /**
     * 构建用于下载模块的 OkHttpClient（带证书固定）。
     * 仅允许与已固定的服务器通信。
     *
     * 注意：证书固定仅在 enableCertificatePinning=true 时启用（Release 构建）。
     * Debug 构建禁用以兼容 Android 模拟器的 TLS CertificatePinner SIGSEGV 问题。
     */
    fun buildModuleClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()

        if (enableCertificatePinning) {
            val pinner = CertificatePinner.Builder().apply {
                // 主域：Leaf + LE 中级 + 根（Leaf 指纹仅在主域上匹配其自身证书）
                MODULE_HOSTS.forEach { (host, includeLeaf) ->
                    if (includeLeaf) add(host, MODULE_SERVER_PINS[0])
                    // LE 中级/根对三边缘与主域全部有效（同为 Let's Encrypt 签发）
                    add(host, MODULE_SERVER_PINS[1])
                    add(host, MODULE_SERVER_PINS[2])
                }
            }.build()
            builder.certificatePinner(pinner)
        }

        return builder
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
        // R10：仅检测占位符不够——默认 host 依然是占位域名同样视为未配置。
        // 真实证书匹配无法在运行时自证，只能保证"非占位"不被误判为已配置。
        val placeholderHost = MODULE_HOST.isEmpty() || MODULE_HOST == "your-server.example.com"
        val hasPlaceholder = MODULE_SERVER_PINS.any { it.contains("REPLACE_WITH_ACTUAL") }
        if (hasPlaceholder || placeholderHost) {
            Log.w(TAG, "⚠️  证书固定指纹尚未配置！请运行以下命令获取真实指纹并替换 SecureOkHttpFactory 中的占位符：\n" +
                    "openssl s_client -connect $MODULE_HOST:443 2>/dev/null | " +
                    "openssl x509 -pubkey -noout | openssl pkey -pubin -outform DER | " +
                    "openssl dgst -sha256 -binary | base64")
        }
        return !hasPlaceholder && !placeholderHost
    }
}
