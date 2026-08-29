package com.gamecenter.app.browser.core.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

/**
 * 播放器手势识别器：长按快进 / 双击暂停 / 单击显隐控件 / 横向 seek / 两侧调亮度音量。
 *
 * <p><b>长按快进（核心）</b>：手指按下不动超过 {@link #LONG_PRESS_TRIGGER_MS} 即进入快进，
 * 倍速由 {@link BrowserPlayerMath#fastForwardRate(long, float)} 决定（默认 2x，
 * 按住超过 {@link BrowserPlayerMath#EXTENDED_HOLD_MS} 自动升到 3x）；
 * 一旦手指移动超过 touchSlop 就取消快进并转为 seek / 亮度 / 音量手势，
 * 保证"想拖进度却误触发倍速"不会发生。
 *
 * <p>状态机：{@code NONE → (长按) FAST_FORWARD | (横移) SEEK | (纵移) VERTICAL}，
 * 任一时刻只可能处于一种模式，松手统一收尾。
 */
public class BrowserPlayerGestureHelper {

    /** 长按判定阈值。略短于系统 500ms，让快进更跟手。 */
    public static final long LONG_PRESS_TRIGGER_MS = 400L;
    /** 双击判定窗口。 */
    private static final long DOUBLE_TAP_WINDOW_MS = 300L;
    /** 快进期间倍速升级的轮询间隔。 */
    private static final long FAST_FORWARD_TICK_MS = 500L;

    private static final int MODE_NONE = 0;
    private static final int MODE_SEEK = 1;
    private static final int MODE_VERTICAL = 2;

    /** 播放器手势回调。 */
    public interface PlayerGestureCallback {
        boolean isPlaying();
        long getDurationMs();
        long getCurrentPositionMs();
        /** 直播 / 时长未知时为 false，此时禁用拖拽 seek。 */
        boolean isSeekable();
        void onTogglePlay();
        void onToggleControls();
        /** 拖拽 seek 过程中的实时预览（targetMs 为将要跳到的位置）。 */
        void onSeekPreview(long deltaMs, long targetMs);
        /** 拖拽 seek 松手提交。 */
        void onSeekCommit(long targetMs);
        /** 音量增量，范围 [-1, 1]，上滑为正。 */
        void onVolumeDelta(float delta);
        /** 亮度增量，范围 [-1, 1]，上滑为正。 */
        void onBrightnessDelta(float delta);
        /** 进入长按快进。 */
        void onFastForwardStart(float rate);
        /** 长按持续中，倍速可能升级。 */
        void onFastForwardUpdate(float rate);
        /** 松手，结束快进。 */
        void onFastForwardEnd();
    }

    private final int touchSlop;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @NonNull private final PlayerGestureCallback callback;

    private int mode = MODE_NONE;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private long downTimeMs;
    private int surfaceWidth = 0;
    private int surfaceHeight = 0;

    private boolean fastForwarding = false;
    private long seekBaseMs = 0L;
    private long seekTargetMs = 0L;

