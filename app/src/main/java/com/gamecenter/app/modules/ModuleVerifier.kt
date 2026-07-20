package com.gamecenter.app.modules

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ModuleVerifier {

    private const val TAG = "ModuleVerifier"

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = FileInputStream(file)
        try {
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        } finally {
            input.close()
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 校验文件的 SHA-256。
     *
     * Batch 21 安全修复：
     * - 默认情况下 [expectedSha256] 为空时拒绝校验（返回 false），防止绕过完整性检查
     * - 仅当显式传入 [allowEmpty]=true 时，空 SHA 才被接受（仅用于内置模块）
     *
     * @param file 待校验的文件
     * @param expectedSha256 期望的 SHA-256 十六进制字符串
     * @param allowEmpty 是否允许空 SHA 跳过校验（仅内置模块应为 true）
     */
    fun verifySha256(file: File, expectedSha256: String, allowEmpty: Boolean = false): Boolean {
        if (!file.exists()) return false
        if (expectedSha256.isEmpty()) {
            if (allowEmpty) {
                Log.d(TAG, "SHA-256 为空，允许跳过校验（内置模块）: ${file.name}")
                return true
            }
            Log.w(TAG, "SHA-256 为空，拒绝校验（非内置模块必须配置 SHA）: ${file.name}")
            return false
        }
        val actual = computeSha256(file)
        val result = actual.equals(expectedSha256, ignoreCase = true)
        if (!result) {
            Log.w(TAG, "SHA-256 mismatch: expected=$expectedSha256 actual=$actual file=${file.name}")
        }
        return result
    }

    fun verifyDexFile(file: File): Boolean {
        if (!file.exists() || file.length() < 12) return false
        val input = FileInputStream(file)
        try {
            val magic = ByteArray(8)
            input.read(magic)
            val isDex = magic[0] == 'd'.code.toByte() &&
                    magic[1] == 'e'.code.toByte() &&
                    magic[2] == 'x'.code.toByte() &&
                    magic[3] == '\n'.code.toByte()
            val isZip = magic[0] == 0x50.toByte() &&
                    magic[1] == 0x4B.toByte() &&
                    magic[2] == 0x03.toByte() &&
                    magic[3] == 0x04.toByte()
            return isDex || isZip
        } finally {
            input.close()
        }
    }
}
