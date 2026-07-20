package com.gamecenter.app.ui.onboarding

import com.gamecenter.app.R

/**
 * 围棋新手引导序列定义。
 *
 * 3 步引导（Spec §6 / 设计 §5.6）：
 * 1. 棋盘（GoView）—— 在交叉点落子，黑先白后
 * 2. 状态栏（tvStatus）—— 通过状态栏说明"气"的概念
 * 3. 操作按钮（btnRow：弃权/认输/重开）—— 通过按钮区说明"打吃"概念
 *
 * 持久化 key：[STORAGE_KEY]
 *
 * 注意：GoActivity 的关键 View 是动态构建的（无 R.id 引用），
 * 通过 [R.id.go_board_view] / [R.id.go_status_view] / [R.id.go_buttons_view]
 * 在 GoActivity 中用 setId() 注入到对应 View 上，CoachmarkSequence 即可定位。
 */
object GoOnboarding {

    /** 持久化完成态的 key（SharedPreferences "onboarding" 文件内） */
    const val STORAGE_KEY = "onboarding_go_completed"

    /** 3 步引导序列（@JvmField 让 Java 端能直接当字段访问） */
    @JvmField
    val steps: List<CoachmarkStep> = listOf(
        CoachmarkStep(
            targetViewId = R.id.go_board_view,
            title = "棋盘",
            description = "在交叉点落子，黑先白后。围棋目标是围地，谁占的地盘多谁赢。",
            shape = CoachmarkShape.ROUNDED_RECT,
        ),
        CoachmarkStep(
            targetViewId = R.id.go_status_view,
            title = "气",
            description = "相邻空交叉点是棋子的「气」，没气则被提子。状态栏会显示提子情况。",
            shape = CoachmarkShape.ROUNDED_RECT,
        ),
        CoachmarkStep(
            targetViewId = R.id.go_buttons_view,
            title = "打吃",
            description = "只剩一口气时叫「打吃」，对方可以提子。点「弃权」可让对方先手。",
            shape = CoachmarkShape.ROUNDED_RECT,
        ),
    )
}
