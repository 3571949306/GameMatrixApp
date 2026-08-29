package com.gamecenter.app.browser.core.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * H-5：播放历史与续播（轻量持久化）。
 *
 * <p>设计取舍：不用 Room 新表。AGENTS.md §4 将数据库迁移列为高风险变更且当前
 * 无法跑迁移回归（无设备/Robolectric），而播放历史只是"URL → 进度"的小型
 * 键值数据，SharedPreferences + JSON 足够，同时把所有可测逻辑做成纯函数。
 *
 * <p>线程模型：IO 由宿主线程池负责（本类不持有线程）；
 * {@link Prefs#read()} / {@link Prefs#write(String)} 的线程安全由实现方保证。
 */
public final class BrowserPlayHistoryStore {

    /** 最多保留多少条历史（超出按 updatedAt 淘汰最旧）。 */
    public static final int MAX_ENTRIES = 100;
    /** 小于该进度的记录没有续播价值，不入库。 */
    public static final long MIN_RECORD_POSITION_MS = 5_000L;
    /** 小于该进度的历史不值得恢复。 */
    public static final long MIN_RESUME_POSITION_MS = 5_000L;
    /** 距片尾不足该时长视为"看完了"，不恢复。 */
    public static final long RESUME_TAIL_MARGIN_MS = 10_000L;

    /** 进度落盘节流：接管中每该间隔写一次，退出时再补一次终值。 */
    public static final long RECORD_INTERVAL_MS = 5_000L;

    /** 持久化后端抽象（SharedPreferences 或测试内存实现）。 */
    public interface Prefs {
        @Nullable String read();
        void write(@NonNull String json);
    }

    /** 一条播放历史。所有字段按不可信输入处理，非法值回落安全默认。 */
    public static final class Entry {
        @NonNull public final String url;
        @NonNull public final String title;
        public final long positionMs;
        public final long durationMs;
        public final long updatedAt;

        public Entry(@NonNull String url, @NonNull String title,
                     long positionMs, long durationMs, long updatedAt) {
            this.url = url == null ? "" : url;
            this.title = title == null ? "" : title;
            this.positionMs = Math.max(0L, positionMs);
            this.durationMs = Math.max(0L, durationMs);
            this.updatedAt = updatedAt;
        }

        @NonNull
        public JSONObject toJson() throws org.json.JSONException {
            JSONObject obj = new JSONObject();
            obj.put("u", url);
            obj.put("t", title);
            obj.put("p", positionMs);
            obj.put("d", durationMs);
            obj.put("ts", updatedAt);
            return obj;
        }

        @Nullable
        public static Entry fromJson(@Nullable JSONObject obj) {
            if (obj == null) return null;
            String url = obj.optString("u", "");
            if (url.isEmpty()) return null;
            return new Entry(
                    url,
                    obj.optString("t", ""),
                    obj.optLong("p", 0L),
                    obj.optLong("d", 0L),
                    obj.optLong("ts", 0L));
        }
    }

    private final Prefs prefs;

    public BrowserPlayHistoryStore(@NonNull Prefs prefs) {
        this.prefs = prefs;
    }

    // ===== 纯逻辑（可 JVM 单测） =====

    /** 是否值得记录该进度（避免给"点开就关"的页面留垃圾条目）。 */
    public static boolean shouldRecord(long positionMs, long durationMs) {
        if (durationMs <= 0) return false; // 直播 / 时长未知
        return positionMs >= MIN_RECORD_POSITION_MS;
    }

    /** 历史进度是否值得恢复。 */
    public static boolean shouldResume(long positionMs, long durationMs) {
        if (durationMs <= 0) return false; // 直播 / 时长未知
        if (positionMs < MIN_RESUME_POSITION_MS) return false;
        return positionMs < durationMs - RESUME_TAIL_MARGIN_MS;
    }

    /**
     * 把条目写入 JSON 快照（键为 URL）。
     *
     * <p>超出 {@link #MAX_ENTRIES} 时按 updatedAt 淘汰最旧。返回新快照；
     * 输入非法（null / 坏 JSON）时按空库处理，不会抛出。
     */
    @NonNull
    public static String upsert(@Nullable String snapshotJson, @NonNull Entry entry) {
        JSONObject root = parseOrEmpty(snapshotJson);
        try {
            root.put(entry.url, entry.toJson());
            evictOverflow(root);
            return root.toString();
        } catch (org.json.JSONException e) {
            // 上面的 put 理论上不会失败（值均为基础类型）；保险起见返回原快照
            return snapshotJson == null ? "{}" : snapshotJson;
        }
    }

    /** 读取指定 URL 的历史；没有返回 null。 */
    @Nullable
    public static Entry get(@Nullable String snapshotJson, @Nullable String url) {
        if (url == null || url.isEmpty()) return null;
        JSONObject root = parseOrEmpty(snapshotJson);
        return Entry.fromJson(root.optJSONObject(url));
    }

    /** 清空快照。 */
    @NonNull
    public static String clear(@Nullable String snapshotJson) {
        return "{}";
    }

    private static void evictOverflow(@NonNull JSONObject root) {
        while (root.length() > MAX_ENTRIES) {
            String oldestKey = null;
            long oldestTs = Long.MAX_VALUE;
            Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String key = it.next();
                JSONObject obj = root.optJSONObject(key);
                if (obj == null) {
                    // 坏条目直接优先清除
                    oldestKey = key;
                    oldestTs = Long.MIN_VALUE;
                    break;
                }
                long ts = obj.optLong("ts", 0L);
                if (ts < oldestTs) {
                    oldestTs = ts;
                    oldestKey = key;
                }
            }
            if (oldestKey == null) return;
            root.remove(oldestKey);
        }
    }

    @NonNull
    private static JSONObject parseOrEmpty(@Nullable String json) {
        if (json == null || json.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(json);
        } catch (org.json.JSONException e) {
            return new JSONObject();
        }
    }

    // ===== 带持久化的实例方法（宿主在 IO 线程调用） =====

    /** 读取当前持久化快照。 */
    @NonNull
    public String snapshot() {
        String raw = prefs.read();
        return raw == null || raw.isEmpty() ? "{}" : raw;
    }

    /** 写入一条记录并持久化。返回 false 表示未达到记录门槛。 */
    public boolean record(@NonNull String url, @NonNull String title,
                          long positionMs, long durationMs, long nowMs) {
        if (!shouldRecord(positionMs, durationMs)) return false;
        String next = upsert(snapshot(), new Entry(url, title, positionMs, durationMs, nowMs));
        prefs.write(next);
        return true;
    }

    /** 查询某 URL 的续播进度（无记录返回 null）。 */
    @Nullable
    public Entry resumeOf(@Nullable String url) {
        return get(snapshot(), url);
    }
}
