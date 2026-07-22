package com.gamecenter.app.modules.bridge

import android.content.Context
import com.gamecenter.app.modules.bridge.generated.ModuleStoreHostApi
import com.gamecenter.app.modules.bridge.generated.NativeCatalog
import com.gamecenter.app.modules.bridge.generated.NativeDownloadProgress
import com.gamecenter.app.modules.bridge.generated.NativeModule
import com.gamecenter.app.modules.bridge.generated.NativeOperationResult
import com.gamecenter.app.modules.core.ModuleCoreFacade

class PigeonModuleApiImpl(context: Context) : ModuleStoreHostApi {
    private val appContext = context.applicationContext
    private val facade = ModuleCoreFacade.getInstance(appContext)
    private val mapper = ModuleBridgeMapper(facade, appContext)
    private val uiPreferences = appContext.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)

    override fun getCatalog(callback: (Result<NativeCatalog>) -> Unit) {
        facade.getCatalog { callback(it.map(mapper::catalog)) }
    }

    override fun refreshCatalog(callback: (Result<NativeCatalog>) -> Unit) {
        facade.refreshCatalog { callback(it.map(mapper::catalog)) }
    }

    override fun getInstalledModules(callback: (Result<List<NativeModule?>>) -> Unit) {
        ensureCatalog { result -> callback(result.map { facade.installedModules().map(mapper::module) }) }
    }

    override fun getModuleStatus(moduleId: String, callback: (Result<NativeModule>) -> Unit) =
        moduleResult(moduleId, callback)

    override fun getModuleDetails(moduleId: String, callback: (Result<NativeModule>) -> Unit) =
        moduleResult(moduleId, callback)

    override fun downloadModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        callback(Result.success(mapper.operation(facade.downloadModule(moduleId), moduleId)))
    }

    override fun cancelDownload(moduleId: String): NativeOperationResult =
        mapper.operation(facade.cancelDownload(moduleId), moduleId)

    override fun installModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        callback(Result.success(mapper.operation(facade.installModule(moduleId), moduleId)))
    }

    override fun updateModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        callback(Result.success(mapper.operation(facade.updateModule(moduleId), moduleId)))
    }

    override fun uninstallModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        callback(Result.success(mapper.operation(facade.uninstallModule(moduleId), moduleId)))
    }

    override fun enableModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        callback(Result.success(mapper.operation(facade.enableModule(moduleId), moduleId)))
    }

    override fun disableModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        callback(Result.success(mapper.operation(facade.disableModule(moduleId), moduleId)))
    }

    override fun rollbackModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        callback(Result.success(mapper.operation(facade.rollbackModule(moduleId), moduleId)))
    }

    override fun openModule(moduleId: String, callback: (Result<NativeOperationResult>) -> Unit) {
        callback(Result.success(mapper.operation(facade.openModule(moduleId), moduleId)))
    }

    override fun getDownloadProgress(moduleId: String): NativeDownloadProgress =
        mapper.progress(facade.progress(moduleId))

    override fun getUpdateableModules(callback: (Result<List<NativeModule?>>) -> Unit) {
        ensureCatalog { result -> callback(result.map { facade.updateableModules().map(mapper::module) }) }
    }

    override fun updateAllModules(callback: (Result<List<NativeOperationResult?>>) -> Unit) {
        ensureCatalog { result ->
            callback(result.map {
                facade.updateableModules().map { module ->
                    mapper.operation(facade.updateModule(module.id), module.id)
                }
            })
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
            callback(catalogResult.mapCatching {
                facade.module(moduleId)?.let(mapper::module)
                    ?: throw NoSuchElementException("Module $moduleId was not found")
            })
        }
    }

    private fun ensureCatalog(callback: (Result<Unit>) -> Unit) {
        if (facade.catalogSnapshot() != null) callback(Result.success(Unit))
        else facade.getCatalog { callback(it.map { Unit }) }
    }

    companion object {
        private const val UI_PREFS = "flutter_module_store_ui"
        private const val MAX_UI_PREF_LENGTH = 16_384
        private val ALLOWED_UI_KEYS = setOf("search_history", "filter_state", "sort_mode", "view_mode")
    }
}
