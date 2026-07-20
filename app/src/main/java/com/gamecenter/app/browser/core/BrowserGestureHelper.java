package com.gamecenter.app.browser.core;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 浏览器左右滑动手势辅助类。
 *
 * <p>实现以下行为：
 * <ul>
 *   <li>屏幕左边缘（32dp）右滑 → goBack()</li>
 *   <li>屏幕右边缘（32dp）左滑 → goForward()</li>
 *   <li>双路检测：GestureDetector.onFling + 手动 ACTION_MOVE 跟踪，避免漏检</li>
 *   <li>触发手势时显示对应方向箭头指示器作为视觉反馈 + 轻微振动反馈</li>
 *   <li>仅在 WebView canGoBack/canGoForward 时才触发，避免误触</li>
 *   <li>边缘起始位置必须落在边缘 32dp 内，但滑动距离需 ≥ 48dp 才触发</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>边缘宽度从 24dp 增至 32dp，提高可触发性</li>
 *   <li>同时使用 GestureDetector（处理快速 fling）和手动 MOVE 跟踪（处理慢速拖拽）</li>
 *   <li>触发阈值 48dp，避免轻微抖动误触</li>
 *   <li>触发后立即消费事件，防止 WebView 同时滚动</li>
 * </ul>
 */
public class BrowserGestureHelper {

    private static final String TAG = "BrowserGestureHelper";

    /** 边缘宽度（dp），仅在该范围内的水平滑动才视为前进/后退手势 */
    public static final int EDGE_WIDTH_DP = 32;
    /** fling 速度阈值（px/s） */
    private static final int FLING_VELOCITY_THRESHOLD = 400;
    /** 手动 MOVE 触发的最小位移（dp） */
    private static final int MOVE_TRIGGER_DP = 48;
    /** 水平/垂直位移比，水平位移需 > 1.5 倍垂直位移才视为水平手势 */
    private static final float HORIZONTAL_RATIO = 1.5f;
    /** 视觉反馈指示器显示时长（ms） */
    private static final int INDICATOR_DURATION_MS = 600;
    /** 振动反馈时长（ms） */
    private static final int HAPTIC_DURATION_MS = 15;

    public interface GestureActionCallback {
        boolean canGoBack();
        boolean canGoForward();
        void onGoBack();
        void onGoForward();
        /** P0-3：长按 WebView 时触发，调用方应显示历史记录面板 */
        void onShowHistory();
    }

    private final GestureDetector gestureDetector;
    private final GestureActionCallback callback;
    private final float edgeWidthPx;
    private final float moveTriggerPx;
    private final int screenWidth;
    @Nullable private ImageView leftIndicator;
    @Nullable private ImageView rightIndicator;
    @Nullable private final Vibrator vibrator;

    // P0-3 手势导航增强：双击前进 / 长按历史开关（运行时可配置）
    private boolean doubleTapForwardEnabled = true;
    private boolean longPressHistoryEnabled = true;

    // 手动 MOVE 跟踪状态
    private float downX = -1;
    private float downY = -1;
    private boolean edgeTracking = false;
    private boolean gestureConsumed = false;

