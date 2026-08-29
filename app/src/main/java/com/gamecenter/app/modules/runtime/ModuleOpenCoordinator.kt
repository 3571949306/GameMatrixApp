package com.gamecenter.app.modules.runtime

import android.content.Context
import android.content.Intent
import com.gamecenter.app.DynamicGameActivity
import com.gamecenter.app.MainActivity
import com.gamecenter.app.R
import com.gamecenter.app.modules.ModuleManager
import com.gamecenter.app.modules.catalog.CatalogModule

object ModuleOpenCoordinator {
    private val hostNavigationIds = setOf("games_hall", "browser", "tools", "ai", "vpn", "wrongbook")

    fun openAndroid(context: Context, module: CatalogModule): RuntimeResult {
        val manifest = module.legacyManifest
            ?: return RuntimeResult(false, "manifest_missing", context.getString(R.string.module_error_manifest_mapping_missing))
        if (!ModuleManager.isModuleEnabled(context, module.id)) {
            return RuntimeResult(false, "module_disabled", context.getString(R.string.module_error_enable_before_open))
        }
        if (manifest.type == "nav" && (manifest.isBaseFramework || module.id in hostNavigationIds)) {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_NAV_TAB, module.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return RuntimeResult(true)
        }
        // 2026-08-29 模块热更改造：游戏统一走 DynamicGameActivity → tryLoadModuleGame 加载
        // 外置模块 APK（预装或商店下载），支持单独热更；宿主直启分支已删除，
        // 数据回退由 DynamicGameActivity 内 getHostGameActivityClassName 兜底接管。
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
            RuntimeResult(false, "module_start_failed", context.getString(R.string.module_error_entry_not_start))
        }
    }
}
