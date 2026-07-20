package com.gamecenter.app.ui.onboarding

/**
 * Coachmark 单步数据模型。
 *
 * @property targetViewId 目标 View 的 R.id.xxx。运行时通过 [android.app.Activity.findViewById]
 *           解析为 [android.view.View]，再读取其在屏幕上的位置矩形作为"挖洞"区域。
 * @property title 步骤标题（短句，建议 ≤ 8 字）。
 * @property description 步骤说明（1-2 句话，告诉用户该区域怎么用）。
 * @property shape 高亮区形状：[CoachmarkShape.CIRCLE] 适合图标/按钮等近方形目标；
 *                 [CoachmarkShape.ROUNDED_RECT] 适合卡片、面板等长条形目标。
 */
data class CoachmarkStep(
    val targetViewId: Int,
    val title: String,
    val description: String,
    val shape: CoachmarkShape = CoachmarkShape.CIRCLE,
)

/**
 * Coachmark 高亮区形状。
 *
 * CIRCLE —— 以目标 View 中心为圆心、外接圆半径加 padding 为半径画圆并描边。
 *           视觉聚焦感强，适合图标/按钮。
 * ROUNDED_RECT —— 以目标 View 矩形外扩 padding 后画圆角矩形并描边。
 *                 适合卡片、列表、面板。
 */
enum class CoachmarkShape {
    CIRCLE,
    ROUNDED_RECT,
}
