package com.gamecenter.app.browser.core.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Locale;

/**
 * 页面视频元素的一次状态快照。
 *
 * <p>由 {@link BrowserVideoController} 注入 JS 探测后解析得到。所有字段都按
 * "不可信输入"处理：JS 侧可能返回任意内容，解析失败一律回落到安全默认值，
 * 绝不让脏数据把播放器 UI 带进非法状态（这正是夸克式接管最容易翻车的地方）。
 */
public final class BrowserVideoState {

    /** 页面中 video 元素数量；0 表示当前页面没有可用视频。 */
    public final int count;
    /** 被接管的 video 元素下标。 */
    public final int index;
    public final long currentTimeMs;
    /** 总时长；<=0 表示直播或时长未知。 */
    public final long durationMs;
    public final boolean paused;
    public final boolean ended;
    public final boolean muted;
    public final float volume;
    public final float rate;
    public final int videoWidth;
    public final int videoHeight;
    @NonNull public final String currentSrc;
    @NonNull public final String title;

    private BrowserVideoState(int count, int index, long currentTimeMs, long durationMs,
                              boolean paused, boolean ended, boolean muted, float volume,
                              float rate, int videoWidth, int videoHeight,
                              @NonNull String currentSrc, @NonNull String title) {
        this.count = count;
        this.index = index;
        this.currentTimeMs = Math.max(0L, currentTimeMs);
        this.durationMs = Math.max(0L, durationMs);
        this.paused = paused;
        this.ended = ended;
        this.muted = muted;
        this.volume = sanitizeUnit(volume);
        this.rate = BrowserPlayerMath.clampRate(rate);
        this.videoWidth = Math.max(0, videoWidth);
        this.videoHeight = Math.max(0, videoHeight);
        this.currentSrc = currentSrc == null ? "" : currentSrc;
        this.title = title == null ? "" : title;
    }

    /** 探测不到任何视频时的空状态。 */
    @NonNull
    public static BrowserVideoState empty() {
        return new BrowserVideoState(0, -1, 0L, 0L, true, false, false, 1f, 1f, 0, 0, "", "");
    }

    public boolean hasVideo() {
        return count > 0;
    }

    /** 直播 / 时长未知：不能显示进度条与拖拽 seek。 */
    public boolean isLive() {
        return hasVideo() && durationMs <= 0;
    }

    public boolean isPlaying() {
        return hasVideo() && !paused && !ended;
    }

    /**
     * 该视频源是否可被原生播放器直接播放。
     *
     * <p>只有 http/https 直链才行；blob:（MSE）、data: 是页面自己拼的流，
     * 脱离页面上下文后无法播放，必须走"接管原页面 video 元素"的路径。
     */
    public boolean isDirectPlayable() {
        if (currentSrc.isEmpty()) return false;
        String lower = currentSrc.toLowerCase(Locale.ROOT);
        if (lower.startsWith("blob:") || lower.startsWith("data:")
                || lower.startsWith("file:") || lower.startsWith("content:")) {
            return false;
        }
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** 进度百分比（0-100）；直播返回 0。 */
    public int progressPercent() {
        if (durationMs <= 0) return 0;
        int percent = (int) (currentTimeMs * 100L / durationMs);
        return Math.max(0, Math.min(100, percent));
    }

    /**
     * 从注入脚本返回的 JSON 字符串解析状态。
     *
     * @param json 可能为 null、空串、"null" 或非法 JSON，一律返回 {@link #empty()}
     */
    @NonNull
    public static BrowserVideoState parse(@Nullable String json) {
        if (json == null || json.isEmpty() || "null".equals(json) || "undefined".equals(json)) {
            return empty();
        }
        try {
            JSONObject obj = new JSONObject(json);
            int count = obj.optInt("count", 0);
            if (count <= 0) return empty();

            double durationSec = obj.optDouble("duration", 0d);
            long durationMs = toMillis(durationSec);
            double currentSec = obj.optDouble("currentTime", 0d);
            long currentMs = toMillis(currentSec);

            return new BrowserVideoState(
                    count,
                    obj.optInt("index", 0),
                    currentMs,
                    durationMs,
                    obj.optBoolean("paused", true),
                    obj.optBoolean("ended", false),
                    obj.optBoolean("muted", false),
                    (float) obj.optDouble("volume", 1d),
                    (float) obj.optDouble("rate", 1d),
                    obj.optInt("width", 0),
                    obj.optInt("height", 0),
                    obj.optString("currentSrc", ""),
                    obj.optString("title", ""));
        } catch (Exception e) {
            return empty();
        }
    }

    /** 秒 → 毫秒；NaN / Infinity（直播的 duration 常为 Infinity）按 0 处理。 */
    private static long toMillis(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds <= 0) return 0L;
        return (long) (seconds * 1000d);
    }

    private static float sanitizeUnit(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1f;
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }

    @Override
    @NonNull
    public String toString() {
        return "BrowserVideoState{count=" + count
                + ", pos=" + BrowserPlayerMath.formatTime(currentTimeMs)
                + "/" + BrowserPlayerMath.formatTime(durationMs)
                + ", paused=" + paused
                + ", rate=" + rate
                + ", live=" + isLive()
                + ", src=" + currentSrc
                + "}";
    }
}
