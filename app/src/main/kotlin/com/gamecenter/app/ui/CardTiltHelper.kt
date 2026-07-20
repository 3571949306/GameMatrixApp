package com.gamecenter.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.google.android.material.card.MaterialCardView

/**
 * Batch 8-2 (CARD_TILT_ANIM): 游戏卡片触摸抬升动效助手
 *
 * 给 [MaterialCardView] 绑定按下抬升 + 释放回弹的轻量动效：
 * - 按下：elevation -> PRESSED_ELEVATION，scaleX/scaleY -> PRESSED_SCALE
 * - 释放：elevation -> 原 elevation，scaleX/scaleY -> 1f（OvershootInterpolator 回弹）
 *
 * 使用方式：
 * ```
 * CardTiltHelper.attach(cardView)
 * ```
 *
 * 注意：调用方需保证 [attach] 仅调用一次；本助手接管触摸事件，
 * 在 ACTION_UP 时若坐标仍在卡片内会调用 `view.performClick()`，
 * 以保证既有的 OnClickListener 仍能正常触发。
 */
object CardTiltHelper {

    private const val PRESSED_ELEVATION = 12f
    private const val PRESSED_SCALE = 1.03f
    private const val PRESS_DURATION = 120L
    private const val RELEASE_DURATION = 220L
    private const val OVERSHOOT_TENSION = 1.6f
    private const val TOUCH_SLOP = 24

    private val UNIT = Any()

    /**
     * 给目标卡片绑定触摸抬升动效。仅绑定一次，重复调用会被忽略。
     */
    fun attach(card: MaterialCardView) {
        if (card.getTag() === UNIT) return
        card.setTag(UNIT)
        val baseElevation = card.cardElevation
        var downX = 0f
        var downY = 0f
        card.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    animatePress(v, baseElevation)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    animateRelease(v, baseElevation)
                    // 在容差范围内才视为点击，避免拖动误触
                    val dx = Math.abs(event.x - downX)
                    val dy = Math.abs(event.y - downY)
                    if (dx <= TOUCH_SLOP && dy <= TOUCH_SLOP) {
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    animateRelease(v, baseElevation)
                    true
                }
                else -> false
            }
        }
    }

    private fun animatePress(v: View, baseElevation: Float) {
        val sx = ObjectAnimator.ofFloat(v, View.SCALE_X, PRESSED_SCALE)
        val sy = ObjectAnimator.ofFloat(v, View.SCALE_Y, PRESSED_SCALE)
        val el = ObjectAnimator.ofFloat(v, View.TRANSLATION_Z, PRESSED_ELEVATION - baseElevation)
        AnimatorSet().apply {
            playTogether(sx, sy, el)
            duration = PRESS_DURATION
            interpolator = OvershootInterpolator(OVERSHOOT_TENSION * 0.6f)
            start()
        }
    }

    private fun animateRelease(v: View, baseElevation: Float) {
        val sx = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f)
        val sy = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f)
        val el = ObjectAnimator.ofFloat(v, View.TRANSLATION_Z, 0f)
        AnimatorSet().apply {
            playTogether(sx, sy, el)
            duration = RELEASE_DURATION
            interpolator = OvershootInterpolator(OVERSHOOT_TENSION)
            start()
        }
    }
}
