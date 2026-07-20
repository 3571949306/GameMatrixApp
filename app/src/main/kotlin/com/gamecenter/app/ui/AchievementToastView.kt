package com.gamecenter.app.ui

import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.gamecenter.app.R

/**
 * Batch 8-3 (ACHIEVEMENT_TOAST): 成就解锁顶部浮层
 *
 * 自定义浮层挂到 Activity 的 DecorView 顶部，滑入动效 + 图标 + 标题 + 描述，3 秒后自动滑出。
 *
 * 使用方式：
 * ```
 * AchievementToastView.show(activity, "首次胜利", "恭喜你赢得了第一局对局")
 * ```
 *
 * 设计要点：
 * - 使用 Application Context 持有引用，避免 Activity 泄漏；移除时立即清空
 * - 浮层自身不接收触摸事件（setClickable(false)），避免遮挡底层交互
 * - 自动消失时间 [DISMISS_DELAY_MS]，滑出动画时长 [OUT_DURATION_MS]
 * - 同一时刻只显示一个浮层，新调用会先移除旧的
 */
class AchievementToastView private constructor(
    private val context: Context
) {

    private val handler = Handler(Looper.getMainLooper())
    private var currentToast: View? = null
    private var currentAnimator: ObjectAnimator? = null

    /**
     * 在当前 Activity 顶部展示成就浮层。
     *
     * @param title 主标题，例如"成就解锁"
     * @param description 副标题，例如"恭喜赢得首胜"
     * @param iconRes 图标资源，默认使用星星图标
     */
    fun show(title: String, description: String, iconRes: Int = R.drawable.ic_star_filled) {
        // 先移除已有浮层
        dismissImmediate()

        val activity = findActivity(context) ?: return
        val decorView = activity.window.decorView as? ViewGroup ?: return
        val toastView = buildToastView(activity, title, description, iconRes)

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(activity, STATUS_BAR_TOP_OFFSET_DP)
            marginStart = dpToPx(activity, 16)
            marginEnd = dpToPx(activity, 16)
        }

        decorView.addView(toastView, params)
        currentToast = toastView

        // 初始位移到屏幕外，再滑入
        toastView.translationY = -dpToPx(activity, SLIDE_DISTANCE_DP).toFloat()
        toastView.alpha = 0f
        val slideIn = ObjectAnimator.ofFloat(
            toastView, View.TRANSLATION_Y,
            -dpToPx(activity, SLIDE_DISTANCE_DP).toFloat(), 0f
        ).apply {
            duration = IN_DURATION_MS
        }
        val fadeIn = ObjectAnimator.ofFloat(toastView, View.ALPHA, 0f, 1f).apply {
            duration = IN_DURATION_MS
        }
        slideIn.start()
        fadeIn.start()
        currentAnimator = slideIn

        // DISMISS_DELAY_MS 后自动滑出
        handler.postDelayed({
            slideOut(toastView) {
                decorView.removeView(toastView)
                currentToast = null
            }
        }, DISMISS_DELAY_MS)
    }

    /** 立即移除当前浮层（不播滑出动画）。 */
    fun dismissImmediate() {
        currentToast?.let { toast ->
            currentAnimator?.cancel()
            (toast.parent as? ViewGroup)?.removeView(toast)
            currentToast = null
        }
        handler.removeCallbacksAndMessages(null)
    }

    private fun slideOut(view: View, onEnd: () -> Unit) {
        val slideOut = ObjectAnimator.ofFloat(
            view, View.TRANSLATION_Y,
            0f, -dpToPx(view.context, SLIDE_DISTANCE_DP).toFloat()
        ).apply {
            duration = OUT_DURATION_MS
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd()
                }
            })
        }
        val fadeOut = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f).apply {
            duration = OUT_DURATION_MS
        }
        slideOut.start()
        fadeOut.start()
        currentAnimator = slideOut
    }

    private fun buildToastView(
        context: Context,
        title: String,
        description: String,
        iconRes: Int
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_achievement_toast)
            setPadding(dpToPx(context, 16), dpToPx(context, 12), dpToPx(context, 16), dpToPx(context, 12))
            elevation = dpToPx(context, 8).toFloat()
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }

        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(Color.WHITE)
            val size = dpToPx(context, 28)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(context, 12)
            }
            contentDescription = context.getString(R.string.achievement_toast_icon_desc)
        }
        container.addView(icon)

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        val tvTitle = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val tvDesc = TextView(context).apply {
            text = description
            setTextColor(ContextCompat.getColor(context, R.color.achievement_toast_desc))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textLayout.addView(tvTitle)
        textLayout.addView(tvDesc)
        container.addView(textLayout)

        // "成就" 标签徽章
        val badge = TextView(context).apply {
            text = context.getString(R.string.achievement_toast_badge)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setBackgroundResource(R.drawable.bg_achievement_badge)
            val padH = dpToPx(context, 8)
            val padV = dpToPx(context, 3)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(context, 8)
            }
        }
        container.addView(badge)

        return container
    }

    private fun dpToPx(context: Context, dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()

    private fun findActivity(context: Context): android.app.Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    companion object {
        private const val IN_DURATION_MS = 280L
        private const val OUT_DURATION_MS = 240L
        private const val DISMISS_DELAY_MS = 3000L
        private const val SLIDE_DISTANCE_DP = 96
        private const val STATUS_BAR_TOP_OFFSET_DP = 48

        @Volatile private var instance: AchievementToastView? = null

        @JvmStatic
        fun getInstance(context: Context): AchievementToastView =
            instance ?: synchronized(this) {
                instance ?: AchievementToastView(context.applicationContext).also { instance = it }
            }

        /**
         * 便捷调用：在当前 Activity 顶部展示成就浮层。
         */
        @JvmStatic
        @JvmOverloads
        fun show(context: Context, title: String, description: String, iconRes: Int = R.drawable.ic_star_filled) {
            getInstance(context).show(title, description, iconRes)
        }
    }
}
