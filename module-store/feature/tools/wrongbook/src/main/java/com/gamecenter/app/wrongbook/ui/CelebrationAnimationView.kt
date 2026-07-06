package com.gamecenter.app.wrongbook.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.*

class CelebrationAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = mutableListOf<Particle>()
    private val random = Random()

    private val colors = intArrayOf(
        0xFFFF5722.toInt(), // Orange
        0xFFFFE800.toInt(), // Yellow
        0xFF4CAF50.toInt(), // Green
        0xFF00BCD4.toInt(), // Cyan
        0xFF2196F3.toInt(), // Blue
        0xFFE91E63.toInt(), // Pink
        0xFF9C27B0.toInt()  // Purple
    )

    fun startConfetti() {
        val cx = width.toFloat() / 2f
        val cy = height.toFloat() / 2f
        if (cx <= 0 || cy <= 0) return

        particles.clear()
        
        // 生成 100 个彩色碎纸屑粒子
        for (i in 0 until 100) {
            val angle = random.nextDouble() * 2 * Math.PI
            val speed = 5f + random.nextFloat() * 15f
            particles.add(
                Particle(
                    x = cx,
                    y = cy,
                    vx = (Math.cos(angle) * speed).toFloat(),
                    vy = (Math.sin(angle) * speed - 5f).toFloat(), // 稍微往上喷射
                    color = colors[random.nextInt(colors.size)],
                    size = 8f + random.nextFloat() * 12f,
                    alpha = 255
                )
            )
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (particles.isEmpty()) return

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            paint.color = p.color
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.size, paint)

            // 更新物理状态
            p.x += p.vx
            p.y += p.vy
            p.vy += 0.4f // 重力加速度
            p.alpha -= 4  // 渐隐

            if (p.alpha <= 0 || p.y > height) {
                iterator.remove()
            }
        }

        if (particles.isNotEmpty()) {
            postInvalidateDelayed(16) // 约 60 帧刷新
        }
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        val vx: Float,
        var vy: Float,
        val color: Int,
        val size: Float,
        var alpha: Int
    )
}
