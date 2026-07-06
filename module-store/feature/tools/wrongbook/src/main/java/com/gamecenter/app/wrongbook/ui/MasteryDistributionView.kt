package com.gamecenter.app.wrongbook.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.gamecenter.app.wrongbook.R

class MasteryDistributionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
    }

    private var distribution = intArrayOf(5, 3, 4, 2) // 未掌握, 模糊, 熟悉, 熟练
    private val labels = arrayOf("未掌握 (0-30%)", "模糊 (30-60%)", "基本掌握 (60-80%)", "完全掌握 (80-100%)")
    private val colors = intArrayOf(
        R.color.wrongbook_mastery_low,
        R.color.wrongbook_mastery_mid,
        R.color.wrongbook_primary,
        R.color.wrongbook_mastery_high
    )

    fun setDistribution(dist: IntArray) {
        if (dist.size == 4) {
            distribution = dist
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val paddingLeft = 30f
        val paddingRight = 80f
        val paddingTop = 20f
        val paddingBottom = 20f

        val totalHeight = h - paddingTop - paddingBottom
        val itemHeight = totalHeight / 4f
        val maxVal = (distribution.maxOrNull() ?: 1).coerceAtLeast(1)

        // 绘制每一行
        for (i in 0 until 4) {
            val y = paddingTop + i * itemHeight
            val count = distribution[i]
            
            // 绘制标签文本
            textPaint.color = ContextCompat.getColor(context, R.color.wrongbook_text_primary)
            canvas.drawText(labels[i], paddingLeft, y + 26f, textPaint)

            // 绘制进度条背景
            val barBgLeft = paddingLeft + 220f
            val barWidth = w - barBgLeft - paddingRight
            bgPaint.color = ContextCompat.getColor(context, R.color.wrongbook_surface_variant)
            canvas.drawRoundRect(
                RectF(barBgLeft, y + 8f, barBgLeft + barWidth, y + 26f),
                9f, 9f, bgPaint
            )

            // 绘制进度条填充
            val progressWidth = if (maxVal > 0) (count.toFloat() / maxVal * barWidth) else 0f
            if (progressWidth > 0) {
                barPaint.color = ContextCompat.getColor(context, colors[i])
                canvas.drawRoundRect(
                    RectF(barBgLeft, y + 8f, barBgLeft + progressWidth, y + 26f),
                    9f, 9f, barPaint
                )
            }

            // 绘制数量文字
            val countText = "${count}题"
            textPaint.color = ContextCompat.getColor(context, R.color.wrongbook_text_secondary)
            canvas.drawText(countText, barBgLeft + barWidth + 12f, y + 26f, textPaint)
        }
    }
}
