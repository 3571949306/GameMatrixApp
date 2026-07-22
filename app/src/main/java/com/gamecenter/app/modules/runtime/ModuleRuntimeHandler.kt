package com.gamecenter.app.modules.runtime

import android.content.Context
import com.gamecenter.app.modules.catalog.CatalogModule
import com.gamecenter.app.modules.catalog.RuntimeType

data class RuntimeResult(
    val success: Boolean,
    val code: String = if (success) "ok" else "runtime_error",
    val message: String = ""
)

interface ModuleRuntimeHandler {
    val runtimeType: RuntimeType

    fun supports(module: CatalogModule): Boolean = module.runtimeType == runtimeType

    fun prepare(context: Context, module: CatalogModule): RuntimeResult

    fun install(context: Context, module: CatalogModule): RuntimeResult

    fun open(context: Context, module: CatalogModule): RuntimeResult

    fun uninstall(context: Context, module: CatalogModule): RuntimeResult

    fun enable(context: Context, module: CatalogModule): RuntimeResult

    fun disable(context: Context, module: CatalogModule): RuntimeResult

    fun rollback(context: Context, module: CatalogModule): RuntimeResult
}
