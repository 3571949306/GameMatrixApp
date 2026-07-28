package com.gamecenter.app.modules.runtime

import android.content.Context
import android.content.Intent
import com.gamecenter.app.R
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.RuntimeType

class AssetRuntimeHandler : BaseRuntimeHandler(RuntimeType.ASSET) {
    override fun install(context: Context, module: CatalogModule): RuntimeResult =
        SecureArchiveInstaller.install(context, module)

    override fun open(context: Context, module: CatalogModule): RuntimeResult {
        val current = SecureArchiveInstaller.currentDirectory(context, module.id)
        if (!current.isDirectory) return RuntimeResult(false, "asset_missing", context.getString(R.string.module_error_asset_not_installed))
        context.sendBroadcast(
            Intent(ACTION_ASSET_MODULE_READY)
                .setPackage(context.packageName)
                .putExtra(EXTRA_MODULE_ID, module.id)
        )
        return RuntimeResult(true, message = context.getString(R.string.module_error_asset_consumers_notified))
    }

    override fun uninstall(context: Context, module: CatalogModule): RuntimeResult {
        val result = SecureArchiveInstaller.uninstall(context, module.id)
        if (result.success && module.legacyManifest != null) ModuleManager.uninstallModule(context, module.id)
        return result
    }

    override fun rollback(context: Context, module: CatalogModule): RuntimeResult =
        SecureArchiveInstaller.rollback(context, module.id)

    companion object {
        const val ACTION_ASSET_MODULE_READY = "com.gamecenter.app.action.ASSET_MODULE_READY"
        const val EXTRA_MODULE_ID = "module_id"
    }
}
