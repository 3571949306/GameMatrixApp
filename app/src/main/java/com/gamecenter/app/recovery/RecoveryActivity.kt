package com.gamecenter.app.recovery

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gamecenter.app.MainActivity
import com.gamecenter.app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File

class RecoveryActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var sourceText: TextView
    private lateinit var errorCard: View
    private lateinit var errorText: TextView
    private lateinit var downloadBtn: Button
    private lateinit var installBtn: Button
    private lateinit var retryBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var launchAnywayBtn: Button

    private var downloadedApk: File? = null
    private var isDownloading = false

    /** R7: 已因缺少安装权限而跳转设置，返回后需自动重试安装 */
    private var pendingInstallAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recovery_modern)

        statusText = findViewById(R.id.tv_recovery_status)
        progressBar = findViewById(R.id.progress_recovery)
        progressText = findViewById(R.id.tv_recovery_progress)
        sourceText = findViewById(R.id.tv_recovery_source)
        errorCard = findViewById(R.id.card_recovery_error)
        errorText = findViewById(R.id.tv_recovery_error)
        downloadBtn = findViewById(R.id.btn_recovery_download)
        installBtn = findViewById(R.id.btn_recovery_install)
        retryBtn = findViewById(R.id.btn_recovery_retry)
        cancelBtn = findViewById(R.id.btn_recovery_cancel)
        launchAnywayBtn = findViewById(R.id.btn_recovery_launch_anyway)

        downloadBtn.setOnClickListener { startDownload() }
        installBtn.setOnClickListener { installDownloadedApk() }
        retryBtn.setOnClickListener { startDownload() }
        cancelBtn.setOnClickListener { cancelDownload() }
        launchAnywayBtn.setOnClickListener { confirmLaunchAnyway() }

        val existingApk = RecoveryDownloader.getRecoveryApkFile(this)
        if (existingApk.exists() && RecoveryVerifier.verifyApkBasic(existingApk)) {
            downloadedApk = existingApk
            installBtn.isEnabled = true
            statusText.text = getString(R.string.recovery_status_apk_ready)
            progressText.text = formatFileSize(existingApk.length())
        }

        CrashDetector.clearRecoveryFlag(this)
    }

    override fun onResume() {
        super.onResume()
        // R7: 从"安装未知应用"设置返回后，若已获授权则自动重试安装
        if (pendingInstallAfterPermission && RecoveryInstaller.canRequestInstall(this)) {
            pendingInstallAfterPermission = false
            installDownloadedApk()
        }
    }

    private fun startDownload() {
        if (isDownloading) return
        isDownloading = true

        hideError()
        downloadBtn.visibility = View.GONE
        retryBtn.visibility = View.GONE
        cancelBtn.visibility = View.VISIBLE
        installBtn.isEnabled = false
        progressBar.progress = 0
        statusText.text = getString(R.string.recovery_status_downloading)

        RecoveryDownloader.downloadStableApk(this, null, object : RecoveryDownloader.Callback {
            override fun onProgress(downloaded: Long, total: Long, speedKbps: Long) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (total > 0) {
                        val percent = (downloaded * 100 / total).toInt()
                        progressBar.progress = percent
                        progressText.text = "${percent}% · ${formatFileSize(downloaded)} / ${formatFileSize(total)} · ${formatSpeed(speedKbps)}"
                    } else {
                        progressText.text = "${formatFileSize(downloaded)} · ${formatSpeed(speedKbps)}"
                    }
                }
            }

            override fun onComplete(file: File) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isDownloading = false
                    downloadedApk = file
                    progressBar.progress = 100
                    statusText.text = getString(R.string.recovery_status_download_complete)
                    progressText.text = formatFileSize(file.length())
                    cancelBtn.visibility = View.GONE
                    downloadBtn.visibility = View.GONE
                    retryBtn.visibility = View.GONE
                    installBtn.isEnabled = true
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isDownloading = false
                    statusText.text = getString(R.string.recovery_status_error)
                    showError(message)
                    cancelBtn.visibility = View.GONE
                    retryBtn.visibility = View.VISIBLE
                    downloadBtn.visibility = View.GONE
                }
            }

            override fun onSourceSwitch(sourceIndex: Int, url: String) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    sourceText.text = "${getString(R.string.recovery_source)}: ${sourceLabel(sourceIndex)}"
                }
            }

            override fun onSlowSpeedSwitch(fromIndex: Int, toIndex: Int) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    statusText.text = getString(R.string.recovery_status_switching_slow)
                    sourceText.text = "${getString(R.string.recovery_source)}: ${sourceLabel(toIndex)}"
                }
            }
        })
    }

    private fun installDownloadedApk() {
        val apk = downloadedApk ?: return
        if (!RecoveryInstaller.canRequestInstall(this)) {
            // R7: 缺少"安装未知应用"授权，跳转设置；返回后由 onResume 自动重试
            pendingInstallAfterPermission = true
            RecoveryInstaller.requestInstallPermission(this)
            return
        }
        // R7: 降级告警 —— 恢复包版本低于当前版本时提示用户
        val curVer = currentVersionCode()
        val apkVer = apkVersionCode(apk)
        if (apkVer != null && curVer != null && apkVer < curVer) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.recovery_title)
                .setMessage(R.string.recovery_downgrade_warning)
                .setPositiveButton(android.R.string.ok) { _, _ -> doInstall(apk) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        doInstall(apk)
    }

    private fun doInstall(apk: File) {
        pendingInstallAfterPermission = false
        val success = RecoveryInstaller.installApk(this, apk)
        if (!success) {
            showError(getString(R.string.recovery_install_failed))
        }
    }

    private fun cancelDownload() {
        RecoveryDownloader.cancel()
        isDownloading = false
        statusText.text = getString(R.string.recovery_status_cancelled)
        cancelBtn.visibility = View.GONE
        downloadBtn.visibility = View.VISIBLE
        retryBtn.visibility = View.GONE
    }

    /** R2: 逃生出口 —— 离线/无网且无法安装时，允许用户跳过恢复直接启动主程序 */
    private fun confirmLaunchAnyway() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recovery_title)
            .setMessage(R.string.recovery_launch_anyway_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                CrashDetector.clearRecoveryFlag(this)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun currentVersionCode(): Long? = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
    } catch (_: Exception) {
        null
    }

    private fun apkVersionCode(apk: File): Long? = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.versionCode?.toLong()
    } catch (_: Exception) {
        null
    }

    private fun sourceLabel(index: Int): String {
        return when (index) {
            0 -> getString(R.string.recovery_source_hk_vps)
            1 -> getString(R.string.recovery_source_github)
            else -> getString(R.string.recovery_source_fallback_format, index + 1)
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorCard.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorCard.visibility = View.GONE
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    private fun formatSpeed(speedKbps: Long): String {
        return if (speedKbps >= 1024) {
            "%.1f MB/s".format(speedKbps / 1024.0)
        } else {
            "$speedKbps KB/s"
        }
    }
}
