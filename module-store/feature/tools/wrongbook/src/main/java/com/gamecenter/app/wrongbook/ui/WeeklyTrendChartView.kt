package com.gamecenter.app.wrongbook.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.gamecenter.app.wrongbook.R
import java.text.SimpleDateFormat
import java.util.*

class WeeklyTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0DCE3")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private var data = intArrayOf(2, 4, 3, 7, 5, 8, 6)
    private var labels = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    init {
        linePaint.color = ContextCompat.getColor(context, R.color.wrongbook_primary)
        pointPaint.color = ContextCompat.getColor(context, R.color.wrongbook_primary)
        textPaint.color = ContextCompat.getColor(context, R.color.wrongbook_text_secondary)
        
        // 生成过去7天的日期标签
        val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val listLabels = mutableListOf<String>()
        for (i in 0 until 7) {
            listLabels.add(0, sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        labels = listLabels.toTypedArray()
    }

    fun setWeeklyData(weeklyCounts: IntArray) {
        if (weeklyCounts.size == 7) {
            data = weeklyCounts
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val paddingLeft = 60f
        val paddingRight = 60f
        val paddingTop = 40f
        val paddingBottom = 60f

        val chartWidth = w - paddingLeft - paddingRight
        val chartHeight = h - paddingTop - paddingBottom

        val maxVal = (data.maxOrNull() ?: 1).coerceAtLeast(5)
        val stepX = chartWidth / 6f

        val points = mutableListOf<PointF>()
        for (i in 0 until 7) {
            val x = paddingLeft + i * stepX
            val y = h - paddingBottom - (data[i].toFloat() / maxVal * chartHeight)
            points.add(PointF(x, y))
        }

        // 绘制虚线网格线 (横向3条)
        val gridSteps = 3
        for (i in 0..gridSteps) {
            val y = paddingTop + i * (chartHeight / gridSteps)
            canvas.drawLine(paddingLeft, y, w - paddingRight, y, gridPaint)
        }

        // 绘制面积阴影渐变
        val pathFill = Path()
        pathFill.moveTo(points[0].x, h - paddingBottom)
        for (i in 0 until 7) {
            pathFill.lineTo(points[i].x, points[i].y)
        }
        pathFill.lineTo(points[6].x, h - paddingBottom)
        pathFill.close()

        val primaryColor = ContextCompat.getColor(context, R.color.wrongbook_primary)
        val gradient = LinearGradient(
            0f, paddingTop, 0f, h - paddingBottom,
            Color.argb(80, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
            Color.argb(0, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = gradient
        canvas.drawPath(pathFill, fillPaint)

        // 绘制折线
        val pathLine = Path()
        pathLine.moveTo(points[0].x, points[0].y)
        for (i in 1 until 7) {
            pathLine.lineTo(points[i].x, points[i].y)
        }
        canvas.drawPath(pathLine, linePaint)

        // 绘制折点与文本标签
        for (i in 0 until 7) {
            val p = points[i]
            // 折点
            canvas.drawCircle(p.x, p.y, 10f, pointPaint)
            
            // 绘制数据标签数字
            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.wrongbook_text_primary)
                textSize = 24f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(data[i].toString(), p.x, p.y - 18f, numPaint)

            // 绘制底部日期 X 轴标签
            canvas.drawText(labels[i], p.x, h - 16f, textPaint)
        }
    }
}
