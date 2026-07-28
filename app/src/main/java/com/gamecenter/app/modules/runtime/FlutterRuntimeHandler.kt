package com.gamecenter.app.modules.runtime

import android.content.Context
import com.gamecenter.app.R
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.RuntimeType

object FlutterRouteRegistry {
    private val routes = mutableSetOf("/store", "/store/downloads", "/store/installed", "/store/updates")

    @Synchronized
    fun register(route: String) {
        require(route.startsWith('/'))
        routes += route
    }

    @Synchronized
    fun contains(route: String): Boolean = route in routes || route.startsWith("/store/module/")
}

class FlutterRuntimeHandler : BaseRuntimeHandler(RuntimeType.FLUTTER) {
    override fun install(context: Context, module: CatalogModule): RuntimeResult {
        if (!FlutterRouteRegistry.contains(module.route)) {
            return RuntimeResult(false, "route_missing", context.getString(R.string.module_error_route_not_compiled))
        }
        ModuleManager.setModuleEnabled(context, module.id, true)
        return RuntimeResult(true)
    }

    override fun open(context: Context, module: CatalogModule): RuntimeResult =
        if (FlutterRouteRegistry.contains(module.route)) RuntimeResult(true)
        else RuntimeResult(false, "route_missing", context.getString(R.string.module_error_update_host_flutter_route))

    override fun uninstall(context: Context, module: CatalogModule): RuntimeResult {
        if (module.required) return RuntimeResult(false, "required_module", context.getString(R.string.module_error_required_routes_no_remove))
        return disable(context, module)
    }
}
