package com.gamecenter.app.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * 二维码 Plus 纯逻辑回归测试（JUnit4；Gradle 与 scripts/verify_qr.py 共用）。
 * <p>
 * 覆盖 {@link QrHistoryCodec} 编解码/规整、{@link QrStyleMath} 几何策略、
 * {@link QrPayloads} WiFi/vCard 拼装。Android 门面（Store/Renderer/ImageIo）不进本套。
 * </p>
 */
public class QrPlusRegressionTest {

    // ---------- QrHistoryCodec ----------

    @Test
    public void encodeDecodeRoundTrip() {
        List<QrHistoryCodec.Entry> entries = new ArrayList<>();
        entries.add(new QrHistoryCodec.Entry(1000L, "https://example.com"));
        entries.add(new QrHistoryCodec.Entry(2000L, "https://example.com/path?q=1"));
        List<QrHistoryCodec.Entry> decoded = QrHistoryCodec.decode(QrHistoryCodec.encode(entries));
        assertEquals(2, decoded.size());
        assertEquals(1000L, decoded.get(0).timestamp);
        assertEquals("https://example.com", decoded.get(0).content);
        assertEquals(2000L, decoded.get(1).timestamp);
        assertEquals("https://example.com/path?q=1", decoded.get(1).content);
    }

