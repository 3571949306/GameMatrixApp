package com.gamecenter.app.tools;

import android.content.Context;
import android.content.SharedPreferences;

import com.gamecenter.app.core.common.ModuleScopedPreferences;

import java.util.List;

/**
 * 二维码生码历史存储（qr_plus 增强功能）。
 * <p>
 * 记录最近生成过的二维码内容，支持点击回填与长按删除。
 * 底层使用模块作用域 SharedPreferences（参照 {@link ToolSectionStore} 的 Phase 3 数据隔离模式，
 * 实际存储文件为 {@code mod_tools__qr_plus_history}），不与其他模块数据混用。
 * </p>
 * <p>
 * 行式编解码与截断逻辑真源在 {@link QrHistoryCodec}（纯 Java，供 verify_qr 回归）；
 * 本类只做 SP 门面，公开 API 保持不变。
 * </p>
 */
public final class QrHistoryStore {

    /** 历史最大保留条数（与 Codec 一致） */
    static final int MAX_ENTRIES = QrHistoryCodec.MAX_ENTRIES;

    /** SharedPreferences 基础名（经 ModuleScopedPreferences 加作用域前缀） */
    private static final String PREFS_BASE_NAME = "qr_plus_history";

    /** 模块作用域 ID（与 catalog.json 中 tools 模块 id 一致，同 ToolSectionStore） */
    private static final String MODULE_ID = "tools";

    /** 历史条目的存储键 */
    private static final String KEY_ENTRIES = "qr_history_entries";

    private final SharedPreferences prefs;

    /**
     * 构造历史存储实例。
     *
     * @param context Android Context，内部转为作用域 SharedPreferences
     */
    public QrHistoryStore(Context context) {
        this.prefs = ModuleScopedPreferences.get(
                context.getApplicationContext(), MODULE_ID, PREFS_BASE_NAME);
    }

    /** 历史条目：时间戳 + 二维码内容 */
    public static final class Entry {
        public final long timestamp;
        public final String content;

        public Entry(long timestamp, String content) {
            this.timestamp = timestamp;
            this.content = content;
        }
    }

    /**
     * 加载全部历史（新的在前）。
     */
    public List<Entry> load() {
        return fromCodec(QrHistoryCodec.decode(prefs.getString(KEY_ENTRIES, "")));
    }

    /**
     * 记录一次生成：同内容去重置顶，超出上限截断。
     */
    public void record(String content) {
        if (content == null || content.isEmpty()) return;
        List<Entry> updated = insertEntry(load(),
                new Entry(System.currentTimeMillis(), content));
        prefs.edit().putString(KEY_ENTRIES, encode(updated)).apply();
    }

    /**
     * 删除指定内容的历史条目。
     */
    public void remove(String content) {
        if (content == null) return;
        List<Entry> before = load();
        List<Entry> after = removeMatching(before, content);
        if (after.size() != before.size()) {
            prefs.edit().putString(KEY_ENTRIES, encode(after)).apply();
        }
    }

    /**
     * 清空全部历史。
     */
    public void clear() {
        prefs.edit().remove(KEY_ENTRIES).apply();
    }

    /** 插入并规整（委托 Codec）。 */
    static List<Entry> insertEntry(List<Entry> list, Entry newEntry) {
        return fromCodec(QrHistoryCodec.insertEntry(toCodec(list), toCodec(newEntry)));
    }

    /** 删除匹配内容（委托 Codec）。 */
    static List<Entry> removeMatching(List<Entry> list, String content) {
        return fromCodec(QrHistoryCodec.removeMatching(toCodec(list), content));
    }

    /** 编码（委托 Codec）。 */
    static String encode(List<Entry> entries) {
        return QrHistoryCodec.encode(toCodec(entries));
    }

    /** 解码（委托 Codec）。 */
    static List<Entry> decode(String raw) {
        return fromCodec(QrHistoryCodec.decode(raw));
    }

    private static QrHistoryCodec.Entry toCodec(Entry e) {
        return new QrHistoryCodec.Entry(e.timestamp, e.content);
    }

    private static List<QrHistoryCodec.Entry> toCodec(List<Entry> list) {
        List<QrHistoryCodec.Entry> out = new java.util.ArrayList<>(list.size());
        for (Entry e : list) out.add(toCodec(e));
        return out;
    }

    private static List<Entry> fromCodec(List<QrHistoryCodec.Entry> list) {
        List<Entry> out = new java.util.ArrayList<>(list.size());
        for (QrHistoryCodec.Entry e : list) out.add(new Entry(e.timestamp, e.content));
        return out;
    }
}
