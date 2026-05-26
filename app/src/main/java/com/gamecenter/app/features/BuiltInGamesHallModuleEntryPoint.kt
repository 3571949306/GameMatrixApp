package com.gamecenter.app.features

import android.content.Context
import androidx.fragment.app.Fragment
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.core.common.ModuleInterface

class BuiltInGamesHallModuleEntryPoint : ModuleInterface, FeatureModule {

    private var running = false

    override fun init(context: Context) = Unit

    override fun start(context: Context) {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun getId(): String = "games_hall"

    override fun getName(): String = "游戏大厅"

    override fun getVersion(): String = "1.0.0"

    override fun getDescription(): String = "内置游戏大厅，可通过模块商店更新"

    override fun isRunning(): Boolean = running

    override fun createFragment(context: Context): Fragment = BuiltInGamesHallFragment()
}
