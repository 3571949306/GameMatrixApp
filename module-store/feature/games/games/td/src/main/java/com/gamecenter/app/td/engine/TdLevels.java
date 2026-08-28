package com.gamecenter.app.td.engine;

import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Campaign repository. Production content is always read from the module's versioned assets;
 * Java holds only the generic loader and validation boundary, never one method per level.
 */
public final class TdLevels {
    private static final String MANIFEST_PATH = "td/manifest.json";
    private static volatile Catalog catalog;

    private TdLevels() {}

    /**
     * Loads all built-in chapter files atomically. A corrupt or incomplete module must fail closed
     * here instead of silently running a stale Java campaign.
     */
    public static synchronized void initialize(AssetManager assets) {
        if (assets == null) throw new IllegalArgumentException("TD module assets are unavailable");
        try {
            TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(readText(assets, MANIFEST_PATH));
            List<TdLevelDefinition> definitions = new ArrayList<>();
            Set<String> chapterIds = new HashSet<>();
            for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
                TdLevelJsonParser.Chapter chapter = TdLevelJsonParser.parseChapter(
                        readText(assets, "td/" + ref.file));
                if (!ref.id.equals(chapter.id) || !chapterIds.add(chapter.id)
                        || chapter.levels.size() != ref.levelCount) {
                    throw new IllegalArgumentException("TD chapter manifest mismatch: " + ref.file);
                }
                definitions.addAll(chapter.levels);
            }
            install(definitions, manifest.contentVersion);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to read TD campaign assets", exception);
        }
    }

    /** JVM-only entry point for parser/repository integration tests. */
    public static synchronized void installForTesting(List<TdLevelDefinition> definitions) {
        install(definitions, 0);
    }

    public static List<String> levelIds() {
        return requireCatalog().ids;
    }

    public static boolean isKnownLevelId(String id) {
        return id != null && requireCatalog().byId.containsKey(canonicalId(id));
    }

    public static int contentVersion() {
        return requireCatalog().contentVersion;
    }

    /** Kept source-compatible with the existing select UI; index is only a display fallback. */
    public static String levelDisplayName(int index, String id) {
        TdLevelDefinition definition = requireCatalog().byId.get(canonicalId(id));
        return definition != null ? definition.name : "第 " + (index + 1) + " 关";
    }

    public static String levelSub(int index, String id) {
        TdLevelDefinition definition = requireCatalog().byId.get(canonicalId(id));
        return definition != null ? definition.subtitle : "";
    }

    /** Builds an isolated game session from immutable validated content. */
    public static TdGame buildLevel(String id) {
        TdLevelDefinition definition = requireCatalog().byId.get(canonicalId(id));
        if (definition == null) throw new IllegalArgumentException("unknown TD level: " + id);
        return definition.newGame();
    }

    private static void install(List<TdLevelDefinition> definitions, int contentVersion) {
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException("TD campaign has no levels");
        }
        List<TdLevelDefinition> ordered = new ArrayList<>(definitions);
        ordered.sort(Comparator.comparingInt(level -> level.order));
        Map<String, TdLevelDefinition> byId = new HashMap<>();
        Set<Integer> orders = new HashSet<>();
        for (TdLevelDefinition definition : ordered) {
            if (definition == null || byId.put(definition.id, definition) != null
                    || !orders.add(definition.order)) {
                throw new IllegalArgumentException("duplicate TD level id/order");
            }
        }
        List<String> ids = new ArrayList<>();
        for (TdLevelDefinition definition : ordered) ids.add(definition.id);
        catalog = new Catalog(contentVersion, ids, byId);
    }

    private static Catalog requireCatalog() {
        Catalog value = catalog;
        if (value == null) {
            throw new IllegalStateException("TD campaign assets were not initialized");
        }
        return value;
    }

    /** Accepts five historical IDs for old callers/deep links; persisted IDs are always main_###. */
    private static String canonicalId(String id) {
        if (id != null && id.matches("level_[0-9]{2,3}")) {
            try {
                int index = Integer.parseInt(id.substring("level_".length()));
                if (index > 0 && index <= 999) return String.format(java.util.Locale.US, "main_%03d", index);
            } catch (NumberFormatException ignored) {
                // The exact regex above already excludes this, but do not allow a bad deep link through.
            }
        }
        return id;
    }

    private static String readText(AssetManager assets, String path) throws IOException {
        StringBuilder text = new StringBuilder();
        try (InputStream input = assets.open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) text.append(buffer, 0, read);
        }
        return text.toString();
    }

    private static final class Catalog {
        final int contentVersion;
        final List<String> ids;
        final Map<String, TdLevelDefinition> byId;

        Catalog(int contentVersion, List<String> ids, Map<String, TdLevelDefinition> byId) {
            this.contentVersion = contentVersion;
            this.ids = Collections.unmodifiableList(new ArrayList<>(ids));
            this.byId = Collections.unmodifiableMap(new HashMap<>(byId));
        }
    }
}
