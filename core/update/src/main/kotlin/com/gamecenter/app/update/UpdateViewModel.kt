package com.gamecenter.app.update

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamecenter.app.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class Available(val info: UpdateInfo) : UpdateCheckState()
    data object NotAvailable : UpdateCheckState()
    data class BetaOnly(val info: UpdateInfo) : UpdateCheckState()
    data class BetaBlocked(val info: UpdateInfo) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val downloaded: Long, val total: Long) : DownloadState()
    data object Verifying : DownloadState()
    data class Completed(val apkFile: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
    data object Cancelled : DownloadState()
}

sealed class CheckResult {
    data class Success(val info: UpdateInfo?) : CheckResult()
    data class Failure(val message: String) : CheckResult()
    data object Cancelled : CheckResult()
}

sealed class DownloadResult {
    data class Complete(val apkFile: File) : DownloadResult()
    data class Failure(val message: String) : DownloadResult()
    data object Cancelled : DownloadResult()
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: UpdateManager,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _updateCheckState = MutableLiveData<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: LiveData<UpdateCheckState> = _updateCheckState

    private val _downloadState = MutableLiveData<DownloadState>(DownloadState.Idle)
    val downloadState: LiveData<DownloadState> = _downloadState

    private var downloadedApk: File? = null
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    fun checkUpdate(context: Context, showToast: Boolean) {
        if (checkJob?.isActive == true) return
        _updateCheckState.value = UpdateCheckState.Checking

        checkJob = viewModelScope.launch {
            val result = checkUpdateSuspend(context)
            when (result) {
                is CheckResult.Success -> {
                    val info = result.info
                    if (info != null && info.hasUpdate()) {
                        if (settingsManager.isAutoDownloadUpdate()) {
                            startAutoDownload(context, info, showToast)
                        } else {
                            _updateCheckState.value = UpdateCheckState.Available(info)
                        }
                    } else if (info != null && info.isBetaUpdateOutdated()) {
                        _updateCheckState.value = UpdateCheckState.BetaOnly(info)
                    } else if (info != null && info.isBetaUpdateBlocked() && showToast) {
                        _updateCheckState.value = UpdateCheckState.BetaBlocked(info)
                    } else if (showToast) {
                        _updateCheckState.value = UpdateCheckState.NotAvailable
                    } else {
                        _updateCheckState.value = UpdateCheckState.Idle
                    }
                }
                is CheckResult.Failure -> {
                    _updateCheckState.value = if (showToast) {
                        UpdateCheckState.Error(result.message)
                    } else {
                        UpdateCheckState.Idle
                    }
                }
                is CheckResult.Cancelled -> {
                    _updateCheckState.value = UpdateCheckState.Idle
                }
            }
        }
    }

    fun startDownload(context: Context, info: UpdateInfo) {
        if (downloadJob?.isActive == true) return
        _downloadState.value = DownloadState.Downloading(0, 0)

        downloadJob = viewModelScope.launch {
            val result = downloadApkSuspend(context, info)
            when (result) {
                is DownloadResult.Complete -> {
                    downloadedApk = result.apkFile
                    _downloadState.value = DownloadState.Completed(result.apkFile)
                }
                is DownloadResult.Failure -> {
                    _downloadState.value = DownloadState.Error(result.message)
                }
                is DownloadResult.Cancelled -> {
                    _downloadState.value = DownloadState.Cancelled
                }
            }
        }
    }

    private fun startAutoDownload(context: Context, info: UpdateInfo, showToast: Boolean) {
        downloadJob = viewModelScope.launch {
            val result = downloadApkSuspend(context, info)
            when (result) {
                is DownloadResult.Complete -> {
                    downloadedApk = result.apkFile
                    if (settingsManager.isPromptInstallAfterAutoDownload()) {
                        _downloadState.value = DownloadState.Completed(result.apkFile)
                    } else {
                        _updateCheckState.value = UpdateCheckState.Idle
                    }
                }
                is DownloadResult.Failure -> {
                    if (showToast) {
                        _downloadState.value = DownloadState.Error(result.message)
                    }
                }
                is DownloadResult.Cancelled -> { }
            }
        }
    }

