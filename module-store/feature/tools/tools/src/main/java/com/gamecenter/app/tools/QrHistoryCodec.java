package com.gamecenter.app.tools;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 生码历史行式编解码（纯 Java，无 Android 依赖）。
 * <p>
 * 格式：每行 {@code timestamp|content}，新记录在前。内容中的反斜杠、竖线、换行、回车
 * 会转义，保证 vCard 等多行内容可逆解析。本类是 {@link QrHistoryStore} 的逻辑真源，
 * 拆出以便 {@code scripts/verify_qr.py} 纯 javac 回归。
 * </p>
 */
public final class QrHistoryCodec {

    /** 历史最大保留条数 */
    public static final int MAX_ENTRIES = 20;

    private QrHistoryCodec() {
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
     * 插入并规整历史列表：同内容旧条目先移除，新条目置顶，超出上限截断最旧条目。
     *
     * @param list     现有历史（新的在前）
     * @param newEntry 待插入条目
     * @return 规整后的新列表（新对象，不改入参）
     */
    public static List<Entry> insertEntry(List<Entry> list, Entry newEntry) {
        List<Entry> result = new ArrayList<>(list);
        for (Iterator<Entry> it = result.iterator(); it.hasNext(); ) {
            if (it.next().content.equals(newEntry.content)) {
                it.remove();
            }
        }
        result.add(0, newEntry);
        while (result.size() > MAX_ENTRIES) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    /**
     * 删除指定内容的全部历史条目。
     *
     * @param list    现有历史
     * @param content 要删除的内容
     * @return 删除后的新列表
     */
    public static List<Entry> removeMatching(List<Entry> list, String content) {
        List<Entry> result = new ArrayList<>(list);
        if (content == null) return result;
        for (Iterator<Entry> it = result.iterator(); it.hasNext(); ) {
            if (content.equals(it.next().content)) {
                it.remove();
            }
        }
        return result;
    }

    /**
     * 将历史列表编码为行式字符串（每行 {@code timestamp|content}，'\n' 分隔）。
     */
    public static String encode(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry entry : entries) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(entry.timestamp).append('|').append(escape(entry.content));
        }
        return sb.toString();
    }

    /**
     * 解析行式历史字符串（容错：空行、格式非法的行直接跳过）。
     */
    public static List<Entry> decode(String raw) {
        List<Entry> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String line : raw.split("\n")) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|", 2);
            if (parts.length != 2) continue;
            long timestamp;
            try {
                timestamp = Long.parseLong(parts[0]);
            } catch (NumberFormatException e) {
                continue;
            }
            result.add(new Entry(timestamp, unescape(parts[1])));
        }
        return result;
    }

    /**
     * 转义内容中的行式格式保留字符：反斜杠、竖线、换行、回车。
     */
    static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("|", "\\p")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * 反转义 {@link #escape} 的结果。未知转义序列原样保留反斜杠和字符。
     */
    static String unescape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 'n') {
                    sb.append('\n');
                    continue;
                }
                if (next == 'r') {
                    sb.append('\r');
                    continue;
                }
                if (next == 'p') {
                    sb.append('|');
                    continue;
                }
                if (next == '\\') {
                    sb.append('\\');
                    continue;
                }
                sb.append(c).append(next);
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
