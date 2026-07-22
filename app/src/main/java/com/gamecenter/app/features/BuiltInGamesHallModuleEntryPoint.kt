package com.gamecenter.app.features

import android.content.Context
import androidx.fragment.app.Fragment
import com.gamecenter.app.BuildConfig
import com.gamecenter.app.GamesFragment
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.core.common.ModuleInterface
import com.gamecenter.app.core.common.ModuleNavigationContribution
import com.gamecenter.app.core.common.NavigationSlot

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

    override fun createFragment(context: Context): Fragment {
        // HOME_REVAMP_V2: 返回 GamesFragment 以加载 V2 布局（fragment_games.xml）
        // 之前返回 BuiltInGamesHallFragment（纯代码 UI），导致 XML 布局改动不生效
        return if (BuildConfig.ENABLE_P4_DYNAMIC_GAMES_HALL) {
            DynamicGamesHallFragment()
        } else if (BuildConfig.HOME_REVAMP_V2 || BuildConfig.HOME_IMMERSIVE_REVAMP) {
            GamesFragment()
        } else {
            BuiltInGamesHallFragment()
        }
    }

    override fun getNavigationContributions(context: Context): List<ModuleNavigationContribution> {
        return listOf(GamesHallNavContribution())
    }

    private class GamesHallNavContribution : ModuleNavigationContribution {
        override fun getContributionId(): String = "games_hall"
        override fun getTitle(context: Context): String = "游戏大厅"
        override fun getIconResId(): Int = 0
        override fun getOrder(): Int = 10
        override fun getSlot(): NavigationSlot = NavigationSlot.BOTTOM_NAV
        override fun createFragment(context: Context): Fragment {
            return if (BuildConfig.ENABLE_P4_DYNAMIC_GAMES_HALL) {
                DynamicGamesHallFragment()
            } else if (BuildConfig.HOME_REVAMP_V2 || BuildConfig.HOME_IMMERSIVE_REVAMP) {
                GamesFragment()
            } else {
                BuiltInGamesHallFragment()
            }
        }
    }
}
