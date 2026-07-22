package com.gamecenter.app.modules.runtime

import android.content.Context
import android.content.Intent
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.RuntimeType

class WebRuntimeHandler : BaseRuntimeHandler(RuntimeType.WEB) {
    override fun install(context: Context, module: CatalogModule): RuntimeResult =
        SecureArchiveInstaller.install(context, module)

    override fun open(context: Context, module: CatalogModule): RuntimeResult {
        val entry = SecureArchiveInstaller.currentDirectory(context, module.id).resolve(module.entry)
        if (!entry.isFile) return RuntimeResult(false, "entry_missing", "The installed Web entry is missing")
        context.startActivity(
            Intent(context, WebModuleActivity::class.java)
                .putExtra(WebModuleActivity.EXTRA_MODULE_ID, module.id)
                .putExtra(WebModuleActivity.EXTRA_ENTRY, module.entry)
                .putExtra(WebModuleActivity.EXTRA_JAVASCRIPT, module.permissions.any { it.id == "web_javascript" })
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return RuntimeResult(true)
    }

    override fun uninstall(context: Context, module: CatalogModule): RuntimeResult {
        val result = SecureArchiveInstaller.uninstall(context, module.id)
        if (result.success && module.legacyManifest != null) ModuleManager.uninstallModule(context, module.id)
        return result
    }

    override fun rollback(context: Context, module: CatalogModule): RuntimeResult =
        SecureArchiveInstaller.rollback(context, module.id)
}
