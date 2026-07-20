package com.gamecenter.app.wrongbook

import android.content.Context
import androidx.fragment.app.Fragment
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.core.common.ModuleInterface
import com.gamecenter.app.core.common.ModuleNavigationContribution
import com.gamecenter.app.core.common.NavigationSlot
import com.gamecenter.app.wrongbook.ui.WrongBookFragment

/**
 * AI 错题本模块入口。
 *
 * 通过模块商店或预装方式加载后，向宿主暴露一个主 Fragment。
 */
class WrongBookModuleEntryPoint : ModuleInterface, FeatureModule {

    private var running = false

    override fun init(context: Context) = Unit

    override fun start(context: Context) {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun getId(): String = "wrongbook"

    override fun getName(): String = "错题本"

    override fun getVersion(): String = "1.0.0"

    override fun getDescription(): String =
        "基于 AI 的错题整理与复习工具，支持拍照识题、AI 分析、艾宾浩斯复习计划。"

    override fun isRunning(): Boolean = running

    override fun createFragment(context: Context): Fragment = WrongBookFragment()

    override fun getNavigationContributions(context: Context): List<ModuleNavigationContribution> {
        return listOf(WrongBookNavContribution())
    }

    private class WrongBookNavContribution : ModuleNavigationContribution {
        override fun getContributionId(): String = "wrongbook"
        override fun getTitle(context: Context): String = "错题本"
        override fun getIconResId(): Int = 0
        override fun getOrder(): Int = 60
        override fun getSlot(): NavigationSlot = NavigationSlot.TOOLS_GRID
        override fun createFragment(context: Context): Fragment = WrongBookFragment()
    }
}
