package com.gamecenter.app.recovery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.gamecenter.app.core.security.ModuleSignatureVerifier
import java.io.File

object RecoveryInstaller {

    private const val TAG = "RecoveryInstaller"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".update.fileprovider"

    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found")
            return false
        }

        if (!RecoveryVerifier.verifyApkBasic(apkFile)) {
            Log.e(TAG, "APK basic verification failed")
            return false
        }

        // R3: 签名/证书强校验。仅当团队在发布流水线配置了 EXPECTED_STABLE_SHA256 时启用，
        // 与下载器的 SHA-256 完整性校验保持同一开关。证书未配置（占位）时按既有"缺失暂放行"
        // 策略放行，避免反向砖化恢复安装；真实签名不匹配/篡改则必须拦截。
        if (RecoveryDownloader.EXPECTED_STABLE_SHA256.isNotEmpty()) {
            val sigResult = ModuleSignatureVerifier.verify(apkFile, context)
            if (sigResult.isFailure) {
                val reason = (sigResult as ModuleSignatureVerifier.Result.Failure).reason
                if (reason.contains("发布证书未配置")) {
                    Log.w(TAG, "发布证书未配置，跳过签名强校验（过渡期放行）")
                } else {
                    Log.e(TAG, "恢复包签名校验失败，拒绝安装: $reason")
                    return false
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Log.w(TAG, "No install permission, requesting...")
                requestInstallPermission(context)
                return false
            }
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
                        apkFile
                    )
                } else {
                    Uri.fromFile(apkFile)
                }
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Install intent launched successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch install intent: ${e.message}")
            false
        }
    }

    fun canRequestInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val uri = Uri.parse("package:${context.packageName}")
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request install permission: ${e.message}")
            }
        }
    }
}
