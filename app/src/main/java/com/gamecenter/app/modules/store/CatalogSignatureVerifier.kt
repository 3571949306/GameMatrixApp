package com.gamecenter.app.modules.store

import android.util.Log
import com.gamecenter.app.BuildConfig
import com.google.crypto.tink.subtle.Base64
import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException

/**
 * 目录签名验证器接口。
 * 
 * 用于验证从服务器下载的catalog.json文件的Ed25519签名，
 * 确保目录内容未被篡改。
 * 
 * @author AI Assistant
 * @since 2026-07-20
 */
interface CatalogSignatureVerifier {
    /**
     * 验证目录签名。
     * 
     * @param catalog 目录JSON内容
     * @param signature Base64编码的签名
     * @return 签名是否有效
     */
    fun verify(catalog: String, signature: String): Boolean
}

/**
 * Ed25519目录签名验证器实现。
 * 
 * 使用Tink库进行Ed25519签名验证。
 * 公钥由构建环境注入，私钥仅允许存在于受控发布环境。
 * 
 * 安全说明：
 * - 公钥可以公开，不影响安全性
 * - 私钥必须严格保护，不能泄露
 * - 支持同时注入当前和下一把公钥完成无停机轮换
 * 
 * @author AI Assistant
 * @since 2026-07-20
 */
class Ed25519CatalogSignatureVerifier(
    publicKeys: List<ByteArray>
) : CatalogSignatureVerifier {

    companion object {
        private const val TAG = "Ed25519CatalogSigVerifier"

        private const val PUBLIC_KEY_LENGTH_BYTES = 32

        fun fromBase64List(encodedKeys: String): Ed25519CatalogSignatureVerifier {
            val decodedKeys = encodedKeys
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { Base64.decode(it, Base64.NO_WRAP) }
            return Ed25519CatalogSignatureVerifier(decodedKeys)
        }
    }

    private val verifiers: List<Ed25519Verify> = publicKeys.map { publicKey ->
        require(publicKey.size == PUBLIC_KEY_LENGTH_BYTES) {
            "Ed25519 public keys must contain exactly $PUBLIC_KEY_LENGTH_BYTES raw bytes"
        }
        require(publicKey.any { it.toInt() != 0 }) {
            "The all-zero Ed25519 placeholder key is forbidden"
        }
        Ed25519Verify(publicKey.copyOf())
    }.also {
        require(it.isNotEmpty()) { "At least one Ed25519 public key is required" }
    }

    override fun verify(catalog: String, signature: String): Boolean {
        return try {
            val signatureBytes = Base64.decode(signature, Base64.NO_WRAP)
            val messageBytes = catalog.toByteArray(Charsets.UTF_8)
            verifiers.any { verifier ->
                try {
                    verifier.verify(signatureBytes, messageBytes)
                    true
                } catch (_: GeneralSecurityException) {
                    false
                }
            }.also { verified ->
                if (verified) Log.d(TAG, "目录签名验证通过")
                else Log.e(TAG, "目录签名验证失败")
            }
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "目录签名验证失败: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "目录签名验证异常: ${e.message}", e)
            false
        }
    }
}

/**
 * 目录签名验证管理器。
 * 
 * 根据 BuildConfig 开关决定是否强制验证签名。公钥由构建环境注入，
 * 可同时携带当前和下一把公钥以支持无停机轮换；客户端永不持有私钥。
 * 
 * @author AI Assistant
 * @since 2026-07-20
 */
object CatalogSignatureVerifierManager {
    
    private const val TAG = "CatalogSigVerifierMgr"
    
    private val verifier: CatalogSignatureVerifier by lazy {
        Ed25519CatalogSignatureVerifier.fromBase64List(
            BuildConfig.CATALOG_ED25519_PUBLIC_KEYS_BASE64
        )
    }
    
    /**
     * 验证目录签名。
     * 
     * @param catalog 目录JSON内容
     * @param signature Base64编码的签名
     * @param forceVerify 是否强制验证（生产由 BuildConfig.CATALOG_SIGNATURE_TRUSTED 控制；外层总开关为 ENABLE_CATALOG_SIGNATURE）
     * @return 验证结果
     */
    fun verify(catalog: String, signature: String?, forceVerify: Boolean): VerifyResult {
        // 如果没有签名
        if (signature.isNullOrEmpty()) {
            return if (forceVerify) {
                Log.e(TAG, "强制验证模式下缺少签名")
                VerifyResult.Failure("缺少目录签名")
            } else {
                Log.w(TAG, "兼容模式下缺少签名，跳过验证")
                VerifyResult.Warning("缺少目录签名，跳过验证")
            }
        }
        
        val isValid = try {
            verifier.verify(catalog, signature)
        } catch (e: Exception) {
            Log.e(TAG, "目录信任配置无效: ${e.message}")
            return if (forceVerify) {
                VerifyResult.Failure("目录信任配置无效")
            } else {
                VerifyResult.Warning("目录信任配置无效，兼容模式允许继续")
            }
        }
        
        return if (isValid) {
            Log.d(TAG, "目录签名验证通过")
            VerifyResult.Success
        } else {
            if (forceVerify) {
                Log.e(TAG, "强制验证模式下签名无效")
                VerifyResult.Failure("目录签名无效")
            } else {
                Log.w(TAG, "兼容模式下签名无效，但允许继续")
                VerifyResult.Warning("目录签名无效，但兼容模式允许继续")
            }
        }
    }
    
    /**
     * 验证结果密封类。
     */
    sealed class VerifyResult {
        object Success : VerifyResult()
        data class Failure(val reason: String) : VerifyResult()
        data class Warning(val reason: String) : VerifyResult()
        
        val isSuccess: Boolean get() = this is Success
        val isFailure: Boolean get() = this is Failure
        val isWarning: Boolean get() = this is Warning
    }
}
