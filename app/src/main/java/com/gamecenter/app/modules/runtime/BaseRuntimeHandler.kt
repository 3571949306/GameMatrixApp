package com.gamecenter.app.modules.runtime

import android.content.Context
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.RuntimeType

abstract class BaseRuntimeHandler(
    override val runtimeType: RuntimeType
) : ModuleRuntimeHandler {

    override fun prepare(context: Context, module: CatalogModule): RuntimeResult {
        if (!module.isCompatibleWithHost(BuildConfig.VERSION_CODE)) {
            return RuntimeResult(false, "host_incompatible", "The host app version is not compatible")
        }
        val missing = module.dependencies.filterNot { ModuleManager.isModuleInstalled(context, it) }
        if (missing.isNotEmpty()) {
            return RuntimeResult(false, "missing_dependencies", "Missing dependencies: ${missing.joinToString()}")
        }
        return RuntimeResult(true)
    }

    override fun enable(context: Context, module: CatalogModule): RuntimeResult {
        return if (ModuleManager.setModuleEnabled(context, module.id, true)) {
            RuntimeResult(true)
        } else {
            RuntimeResult(false, "enable_failed", "Unable to enable ${module.id}")
        }
    }

    override fun disable(context: Context, module: CatalogModule): RuntimeResult {
        return if (ModuleManager.setModuleEnabled(context, module.id, false)) {
            RuntimeResult(true)
        } else {
            RuntimeResult(false, "required_module", "Required modules cannot be disabled")
        }
    }

    override fun rollback(context: Context, module: CatalogModule): RuntimeResult {
        return if (ModuleManager.rollbackModule(context, module.id)) {
            RuntimeResult(true)
        } else {
            RuntimeResult(false, "rollback_unavailable", "No verified last_good version is available")
        }
    }
}
