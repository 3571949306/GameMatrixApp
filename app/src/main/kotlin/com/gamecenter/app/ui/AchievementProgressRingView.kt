package com.gamecenter.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import com.gamecenter.app.R

/**
 * Batch 10-4 (ACHIEVEMENT_PROGRESS_RING): 成就总览圆环进度头部自定义 View。
 *
 * 绘制：
 * - 外圈：SweepGradient 渐变进度环（基于 [progressPercent]）
 * - 内圈：背景圆环（底色）
 * - 中心：百分比大字 + "Lv.X" 等级文案 + "已解锁 / 总数" 文案
 *
 * 调用 [setData] 更新进度，调用 [setUnlockedAndTotal] 设置数量与等级文案。
 *
 * 设计要点：
 * - 自适应正方形尺寸（取 width / height 较小值作为直径）
 * - 主题感知：通过 `?attr/colorSurface` 与 `?attr/colorPrimary` 取主题色，自动适配浅/深色
 * - 默认尺寸 160dp，可在 XML 中通过 layout_width/height 覆盖
 */
class AchievementProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 24f
        color = 0x33888888 // 半透明灰色底环（在浅/深色都可见）
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 24f
        strokeCap = Paint.Cap.ROUND
    }

    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 40f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f
    }

    private val rectF = RectF()

    private var progressPercent: Int = 0
    private var unlockedCount: Int = 0
    private var totalCount: Int = 0
    private var levelText: String = ""
    private var percentText: String = "0%"
    private var countText: String = "0 / 0"

    init {
        // 主题感知：通过 TypedValue 读取当前主题的 colorPrimary / colorOnSurfaceVariant
        // 注意：不能直接引用 com.google.android.material.R.attr.colorPrimary，否则 Kotlin 编译期报 Unresolved
        val tv = android.util.TypedValue()
        val primaryColor = if (context.theme.resolveAttribute(
                android.R.attr.colorPrimary, tv, true)) {
            tv.data
        } else 0xFF5C6BC0.toInt()

        val tv2 = android.util.TypedValue()
        val variantColor = if (context.theme.resolveAttribute(
                android.R.attr.textColorSecondary, tv2, true)) {
            tv2.data
        } else 0xFF888888.toInt()

        percentPaint.color = primaryColor
        levelPaint.color = primaryColor
        countPaint.color = variantColor
    }

    /**
     * 设置进度数据。会同步更新中心文案。
     *
     * @param unlocked 已解锁数
     * @param total 总数
     */
    fun setUnlockedAndTotal(unlocked: Int, total: Int) {
        unlockedCount = unlocked
        totalCount = total
        val percent = if (total > 0) (unlocked.toFloat() / total * 100).toInt() else 0
        progressPercent = percent.coerceIn(0, 100)
        percentText = context.getString(R.string.achievement_ring_percent_format, progressPercent)
        countText = context.getString(R.string.achievement_ring_progress_format, unlocked, total)
        levelText = computeLevelText(percent)
        invalidate()
    }

    /**
     * 根据百分比计算等级文案。
     * 0-19% 新手 / 20-39% 探索者 / 40-59% 专家 / 60-79% 大师 / 80-100% 传奇
     * 等级数字：percent / 20 + 1（1~5）
     */
    private fun computeLevelText(percent: Int): String {
        val level = (percent / 20) + 1
        val levelNameRes = when {
            percent >= 80 -> R.string.achievement_ring_level_legend
            percent >= 60 -> R.string.achievement_ring_level_master
            percent >= 40 -> R.string.achievement_ring_level_expert
            percent >= 20 -> R.string.achievement_ring_level_explorer
            else -> R.string.achievement_ring_level_beginner
        }
        val levelName = context.getString(levelNameRes)
        return context.getString(R.string.achievement_ring_level_format, level) + " · " + levelName
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // 默认 160dp 正方形，若父布局给了确切尺寸则以父布局为准
        val defaultSize = (160 * resources.displayMetrics.density).toInt()
        val w = resolveSize(defaultSize, widthMeasureSpec)
        val h = resolveSize(defaultSize, heightMeasureSpec)
        val size = minOf(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - bgPaint.strokeWidth / 2f - 8f
        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 1. 底环
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // 2. 进度环（带 SweepGradient）
        if (progressPercent > 0) {
            val sweepAngle = 360f * progressPercent / 100f
            val colors = intArrayOf(
                0xFF5C6BC0.toInt(),
                0xFF7E57C2.toInt(),
                0xFFFF7043.toInt()
            )
            val positions = floatArrayOf(0f, 0.5f, 1f)
            val gradient = SweepGradient(cx, cy, colors, positions)
            progressPaint.shader = gradient
            canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)
        }

        // 3. 中心百分比
        canvas.drawText(percentText, cx, cy + 12f, percentPaint)

        // 4. 等级文案（百分比上方）
        canvas.drawText(levelText, cx, cy - 18f, levelPaint)

        // 5. 数量文案（百分比下方）
        canvas.drawText(countText, cx, cy + 40f, countPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressPaint.shader = null
    }
}
