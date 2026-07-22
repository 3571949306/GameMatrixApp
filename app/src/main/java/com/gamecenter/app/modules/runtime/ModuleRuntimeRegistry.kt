package com.gamecenter.app.modules.runtime

import com.gamecenter.app.modules.catalog.CatalogModule

class ModuleRuntimeRegistry(
    handlers: List<ModuleRuntimeHandler> = listOf(
        FlutterRuntimeHandler(),
        WebRuntimeHandler(),
        AssetRuntimeHandler(),
        AndroidRuntimeHandler(),
        NativeServiceRuntimeHandler(),
        UnityRuntimeHandler()
    )
) {
    private val handlersByType = handlers.associateBy { it.runtimeType }

    fun forModule(module: CatalogModule): ModuleRuntimeHandler =
        handlersByType[module.runtimeType]
            ?: error("No runtime handler registered for ${module.runtimeType.wireValue}")
}
