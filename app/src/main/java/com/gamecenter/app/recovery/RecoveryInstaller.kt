package com.gamecenter.app.recovery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
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