    /** 多行内容（vCard）与竖线/反斜杠/回车必须无损往返。 */
    @Test
    public void roundTripPreservesNewlinesAndPipes() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:张三|李四\nTEL:13800000000\nEND:VCARD";
        String backslash = "a\\b|c\rd";
        List<QrHistoryCodec.Entry> entries = new ArrayList<>();
        entries.add(new QrHistoryCodec.Entry(1L, vcard));
        entries.add(new QrHistoryCodec.Entry(2L, backslash));
        List<QrHistoryCodec.Entry> decoded = QrHistoryCodec.decode(QrHistoryCodec.encode(entries));
        assertEquals(2, decoded.size());
        assertEquals(vcard, decoded.get(0).content);
        assertEquals(backslash, decoded.get(1).content);
    }

    /** 含换行内容 encode 后仍是一条历史行（未转义即红）。 */
    @Test
    public void multilineContentKeepsSingleLine() {
        String raw = QrHistoryCodec.encode(java.util.Collections.singletonList(
                new QrHistoryCodec.Entry(1L, "第一行\n第二行")));
        assertEquals(1, raw.split("\n", -1).length);
    }

    @Test
    public void insertEntryDeduplicatesAndPrepends() {
        List<QrHistoryCodec.Entry> entries = new ArrayList<>();
        entries.add(new QrHistoryCodec.Entry(1L, "a"));
        entries.add(new QrHistoryCodec.Entry(2L, "b"));
        List<QrHistoryCodec.Entry> updated = QrHistoryCodec.insertEntry(
                entries, new QrHistoryCodec.Entry(3L, "a"));
        assertEquals(2, updated.size());
        assertEquals("a", updated.get(0).content);
        assertEquals(3L, updated.get(0).timestamp);
        assertEquals("b", updated.get(1).content);
    }

    @Test
    public void insertEntryCapsAtTwenty() {
        assertEquals(20, QrHistoryCodec.MAX_ENTRIES);
        List<QrHistoryCodec.Entry> entries = new ArrayList<>();
        for (int i = QrHistoryCodec.MAX_ENTRIES - 1; i >= 0; i--) {
            entries.add(new QrHistoryCodec.Entry(i, "item" + i));
        }
        List<QrHistoryCodec.Entry> updated = QrHistoryCodec.insertEntry(
                entries, new QrHistoryCodec.Entry(999L, "new"));
        assertEquals(QrHistoryCodec.MAX_ENTRIES, updated.size());
        assertEquals("new", updated.get(0).content);
        for (QrHistoryCodec.Entry entry : updated) {
            assertFalse(entry.content.equals("item0"));
        }
        assertEquals("item19", updated.get(1).content);
    }

    @Test
    public void decodeSkipsMalformedLines() {
        List<QrHistoryCodec.Entry> decoded = QrHistoryCodec.decode(
                "abc|no-leading-number\n\n100|ok\n200\n");
        assertEquals(1, decoded.size());
        assertEquals("ok", decoded.get(0).content);
        assertEquals(100L, decoded.get(0).timestamp);
        assertEquals(0, QrHistoryCodec.decode("").size());
        assertEquals(0, QrHistoryCodec.decode(null).size());
    }

    /** 时间戳后的第一个竖线不参与分割。 */
    @Test
    public void decodeSplitsOnlyFirstPipe() {
        List<QrHistoryCodec.Entry> decoded = QrHistoryCodec.decode("300|a|b|c");
        assertEquals(1, decoded.size());
        assertEquals(300L, decoded.get(0).timestamp);
        assertEquals("a|b|c", decoded.get(0).content);
    }

    /** 未知转义序列 \\x 原样保留反斜杠+字符，不丢字。 */
    @Test
    public void escapeUnknownSequencePassthrough() {
        List<QrHistoryCodec.Entry> decoded = QrHistoryCodec.decode("1|a\\xb");
        assertEquals(1, decoded.size());
        assertEquals("a\\xb", decoded.get(0).content);
        // 尾部孤立反斜杠
        decoded = QrHistoryCodec.decode("2|end\\");
        assertEquals(1, decoded.size());
        assertEquals("end\\", decoded.get(0).content);
    }

    /** CJK / emoji 往返。 */
    @Test
    public void cjkEmojiRoundTrip() {
        String content = "二维码🎉测试|中文\n多行";
        List<QrHistoryCodec.Entry> decoded = QrHistoryCodec.decode(
                QrHistoryCodec.encode(java.util.Collections.singletonList(
                        new QrHistoryCodec.Entry(1L, content))));
        assertEquals(1, decoded.size());
        assertEquals(content, decoded.get(0).content);
    }

    @Test
    public void removeMatchingRemovesAllCopies() {
        List<QrHistoryCodec.Entry> entries = new ArrayList<>();
        entries.add(new QrHistoryCodec.Entry(1L, "a"));
        entries.add(new QrHistoryCodec.Entry(2L, "b"));
        entries.add(new QrHistoryCodec.Entry(3L, "a"));
        List<QrHistoryCodec.Entry> after = QrHistoryCodec.removeMatching(entries, "a");
        assertEquals(1, after.size());
        assertEquals("b", after.get(0).content);
    }

    // ---------- QrStyleMath ----------

    @Test
    public void logoForcesEcLevelH() {
        assertEquals('H', QrStyleMath.effectiveEcLevel(true, 'L'));
        assertEquals('H', QrStyleMath.effectiveEcLevel(true, 'M'));
        assertEquals('Q', QrStyleMath.effectiveEcLevel(false, 'Q'));
        assertEquals('L', QrStyleMath.effectiveEcLevel(false, 'L'));
    }

    @Test
    public void quietZoneFloor() {
        assertEquals(0, QrStyleMath.clampedMargin(-3));
        assertEquals(2, QrStyleMath.clampedMargin(2));
        assertEquals(2, QrStyleMath.QUIET_ZONE_MODULES);
    }

    @Test
    public void captionEmptySkipsBar() {
        assertFalse(QrStyleMath.hasCaption(null));
        assertFalse(QrStyleMath.hasCaption(""));
        assertFalse(QrStyleMath.hasCaption("   "));
        assertTrue(QrStyleMath.hasCaption("标题"));
        assertEquals(0, QrStyleMath.captionBarHeight(null, 40f));
        assertEquals(0, QrStyleMath.captionBarHeight("  ", 40f));
        assertEquals(Math.round(40f * 2.4f), QrStyleMath.captionBarHeight("标题", 40f));
    }

    @Test
    public void sizeConstants() {
        assertEquals(720, QrStyleMath.DEFAULT_QR_SIZE);
        assertEquals(240, QrStyleMath.BATCH_QR_SIZE);
        assertEquals(50, QrStyleMath.BATCH_MAX_LINES);
        assertTrue(QrStyleMath.LOGO_SIZE_RATIO <= 1f / 5f + 1e-6f);
        assertEquals(Math.round(720 * QrStyleMath.LOGO_SIZE_RATIO), QrStyleMath.logoSize(720));
        assertEquals(Math.round(240 * QrStyleMath.LOGO_SIZE_RATIO), QrStyleMath.logoSize(240));
    }

    /** 长标题等比缩字号，下限 3%。 */
    @Test
    public void captionTextSizeShrinksButFloors() {
        float base = 720 * QrStyleMath.CAPTION_TEXT_RATIO; // 43.2
        // 未超宽：保持原字号
        assertEquals(base, QrStyleMath.captionTextSize(720, base, 100f, 600f), 0.01f);
        // 超宽两倍：缩一半
        assertEquals(base / 2f, QrStyleMath.captionTextSize(720, base, 1200f, 600f), 0.01f);
        // 极端超宽：钳到下限 size*0.03
        float floor = 720 * QrStyleMath.CAPTION_MIN_TEXT_RATIO;
        assertEquals(floor, QrStyleMath.captionTextSize(720, base, 1_000_000f, 600f), 0.01f);
    }

    // ---------- QrPayloads ----------

    @Test
    public void escapeWifiEscapesReservedChars() {
        assertEquals("a\\;b\\,c\\:d\\\\e", QrPayloads.escapeWifi("a;b,c:d\\e"));
    }

    @Test
    public void buildWifiFormat() {
        String qr = QrPayloads.buildWifi("MyNet", "p@ss;1", "WPA");
        assertEquals("WIFI:T:WPA;S:MyNet;P:p@ss\\;1;;", qr);
        // 含逗号的 SSID 被转义
        assertTrue(QrPayloads.buildWifi("A,B", "x", "WPA").contains("S:A\\,B"));
    }

    @Test
    public void buildVCardWithAndWithoutEmail() {
        String with = QrPayloads.buildVCard("张三", "13800000000", "a@b.com");
        assertEquals("BEGIN:VCARD\nVERSION:3.0\nFN:张三\nTEL:13800000000\nEMAIL:a@b.com\nEND:VCARD", with);
        String without = QrPayloads.buildVCard("张三", "13800000000", "");
        assertEquals("BEGIN:VCARD\nVERSION:3.0\nFN:张三\nTEL:13800000000\nEND:VCARD", without);
        assertFalse(without.contains("EMAIL"));
    }

    /**
     * scripts/verify_qr.py 的纯 javac 自运行入口。
     */
    public static void main(String[] args) {
        int passed = 0;
        int failed = 0;
        Method[] methods = QrPlusRegressionTest.class.getDeclaredMethods();
        for (Method method : methods) {
            if (!method.isAnnotationPresent(Test.class)) continue;
            if (method.getParameterCount() != 0) continue;
            try {
                method.invoke(new QrPlusRegressionTest());
                System.out.println("  PASS  " + method.getName());
                passed++;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.out.println("  FAIL  " + method.getName() + " — " + cause);
                failed++;
            }
        }
        System.out.println("QR_PLUS_TEST_RESULT=" + (failed == 0 ? "PASS" : "FAIL")
                + " (passed=" + passed + " failed=" + failed + ")");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
