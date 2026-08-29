package com.gamecenter.app.browser.core.player;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * 播放器纯计算工具（不依赖 Android 框架，便于纯 JVM 单测覆盖）。
 *
 * <p>把"倍速钳制、时间格式化、拖拽 seek 换算、倍速档位轮转"这类容易算错的逻辑集中在此，
 * 避免散落在 Controller / Overlay / GestureHelper 三处各自实现。
 */
public final class BrowserPlayerMath {

    /** 倍速下限（网页 playbackRate 低于此值多数站点会静音或直接忽略）。 */
    public static final float RATE_MIN = 0.5f;
    /** 倍速上限（超过 3x 后音画多数浏览器内核不再保证同步）。 */
    public static final float RATE_MAX = 3.0f;
    /** 默认长按快进倍速。 */
    public static final float DEFAULT_FAST_FORWARD_RATE = 2.0f;
    /** 长按超过 {@link #EXTENDED_HOLD_MS} 后升级到的快进倍速。 */
    public static final float EXTENDED_FAST_FORWARD_RATE = 3.0f;
    /** 长按多久后从默认倍速升级到 {@link #EXTENDED_FAST_FORWARD_RATE}。 */
    public static final long EXTENDED_HOLD_MS = 3000L;

    /** 倍速档位轮转表（播放器倍速按钮点击顺序）。 */
    public static final float[] SPEED_LADDER = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f};

    /** 单次横向拖拽最多跳过的比例，避免整屏拖到底。 */
    private static final float MAX_SEEK_RATIO_PER_DRAG = 0.5f;

    private BrowserPlayerMath() {}

    /** 把任意倍速钳制到合法区间；NaN / Infinity 回落到 1.0。 */
    public static float clampRate(float rate) {
        if (Float.isNaN(rate) || Float.isInfinite(rate)) return 1.0f;
        if (rate < RATE_MIN) return RATE_MIN;
        if (rate > RATE_MAX) return RATE_MAX;
        return rate;
    }

    /**
     * 计算长按快进应使用的倍速。
     *
     * @param holdMs    已按住时长
     * @param baseRate  用户在设置里配置的快进倍速
     */
    public static float fastForwardRate(long holdMs, float baseRate) {
        if (holdMs >= EXTENDED_HOLD_MS) {
            return clampRate(EXTENDED_FAST_FORWARD_RATE);
        }
        return clampRate(baseRate);
    }

    /** 毫秒格式化为 mm:ss 或 h:mm:ss。负数与超界值一律按 0 处理。 */
    @NonNull
    public static String formatTime(long millis) {
        if (millis <= 0) return "00:00";
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    /**
     * 横向拖拽换算成 seek 增量（毫秒）。
     *
     * @param dxPx        本次拖拽水平位移（右为正）
     * @param trackWidth  可拖区域宽度，<=0 时返回 0
     * @param durationMs  视频总时长
     */
    public static long seekDeltaMs(float dxPx, int trackWidth, long durationMs) {
        if (trackWidth <= 0 || durationMs <= 0) return 0L;
        float ratio = dxPx / (float) trackWidth;
        ratio = clampRatio(ratio);
        return (long) (ratio * durationMs);
    }

    /** 把目标位置钳制到 [0, duration]；duration <= 0 表示直播/未知时长，直接返回 0。 */
    public static long clampSeek(long targetMs, long durationMs) {
        if (durationMs <= 0) return 0L;
        if (targetMs < 0) return 0L;
        if (targetMs > durationMs) return durationMs;
        return targetMs;
    }

    /** 垂直拖拽换算成亮度/音量增量比例，返回 [-1, 1]。dyPx 上滑为正。 */
    public static float verticalDelta(float dyPx, int trackHeight) {
        if (trackHeight <= 0) return 0f;
        return clampRatio(-dyPx / (float) trackHeight);
    }

    /** 在倍速档位表上取下一个档位，超出末尾回到第一档。 */
    public static float nextSpeed(float currentRate, float[] ladder) {
        if (ladder == null || ladder.length == 0) return clampRate(currentRate);
        for (int i = 0; i < ladder.length; i++) {
            // 容差比较，避免浮点累加后 1.0000001 匹配不上 1.0
            if (Math.abs(ladder[i] - currentRate) < 0.01f) {
                return clampRate(ladder[(i + 1) % ladder.length]);
            }
        }
        // 当前值不在档位表内：取第一个大于它的档位，否则回第一档
        for (float candidate : ladder) {
            if (candidate > currentRate) return clampRate(candidate);
        }
        return clampRate(ladder[0]);
    }

    /** 把倍速格式化为展示文案（2.0 → "2.0x"，1.0 → "1.0x"）。 */
    @NonNull
    public static String formatRate(float rate) {
        return String.format(Locale.US, "%.1fx", clampRate(rate));
    }

    private static float clampRatio(float ratio) {
        if (Float.isNaN(ratio) || Float.isInfinite(ratio)) return 0f;
        if (ratio > MAX_SEEK_RATIO_PER_DRAG) return MAX_SEEK_RATIO_PER_DRAG;
        if (ratio < -MAX_SEEK_RATIO_PER_DRAG) return -MAX_SEEK_RATIO_PER_DRAG;
        return ratio;
    }
}
