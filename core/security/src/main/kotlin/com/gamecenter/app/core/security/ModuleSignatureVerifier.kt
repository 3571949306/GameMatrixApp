package com.gamecenter.app.core.security

import android.content.Context
import android.util.Log
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * 模块 APK 签名者强校验器（S1: P0-5）。
 *
 * 现状背景：
 *   - [com.gamecenter.app.moduleloader.ModuleVerifier] 仅用 PackageManager.GET_SIGNATURES
 *     校验 v1 JAR 签名 + 签名公钥 SHA-256 指纹白名单（含占位）。
 *   - 攻击者可绕过 v1 仅伪造 v2/v3 签名块替换模块。
 *
 * 本校验器：
 *   1. 使用 apksig 库的 [ApkVerifier] 验证 APK 的 v2/v3 签名块（比 PackageManager 更严格）。
 *   2. 取 [ApkVerifier.Result.getSignerCertificates] 与内置发布证书（X.509 DER,
 *      res/raw/release_signer.cer）逐字节比对编码。
 *   3. 与既有 SHA-256 完整性校验并行（SHA-256 由 ModuleVerifier 保留，本类不重复）。
 *
 * 安全策略：发布证书缺失、APK 签名无效或签名者不匹配时一律硬失败。
 *
 * 不在本类职责：
 *   - SHA-256 完整性校验（仍由 ModuleVerifier.verifyIntegrity 负责，SPEC S1-3 保留）。
 *   - 文件存在性 / 大小预校验（仍由 ModuleVerifier 负责）。
 */
object ModuleSignatureVerifier {

    private const val TAG = "ModuleSigVerifier"

    /**
     * 签名校验结果。
     *
     * - [Success]：签名者证书匹配内置发布证书，可继续安装。
     * - [Warning]：保留给非安全性提示；签名问题不会返回此结果。
     * - [Failure]：签名或发布证书校验失败，调用方必须拒绝安装。
     */
    sealed class Result {
        object Success : Result()
        data class Warning(val reason: String) : Result()
        data class Failure(val reason: String) : Result()

        val isSuccess get() = this is Success
        val isWarning get() = this is Warning
        val isFailure get() = this is Failure
    }

    /**
     * 校验 APK 签名者证书。
     *
     * 调用时机：在 [com.gamecenter.app.moduleloader.ModuleVerifier.verify] 通过后
     * （即 SHA-256 完整性已通过）并行调用本方法。
     *
     * @param apkFile 已下载的模块 APK 文件
     * @param context Android Context（用于读取 res/raw/release_signer.cer）
     * @return [Result.Success] / [Result.Warning] / [Result.Failure]
     */
    fun verify(apkFile: File, context: Context): Result {
        // 1. 加载内置发布证书。缺失或损坏时拒绝所有外置模块。
        val pinnedCert = loadPinnedCertificate(context)
        if (pinnedCert == null) {
            Log.e(TAG, "release_signer.cer 缺失或无效，拒绝安装模块")
            return Result.Failure("发布证书未配置或无效")
        }

        // 2. 调用 apksig ApkVerifier 验证 v2/v3 签名块
        return try {
            val result = ApkVerifier.Builder(apkFile).build().verify()

            // 2.1 整体签名是否通过任一签名方案验证
            if (!result.isVerified) {
                val errors = result.errors.joinToString("; ") { it.toString() }
                Log.e(TAG, "APK 签名未通过验证: $errors")
                return Result.Failure("APK 签名验证失败")
            }

            // 2.2 取签名者证书
            val signerCerts: List<X509Certificate> = result.signerCertificates
            if (signerCerts.isEmpty()) {
                Log.e(TAG, "APK 无签名者证书")
                return Result.Failure("APK 无签名者证书")
            }

            // 3. 与内置发布证书逐字节比对（X.509 DER 编码）
            val pinnedEncoded = pinnedCert.encoded
            val matched = signerCerts.any { it.encoded.contentEquals(pinnedEncoded) }

            if (matched) {
                val actualFp = sha256Of(pinnedEncoded)
                Log.i(TAG, "签名者证书校验通过: ${apkFile.name}, sha256=$actualFp")
                Result.Success
            } else {
                val pinnedFp = sha256Of(pinnedEncoded)
                val actualFp = signerCerts.firstOrNull()?.let { sha256Of(it.encoded) }
                Log.w(
                    TAG,
                    "签名者证书不匹配。" +
                        "期望指纹=$pinnedFp, 实际指纹=$actualFp"
                )
                Result.Failure("签名者证书不匹配")
            }
        } catch (e: Exception) {
            Log.e(TAG, "APK 签名验证异常: ${e.message}", e)
            Result.Failure("APK 签名验证异常")
        }
    }

    /**
     * 加载内置发布证书（X.509 DER）。
     *
     * - 占位文件（空或非 DER 文本）会触发解析异常，返回 null。
     * - 占位返回 null 时调用方按"未配置"处理（过渡期告警放行）。
     *
     * Batch 21 Fix 9：成功加载时输出证书指纹 + Subject DN + 过期时间，
     * 便于在 logcat 中快速诊断证书配置/轮换问题。
     */
    private fun loadPinnedCertificate(context: Context): X509Certificate? {
        return try {
            // The public repository intentionally does not contain the release
            // certificate; release builds inject it into the merged app resources.
            // Resolve the resource by name so the library remains compilable in
            // CI and in development builds without weakening the hard-fail path
            // in verify() when the certificate is absent.
            val resourceId = context.resources.getIdentifier(
                "release_signer",
                "raw",
                context.packageName
            )
            if (resourceId == 0) {
                Log.w(TAG, "release_signer.cer 资源缺失")
                return null
            }
            context.resources.openRawResource(resourceId).use { input ->
                val certBytes = input.readBytes()
                if (certBytes.isEmpty()) {
                    Log.w(TAG, "release_signer.cer 为空占位，签名者强校验暂未启用")
                    return null
                }
                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(certBytes.inputStream())
                val x509 = cert as? X509Certificate
                if (x509 != null) {
                    val fp = sha256Of(x509.encoded)
                    val subject = try { x509.subjectX500Principal.name } catch (_: Throwable) { "<unavailable>" }
                    val notAfter = try { x509.notAfter?.toString() } catch (_: Throwable) { "<unavailable>" }
                    Log.i(TAG, "已加载内置发布证书: sha256=$fp, subject=$subject, notAfter=$notAfter")
                }
                x509
            }
        } catch (e: Exception) {
            // 占位文件（文本）解析为 X.509 DER 必然失败，属于预期行为
            Log.w(
                TAG,
                "内置发布证书占位无法解析（预期行为，待替换为真实 X.509 DER）：" +
                    "${e.javaClass.simpleName}: ${e.message}"
            )
            null
        }
    }

    /** 计算 SHA-256 并返回小写十六进制字符串。 */
    private fun sha256Of(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
