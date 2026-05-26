package com.gamecenter.app.modules

import android.content.Context
import androidx.fragment.app.Fragment
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.core.common.ModuleInterface
import com.gamecenter.app.fragments.BrowserFragment

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

    override fun getVersion(): String = "1.0.0"

    override fun getDescription(): String = "内置浏览器，支持多标签页、书签管理、文件下载和桌面/移动模式切换。"

    override fun isRunning(): Boolean = running

    override fun createFragment(context: Context): Fragment {
        return BrowserFragment()
    }
}
