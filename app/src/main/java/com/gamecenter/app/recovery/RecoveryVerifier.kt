package com.gamecenter.app.recovery

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object RecoveryVerifier {

    private const val TAG = "RecoveryVerifier"

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = FileInputStream(file)
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        input.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) {
            Log.e(TAG, "File not found: ${file.path}")
            return false
        }
        val actual = computeSha256(file)
        val result = actual.equals(expectedSha256, ignoreCase = true)
        if (result) {
            Log.d(TAG, "SHA-256 verification passed")
        } else {
            Log.w(TAG, "SHA-256 mismatch: expected=$expectedSha256 actual=$actual")
        }
        return result
    }

    fun verifyApkBasic(file: File): Boolean {
        if (!file.exists()) return false
        if (file.length() == 0L) return false
        val name = file.name.lowercase()
        if (!name.endsWith(".apk")) return false
        val input = FileInputStream(file)
        val magic = ByteArray(4)
        input.read(magic)
        input.close()
        val isZip = magic[0] == 0x50.toByte() &&
                magic[1] == 0x4B.toByte() &&
                magic[2] == 0x03.toByte() &&
                magic[3] == 0x04.toByte()
        if (!isZip) {
            Log.e(TAG, "Not a valid ZIP/APK file")
        }
        return isZip
    }
}
