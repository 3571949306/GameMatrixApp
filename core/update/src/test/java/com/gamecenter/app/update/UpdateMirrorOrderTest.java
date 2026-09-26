package com.gamecenter.app.update;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UpdateMirrorOrderTest {

    @Test
    public void parsesAndReordersConfiguredMirrorBasesWithoutDroppingFallbacks() {
        List<String> bases = UpdateMirrorOrder.parseBases(
                " https://jp.example.test:2088/app/ ,https://hk.example.test:2088/app," +
                        "https://jp.example.test:2088/app, http://unsafe.example.test/app");

        assertEquals(2, bases.size());
        assertEquals(Arrays.asList(
                "https://hk.example.test:2088/app", "https://jp.example.test:2088/app"),
                UpdateMirrorOrder.orderBases(bases, "HK.EXAMPLE.TEST"));
        assertEquals(bases, UpdateMirrorOrder.orderBases(bases, "not-configured.example.test"));
    }

    @Test
    public void reusesRecentMobileWinnerAndThenMobileAverage() throws Exception {
        long now = 1_800_000_000_000L;
        List<String> bases = UpdateMirrorOrder.parseBases(
                "https://jp.example.test:2088/app,https://hk.example.test:2088/app");
        JSONObject latestWinner = session(now - 1_000, "mobile", "hk.example.test",
                edge("jp.example.test", 5, true), edge("hk.example.test", 50, true));
        JSONObject json = new JSONObject().put("sessions", new JSONArray().put(latestWinner));

        assertEquals("hk.example.test", UpdateMirrorOrder.preferredHostFromHistoryJson(
                json.toString(), bases, now));

        JSONObject firstSample = session(now - 5_000, "mobile", "",
                edge("jp.example.test", 120, true), edge("hk.example.test", 40, true));
        JSONObject secondSample = session(now - 1_000, "mobile", "",
                edge("jp.example.test", 100, true), edge("hk.example.test", 45, true));
        json = new JSONObject().put("sessions", new JSONArray().put(firstSample).put(secondSample));

        assertEquals("hk.example.test", UpdateMirrorOrder.preferredHostFromHistoryJson(
                json.toString(), bases, now));
    }

    @Test
    public void ignoresStaleOrUnconfiguredHistoryAndMatchesMirrorPathsExactly() throws Exception {
        long now = 1_800_000_000_000L;
        List<String> bases = UpdateMirrorOrder.parseBases(
                "https://jp.example.test:2088/app,https://hk.example.test:2088/app");
        JSONObject stale = session(now - 31L * 24L * 60L * 60L * 1000L,
                "wifi", "jp.example.test");
        JSONObject unknown = session(now - 1_000, "wifi", "unknown.example.test");

        assertNull(UpdateMirrorOrder.preferredHostFromHistoryJson(
                new JSONObject().put("sessions", new JSONArray().put(stale).put(unknown)).toString(),
                bases, now));
        assertEquals("https://jp.example.test:2088/app",
                UpdateMirrorOrder.baseForUrl(
                        "https://jp.example.test:2088/app/version-release.json", bases));
        assertNull(UpdateMirrorOrder.baseForUrl(
                "https://jp.example.test:2088/appx/version-release.json", bases));
        assertFalse(UpdateMirrorOrder.hasConfiguredOrigin(
                "https://jp.example.test:2089/app/version-release.json", bases));
    }

    @Test
    public void apkDownloadQueueStartsAtMetadataMirrorThenIncludesEveryConfiguredEdge() throws Exception {
        List<String> bases = UpdateMirrorOrder.parseBases(
                "https://jp.example.test:2088/app,https://hk.example.test:2088/app," +
                        "https://us.example.test:2088/app");
        String selectedBase = bases.get(2);
        String apkName = "app-release.apk";
        List<String> ordered = UpdateMirrorOrder.orderBases(
                bases, UpdateMirrorOrder.hostForBase(selectedBase));
        List<String> urls = UpdateMirrorOrder.buildApkDownloadQueue(
                ordered, apkName, bases.get(0) + "/" + apkName,
                false, "https://github.com/example/release/app-release.apk", "");

        assertEquals(selectedBase + "/" + apkName, urls.get(0));
        for (String base : bases) {
            assertTrue(urls.contains(base + "/" + apkName));
        }
        assertEquals(4, urls.size());
    }

    @Test
    public void customDownloadSourceRemainsFirstAndStillGetsMirrorFallbacks() {
        List<String> bases = UpdateMirrorOrder.parseBases(
                "https://jp.example.test:2088/app,https://hk.example.test:2088/app");
        String customUrl = "https://custom.example.test/app-release.apk";

        List<String> urls = UpdateMirrorOrder.buildApkDownloadQueue(
                bases, "app-release.apk", customUrl, true, "", "");

        assertEquals(customUrl, urls.get(0));
        assertTrue(urls.contains("https://jp.example.test:2088/app/app-release.apk"));
        assertTrue(urls.contains("https://hk.example.test:2088/app/app-release.apk"));
    }

    private static JSONObject session(long timestamp, String network, String winner,
                                      JSONObject... edges) throws Exception {
        JSONArray edgeArray = new JSONArray();
        for (JSONObject edge : edges) {
            edgeArray.put(edge);
        }
        return new JSONObject()
                .put("ts", timestamp)
                .put("net", network)
                .put("winner", winner)
                .put("edges", edgeArray);
    }

    private static JSONObject edge(String host, long elapsedMs, boolean ok) throws Exception {
        return new JSONObject().put("host", host).put("ms", elapsedMs).put("ok", ok);
    }
}
