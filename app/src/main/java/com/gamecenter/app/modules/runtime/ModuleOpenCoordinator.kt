package com.gamecenter.app.modules.runtime

import android.content.Context
import android.content.Intent
import com.gamecenter.app.DynamicGameActivity
import com.gamecenter.app.MainActivity
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule

object ModuleOpenCoordinator {
    private val hostNavigationIds = setOf("games_hall", "browser", "tools", "ai", "vpn", "wrongbook")

    fun openAndroid(context: Context, module: CatalogModule): RuntimeResult {
        val manifest = module.legacyManifest
            ?: return RuntimeResult(false, "manifest_missing", "No Android manifest mapping is available")
        if (!ModuleManager.isModuleEnabled(context, module.id)) {
            return RuntimeResult(false, "module_disabled", "Enable the module before opening it")
        }
        if (manifest.type == "nav" && (manifest.isBaseFramework || module.id in hostNavigationIds)) {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_NAV_TAB, module.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return RuntimeResult(true)
        }
        if (manifest.type == "game" && manifest.activityClass.isNotEmpty() && manifest.builtIn) {
            return runCatching {
                val activityClass = Class.forName(manifest.activityClass)
                context.startActivity(Intent(context, activityClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                RuntimeResult(true)
            }.getOrElse { RuntimeResult(false, "activity_not_found", it.message.orEmpty()) }
        }
        if (manifest.type == "game") {
            val gameId = manifest.gameId.ifEmpty { manifest.id }
            context.startActivity(
                Intent(context, DynamicGameActivity::class.java)
                    .putExtra(DynamicGameActivity.EXTRA_GAME_ID, gameId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return RuntimeResult(true)
        }
        ModuleManager.loadModule(context, module.id)
        return if (ModuleManager.startModule(context, module.id)) {
            RuntimeResult(true)
        } else {
            RuntimeResult(false, "module_start_failed", "The module entry point did not start")
        }
    }
}
