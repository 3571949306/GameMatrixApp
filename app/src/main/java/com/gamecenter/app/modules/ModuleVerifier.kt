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

    fun verifySha256(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        if (expectedSha256.isEmpty()) {
            Log.d(TAG, "SHA-256 为空，跳过校验: ${file.name}")
            return true
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
