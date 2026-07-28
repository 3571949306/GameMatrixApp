package com.gamecenter.app.modules.runtime

import android.content.Context
import com.gamecenter.app.R
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.DeliveryType
import com.gamecenter.app.modules.catalog.RuntimeType
import com.gamecenter.app.modules.unity.UnityModuleManager

class UnityRuntimeHandler : BaseRuntimeHandler(RuntimeType.UNITY) {
    override fun install(context: Context, module: CatalogModule): RuntimeResult =
        if (module.deliveryType == DeliveryType.CONTENT) SecureArchiveInstaller.install(context, module)
        else if (ModuleManager.isModuleInstalled(context, module.id)) RuntimeResult(true)
        else RuntimeResult(false, "content_not_installed", context.getString(R.string.module_error_unity_not_installed))

    override fun open(context: Context, module: CatalogModule): RuntimeResult {
        if (module.deliveryType == DeliveryType.CONTENT &&
            !SecureArchiveInstaller.currentDirectory(context, module.id).isDirectory
        ) {
            return RuntimeResult(false, "content_not_installed", context.getString(R.string.module_error_unity_not_installed))
        }
        val launcherId = module.launcherId.ifEmpty { module.id }
        return if (UnityModuleManager.launchStandalone(context, launcherId)) RuntimeResult(true)
        else RuntimeResult(false, "unity_launcher_unavailable", context.getString(R.string.module_error_unity_launcher_unavailable))
    }

    override fun uninstall(context: Context, module: CatalogModule): RuntimeResult {
        if (module.required) return RuntimeResult(false, "required_module", context.getString(R.string.module_error_required_unity_no_remove))
        if (module.deliveryType == DeliveryType.CONTENT) {
            return SecureArchiveInstaller.uninstall(context, module.id)
        }
        ModuleManager.uninstallModule(context, module.id)
        return RuntimeResult(true)
    }

    override fun rollback(context: Context, module: CatalogModule): RuntimeResult =
        if (module.deliveryType == DeliveryType.CONTENT) {
            SecureArchiveInstaller.rollback(context, module.id)
        } else {
            super.rollback(context, module)
        }
}