    private long lastTapTimeMs = 0L;
    private boolean pendingSingleTap = false;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            startFastForward();
        }
    };

    private final Runnable fastForwardTickRunnable = new Runnable() {
        @Override
        public void run() {
            long holdMs = System.currentTimeMillis() - downTimeMs;
            callback.onFastForwardUpdate(
                    BrowserPlayerMath.fastForwardRate(holdMs, getEffectiveRate()));
            mainHandler.postDelayed(this, FAST_FORWARD_TICK_MS);
        }
    };

    private final Runnable singleTapRunnable = new Runnable() {
        @Override
        public void run() {
            if (!pendingSingleTap) return;
            pendingSingleTap = false;
            callback.onToggleControls();
        }
    };

    /** 用户配置的快进倍速，由宿主通过 {@link #setFastForwardRate(float)} 注入。 */
    private float configuredRate = BrowserPlayerMath.DEFAULT_FAST_FORWARD_RATE;
    /** 当前生效的快进倍速（长按升级后变化）。 */
    private float currentRate = BrowserPlayerMath.DEFAULT_FAST_FORWARD_RATE;
    /** H-4：长按快进手势总开关（关闭后长按不再触发倍速，其余手势不受影响）。 */
    private boolean longPressEnabled = true;

    public BrowserPlayerGestureHelper(@NonNull Context context,
                                      @NonNull PlayerGestureCallback callback) {
        this.touchSlop = ViewConfiguration.get(context.getApplicationContext()).getScaledTouchSlop();
        this.callback = callback;
    }

    public void setFastForwardRate(float rate) {
        configuredRate = BrowserPlayerMath.clampRate(rate);
    }

    /** H-4：开关长按快进；关闭时若正处于快进中会立即结束。 */
    public void setLongPressEnabled(boolean enabled) {
        if (longPressEnabled == enabled) return;
        longPressEnabled = enabled;
        if (!enabled) {
            mainHandler.removeCallbacks(longPressRunnable);
            cancelFastForward();
        }
    }

    private float getEffectiveRate() {
        return configuredRate;
    }

    /**
     * 在手勢接收 View 的 {@code onTouch} 中调用。
     *
     * @return true 表示手势已消费
     */
    public boolean onTouch(@NonNull View view, @NonNull MotionEvent event) {
        surfaceWidth = view.getWidth();
        surfaceHeight = view.getHeight();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                handleDown(event);
                return true;

            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                return true;

            case MotionEvent.ACTION_UP:
                handleUp(view, event);
                return true;

            case MotionEvent.ACTION_CANCEL:
                handleCancel();
                return true;

            default:
                return false;
        }
    }

    private void handleDown(@NonNull MotionEvent event) {
        mainHandler.removeCallbacks(longPressRunnable);
        mainHandler.removeCallbacks(fastForwardTickRunnable);
        mainHandler.removeCallbacks(singleTapRunnable);

        mode = MODE_NONE;
        downX = event.getX();
        downY = event.getY();
        lastX = downX;
        lastY = downY;
        downTimeMs = System.currentTimeMillis();
        if (callback.isSeekable()) {
            seekBaseMs = callback.getCurrentPositionMs();
            seekTargetMs = seekBaseMs;
        }
        if (longPressEnabled) {
            mainHandler.postDelayed(longPressRunnable, LONG_PRESS_TRIGGER_MS);
        }
    }

    private void handleMove(@NonNull MotionEvent event) {
        float dx = event.getX() - downX;
        float dy = event.getY() - downY;

        if (mode == MODE_NONE) {
            if (Math.abs(dx) <= touchSlop && Math.abs(dy) <= touchSlop) {
                return; // 还在长按判定范围内，交给 longPressRunnable
            }
            // 移动超阈值：取消长按，进入拖拽类手势
            cancelFastForward();
            mainHandler.removeCallbacks(longPressRunnable);
            mode = Math.abs(dx) > Math.abs(dy) ? MODE_SEEK : MODE_VERTICAL;
            lastX = event.getX();
            lastY = event.getY();
            return;
        }

        if (mode == MODE_SEEK) {
            if (!callback.isSeekable()) return;
            long delta = BrowserPlayerMath.seekDeltaMs(dx, surfaceWidth, callback.getDurationMs());
            seekTargetMs = BrowserPlayerMath.clampSeek(seekBaseMs + delta, callback.getDurationMs());
            callback.onSeekPreview(delta, seekTargetMs);
        } else if (mode == MODE_VERTICAL) {
            float step = BrowserPlayerMath.verticalDelta(event.getY() - lastY, surfaceHeight);
            if (step != 0f) {
                if (downX < surfaceWidth / 2f) {
                    callback.onBrightnessDelta(step);
                } else {
                    callback.onVolumeDelta(step);
                }
            }
        }
        lastX = event.getX();
        lastY = event.getY();
    }

    private void handleUp(@NonNull View view, @NonNull MotionEvent event) {
        mainHandler.removeCallbacks(longPressRunnable);
        mainHandler.removeCallbacks(fastForwardTickRunnable);

        long elapsed = System.currentTimeMillis() - downTimeMs;
        boolean moved = mode != MODE_NONE;

        if (fastForwarding) {
            cancelFastForward();
        } else if (!moved) {
            // 未移动 → 点击系列
            if (elapsed < LONG_PRESS_TRIGGER_MS) {
                handleTap(view);
            }
        } else if (mode == MODE_SEEK) {
            if (callback.isSeekable()) {
                callback.onSeekCommit(seekTargetMs);
            }
        }
        mode = MODE_NONE;
    }

    private void handleTap(@NonNull View view) {
        long now = System.currentTimeMillis();
        if (now - lastTapTimeMs <= DOUBLE_TAP_WINDOW_MS) {
            // 双击：取消待执行的单击，直接切换播放态
            mainHandler.removeCallbacks(singleTapRunnable);
            pendingSingleTap = false;
            lastTapTimeMs = 0L;
            callback.onTogglePlay();
        } else {
            lastTapTimeMs = now;
            pendingSingleTap = true;
            mainHandler.postDelayed(singleTapRunnable, DOUBLE_TAP_WINDOW_MS);
        }
        view.performClick();
    }

    private void handleCancel() {
        mainHandler.removeCallbacks(longPressRunnable);
        mainHandler.removeCallbacks(fastForwardTickRunnable);
        cancelFastForward();
        mode = MODE_NONE;
    }

    /** 宿主在 Fragment 暂停 / 页面不可见时必须调用，防止手指离开后仍在倍速。 */
    public void cancel() {
        handleCancel();
        mainHandler.removeCallbacks(singleTapRunnable);
        pendingSingleTap = false;
    }

    private void startFastForward() {
        if (fastForwarding || !longPressEnabled) return;
        fastForwarding = true;
        mode = MODE_NONE;
        currentRate = BrowserPlayerMath.fastForwardRate(
                System.currentTimeMillis() - downTimeMs, configuredRate);
        callback.onFastForwardStart(currentRate);
        mainHandler.postDelayed(fastForwardTickRunnable, FAST_FORWARD_TICK_MS);
    }

    private void cancelFastForward() {
        mainHandler.removeCallbacks(fastForwardTickRunnable);
        if (!fastForwarding) return;
        fastForwarding = false;
        callback.onFastForwardEnd();
    }

    public boolean isFastForwarding() {
        return fastForwarding;
    }

    public float getCurrentRate() {
        return currentRate;
    }
}
