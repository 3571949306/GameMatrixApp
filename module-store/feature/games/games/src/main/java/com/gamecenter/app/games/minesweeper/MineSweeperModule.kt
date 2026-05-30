package com.gamecenter.app.games.minesweeper

import android.content.Context
import android.content.Intent
import com.gamecenter.app.core.common.ModuleInterface

class MineSweeperModule : ModuleInterface {

    private var initialized = false

    override fun init(context: Context) {
        initialized = true
    }

    override fun start(context: Context) {
        val intent = Intent(context, MineSweeperActivity::class.java).apply {
            putExtra("rows", 9)
            putExtra("cols", 9)
            putExtra("mines", 10)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun stop() {
        initialized = false
    }

    override fun getId(): String = "minesweeper"

    override fun getName(): String = "扫雷"

    override fun getVersion(): String = "1.0.0"

    override fun getDescription(): String = "经典扫雷游戏，支持初级/中级/高级难度"

    override fun isRunning(): Boolean = initialized
}
