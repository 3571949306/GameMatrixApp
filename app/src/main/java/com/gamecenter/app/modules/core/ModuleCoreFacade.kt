package com.gamecenter.app.modules.core

import android.content.Context
import com.gamecenter.app.modules.ModuleDownloader
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.CatalogV2
import com.gamecenter.app.modules.catalog.CatalogV2Repository
import com.gamecenter.app.modules.catalog.RuntimeType
import com.gamecenter.app.modules.runtime.ModuleRuntimeRegistry
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Stable native facade used by both Flutter and the legacy store migration. */
class ModuleCoreFacade private constructor(private val context: Context) {
    private val catalogRepository = CatalogV2Repository.getInstance(context)
    private val runtimeRegistry = ModuleRuntimeRegistry()
    private val modules = ConcurrentHashMap<String, CatalogModule>()
    private val progress = ConcurrentHashMap<String, ModuleProgress>()
    @Volatile private var lastCatalog: CatalogV2? = null

    fun getCatalog(callback: (Result<CatalogV2>) -> Unit) {
        catalogRepository.getCatalog { result ->
            result.onSuccess(::rememberCatalog)
            callback(result)
        }
    }

    fun refreshCatalog(callback: (Result<CatalogV2>) -> Unit) {
        catalogRepository.refresh { result ->
            result.onSuccess { catalog ->
                rememberCatalog(catalog)
                ModuleEventBus.publish(
                    ModuleCoreEvent("CatalogUpdated", "", RuntimeType.ANDROID, ModuleState.INSTALLED)
                )
            }
            callback(result)
        }
    }

    fun catalogSnapshot(): CatalogV2? = lastCatalog

    fun module(moduleId: String): CatalogModule? = modules[moduleId]

    fun installedModules(): List<CatalogModule> = modules.values.filter {
        ModuleManager.isModuleInstalled(context, it.id)
    }

    fun updateableModules(): List<CatalogModule> = modules.values.filter { module ->
        val installed = ModuleManager.getInstalledVersionCode(context, module.id)
        installed > 0 && module.versionCode > installed
    }

    fun state(module: CatalogModule): ModuleState {
        progress[module.id]?.let { active ->
            if (active.state in setOf(ModuleState.QUEUED, ModuleState.DOWNLOADING, ModuleState.VERIFYING, ModuleState.INSTALLING)) {
                return active.state
            }
        }
        if (!ModuleManager.isModuleInstalled(context, module.id)) return ModuleState.NOT_INSTALLED
        if (!ModuleManager.isModuleEnabled(context, module.id)) return ModuleState.DISABLED
        val installed = ModuleManager.getInstalledVersionCode(context, module.id)
        return if (installed > 0 && module.versionCode > installed) ModuleState.UPDATE_AVAILABLE
        else ModuleState.INSTALLED
    }

    fun progress(moduleId: String): ModuleProgress = progress[moduleId]
        ?: ModuleProgress(moduleId, 0, 0, 0, module(moduleId)?.let(::state) ?: ModuleState.NOT_INSTALLED)

    fun downloadModule(moduleId: String): ModuleOperationResult {
        val module = modules[moduleId] ?: return failure(moduleId, "module_not_found", "Module not found", true, "Refresh the catalog")
        val prepared = runtimeRegistry.forModule(module).prepare(context, module)
        if (!prepared.success) return failure(module, prepared.code, prepared.message, true, "Resolve compatibility requirements")
        if (module.deliveryType.wireValue == "builtin") {
            val installed = runtimeRegistry.forModule(module).install(context, module)
            return if (installed.success) success() else failure(module, installed.code, installed.message, false, "Update the host app")
        }
        if (module.legacyManifest == null) {
            return failure(
                module,
                "package_not_registered",
                "This Catalog V2 package is not registered with the authoritative downloader",
                false,
                "Publish a signed manifest mapping before enabling downloads"
            )
        }
        val initial = ModuleProgress(moduleId, 0, module.packageInfo.fileSize, 0, ModuleState.QUEUED)
        progress[moduleId] = initial
        publish("DownloadQueued", module, ModuleState.QUEUED, initial)
        ModuleManager.downloadModule(context, moduleId, createDownloadCallback(module))
        return success()
    }

    fun installModule(moduleId: String): ModuleOperationResult = downloadModule(moduleId)

    fun updateModule(moduleId: String): ModuleOperationResult {
        val module = modules[moduleId] ?: return failure(moduleId, "module_not_found", "Module not found", true, "Refresh the catalog")
        if (state(module) != ModuleState.UPDATE_AVAILABLE) {
            return failure(module, "no_update", "No newer compatible version is available", true, "Refresh the catalog")
        }
        return downloadModule(moduleId)
    }

    fun cancelDownload(moduleId: String): ModuleOperationResult {
        val module = modules[moduleId] ?: return failure(moduleId, "module_not_found", "Module not found", false, "Refresh the catalog")
        ModuleManager.cancelDownload(moduleId)
        val cancelled = ModuleProgress(moduleId, progress[moduleId]?.downloadedBytes ?: 0, progress[moduleId]?.totalBytes ?: 0, 0, ModuleState.NOT_INSTALLED)
        progress[moduleId] = cancelled
        publish("DownloadCancelled", module, ModuleState.NOT_INSTALLED, cancelled)
        return success()
    }

    fun uninstallModule(moduleId: String): ModuleOperationResult = execute(moduleId, "ModuleRemoved", ModuleState.UNINSTALLING) {
        runtimeRegistry.forModule(it).uninstall(context, it)
    }

