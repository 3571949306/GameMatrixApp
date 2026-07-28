package com.gamecenter.app.modules.runtime

import android.content.Context
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.R
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.RuntimeType

abstract class BaseRuntimeHandler(
    override val runtimeType: RuntimeType
) : ModuleRuntimeHandler {

    override fun prepare(context: Context, module: CatalogModule): RuntimeResult {
        if (!module.isCompatibleWithHost(BuildConfig.VERSION_CODE)) {
            return RuntimeResult(false, "host_incompatible", context.getString(R.string.module_error_host_incompatible))
        }
        val missing = module.dependencies.filterNot { ModuleManager.isModuleInstalled(context, it) }
        if (missing.isNotEmpty()) {
            return RuntimeResult(false, "missing_dependencies", context.getString(R.string.module_error_missing_dependencies, missing.joinToString()))
        }
        return RuntimeResult(true)
    }

    override fun enable(context: Context, module: CatalogModule): RuntimeResult {
        return if (ModuleManager.setModuleEnabled(context, module.id, true)) {
            RuntimeResult(true)
        } else {
            RuntimeResult(false, "enable_failed", context.getString(R.string.module_error_unable_to_enable, module.id))
        }
    }

    override fun disable(context: Context, module: CatalogModule): RuntimeResult {
        return if (ModuleManager.setModuleEnabled(context, module.id, false)) {
            RuntimeResult(true)
        } else {
            RuntimeResult(false, "required_module", context.getString(R.string.module_error_required_no_disable))
        }
    }

    override fun rollback(context: Context, module: CatalogModule): RuntimeResult {
        return if (ModuleManager.rollbackModule(context, module.id)) {
            RuntimeResult(true)
        } else {
            RuntimeResult(false, "rollback_unavailable", context.getString(R.string.module_error_rollback_unavailable))
        }
    }
}
