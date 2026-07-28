package com.gamecenter.app.ui.onboarding

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.gamecenter.app.ui.theme.GameMatrixTheme

/**
 * Coachmark 序列管理器。
 *
 * 职责：
 * 1. 持有一组 [CoachmarkStep]，管理当前步骤索引
 * 2. 通过 [ComposeView] 把 [CoachmarkOverlay] 挂到 Activity 的 decorView 上
 *    （作为 [WindowManager.LayoutParams.TYPE_APPLICATION_PANEL] 浮层，覆盖在所有 View 之上）
 * 3. 渐进揭示：当前步高亮，其余遮罩（由 [CoachmarkOverlay] 内部处理）
 * 4. 完成后回调 [onComplete] + 持久化完成态到 [SharedPreferences]
 *    （文件名 "onboarding"，key 由调用方传入）
 * 5. 跳过时回调 [onSkipped] + 同样持久化（默认视为已完成，避免反复弹出）
 *
 * 用法：
 * ```
 * CoachmarkSequence(activity, DoudizhuOnboarding.steps, "onboarding_doudizhu_completed")
 *     .start()
 * ```
 *
 * 注意：
 * - 不在 Activity 的 onCreate 里立刻调用 [start]，需等目标 View 完成布局
 *   （建议用 window.decorView.post { } 或延迟 200ms）
 * - Activity 销毁时自动清理浮层（[DisposableEffect]）
 */
class CoachmarkSequence @JvmOverloads constructor(
    private val activity: Activity,
    private val steps: List<CoachmarkStep>,
    private val storageKey: String,
    private val onComplete: () -> Unit = {},
    private val onSkipped: () -> Unit = {},
) {
    /** 持久化完成态用的 SharedPreferences 文件名（Spec §6：免登录 / 设备本地） */
    private val prefsName = "onboarding"

    /** 是否已完成（设备级持久化，无账号） */
    fun isCompleted(): Boolean {
        return activity.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getBoolean(storageKey, false)
    }

    /** 标记完成（手动重置入口可调用 [reset] 后再 [start]） */
    fun markCompleted() {
        activity.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(storageKey, true)
            .apply()
    }

    /** 重置完成态，下次再 [start] 会真正弹出 */
    fun reset() {
        activity.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(storageKey, false)
            .apply()
    }

    /**
     * 启动引导序列。若已标记完成则不弹出（调用方仍可主动 [reset] 后再调用）。
     */
    fun start() {
        if (isCompleted() || steps.isEmpty()) return
        showStep(0)
    }

    private var overlayView: ComposeView? = null

    private fun showStep(index: Int) {
        showStepWithRetry(index, retryCount = 0)
    }

    private fun showStepWithRetry(index: Int, retryCount: Int) {
        if (index >= steps.size) {
            finish(completed = true)
            return
        }
        // P0 内存泄漏修复：Activity 销毁后不再执行重试，避免 postDelayed lambda 持有已销毁的 Activity
        if (activity.isFinishing || activity.isDestroyed) return
        val step = steps[index]
        val target = activity.findViewById<View>(step.targetViewId)
        // 目标 View 还没布局好或找不到，等一帧再试（最多 20 次 ≈ 1.2s）
        // 上限是为了防止目标 View 永远 GONE 时无限重试
        if (target == null || target.width == 0 || target.height == 0) {
            if (retryCount >= 20) {
                // 找不到目标，跳过当前步骤继续下一步
                showStepWithRetry(index + 1, retryCount = 0)
                return
            }
            activity.window.decorView.postDelayed({
                // P0 内存泄漏修复：重试前检查 Activity 是否已销毁
                if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                showStepWithRetry(index, retryCount + 1)
            }, 60)
            return
        }
        showStepInternal(index, step, target)
    }

    private fun showStepInternal(index: Int, step: CoachmarkStep, target: View) {

        // 移除上一步的 ComposeView（如果有）
        overlayView?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }

        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                GameMatrixTheme {
                    SequenceHost(
                        activity = activity,
                        step = step,
                        currentStepIndex = index,
                        totalSteps = steps.size,
                        onNext = { showStep(index + 1) },
                        onSkip = { finish(completed = false) },
                    )
                }
            }
        }
        overlayView = composeView

        // 用 WindowManager 加为系统级浮层（覆盖在 decorView 之上，能拿到全屏坐标）
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            format = android.graphics.PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            token = activity.window.decorView.windowToken
        }
        try {
            activity.windowManager.addView(composeView, params)
        } catch (e: Exception) {
            // 部分 Activity 的 window token 还未就绪，回退到 decorView.addView
            // decorView 本身就是 FrameLayout，这里强转 ViewGroup 添加浮层
            (activity.window.decorView as? android.view.ViewGroup)?.addView(
                composeView,
                android.widget.FrameLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun finish(completed: Boolean) {
        overlayView?.let { view ->
            (view.parent as? android.view.ViewGroup)?.removeView(view)
        }
        overlayView = null
        if (completed) {
            markCompleted()
            onComplete()
        } else {
            // 跳过也标记完成，避免每次进入游戏都弹（无账号场景下重复弹很烦人）
            markCompleted()
            onSkipped()
        }
    }

    /** Activity 销毁时务必调用，避免 window 泄漏 */
    fun destroy() {
        // P0 内存泄漏修复：destroy 后重试 lambda 内的 isFinishing/isDestroyed 检查会短路，
        // 不会继续执行；此处只需清理 overlayView。
        overlayView?.let { view ->
            (view.parent as? android.view.ViewGroup)?.removeView(view)
        }
        overlayView = null
    }
}

/**
 * 把目标 View 的屏幕坐标换算成 Compose 坐标系下的 [Rect]，传给 [CoachmarkOverlay]。
 *
 * 关键点：
 * - LocalView 拿到的是 ComposeView（即 overlayView），它与目标 View 同属一个 decorView，
 *   所以两者屏幕坐标可以直接相减换算
 * - 减去状态栏偏移：target 在屏幕上的 location[1] 包含状态栏高度，
 *   而 overlay 的 Compose 坐标系从 (0,0) 开始（因为 decorFitsSystemWindows=false）
 */
@Composable
private fun SequenceHost(
    activity: Activity,
    step: CoachmarkStep,
    currentStepIndex: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val overlayView = LocalView.current
    var targetRect by remember(step.targetViewId) {
        mutableStateOf(Rect(0f, 0f, 0f, 0f))
    }
    var resolved by remember(step.targetViewId) { mutableStateOf(false) }

    // 用 LaunchedEffect 等下一帧再读 View 位置，确保 measure/layout 完成
    LaunchedEffect(step.targetViewId) {
        overlayView.post {
            val target = activity.findViewById<View>(step.targetViewId) ?: return@post
            val overlayLoc = IntArray(2).also { overlayView.getLocationOnScreen(it) }
            val targetLoc = IntArray(2).also { target.getLocationOnScreen(it) }
            val left = (targetLoc[0] - overlayLoc[0]).toFloat()
            val top = (targetLoc[1] - overlayLoc[1]).toFloat()
            targetRect = Rect(
                left = left,
                top = top,
                right = left + target.width,
                bottom = top + target.height,
            )
            resolved = true
        }
    }

    val reduceMotion = remember {
        android.provider.Settings.Global.getFloat(
            activity.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    if (resolved) {
        CoachmarkOverlay(
            targetRect = targetRect,
            step = step,
            currentStepIndex = currentStepIndex,
            totalSteps = totalSteps,
            reduceMotion = reduceMotion,
            onNext = onNext,
            onSkip = onSkip,
        )
    }
}
