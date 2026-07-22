package com.gamecenter.app.modules.core

import com.gamecenter.app.modules.catalog.RuntimeType

enum class ModuleState(val wireValue: String) {
    NOT_INSTALLED("not_installed"),
    QUEUED("queued"),
    DOWNLOADING("downloading"),
    PAUSED("paused"),
    VERIFYING("verifying"),
    INSTALLING("installing"),
    INSTALLED("installed"),
    UPDATE_AVAILABLE("update_available"),
    DISABLED("disabled"),
    FAILED("failed"),
    ROLLING_BACK("rolling_back"),
    ROLLED_BACK("rolled_back"),
    UNINSTALLING("uninstalling");

    companion object {
        fun fromWire(value: String): ModuleState = entries.firstOrNull { it.wireValue == value }
            ?: FAILED
    }
}

data class ModuleCoreError(
    val errorCode: String,
    val message: String,
    val moduleId: String,
    val runtimeType: RuntimeType,
    val recoverable: Boolean,
    val suggestedAction: String,
    val technicalDetails: String = ""
)

data class ModuleProgress(
    val moduleId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedKbps: Long,
    val state: ModuleState
) {
    val percent: Int = if (totalBytes > 0) {
        ((downloadedBytes * 100L) / totalBytes).coerceIn(0, 100).toInt()
    } else {
        0
    }
}

data class ModuleCoreEvent(
    val eventType: String,
    val moduleId: String,
    val runtimeType: RuntimeType,
    val state: ModuleState,
    val timestampMillis: Long = System.currentTimeMillis(),
    val progress: ModuleProgress? = null,
    val error: ModuleCoreError? = null
)

data class ModuleOperationResult(
    val success: Boolean,
    val error: ModuleCoreError? = null
)