    public BrowserGestureHelper(@NonNull Context context,
                                @NonNull GestureActionCallback callback,
                                int screenWidth) {
        this.callback = callback;
        float density = context.getResources().getDisplayMetrics().density;
        this.edgeWidthPx = EDGE_WIDTH_DP * density;
        this.moveTriggerPx = MOVE_TRIGGER_DP * density;
        this.screenWidth = screenWidth;
        this.gestureDetector = new GestureDetector(context, new GestureListener());
        this.vibrator = acquireVibrator(context);
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static Vibrator acquireVibrator(@NonNull Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                return vm != null ? vm.getDefaultVibrator() : null;
            } else {
                return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 绑定视觉反馈指示器（左右两个 ImageView，会在触发手势时短暂显示）。
     * 调用方需把这些 ImageView 添加到根 FrameLayout 上层，初始 visibility=gone。
     */
    public void bindIndicators(@Nullable ImageView left, @Nullable ImageView right) {
        this.leftIndicator = left;
        this.rightIndicator = right;
    }

    /** P0-3：控制双击前进手势是否启用 */
    public void setDoubleTapForwardEnabled(boolean enabled) {
        this.doubleTapForwardEnabled = enabled;
    }

    /** P0-3：控制长按显示历史手势是否启用 */
    public void setLongPressHistoryEnabled(boolean enabled) {
        this.longPressHistoryEnabled = enabled;
    }

    /** 将此方法的返回值设为 WebView.setOnTouchListener 的 onTouch 返回值 */
    public boolean onTouch(@NonNull MotionEvent ev) {
        int action = ev.getActionMasked();
        float x = ev.getX();
        float y = ev.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                // 检测是否从边缘开始
                edgeTracking = (x <= edgeWidthPx) || (x >= (screenWidth - edgeWidthPx));
                gestureConsumed = false;
                if (edgeTracking) {
                    Log.d(TAG, "onTouch ACTION_DOWN edge x=" + x + " y=" + y
                            + " edgeWidthPx=" + edgeWidthPx + " screenWidth=" + screenWidth);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                // 手动 MOVE 检测：处理慢速拖拽（GestureDetector.onFling 仅处理快速 fling）
                if (edgeTracking && !gestureConsumed && downX >= 0) {
                    float dx = x - downX;
                    float dy = y - downY;
                    float absDx = Math.abs(dx);
                    float absDy = Math.abs(dy);
                    // 水平位移超过阈值且为水平方向
                    if (absDx >= moveTriggerPx && absDx > absDy * HORIZONTAL_RATIO) {
                        boolean fromLeftEdge = downX <= edgeWidthPx;
                        boolean fromRightEdge = downX >= (screenWidth - edgeWidthPx);
                        if (dx > 0 && fromLeftEdge && callback.canGoBack()) {
                            Log.d(TAG, "MOVE BACK triggered: dx=" + dx + " dy=" + dy);
                            triggerGesture(true);
                            return true;
                        } else if (dx < 0 && fromRightEdge && callback.canGoForward()) {
                            Log.d(TAG, "MOVE FORWARD triggered: dx=" + dx + " dy=" + dy);
                            triggerGesture(false);
                            return true;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (edgeTracking) {
                    Log.d(TAG, "onTouch ACTION_UP/CANCEL edgeTracking=" + edgeTracking
                            + " consumed=" + gestureConsumed);
                }
                downX = -1;
                downY = -1;
                edgeTracking = false;
                // 重置 gestureConsumed 需延迟到下一轮 DOWN，此处保留避免被后续 MOVE 误触
                break;
        }

        // 让 GestureDetector 处理 fling 检测
        boolean result = gestureDetector.onTouchEvent(ev);
        // 如果 GestureDetector 消费了事件（onFling 返回 true），或我们手动消费了
        return result || gestureConsumed;
    }

    /** 触发手势的统一入口：显示指示器 + 振动 + 调用回调 */
    private void triggerGesture(boolean isBack) {
        gestureConsumed = true;
        showIndicator(isBack ? leftIndicator : rightIndicator);
        performHaptic();
        if (isBack) {
            Log.d(TAG, "BACK gesture triggered");
            callback.onGoBack();
        } else {
            Log.d(TAG, "FORWARD gesture triggered");
            callback.onGoForward();
        }
    }

    private void performHaptic() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        HAPTIC_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(HAPTIC_DURATION_MS);
            }
        } catch (Throwable t) {
            Log.w(TAG, "vibrate failed", t);
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            // 返回 false 让 WebView 处理 ACTION_DOWN，保证页面正常滚动/点击
            // 手动 MOVE 跟踪已处理边缘触发
            return false;
        }

        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
            // P0-3：双击 WebView 触发前进（仅当启用且 canGoForward 时）
            if (!doubleTapForwardEnabled) return false;
            if (gestureConsumed) return false;
            if (!callback.canGoForward()) {
                Log.d(TAG, "onDoubleTap: canGoForward=false, ignored");
                return false;
            }
            Log.d(TAG, "onDoubleTap: FORWARD triggered at x=" + e.getX() + " y=" + e.getY());
            triggerGesture(false);
            return true;
        }

        @Override
        public void onLongPress(@NonNull MotionEvent e) {
            // P0-3：长按 WebView 触发显示历史记录面板
            if (!longPressHistoryEnabled) return;
            if (gestureConsumed) return;
            Log.d(TAG, "onLongPress: show history triggered at x=" + e.getX() + " y=" + e.getY());
            performHaptic();
            callback.onShowHistory();
        }

        @Override
        public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2,
                               float velocityX, float velocityY) {
            if (gestureConsumed) return false;

            float dx = e2.getX() - e1.getX();
            float dy = e2.getY() - e1.getY();
            float absDx = Math.abs(dx);
            float absDy = Math.abs(dy);

            Log.d(TAG, "onFling: dx=" + dx + " dy=" + dy + " vx=" + velocityX
                    + " vy=" + velocityY + " startX=" + e1.getX() + " edgeWidthPx=" + edgeWidthPx);

            // 仅水平方向手势（避免与垂直滚动冲突）
            if (absDx <= absDy * HORIZONTAL_RATIO) {
                Log.d(TAG, "ignored: not horizontal enough");
                return false;
            }
            // 速度阈值
            if (Math.abs(velocityX) < FLING_VELOCITY_THRESHOLD) {
                Log.d(TAG, "ignored: velocity too low");
                return false;
            }

            float startX = e1.getX();
            boolean fromLeftEdge = startX <= edgeWidthPx;
            boolean fromRightEdge = startX >= (screenWidth - edgeWidthPx);

            if (dx > 0 && fromLeftEdge && callback.canGoBack()) {
                // 从左边缘向右滑 → 后退
                triggerGesture(true);
                return true;
            } else if (dx < 0 && fromRightEdge && callback.canGoForward()) {
                // 从右边缘向左滑 → 前进
                triggerGesture(false);
                return true;
            }
            Log.d(TAG, "no match: fromLeftEdge=" + fromLeftEdge + " fromRightEdge=" + fromRightEdge
                    + " canGoBack=" + callback.canGoBack() + " canGoForward=" + callback.canGoForward());
            return false;
        }
    }

    private void showIndicator(@Nullable ImageView indicator) {
        if (indicator == null) return;
        indicator.clearAnimation();
        indicator.setVisibility(View.VISIBLE);
        indicator.setAlpha(1f);

        Animation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(INDICATOR_DURATION_MS);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override public void onAnimationEnd(Animation animation) {
                indicator.setVisibility(View.GONE);
            }
        });
        indicator.startAnimation(fadeOut);
    }
}
