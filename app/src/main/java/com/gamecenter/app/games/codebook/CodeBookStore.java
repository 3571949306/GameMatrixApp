package com.gamecenter.app.games.codebook;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 关卡码合集本存储（Android SP 门面）。
 *
 * <p>收藏外部分享的关卡码（推箱子 / 数独），供一键复制导入对应游戏编辑器。
 * 持久化真源在 SharedPreferences（"code_book"）的 {@code entries} 键：行式存储，
 * 每行 {@code id|title|code}，行间以 \n 分隔，按添加顺序正序排列，
 * 持久化时最多保留最近 {@link #MAX_ENTRIES} 条（超出裁掉最旧的）。</p>
 *
 * <p>所有方法为静态入口，内部先读 SP 重建列表、变更后整体写回。
 * 行解析对坏行做防御处理（丢弃并留日志、不抛异常），行写入前对
 * title/code 做行式安全清洗（剥离 '|' 与换行符），保证存储格式不被破坏。</p>
 */
public final class CodeBookStore {

    private static final String TAG = "CodeBookStore";
    private static final String PREFS_NAME = "code_book";
    private static final String KEY_ENTRIES = "entries";
    /** 下一个可用 id 的 SP 键（单调递增，删除不回收）。 */
    private static final String KEY_NEXT_ID = "next_id";
    /** 持久化时最多保留的条目数，超出裁掉最旧的。 */
    private static final int MAX_ENTRIES = 100;
    /** 标题长度上限（字符数）。 */
    private static final int MAX_TITLE_LEN = 16;
    /** 标题为空时的兜底文案。 */
    private static final String DEFAULT_TITLE = "未命名";

    private CodeBookStore() {
    }

    /** 一条不可变的关卡码收藏记录。 */
    public static final class Entry {
        /** 记录 id（添加时分配，单调递增）。 */
        public final int id;
        /** 展示标题（已清洗，非空且不超过 16 字）。 */
        public final String title;
        /** 关卡码原文（已剥离 '|' 与换行符）。 */
        public final String code;

        public Entry(int id, String title, String code) {
            this.id = id;
            this.title = title;
            this.code = code;
        }
    }

    /**
     * 读取全部收藏（按添加顺序，旧→新）。
     *
     * @param context 任意 Context
     * @return 收藏列表；坏行（段数不足 / id 非数字 / 内容为空）丢弃并留日志，不抛异常
     */
    public static List<Entry> loadAll(Context context) {
        List<Entry> entries = new ArrayList<>();
        String raw = prefs(context).getString(KEY_ENTRIES, "");
        if (raw == null || raw.isEmpty()) return entries;
        String[] lines = raw.split("\n", -1);
        for (String line : lines) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length != 3) {
                Log.w(TAG, "坏行丢弃（段数=" + parts.length + "）: " + line);
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                Log.w(TAG, "坏行丢弃（id 非数字）: " + line);
                continue;
            }
            if (id <= 0 || parts[1].isEmpty() || parts[2].isEmpty()) {
                Log.w(TAG, "坏行丢弃（id 非正或内容为空）: " + line);
                continue;
            }
            entries.add(new Entry(id, parts[1], parts[2]));
        }
        return entries;
    }

    /**
     * 追加一条收藏。
     *
     * <p>title 清洗：剥离 '|' 与换行符、trim、截断到 16 字、空串兜底"未命名"。
     * code 清洗：剥离 '|' 与换行符（防御，合法关卡码不含这些字符）。
     * 超过 {@link #MAX_ENTRIES} 条时裁掉最旧的。格式合法性（detect）由调用方
     * 在调用前校验，本方法不做游戏格式判定。</p>
     *
     * @param context 任意 Context
     * @param title   展示标题（允许 null / 空 / 超长，内部清洗）
     * @param code    关卡码
     * @return 新记录的 id（从 1 起单调递增）
     */
    public static int add(Context context, String title, String code) {
        SharedPreferences prefs = prefs(context);
        List<Entry> entries = loadAll(context);
        int id = prefs.getInt(KEY_NEXT_ID, 1);
        Entry entry = new Entry(id, sanitizeTitle(title), sanitizeCode(code));
        entries.add(entry);
        // 裁旧：只保留最近 MAX_ENTRIES 条
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        // 条目与 next_id 同一次原子写回，避免半写状态导致 id 复用
        prefs.edit()
                .putString(KEY_ENTRIES, serialize(entries))
                .putInt(KEY_NEXT_ID, id + 1)
                .apply();
        return id;
    }

    /**
     * 按 id 删除一条收藏；id 不存在时无副作用。
     *
     * @param context 任意 Context
     * @param id      要删除的记录 id
     */
    public static void remove(Context context, int id) {
        List<Entry> entries = loadAll(context);
        boolean changed = entries.removeIf(entry -> entry.id == id);
        if (changed) {
            prefs(context).edit().putString(KEY_ENTRIES, serialize(entries)).apply();
        }
    }

    /**
     * 按 id 查找收藏。
     *
     * @param context 任意 Context
     * @param id      记录 id
     * @return 命中的记录；不存在返回 null
     */
    public static Entry find(Context context, int id) {
        for (Entry entry : loadAll(context)) {
            if (entry.id == id) return entry;
        }
        return null;
    }

    /** 列表序列化为行式文本（"id|title|code"，\n 分隔）。 */
    private static String serialize(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append('\n');
            Entry e = entries.get(i);
            sb.append(e.id).append('|').append(e.title).append('|').append(e.code);
        }
        return sb.toString();
    }

    /** 标题清洗：剥离行式保留字符（'|' 与换行）、trim、限 16 字、空串兜底。 */
    private static String sanitizeTitle(String title) {
        String cleaned = stripUnsafeChars(title).trim();
        if (cleaned.isEmpty()) return DEFAULT_TITLE;
        if (cleaned.length() > MAX_TITLE_LEN) {
            return cleaned.substring(0, MAX_TITLE_LEN);
        }
        return cleaned;
    }

    /** 关卡码清洗：剥离行式保留字符（'|' 与换行），破坏存储格式的字符一律不要。 */
    private static String sanitizeCode(String code) {
        return stripUnsafeChars(code);
    }

    /** 剥离 '|'、'\n'、'\r'（null 安全）。 */
    private static String stripUnsafeChars(String text) {
        if (text == null) return "";
        return text.replace("|", "").replace("\n", "").replace("\r", "");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
