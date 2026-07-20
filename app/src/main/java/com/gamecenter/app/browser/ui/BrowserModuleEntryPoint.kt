package com.gamecenter.app.browser.ui

import android.content.Context
import androidx.fragment.app.Fragment
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.core.common.ModuleInterface

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
}
