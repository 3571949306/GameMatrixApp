package com.gamecenter.app.modules.runtime

import android.content.Context
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.RuntimeType

class AndroidRuntimeHandler : BaseRuntimeHandler(RuntimeType.ANDROID) {
    override fun install(context: Context, module: CatalogModule): RuntimeResult {
        return if (ModuleManager.isModuleInstalled(context, module.id)) RuntimeResult(true)
        else RuntimeResult(false, "not_installed", "Download and verify the APK before installing")
    }

    override fun open(context: Context, module: CatalogModule): RuntimeResult =
        ModuleOpenCoordinator.openAndroid(context, module)

    override fun uninstall(context: Context, module: CatalogModule): RuntimeResult {
        if (module.required || module.legacyManifest?.isBaseFramework == true) {
            return RuntimeResult(false, "required_module", "Required modules cannot be uninstalled")
        }
        ModuleManager.uninstallModule(context, module.id)
        return RuntimeResult(true)
    }
}
