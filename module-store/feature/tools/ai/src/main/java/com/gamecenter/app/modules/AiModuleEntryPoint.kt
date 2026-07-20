package com.gamecenter.app.modules

import android.content.Context
import androidx.fragment.app.Fragment
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.core.common.ModuleInterface
import com.gamecenter.app.core.common.ModuleNavigationContribution
import com.gamecenter.app.core.common.NavigationSlot
import com.gamecenter.app.ai.ui.AiFragment

class AiModuleEntryPoint : ModuleInterface, FeatureModule {

    private var running = false

    override fun init(context: Context) {}

    override fun start(context: Context) {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun getId(): String = "ai"

    override fun getName(): String = "AI助手"

    override fun getVersion(): String = "1.0.0"

    override fun getDescription(): String = "智能AI对话助手，支持多轮对话、历史记录和能力调用。"

    override fun isRunning(): Boolean = running

    override fun createFragment(context: Context): Fragment {
        return AiFragment()
    }

    override fun getNavigationContributions(context: Context): List<ModuleNavigationContribution> {
        return listOf(
            AiBottomNavContribution(),
            AiToolsGridContribution()
        )
    }

    private class AiBottomNavContribution : ModuleNavigationContribution {
        override fun getContributionId(): String = "ai"
        override fun getTitle(context: Context): String = "AI助手"
        override fun getIconResId(): Int = 0
        override fun getOrder(): Int = 40
        override fun getSlot(): NavigationSlot = NavigationSlot.BOTTOM_NAV
        override fun createFragment(context: Context): Fragment = AiFragment()
    }

    private class AiToolsGridContribution : ModuleNavigationContribution {
        override fun getContributionId(): String = "ai_tools"
        override fun getTitle(context: Context): String = "AI助手"
        override fun getIconResId(): Int = 0
        override fun getOrder(): Int = 40
        override fun getSlot(): NavigationSlot = NavigationSlot.TOOLS_GRID
        override fun createFragment(context: Context): Fragment = AiFragment()
    }
}
