package com.gamecenter.app.core.security

import android.util.Log
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * 模块文件安全校验器。
 *
 * 所有通过模块商店下载的 APK/DEX 文件在安装前必须通过此校验，
 * 确保文件完整性，防止下载过程中文件被篡改。
 *
 * 规则：
 * 1. SHA-256 为空时，直接拒绝——不允许跳过校验。
 * 2. 文件不存在时，直接拒绝。
 * 3. 文件大小与清单不符时，拒绝（快速预校验）。
 */
object ModuleVerifier {

    private const val TAG = "ModuleVerifier"
    private const val BUFFER_SIZE = 4 * 1024 * 1024 // 4 MB

    /**
     * 校验结果封装，携带失败原因。
     */
    sealed class VerifyResult {
        object Success : VerifyResult()
        data class Failure(val reason: String) : VerifyResult()

        val isSuccess get() = this is Success
    }

    /**
     * 验证下载文件的完整性。
     *
     * @param file 已下载的文件
     * @param expectedSha256 来自 modules.json 的预期 SHA-256 哈希（小写十六进制）
     * @param expectedSize 来自 modules.json 的预期文件大小（字节），0 表示跳过大小校验
     * @return VerifyResult.Success 或 VerifyResult.Failure（含原因）
     */
    fun verify(file: File, expectedSha256: String, expectedSize: Long = 0): VerifyResult {
        // 规则 1：SHA-256 不能为空，空 SHA-256 = 未配置 = 拒绝
        if (expectedSha256.isBlank()) {
            Log.e(TAG, "拒绝安装模块 ${file.name}：modules.json 中 sha256 字段为空")
            return VerifyResult.Failure("安全校验配置错误：sha256 不能为空")
        }

        // 规则 2：文件必须存在
        if (!file.exists() || !file.isFile) {
            Log.e(TAG, "拒绝安装模块 ${file.name}：文件不存在")
            return VerifyResult.Failure("文件不存在：${file.absolutePath}")
        }

        // 规则 3：文件大小预校验（快速失败）
        if (expectedSize > 0 && file.length() != expectedSize) {
            Log.e(TAG, "拒绝安装模块 ${file.name}：" +
                    "文件大小不符（期望 $expectedSize 字节，实际 ${file.length()} 字节）")
            return VerifyResult.Failure(
                "文件大小不符：期望 $expectedSize 字节，实际 ${file.length()} 字节"
            )
        }

        // 规则 4：SHA-256 哈希校验
        return try {
            val actual = computeSha256(file)
            val expected = expectedSha256.trim().lowercase()
            if (actual == expected) {
                Log.d(TAG, "模块 ${file.name} SHA-256 校验通过")
                VerifyResult.Success
            } else {
                Log.e(TAG, "拒绝安装模块 ${file.name}：SHA-256 不符\n" +
                        "  期望：$expected\n  实际：$actual")
                VerifyResult.Failure("SHA-256 校验失败，文件可能已损坏或被篡改")
            }
        } catch (e: Exception) {
            Log.e(TAG, "计算文件 SHA-256 时发生异常：${e.message}", e)
            VerifyResult.Failure("SHA-256 计算异常：${e.message}")
        }
    }

    /**
     * 流式计算文件 SHA-256，避免大文件 OOM。
     */
    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        file.inputStream().buffered(BUFFER_SIZE).use { input: InputStream ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 便捷方法：计算文件 SHA-256 并返回十六进制字符串。
     * 用于打包模块时生成 modules.json 中的 sha256 字段。
     */
    fun computeFileSha256(file: File): String = computeSha256(file)
}
