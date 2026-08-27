package com.gamecenter.app.modules.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 目录 Ed25519 签名验证回环测试（P3 补强）。
 *
 * 固定测试密钥（seed=0x00..1f，公钥 PUB）签名由 Python cryptography 预计算并硬编码，
 * 测试只做验签，避免依赖 Tink 签名 API 的差异。另覆盖 CatalogSignatureVerifierManager
 * 的 forceVerify 语义（缺失签名：强制=失败，兼容=警告不失败）。
 */
class CatalogSignatureTest {

    private val pubHex = "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8"
    private val message = "{\"catalogVersion\":1,\"hello\":\"world\"}"

    // 由 python cryptography 预计算（RFC8032 标准 Ed25519），以 Base64 传入（验证层内部 Base64.decode）
    private val validSignature =
        "QM44DfLkdnJLtHpnF1247OHbkndRgcytF5wg2Qj59bE0mjIFpGlYaJjorDiyUPqZbfofHN5Z+Xpq4tGTTyy1Cg=="
    // 另一把私钥（seed=0x1f,0x01..0x1e）对同一消息的签名
    private val otherKeySignature =
        "mGgmt7PBdII6JwJygZ5TgdZH6Cv0Z9zSTEc7Y6THbTxwJn1YVHJg4xs2qAQRHLLbsdOuelUKvBbxALYx2O2pBg=="

    private fun buildVerifier() = Ed25519CatalogSignatureVerifier(listOf(hexToBytes(pubHex)))

    @Test
    fun `valid signature passes`() {
        assertTrue(buildVerifier().verify(message, validSignature))
    }

    @Test
    fun `tampered message fails`() {
        assertFalse(buildVerifier().verify(message + " ", validSignature))
    }

    @Test
    fun `signature from other key fails`() {
        assertFalse(buildVerifier().verify(message, otherKeySignature))
    }

    @Test
    fun `missing signature in force mode fails`() {
        val result = CatalogSignatureVerifierManager.verify(message, null, forceVerify = true)
        assertTrue(result.isFailure)
    }

    @Test
    fun `missing signature in compat mode is warning not failure`() {
        val result = CatalogSignatureVerifierManager.verify(message, null, forceVerify = false)
        assertTrue(result.isWarning)
        assertFalse(result.isFailure)
    }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}