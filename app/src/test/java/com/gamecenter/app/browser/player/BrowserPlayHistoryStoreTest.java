package com.gamecenter.app.browser.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.browser.core.player.BrowserPlayHistoryStore;

import org.junit.Test;

/**
 * H-5 播放历史与续播的纯 JVM 回归测试。
 *
 * <p>覆盖：记录/恢复门槛、快照编解码、LRU 淘汰、坏输入回落。
 * 不依赖 Android 框架（SharedPreferences 用内存实现注入）。
 */
public class BrowserPlayHistoryStoreTest {

    // ===== 记录门槛 =====

    @Test
    public void shouldRecord_rejectsLiveOrShortProgress() {
        assertFalse(BrowserPlayHistoryStore.shouldRecord(0L, 0L));   // 直播 / 时长未知
        assertFalse(BrowserPlayHistoryStore.shouldRecord(100_000L, 0L));
        assertFalse(BrowserPlayHistoryStore.shouldRecord(4_999L, 600_000L)); // 刚点开就关
        assertTrue(BrowserPlayHistoryStore.shouldRecord(5_000L, 600_000L));
        assertTrue(BrowserPlayHistoryStore.shouldRecord(100_000L, 600_000L));
    }

    // ===== 续播门槛 =====

    @Test
    public void shouldResume_rejectsInvalidDurations() {
        assertFalse(BrowserPlayHistoryStore.shouldResume(0L, 0L));          // 直播
        assertFalse(BrowserPlayHistoryStore.shouldResume(60_000L, -1L));    // 非法
    }

    @Test
    public void shouldResume_rejectsTooShortAndNearEnd() {
        assertFalse(BrowserPlayHistoryStore.shouldResume(4_999L, 600_000L)); // 进度太短
        assertFalse(BrowserPlayHistoryStore.shouldResume(595_000L, 600_000L)); // 已看完（片尾余量内）
        assertFalse(BrowserPlayHistoryStore.shouldResume(600_000L, 600_000L));
        assertTrue(BrowserPlayHistoryStore.shouldResume(120_000L, 600_000L));
        assertTrue(BrowserPlayHistoryStore.shouldResume(5_000L, 600_000L));
    }

    // ===== 快照编解码 =====

    @Test
    public void upsert_then_get_roundTrips() {
        String json = BrowserPlayHistoryStore.upsert(null,
                entry("https://example.com/v/1", "示例视频", 120_000L, 600_000L, 1_000L));
        BrowserPlayHistoryStore.Entry e = BrowserPlayHistoryStore.get(json, "https://example.com/v/1");
        assertNotNull(e);
        assertEquals("示例视频", e.title);
        assertEquals(120_000L, e.positionMs);
        assertEquals(600_000L, e.durationMs);
        assertEquals(1_000L, e.updatedAt);
    }

    @Test
    public void upsert_overwritesSameUrl() {
        String json = "{}";
        json = BrowserPlayHistoryStore.upsert(json,
                entry("https://example.com/v/1", "第一次", 10_000L, 600_000L, 1L));
        json = BrowserPlayHistoryStore.upsert(json,
                entry("https://example.com/v/1", "第二次", 300_000L, 600_000L, 2L));
        BrowserPlayHistoryStore.Entry e = BrowserPlayHistoryStore.get(json, "https://example.com/v/1");
        assertNotNull(e);
        assertEquals("第二次", e.title);
        assertEquals(300_000L, e.positionMs);
        assertEquals(1, countEntries(json));
    }

    @Test
    public void upsert_survivesCorruptSnapshot() {
        // 输入坏 JSON 时不抛出，按空库继续
        String json = BrowserPlayHistoryStore.upsert("not-json{{{",
                entry("https://example.com/v/1", "t", 10_000L, 600_000L, 1L));
        assertNotNull(BrowserPlayHistoryStore.get(json, "https://example.com/v/1"));
    }

    @Test
    public void get_returnsNullForUnknownOrEmptyUrl() {
        assertNull(BrowserPlayHistoryStore.get("{}", "https://missing.example.com"));
        assertNull(BrowserPlayHistoryStore.get("{}", null));
        assertNull(BrowserPlayHistoryStore.get("{}", ""));
        assertNull(BrowserPlayHistoryStore.get(null, "https://example.com"));
    }

    // ===== LRU 淘汰 =====

    @Test
    public void upsert_evictsOldestBeyondCap() {
        String json = "{}";
        for (int i = 0; i < BrowserPlayHistoryStore.MAX_ENTRIES; i++) {
            json = BrowserPlayHistoryStore.upsert(json,
                    entry("https://example.com/v/" + i, "t" + i, 60_000L, 600_000L, i));
        }
        assertEquals(BrowserPlayHistoryStore.MAX_ENTRIES, countEntries(json));
        assertNotNull(BrowserPlayHistoryStore.get(json, "https://example.com/v/0")); // 最旧但未超

        // 再插入一条：最旧（ts=0）被淘汰
        json = BrowserPlayHistoryStore.upsert(json,
                entry("https://example.com/v/new", "new", 60_000L, 600_000L,
                        BrowserPlayHistoryStore.MAX_ENTRIES));
        assertEquals(BrowserPlayHistoryStore.MAX_ENTRIES, countEntries(json));
        assertNull(BrowserPlayHistoryStore.get(json, "https://example.com/v/0"));
        assertNotNull(BrowserPlayHistoryStore.get(json, "https://example.com/v/1"));
        assertNotNull(BrowserPlayHistoryStore.get(json, "https://example.com/v/new"));
    }

    // ===== 实例方法（内存后端） =====

    @Test
    public void record_and_resume_viaInstance() {
        MemoryPrefs prefs = new MemoryPrefs("{}");
        BrowserPlayHistoryStore store = new BrowserPlayHistoryStore(prefs);

        // 未达记录门槛：不落盘
        assertFalse(store.record("https://example.com/v/1", "t", 1_000L, 600_000L, 1L));
        assertTrue(prefs.json.equals("{}") || prefs.json.contains("example.com") == false);

        // 达门槛：落盘且可续播
        assertTrue(store.record("https://example.com/v/1", "t", 120_000L, 600_000L, 2L));
        BrowserPlayHistoryStore.Entry e = store.resumeOf("https://example.com/v/1");
        assertNotNull(e);
        assertEquals(120_000L, e.positionMs);
        assertTrue(BrowserPlayHistoryStore.shouldResume(e.positionMs, 600_000L));
    }

    @Test
    public void record_rejectsLiveStreams() {
        BrowserPlayHistoryStore store = new BrowserPlayHistoryStore(new MemoryPrefs("{}"));
        assertFalse(store.record("https://live.example.com", "live", 60_000L, 0L, 1L));
        assertNull(store.resumeOf("https://live.example.com"));
    }

    // ===== 工具 =====

    private static BrowserPlayHistoryStore.Entry entry(String url, String title,
                                                       long positionMs, long durationMs, long ts) {
        return new BrowserPlayHistoryStore.Entry(url, title, positionMs, durationMs, ts);
    }

    private static int countEntries(String json) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            return obj.length();
        } catch (Exception e) {
            return -1;
        }
    }

    /** SharedPreferences 的内存替身。 */
    private static final class MemoryPrefs implements BrowserPlayHistoryStore.Prefs {
        String json;

        MemoryPrefs(String initial) {
            this.json = initial;
        }

        @Override
        public String read() {
            return json;
        }

        @Override
        public void write(String json) {
            this.json = json;
        }
    }
}
