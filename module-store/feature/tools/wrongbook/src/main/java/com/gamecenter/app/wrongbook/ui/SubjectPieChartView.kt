package com.gamecenter.app.wrongbook.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.gamecenter.app.wrongbook.R

class SubjectPieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = Color.DKGRAY
    }

    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var subjectsData = listOf(
        SubjectShare("语文", 5, Color.parseColor("#FF6B6B")),
        SubjectShare("数学", 10, Color.parseColor("#4D96FF")),
        SubjectShare("英语", 3, Color.parseColor("#6BCB77"))
    )

    private val defaultColors = intArrayOf(
        Color.parseColor("#FF6B6B"),
        Color.parseColor("#4D96FF"),
        Color.parseColor("#6BCB77"),
        Color.parseColor("#FFD93D"),
        Color.parseColor("#AC25E2"),
        Color.parseColor("#00C9A7"),
        Color.parseColor("#845EC2")
    )

    fun setData(dataMap: Map<String, Int>) {
        if (dataMap.isEmpty()) {
            subjectsData = emptyList()
            invalidate()
            return
        }
        val list = mutableListOf<SubjectShare>()
        var idx = 0
        dataMap.forEach { (name, count) ->
            if (count > 0) {
                val color = defaultColors[idx % defaultColors.size]
                list.add(SubjectShare(name, count, color))
                idx++
            }
        }
        subjectsData = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val total = subjectsData.sumOf { it.count }
        val cx = w * 0.35f
        val cy = h * 0.5f
        val radius = (h * 0.4f).coerceAtMost(w * 0.3f)

        if (total == 0) {
            // 绘制空环
            arcPaint.color = Color.parseColor("#E0DCE3")
            canvas.drawCircle(cx, cy, radius, arcPaint)
            arcPaint.color = ContextCompat.getColor(context, R.color.wrongbook_background)
            canvas.drawCircle(cx, cy, radius * 0.6f, arcPaint)

            centerTextPaint.color = ContextCompat.getColor(context, R.color.wrongbook_text_tertiary)
            centerTextPaint.textSize = 28f
            canvas.drawText(context.getString(R.string.wrongbook_no_data), cx, cy + 10f, centerTextPaint)
            return
        }

        val rectF = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        var startAngle = 0f

        subjectsData.forEach { item ->
            val sweep = (item.count.toFloat() / total) * 360f
            arcPaint.color = item.color
            canvas.drawArc(rectF, startAngle, sweep, true, arcPaint)
            startAngle += sweep
        }

        // 绘制内层空心圆，形成现代 Donut 图
        arcPaint.color = ContextCompat.getColor(context, R.color.wrongbook_background)
        canvas.drawCircle(cx, cy, radius * 0.62f, arcPaint)

        // 绘制圆心文字
        centerTextPaint.textSize = 24f
        centerTextPaint.color = ContextCompat.getColor(context, R.color.wrongbook_text_secondary)
        canvas.drawText(context.getString(R.string.wrongbook_total_label), cx, cy - 12f, centerTextPaint)

        centerTextPaint.textSize = 36f
        centerTextPaint.color = ContextCompat.getColor(context, R.color.wrongbook_text_primary)
        centerTextPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("$total", cx, cy + 24f, centerTextPaint)
        centerTextPaint.typeface = Typeface.DEFAULT // 恢复字体

        // 绘制右侧图例 (Legend)
        val legendLeft = w * 0.68f
        val legendStartY = cy - (subjectsData.size * 32f) / 2f
        val itemHeight = 36f

        subjectsData.forEachIndexed { i, item ->
            val y = legendStartY + i * itemHeight
            // 绘制小色块
            val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.color
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                RectF(legendLeft, y, legendLeft + 24f, y + 18f),
                6f, 6f, legendPaint
            )

            // 绘制文本
            val percent = (item.count.toFloat() / total * 100).toInt()
            val text = "${item.name} (${item.count}题, $percent%)"
            val textColPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.wrongbook_text_primary)
                textSize = 22f
            }
            canvas.drawText(text, legendLeft + 36f, y + 16f, textColPaint)
        }
    }

    data class SubjectShare(val name: String, val count: Int, val color: Int)
}