    private suspend fun checkUpdateSuspend(context: Context): CheckResult {
        return try {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                updateManager.checkUpdate(context, object : UpdateManager.UpdateCheckCallback {
                    override fun onResult(info: UpdateInfo?) {
                        if (continuation.isActive) {
                            continuation.resumeWith(kotlin.Result.success(CheckResult.Success(info)))
                        }
                    }
                    override fun onError(message: String) {
                        if (continuation.isActive) {
                            continuation.resumeWith(kotlin.Result.success(CheckResult.Failure(message)))
                        }
                    }
                    override fun onCancelled() {
                        if (continuation.isActive) {
                            continuation.resumeWith(kotlin.Result.success(CheckResult.Cancelled))
                        }
                    }
                })
                continuation.invokeOnCancellation { updateManager.cancel() }
            }
        } catch (e: CancellationException) {
            CheckResult.Cancelled
        }
    }

    private suspend fun downloadApkSuspend(context: Context, info: UpdateInfo): DownloadResult {
        return try {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                updateManager.downloadApk(context, info, object : UpdateManager.DownloadCallback {
                    override fun onProgress(downloaded: Long, total: Long) {
                        _downloadState.postValue(DownloadState.Downloading(downloaded, total))
                    }
                    override fun onVerifying() {
                        _downloadState.postValue(DownloadState.Verifying)
                    }
                    override fun onComplete(apkFile: File) {
                        if (continuation.isActive) {
                            continuation.resumeWith(kotlin.Result.success(DownloadResult.Complete(apkFile)))
                        }
                    }
                    override fun onError(message: String) {
                        if (continuation.isActive) {
                            continuation.resumeWith(kotlin.Result.success(DownloadResult.Failure(message)))
                        }
                    }
                    override fun onCancelled() {
                        if (continuation.isActive) {
                            continuation.resumeWith(kotlin.Result.success(DownloadResult.Cancelled))
                        }
                    }
                })
                continuation.invokeOnCancellation { updateManager.cancel() }
            }
        } catch (e: CancellationException) {
            DownloadResult.Cancelled
        }
    }

    fun enableBetaAndRecheck(context: Context) {
        settingsManager.setAcceptBetaUpdate(true)
        checkUpdate(context, true)
    }

    fun installApk(context: Context) {
        val apk = downloadedApk
        if (apk != null && apk.exists()) {
            if (!updateManager.canRequestInstall(context)) {
                updateManager.requestInstallPermission(context as androidx.appcompat.app.AppCompatActivity, REQUEST_INSTALL_PERMISSION)
            } else {
                updateManager.installApk(context, apk)
            }
        }
    }

    fun canRequestInstall(context: Context): Boolean {
        return updateManager.canRequestInstall(context)
    }

    fun requestInstallPermission(activity: androidx.appcompat.app.AppCompatActivity) {
        updateManager.requestInstallPermission(activity, REQUEST_INSTALL_PERMISSION)
    }

    fun openDownloadDirectory(context: Context) {
        updateManager.openDownloadDirectory(context)
    }

    fun onInstallPermissionResult(context: Context, resultCode: Int) {
        val apk = downloadedApk
        if (apk == null || !apk.exists()) {
            downloadedApk = null
            return
        }
        if (resultCode == androidx.appcompat.app.AppCompatActivity.RESULT_OK || updateManager.canRequestInstall(context)) {
            updateManager.installApk(context, apk)
        }
    }

    override fun onCleared() {
        super.onCleared()
        checkJob?.cancel()
        downloadJob?.cancel()
        updateManager.cancel()
    }

    companion object {
        const val REQUEST_INSTALL_PERMISSION = 1001
    }
}
