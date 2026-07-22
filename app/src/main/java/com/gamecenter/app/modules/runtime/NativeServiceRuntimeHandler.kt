package com.gamecenter.app.modules.runtime

import android.content.Context
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.RuntimeType
import java.util.concurrent.ConcurrentHashMap

object NativeServiceControllerRegistry {
    private val openers = ConcurrentHashMap<String, (Context, CatalogModule) -> RuntimeResult>().apply {
        put("vpn") { context, module -> ModuleOpenCoordinator.openAndroid(context, module) }
    }

    fun register(serviceType: String, opener: (Context, CatalogModule) -> RuntimeResult) {
        require(serviceType.matches(Regex("[a-z][a-z0-9_.-]{1,63}"))) { "Invalid service type" }
        openers[serviceType] = opener
    }

    fun supports(serviceType: String): Boolean = openers.containsKey(serviceType)

    fun open(context: Context, module: CatalogModule): RuntimeResult {
        val opener = openers[module.serviceType]
            ?: return RuntimeResult(
                false,
                "service_type_unsupported",
                "No host controller is registered for ${module.serviceType}"
            )
        return opener(context, module)
    }
}

class NativeServiceRuntimeHandler : BaseRuntimeHandler(RuntimeType.NATIVE_SERVICE) {
    override fun install(context: Context, module: CatalogModule): RuntimeResult =
        if (ModuleManager.isModuleInstalled(context, module.id)) RuntimeResult(true)
        else RuntimeResult(false, "not_installed", "The native service package is not installed")

    override fun open(context: Context, module: CatalogModule): RuntimeResult =
        NativeServiceControllerRegistry.open(context, module)

    override fun uninstall(context: Context, module: CatalogModule): RuntimeResult {
        if (module.required) return RuntimeResult(false, "required_module", "Required services cannot be uninstalled")
        ModuleManager.uninstallModule(context, module.id)
        return RuntimeResult(true)
    }
}
