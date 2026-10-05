package com.gamecenter.app.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * 生码历史存储的编解码与规整逻辑回归测试（纯 JVM，不依赖 Android 环境）。
 * <p>
 * 重点覆盖行式格式 "timestamp|content" 的转义往返：
 * vCard 等多行内容必然含换行，缺失转义会导致历史解析错乱（回归前 encode/decode 若无转义则本组用例失败）。
 */
public class QrHistoryStoreTest {

    @Test
    public void encodeDecodeRoundTrip() {
        List<QrHistoryStore.Entry> entries = new ArrayList<>();
        entries.add(new QrHistoryStore.Entry(1000L, "https://example.com"));
        entries.add(new QrHistoryStore.Entry(2000L, "https://example.com/path?q=1"));

        List<QrHistoryStore.Entry> decoded = QrHistoryStore.decode(QrHistoryStore.encode(entries));
        assertEquals(2, decoded.size());
        assertEquals(1000L, decoded.get(0).timestamp);
        assertEquals("https://example.com", decoded.get(0).content);
        assertEquals(2000L, decoded.get(1).timestamp);
        assertEquals("https://example.com/path?q=1", decoded.get(1).content);
    }

    /** 多行内容（vCard）与竖线内容必须无损往返。 */
    @Test
    public void roundTripPreservesNewlinesAndPipes() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:张三|李四\nTEL:13800000000\nEND:VCARD";
        String backslash = "a\\b|c\rd";
        List<QrHistoryStore.Entry> entries = new ArrayList<>();
        entries.add(new QrHistoryStore.Entry(1L, vcard));
        entries.add(new QrHistoryStore.Entry(2L, backslash));

        List<QrHistoryStore.Entry> decoded = QrHistoryStore.decode(QrHistoryStore.encode(entries));
        assertEquals(2, decoded.size());
        assertEquals(vcard, decoded.get(0).content);
        assertEquals(backslash, decoded.get(1).content);
    }

    /** 内容中的换行未转义时不应撑破行数（历史记录数与行数一致）。 */
    @Test
    public void multilineContentKeepsSingleLine() {
        String raw = QrHistoryStore.encode(java.util.Collections.singletonList(
                new QrHistoryStore.Entry(1L, "第一行\n第二行")));
        assertEquals(1, raw.split("\n", -1).length);
    }

    /** 重复内容去重置顶，新条目在前。 */
    @Test
    public void insertEntryDeduplicatesAndPrepends() {
        List<QrHistoryStore.Entry> entries = new ArrayList<>();
        entries.add(new QrHistoryStore.Entry(1L, "a"));
        entries.add(new QrHistoryStore.Entry(2L, "b"));

        List<QrHistoryStore.Entry> updated = QrHistoryStore.insertEntry(
                entries, new QrHistoryStore.Entry(3L, "a"));
        assertEquals(2, updated.size());
        assertEquals("a", updated.get(0).content);
        assertEquals(3L, updated.get(0).timestamp);
        assertEquals("b", updated.get(1).content);
    }

    /** 超出上限 20 条时截断最旧条目。 */
    @Test
    public void insertEntryCapsAtTwenty() {
        // 列表语义：新的在前（时间戳大在前），item19 最新、item0 最旧
        List<QrHistoryStore.Entry> entries = new ArrayList<>();
        for (int i = QrHistoryStore.MAX_ENTRIES - 1; i >= 0; i--) {
            entries.add(new QrHistoryStore.Entry(i, "item" + i));
        }
        List<QrHistoryStore.Entry> updated = QrHistoryStore.insertEntry(
                entries, new QrHistoryStore.Entry(999L, "new"));
        assertEquals(QrHistoryStore.MAX_ENTRIES, updated.size());
        assertEquals("new", updated.get(0).content);
        // 最旧的 item0 被挤出
        for (QrHistoryStore.Entry entry : updated) {
            assertTrue(!entry.content.equals("item0"));
        }
        // item1..item19 仍在
        assertEquals("item19", updated.get(1).content);
    }

    /** 解析容错：空串/null、坏行直接跳过，不抛异常。 */
    @Test
    public void decodeSkipsMalformedLines() {
        List<QrHistoryStore.Entry> decoded = QrHistoryStore.decode(
                "abc|no-leading-number\n\n100|ok\n200\n");
        assertEquals(1, decoded.size());
        assertEquals("ok", decoded.get(0).content);
        assertEquals(100L, decoded.get(0).timestamp);
        assertEquals(0, QrHistoryStore.decode("").size());
        assertEquals(0, QrHistoryStore.decode(null).size());
    }

    /** 时间戳后的第一个竖线不参与分割，内容含竖线时时间戳解析仍正确。 */
    @Test
    public void decodeSplitsOnlyFirstPipe() {
        // 模拟旧版本未转义竖线的存量数据：仍应解析为 timestamp + 剩余整体
        List<QrHistoryStore.Entry> decoded = QrHistoryStore.decode("300|a|b|c");
        assertEquals(1, decoded.size());
        assertEquals(300L, decoded.get(0).timestamp);
        assertEquals("a|b|c", decoded.get(0).content);
    }
}
