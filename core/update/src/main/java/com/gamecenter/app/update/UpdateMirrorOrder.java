package com.gamecenter.app.update;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared ordering and history lookup for module and application update mirrors. */
public final class UpdateMirrorOrder {

    private static final String HISTORY_FILE_NAME = "source_tests.json";
    private static final long HISTORY_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final long MAX_FUTURE_SKEW_MS = 5L * 60L * 1000L;
    private static final long MAX_HISTORY_FILE_BYTES = 256L * 1024L;
    private static final Pattern SAFE_ASSET_NAME =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private UpdateMirrorOrder() {
    }

    /** Parse a comma-separated mirror base list, keeping only distinct HTTPS bases. */
    public static List<String> parseBases(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String raw : csv.split(",")) {
            String base = trimTrailingSlash(raw == null ? "" : raw.trim());
            if (base.isEmpty() || !UpdateUrlValidator.isValidHttpsUrl(base)) {
                continue;
            }
            URI uri = URI.create(base);
            if (uri.getRawQuery() != null || uri.getHost() == null) {
                continue;
            }
            boolean duplicate = false;
            for (String existing : result) {
                if (existing.equalsIgnoreCase(base)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                result.add(base);
            }
        }
        return result;
    }

    /** Move the configured base for preferredHost to the front, preserving every other base. */
    public static List<String> orderBases(List<String> bases, String preferredHost) {
        if (bases == null || bases.isEmpty() || preferredHost == null || preferredHost.trim().isEmpty()) {
            return bases == null ? Collections.emptyList() : new ArrayList<>(bases);
        }
        String expectedHost = preferredHost.trim().toLowerCase(Locale.ROOT);
        List<String> ordered = new ArrayList<>(bases.size());
        String winner = null;
        for (String base : bases) {
            if (expectedHost.equals(hostForBase(base))) {
                winner = base;
                break;
            }
        }
        if (winner == null) {
            ordered.addAll(bases);
            return ordered;
        }
        ordered.add(winner);
        for (String base : bases) {
            if (!base.equalsIgnoreCase(winner)) {
                ordered.add(base);
            }
        }
        return ordered;
    }

    /** Read the same bounded probe history used by the module mirror selector. */
    public static String preferredHostFromHistory(Context context, List<String> bases) {
        if (context == null) {
            return null;
        }
        File history = new File(context.getFilesDir(), HISTORY_FILE_NAME);
        long length = history.length();
        if (!history.isFile() || length <= 0 || length > MAX_HISTORY_FILE_BYTES) {
            return null;
        }
        String rawJson = null;
        try (FileInputStream input = new FileInputStream(history)) {
            byte[] bytes = new byte[(int) length];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            if (offset == bytes.length) {
                rawJson = new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception error) {
            android.util.Log.d("UpdateMirrorOrder", "Probe history unavailable", error);
        }
        return rawJson == null ? null : preferredHostFromHistoryJson(
                rawJson, bases, System.currentTimeMillis());
    }

    /** Pure history selection seam for deterministic tests. */
    public static String preferredHostFromHistoryJson(String rawJson, List<String> bases, long nowMs) {
        if (rawJson == null || rawJson.trim().isEmpty() || bases == null || bases.isEmpty()) {
            return null;
        }
        try {
            return selectPreferredHost(rawJson, bases, nowMs);
        } catch (Exception ignored) {
            android.util.Log.d("UpdateMirrorOrder", "Ignoring invalid probe history", ignored);
        }
        return null;
    }

    private static String selectPreferredHost(String rawJson, List<String> bases, long nowMs)
            throws org.json.JSONException {
        Set<String> configuredHosts = new HashSet<>();
        for (String base : bases) {
            String host = hostForBase(base);
            if (host != null) {
                configuredHosts.add(host);
            }
        }
        if (configuredHosts.isEmpty()) {
            return null;
        }

        JSONArray sessions = new JSONObject(rawJson).optJSONArray("sessions");
        if (sessions == null) {
            return null;
        }
        long cutoff = nowMs - HISTORY_MAX_AGE_MS;
        JSONObject latestMobile = null;
        JSONObject latestWifi = null;
        Map<String, double[]> mobileTotals = new HashMap<>();
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);
            if (session == null) {
                continue;
            }
            long timestamp = session.optLong("ts", 0L);
            if (timestamp < cutoff || timestamp > nowMs + MAX_FUTURE_SKEW_MS) {
                continue;
            }
            String network = session.optString("net", "");
            if ("mobile".equals(network)) {
                if (latestMobile == null || timestamp > latestMobile.optLong("ts", 0L)) {
                    latestMobile = session;
                }
                JSONArray edges = session.optJSONArray("edges");
                if (edges != null) {
                    for (int edgeIndex = 0; edgeIndex < edges.length(); edgeIndex++) {
                        JSONObject edge = edges.optJSONObject(edgeIndex);
                        if (edge == null || !edge.optBoolean("ok", false)) {
                            continue;
                        }
                        String host = normalizeHost(edge.optString("host", ""));
                        long elapsed = edge.optLong("ms", -1L);
                        if (!configuredHosts.contains(host) || elapsed <= 0L) {
                            continue;
                        }
                        double[] total = mobileTotals.computeIfAbsent(host, ignored -> new double[2]);
                        total[0] += elapsed;
                        total[1] += 1.0;
                    }
                }
            } else if ("wifi".equals(network)
                    && (latestWifi == null || timestamp > latestWifi.optLong("ts", 0L))) {
                latestWifi = session;
            }
        }

