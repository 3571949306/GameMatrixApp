package com.gamecenter.app.modules.store

import android.util.Log
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
 * 公钥硬编码在代码中，私钥仅保存在VPS发布环境。
 * 
 * 安全说明：
 * - 公钥可以公开，不影响安全性
 * - 私钥必须严格保护，不能泄露
 * - 后续可以实现密钥轮换机制
 * 
 * @author AI Assistant
 * @since 2026-07-20
 */
class Ed25519CatalogSignatureVerifier : CatalogSignatureVerifier {
    
    companion object {
        private const val TAG = "Ed25519CatalogSigVerifier"
        
        // Ed25519公钥（32字节，Base64编码）
        // TODO: 替换为实际生成的公钥
        // 生成方法：使用Tink的Ed25519KeyPairGenerator生成密钥对
        // 示例代码：
        // val keyPair = Ed25519KeyPairGenerator.generateKeyPair()
        // val publicKey = Base64.encodeToString(keyPair.publicKey, Base64.NO_WRAP)
        private const val PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEA" + // Ed25519公钥前缀
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" // 占位符，需要替换为实际公钥
    }
    
    private val publicKeyBytes: ByteArray = android.util.Base64.decode(PUBLIC_KEY_BASE64, android.util.Base64.NO_WRAP)
    
    override fun verify(catalog: String, signature: String): Boolean {
        return try {
            // 解码签名
            val signatureBytes = android.util.Base64.decode(signature, android.util.Base64.NO_WRAP)
            
            // 创建Ed25519验证器
            val verifier = Ed25519Verify(publicKeyBytes)
            
            // 验证签名
            verifier.verify(signatureBytes, catalog.toByteArray(Charsets.UTF_8))
            
            Log.d(TAG, "目录签名验证通过")
            true
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
 * 根据BuildConfig开关决定是否强制验证签名。
 * 当前处于兼容模式（不强制验证），后续可以启用强制验证。
 * 
 * @author AI Assistant
 * @since 2026-07-20
 */
object CatalogSignatureVerifierManager {
    
    private const val TAG = "CatalogSigVerifierMgr"
    
    private val verifier: CatalogSignatureVerifier = Ed25519CatalogSignatureVerifier()
    
    /**
     * 验证目录签名。
     * 
     * @param catalog 目录JSON内容
     * @param signature Base64编码的签名
     * @param forceVerify 是否强制验证（由BuildConfig.ENABLE_CATALOG_SIGNATURE控制）
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
        
        // 验证签名
        val isValid = verifier.verify(catalog, signature)
        
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
