package com.gamecenter.app.modules

import android.content.Context
import androidx.fragment.app.Fragment
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.core.common.ModuleInterface
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
}