        String mobileWinner = validWinner(latestMobile, configuredHosts);
        if (mobileWinner != null) {
            return mobileWinner;
        }

        String bestAverageHost = null;
        double bestAverageMs = Double.MAX_VALUE;
        for (String base : bases) {
            String host = hostForBase(base);
            double[] total = mobileTotals.get(host);
            if (total != null && total[1] > 0.0) {
                double average = total[0] / total[1];
                if (average < bestAverageMs) {
                    bestAverageMs = average;
                    bestAverageHost = host;
                }
            }
        }
        if (bestAverageHost != null) {
            return bestAverageHost;
        }
        return validWinner(latestWifi, configuredHosts);
    }

    /** Return the configured mirror base whose origin and path contain this URL. */
    public static String baseForUrl(String rawUrl, List<String> bases) {
        if (!UpdateUrlValidator.isValidHttpsUrl(rawUrl) || bases == null) {
            return null;
        }
        URI candidate = URI.create(rawUrl);
        String candidatePath = candidate.getRawPath() == null ? "" : candidate.getRawPath();
        for (String base : bases) {
            URI configured = URI.create(base);
            if (!sameOrigin(candidate, configured)) {
                continue;
            }
            String basePath = trimTrailingSlash(
                    configured.getRawPath() == null ? "" : configured.getRawPath());
            if (candidatePath.equals(basePath)
                    || candidatePath.startsWith(basePath.isEmpty() ? "/" : basePath + "/")) {
                return base;
            }
        }
        return null;
    }

    /** Whether a URL uses the same HTTPS origin as a configured base. */
    public static boolean hasConfiguredOrigin(String rawUrl, List<String> bases) {
        if (!UpdateUrlValidator.isValidHttpsUrl(rawUrl) || bases == null) {
            return false;
        }
        URI candidate = URI.create(rawUrl);
        for (String base : bases) {
            if (sameOrigin(candidate, URI.create(base))) {
                return true;
            }
        }
        return false;
    }

    public static boolean sameOrigin(String rawUrl, String configuredBase) {
        if (!UpdateUrlValidator.isValidHttpsUrl(rawUrl)
                || !UpdateUrlValidator.isValidHttpsUrl(configuredBase)) {
            return false;
        }
        return sameOrigin(URI.create(rawUrl), URI.create(configuredBase));
    }

    public static String hostForBase(String base) {
        if (base == null || !UpdateUrlValidator.isValidHttpsUrl(base)) {
            return null;
        }
        return normalizeHost(URI.create(base).getHost());
    }

    public static String appendAsset(String base, String assetName) {
        if (base == null || assetName == null || assetName.isEmpty()) {
            return "";
        }
        return trimTrailingSlash(base.trim()) + "/" + assetName;
    }

    /** Build the deterministic APK retry queue from the selected edge and configured fallbacks. */
    public static List<String> buildApkDownloadQueue(List<String> orderedBases, String assetName,
                                                      String primaryUrl, boolean customSource,
                                                      String githubUrl, String legacyUrl) {
        List<String> urls = new ArrayList<>();
        if (customSource || primaryUrl == null || primaryUrl.isEmpty()) {
            addSafeUrl(urls, primaryUrl);
        }
        if (orderedBases != null && assetName != null && SAFE_ASSET_NAME.matcher(assetName).matches()) {
            for (String base : orderedBases) {
                addSafeUrl(urls, appendAsset(base, assetName));
            }
        }
        addSafeUrl(urls, primaryUrl);
        addSafeUrl(urls, githubUrl);
        addSafeUrl(urls, legacyUrl);
        return urls;
    }

    private static void addSafeUrl(List<String> urls, String candidate) {
        if (candidate != null && !candidate.isEmpty()
                && UpdateUrlValidator.isValidHttpsUrl(candidate)
                && !urls.contains(candidate)) {
            urls.add(candidate);
        }
    }

    private static String validWinner(JSONObject session, Set<String> configuredHosts) {
        if (session == null) {
            return null;
        }
        String winner = normalizeHost(session.optString("winner", ""));
        return configuredHosts.contains(winner) ? winner : null;
    }

    private static String normalizeHost(String host) {
        return host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left != null && right != null
                && "https".equalsIgnoreCase(left.getScheme())
                && "https".equalsIgnoreCase(right.getScheme())
                && left.getHost() != null
                && right.getHost() != null
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() < 0 ? 443 : uri.getPort();
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/") && !result.endsWith("://")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
