package com.gamecenter.app.features

import android.content.Context
import androidx.fragment.app.Fragment
import com.gamecenter.app.BuildConfig
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
        // G1 单一真源：入口优先级与 Fragment 创建全部收敛到工厂，
        // FeatureModule 入口与导航贡献点共用同一选路结果
        return GamesHallDestinationFactory.createFragment(
            GamesHallDestinationFactory.Options(
                dynamicGamesHall = BuildConfig.ENABLE_P4_DYNAMIC_GAMES_HALL,
                libraryRevamp = BuildConfig.HOME_LIBRARY_REVAMP
            )
        )
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
            // G1 单一真源：禁止在调用点复制优先级判断，一律走工厂
            return GamesHallDestinationFactory.createFragment(
                GamesHallDestinationFactory.Options(
                    dynamicGamesHall = BuildConfig.ENABLE_P4_DYNAMIC_GAMES_HALL,
                    libraryRevamp = BuildConfig.HOME_LIBRARY_REVAMP
                )
            )
        }
    }
}
