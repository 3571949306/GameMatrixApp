package com.gamecenter.app.browser.ui

import android.content.Context
import androidx.fragment.app.Fragment
import com.gamecenter.app.R
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.core.common.ModuleInterface
import com.gamecenter.app.core.common.ModuleNavigationContribution
import com.gamecenter.app.core.common.NavigationSlot

/**
 * 浏览器模块入口点。
 *
 * 通过模块系统加载浏览器 Fragment。
 */
class BrowserModuleEntryPoint : ModuleInterface, FeatureModule {

    private var running = false

    override fun init(context: Context) {}

    override fun start(context: Context) {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun getId(): String = "browser"

    override fun getName(): String = "浏览器"

    override fun getVersion(): String = "1.4.1"

    override fun getDescription(): String = "内置浏览器，支持网页浏览、历史记录、收藏夹、下载管理。"

    override fun isRunning(): Boolean = running

    override fun createFragment(context: Context): Fragment {
        return BrowserFragment()
    }

    override fun getNavigationContributions(context: Context): List<ModuleNavigationContribution> {
        return listOf(BrowserNavContribution())
    }

    private class BrowserNavContribution : ModuleNavigationContribution {
        override fun getContributionId(): String = "browser"
        override fun getTitle(context: Context): String = "浏览器"
        override fun getIconResId(): Int = R.drawable.ic_browser
        override fun getOrder(): Int = 20
        override fun getSlot(): NavigationSlot = NavigationSlot.BOTTOM_NAV
        override fun createFragment(context: Context): Fragment = BrowserFragment()
    }
}
