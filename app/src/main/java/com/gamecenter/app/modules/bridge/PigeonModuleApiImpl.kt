package com.gamecenter.app.modules.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.gamecenter.app.modules.bridge.generated.ModuleStoreHostApi
import com.gamecenter.app.modules.bridge.generated.NativeCatalog
import com.gamecenter.app.modules.bridge.generated.NativeDownloadProgress
import com.gamecenter.app.modules.bridge.generated.NativeModule
import com.gamecenter.app.modules.bridge.generated.NativeOperationResult
import com.gamecenter.app.modules.core.ModuleCoreFacade
import java.util.concurrent.Executors

class PigeonModuleApiImpl(context: Context) : ModuleStoreHostApi {
    private val appContext = context.applicationContext
    private val facade = ModuleCoreFacade.getInstance(appContext)
    private val mapper = ModuleBridgeMapper(facade, appContext)
    private val uiPreferences = appContext.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    /** Catalog mapping and synchronous facade operations may verify large module files. */
    private val bridgeExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "module-store-bridge")
    }

    override fun getCatalog(callback: (Result<NativeCatalog>) -> Unit) {
        facade.getCatalog { mapResultOnWorker(it, mapper::catalog, callback) }
    }

    override fun refreshCatalog(callback: (Result<NativeCatalog>) -> Unit) {
        facade.refreshCatalog { mapResultOnWorker(it, mapper::catalog, callback) }
    }

    override fun getInstalledModules(callback: (Result<List<NativeModule?>>) -> Unit) {
        ensureCatalog { result ->
            mapResultOnWorker(result, { facade.installedModules().map(mapper::module) }, callback)
        }
    }

    override fun getModuleStatus(moduleId: String, callback: (Result<NativeModule>) -> Unit) =
        moduleResult(moduleId, callback)

    override fun getModuleDetails(moduleId: String, callback: (Result<NativeModule>) -> Unit) =
        moduleResult(moduleId, callback)

    override fun downloadModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        operationOnWorker(moduleId, facade::downloadModule, callback)
    }

    override fun cancelDownload(moduleId: String): NativeOperationResult =
        mapper.operation(facade.cancelDownload(moduleId), moduleId)

    override fun installModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        operationOnWorker(moduleId, facade::installModule, callback)
    }

    override fun updateModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        operationOnWorker(moduleId, facade::updateModule, callback)
    }

    override fun uninstallModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        operationOnWorker(moduleId, facade::uninstallModule, callback)
    }

    override fun enableModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        operationOnWorker(moduleId, facade::enableModule, callback)
    }

    override fun disableModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        operationOnWorker(moduleId, facade::disableModule, callback)
    }

    override fun rollbackModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        operationOnWorker(moduleId, facade::rollbackModule, callback)
    }

    override fun openModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        operationOnWorker(moduleId, facade::openModule, callback)
    }

    override fun getDownloadProgress(moduleId: String): NativeDownloadProgress =
        mapper.progress(facade.progress(moduleId))

    override fun getUpdateableModules(callback: (Result<List<NativeModule?>>) -> Unit) {
        ensureCatalog { result ->
            mapResultOnWorker(result, { facade.updateableModules().map(mapper::module) }, callback)
        }
    }

    override fun updateAllModules(callback: (Result<List<NativeOperationResult?>>) -> Unit) {
        ensureCatalog { result ->
            mapResultOnWorker(result, {
                facade.updateableModules().map { module ->
                    mapper.operation(facade.updateModule(module.id), module.id)
                }
            }, callback)
        }
    }

    override fun getUiPreference(key: String): String {
        require(key in ALLOWED_UI_KEYS) { "Unsupported Flutter store preference key" }
        return uiPreferences.getString(key, "").orEmpty()
    }

    override fun setUiPreference(key: String, value: String) {
        require(key in ALLOWED_UI_KEYS) { "Unsupported Flutter store preference key" }
        require(value.length <= MAX_UI_PREF_LENGTH) { "Flutter store preference value is too large" }
        uiPreferences.edit().putString(key, value).apply()
    }

    override fun openLegacyStore() {
        appContext.startActivity(
            android.content.Intent(appContext, com.gamecenter.app.modules.ModuleStoreActivity::class.java)
                .putExtra(com.gamecenter.app.modules.ModuleStoreActivity.EXTRA_FORCE_LEGACY_STORE, true)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun moduleResult(moduleId: String, callback: (Result<NativeModule>) -> Unit) {
        ensureCatalog { catalogResult ->
            mapResultOnWorker(catalogResult, {
                facade.module(moduleId)?.let(mapper::module)
                    ?: throw NoSuchElementException("Module $moduleId was not found")
            }, callback)
        }
    }

    private fun ensureCatalog(callback: (Result<Unit>) -> Unit) {
        if (facade.catalogSnapshot() != null) callback(Result.success(Unit))
        else facade.getCatalog { callback(it.map { Unit }) }
    }

    private fun <T, R> mapResultOnWorker(
        result: Result<T>,
        transform: (T) -> R,
        callback: (Result<R>) -> Unit
    ) {
        bridgeExecutor.execute {
            val mapped = runCatching { result.map(transform) }.getOrElse { Result.failure(it) }
            mainHandler.post { callback(mapped) }
        }
    }

    private fun operationOnWorker(
        moduleId: String,
        operation: (String) -> com.gamecenter.app.modules.core.ModuleOperationResult,
        callback: (Result<NativeOperationResult>) -> Unit
    ) {
        bridgeExecutor.execute {
            val mapped: Result<NativeOperationResult> = runCatching {
                mapper.operation(operation(moduleId), moduleId)
            }
            mainHandler.post { callback(mapped) }
        }
    }

    companion object {
        private const val UI_PREFS = "flutter_module_store_ui"
        private const val MAX_UI_PREF_LENGTH = 16_384
        private val ALLOWED_UI_KEYS = setOf("search_history", "filter_state", "sort_mode", "view_mode")
    }
}
