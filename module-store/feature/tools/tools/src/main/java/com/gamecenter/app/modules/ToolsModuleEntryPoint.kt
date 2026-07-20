package com.gamecenter.app.modules

import android.content.Context
import androidx.fragment.app.Fragment
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.core.common.ModuleInterface
import com.gamecenter.app.core.common.ModuleNavigationContribution
import com.gamecenter.app.core.common.NavigationSlot
import com.gamecenter.app.fragments.ToolsFragment

class ToolsModuleEntryPoint : ModuleInterface, FeatureModule {

    private var running = false

    override fun init(context: Context) {}

    override fun start(context: Context) {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun getId(): String = "tools"

    override fun getName(): String = "工具箱"

    override fun getVersion(): String = "1.0.0"

    override fun getDescription(): String = "网络诊断、DNS查询、二维码扫描、电池信息、设备信息、传感器等20+实用工具。"

    override fun isRunning(): Boolean = running

    override fun createFragment(context: Context): Fragment {
        return ToolsFragment()
    }

    override fun getNavigationContributions(context: Context): List<ModuleNavigationContribution> {
        return listOf(ToolsNavContribution())
    }

    private class ToolsNavContribution : ModuleNavigationContribution {
        override fun getContributionId(): String = "tools"
        override fun getTitle(context: Context): String = "工具箱"
        override fun getIconResId(): Int = 0
        override fun getOrder(): Int = 30
        override fun getSlot(): NavigationSlot = NavigationSlot.BOTTOM_NAV
        override fun createFragment(context: Context): Fragment = ToolsFragment()
    }
}
