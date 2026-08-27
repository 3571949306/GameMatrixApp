package com.gamecenter.app.modules

import android.util.Log
import java.io.File
import java.io.FileInputStream

/**
 * 模块文件校验兼容门面。
 *
 * SHA-256 计算与完整性校验已统一收敛到核心层 [com.gamecenter.app.core.security.ModuleVerifier]
 * （唯一真源）；本对象保留历史调用签名（verifySha256/allowEmpty/verifyDexFile），
 * 使宿主下载/安装/加载链路无需逐点改写即可使用统一实现。
 */
object ModuleVerifier {

    private const val TAG = "ModuleVerifier"

    fun computeSha256(file: File): String =
        com.gamecenter.app.core.security.ModuleVerifier.computeFileSha256(file)

    /**
     * 校验文件的 SHA-256。
     *
     * 语义与历史实现一致：
     * - [expectedSha256] 为空时默认拒绝（防止绕过完整性检查）；
     * - 仅当显式传入 [allowEmpty]=true 时，空 SHA 才被接受（仅用于内置模块）。
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
        return com.gamecenter.app.core.security.ModuleVerifier
            .verify(file, expectedSha256)
            .isSuccess
    }

    /** 校验文件头为 dex 或 zip/apk 格式（仅格式预检，不参与哈希计算）。 */
    fun verifyDexFile(file: File): Boolean {
        if (!file.exists() || file.length() < 12) return false
        return try {
            FileInputStream(file).use { input ->
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
                isDex || isZip
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dex 文件头校验异常: ${file.name} - ${e.message}")
            false
        }
    }
}