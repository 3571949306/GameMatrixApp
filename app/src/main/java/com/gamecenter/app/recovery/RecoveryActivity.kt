package com.gamecenter.app.recovery

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gamecenter.app.R
import java.io.File

class RecoveryActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var sourceText: TextView
    private lateinit var errorText: TextView
    private lateinit var downloadBtn: Button
    private lateinit var installBtn: Button
    private lateinit var retryBtn: Button
    private lateinit var cancelBtn: Button

    private var downloadedApk: File? = null
    private var isDownloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recovery)

        statusText = findViewById(R.id.recoveryStatusText)
        progressBar = findViewById(R.id.recoveryProgressBar)
        progressText = findViewById(R.id.recoveryProgressText)
        sourceText = findViewById(R.id.recoverySourceText)
        errorText = findViewById(R.id.recoveryErrorText)
        downloadBtn = findViewById(R.id.recoveryDownloadBtn)
        installBtn = findViewById(R.id.recoveryInstallBtn)
        retryBtn = findViewById(R.id.recoveryRetryBtn)
        cancelBtn = findViewById(R.id.recoveryCancelBtn)

        downloadBtn.setOnClickListener { startDownload() }
        installBtn.setOnClickListener { installDownloadedApk() }
        retryBtn.setOnClickListener { startDownload() }
        cancelBtn.setOnClickListener { cancelDownload() }

        val existingApk = RecoveryDownloader.getRecoveryApkFile(this)
        if (existingApk.exists() && RecoveryVerifier.verifyApkBasic(existingApk)) {
            downloadedApk = existingApk
            installBtn.isEnabled = true
            statusText.text = getString(R.string.recovery_status_apk_ready)
            progressText.text = formatFileSize(existingApk.length())
        }

        CrashDetector.clearRecoveryFlag(this)
    }

    private fun startDownload() {
        if (isDownloading) return
        isDownloading = true

        errorText.visibility = View.GONE
        downloadBtn.visibility = View.GONE
        retryBtn.visibility = View.GONE
        cancelBtn.visibility = View.VISIBLE
        installBtn.isEnabled = false
        progressBar.progress = 0
        statusText.text = getString(R.string.recovery_status_downloading)

        RecoveryDownloader.downloadStableApk(this, null, object : RecoveryDownloader.Callback {
            override fun onProgress(downloaded: Long, total: Long, speedKbps: Long) {
                runOnUiThread {
                    if (total > 0) {
                        val percent = (downloaded * 100 / total).toInt()
                        progressBar.progress = percent
                        progressText.text = "${percent}% · ${formatFileSize(downloaded)} / ${formatFileSize(total)} · ${speedKbps} KB/s"
                    } else {
                        progressText.text = "${formatFileSize(downloaded)} · ${speedKbps} KB/s"
                    }
                }
            }

            override fun onComplete(file: File) {
                runOnUiThread {
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
                    isDownloading = false
                    statusText.text = getString(R.string.recovery_status_error)
                    errorText.text = message
                    errorText.visibility = View.VISIBLE
                    cancelBtn.visibility = View.GONE
                    retryBtn.visibility = View.VISIBLE
                    downloadBtn.visibility = View.GONE
                }
            }

            override fun onSourceSwitch(sourceIndex: Int, url: String) {
                runOnUiThread {
                    val label = when (sourceIndex) {
                        0 -> "HK VPS"
                        1 -> "US VPS"
                        2 -> "GitHub"
                        else -> "Source ${sourceIndex + 1}"
                    }
                    sourceText.text = "${getString(R.string.recovery_source)}: $label"
                }
            }
        })
    }

    private fun installDownloadedApk() {
        val apk = downloadedApk ?: return
        if (!RecoveryInstaller.canRequestInstall(this)) {
            RecoveryInstaller.requestInstallPermission(this)
            return
        }
        val success = RecoveryInstaller.installApk(this, apk)
        if (!success) {
            errorText.text = getString(R.string.recovery_install_failed)
            errorText.visibility = View.VISIBLE
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

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