    fun enableModule(moduleId: String): ModuleOperationResult = execute(moduleId, "ModuleEnabled", ModuleState.INSTALLED) {
        runtimeRegistry.forModule(it).enable(context, it)
    }

    fun disableModule(moduleId: String): ModuleOperationResult = execute(moduleId, "ModuleDisabled", ModuleState.DISABLED) {
        runtimeRegistry.forModule(it).disable(context, it)
    }

    fun rollbackModule(moduleId: String): ModuleOperationResult = execute(moduleId, "RollbackCompleted", ModuleState.ROLLED_BACK) {
        publish("RollbackStarted", it, ModuleState.ROLLING_BACK)
        runtimeRegistry.forModule(it).rollback(context, it)
    }

    fun openModule(moduleId: String): ModuleOperationResult = execute(moduleId, "ModuleOpened", ModuleState.INSTALLED) {
        runtimeRegistry.forModule(it).open(context, it)
    }

    fun updateAllModules(): List<ModuleOperationResult> = updateableModules().map { updateModule(it.id) }

    private fun execute(
        moduleId: String,
        eventType: String,
        targetState: ModuleState,
        operation: (CatalogModule) -> com.gamecenter.app.modules.runtime.RuntimeResult
    ): ModuleOperationResult {
        val module = modules[moduleId] ?: return failure(moduleId, "module_not_found", "Module not found", true, "Refresh the catalog")
        val result = operation(module)
        return if (result.success) {
            publish(eventType, module, targetState)
            success()
        } else {
            failure(module, result.code, result.message, true, "Retry or use the legacy store")
        }
    }

    private fun createDownloadCallback(module: CatalogModule) = object : ModuleDownloader.Callback {
        override fun onProgress(moduleId: String, downloaded: Long, total: Long, speedKbps: Long) {
            val item = ModuleProgress(moduleId, downloaded, total, speedKbps, ModuleState.DOWNLOADING)
            progress[moduleId] = item
            publish("DownloadProgress", module, ModuleState.DOWNLOADING, item)
        }

        override fun onStateChanged(moduleId: String, state: String) {
            val mapped = ModuleState.fromWire(state)
            val item = progress[moduleId]?.copy(state = mapped)
                ?: ModuleProgress(moduleId, 0, module.packageInfo.fileSize, 0, mapped)
            progress[moduleId] = item
            val eventType = when (mapped) {
                ModuleState.QUEUED -> "DownloadQueued"
                ModuleState.DOWNLOADING -> "DownloadStarted"
                ModuleState.VERIFYING -> "VerificationStarted"
                ModuleState.INSTALLING -> "InstallStarted"
                else -> "ModuleStateChanged"
            }
            publish(eventType, module, mapped, item)
        }

        override fun onComplete(moduleId: String, file: File) {
            val installResult = runtimeRegistry.forModule(module).install(context, module)
            if (!installResult.success) {
                val error = error(module, installResult.code, installResult.message, true, "Retry installation")
                progress[moduleId] = ModuleProgress(moduleId, file.length(), file.length(), 0, ModuleState.FAILED)
                publish("InstallFailed", module, ModuleState.FAILED, error = error)
                return
            }
            val complete = ModuleProgress(moduleId, file.length(), file.length(), 0, ModuleState.INSTALLED)
            progress[moduleId] = complete
            publish("DownloadCompleted", module, ModuleState.INSTALLED, complete)
            publish("InstallCompleted", module, ModuleState.INSTALLED, complete)
        }

        override fun onError(moduleId: String, message: String) {
            val failure = error(module, "download_failed", message, true, "Retry the download")
            val item = progress[moduleId]?.copy(state = ModuleState.FAILED)
                ?: ModuleProgress(moduleId, 0, module.packageInfo.fileSize, 0, ModuleState.FAILED)
            progress[moduleId] = item
            publish("InstallFailed", module, ModuleState.FAILED, item, failure)
        }

        override fun onSourceSwitch(moduleId: String, sourceIndex: Int, url: String) = Unit
    }

    private fun rememberCatalog(catalog: CatalogV2) {
        lastCatalog = catalog
        modules.clear()
        catalog.modules.forEach { modules[it.id] = it }
    }

    private fun publish(
        eventType: String,
        module: CatalogModule,
        state: ModuleState,
        progress: ModuleProgress? = null,
        error: ModuleCoreError? = null
    ) {
        ModuleEventBus.publish(ModuleCoreEvent(eventType, module.id, module.runtimeType, state, progress = progress, error = error))
    }

    private fun success() = ModuleOperationResult(true)

    private fun failure(
        module: CatalogModule,
        code: String,
        message: String,
        recoverable: Boolean,
        action: String
    ) = ModuleOperationResult(false, error(module, code, message, recoverable, action))

    private fun failure(
        moduleId: String,
        code: String,
        message: String,
        recoverable: Boolean,
        action: String
    ) = ModuleOperationResult(false, ModuleCoreError(code, message, moduleId, RuntimeType.ANDROID, recoverable, action))

    private fun error(
        module: CatalogModule,
        code: String,
        message: String,
        recoverable: Boolean,
        action: String
    ) = ModuleCoreError(code, message, module.id, module.runtimeType, recoverable, action)

    companion object {
        @Volatile private var instance: ModuleCoreFacade? = null

        fun getInstance(context: Context): ModuleCoreFacade = instance ?: synchronized(this) {
            instance ?: ModuleCoreFacade(context.applicationContext).also { instance = it }
        }
    }
}
