package com.gamecenter.app.ui.onboarding

import com.gamecenter.app.R

/**
 * 斗地主新手引导序列定义。
 *
 * 4 步引导（Spec §6 / 设计 §5.6）：
 * 1. 出牌区（手牌区）—— 教用户怎么选牌
 * 2. 地主牌（顶部状态栏的地主指示器）—— 提示地主先出牌
 * 3. 操作按钮（出牌/不要/提示）—— 主要操作入口
 * 4. 记牌器（桌面右上区域，由 TableView 渲染）—— 算牌辅助
 *
 * 目标 View id 全部来自 [R.id]，与 activity_doudizhu.xml 中的声明对应。
 *
 * 持久化 key：[STORAGE_KEY] —— 写入 SharedPreferences("onboarding")，
 * 由 [CoachmarkSequence] 在完成/跳过时自动标记。
 */
object DoudizhuOnboarding {

    /** 持久化完成态的 key（SharedPreferences "onboarding" 文件内） */
    const val STORAGE_KEY = "onboarding_doudizhu_completed"

    /** 4 步引导序列（@JvmField 让 Java 端能直接当字段访问，避免走 getter） */
    @JvmField
    val steps: List<CoachmarkStep> = listOf(
        CoachmarkStep(
            targetViewId = R.id.handCardArea,
            title = "出牌区",
            description = "这里是你的手牌区，点击牌即可选中要出的牌，再次点击取消选中。",
            shape = CoachmarkShape.ROUNDED_RECT,
        ),
        CoachmarkStep(
            targetViewId = R.id.tvLandlordIndicator,
            title = "地主标识",
            description = "地主拥有先出牌权，并且多拿 3 张底牌。注意配合队友防守地主。",
            shape = CoachmarkShape.CIRCLE,
        ),
        CoachmarkStep(
            targetViewId = R.id.buttonContainer,
            title = "操作按钮",
            description = "出牌 / 不要 / 提示 三个核心按钮。不确定怎么出时点「提示」让 AI 建议。",
            shape = CoachmarkShape.ROUNDED_RECT,
        ),
        CoachmarkStep(
            targetViewId = R.id.tableView,
            title = "记牌器",
            description = "桌面右上角的记牌器帮你追踪剩余牌型，是算牌利器。",
            shape = CoachmarkShape.ROUNDED_RECT,
        ),
    )
}
