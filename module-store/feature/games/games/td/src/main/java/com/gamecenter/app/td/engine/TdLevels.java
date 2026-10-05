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
import java.util.Locale;
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
            List<ChapterGroup> chapters = new ArrayList<>();
            Set<String> chapterIds = new HashSet<>();
            for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
                TdLevelJsonParser.Chapter chapter = TdLevelJsonParser.parseChapter(
                        readText(assets, "td/" + ref.file));
                chapters.add(buildChapterGroup(ref, chapter, chapterIds));
                definitions.addAll(chapter.levels);
            }
            install(definitions, manifest.contentVersion, chapters);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to read TD campaign assets", exception);
        }
    }

    /** JVM-only entry point for parser/repository integration tests. */
    public static synchronized void installForTesting(List<TdLevelDefinition> definitions) {
        install(definitions, 0, Collections.emptyList());
    }

    /**
     * JVM-only entry point that also installs chapter grouping metadata (select-UI sections).
     * {@code definitions} must be listed in manifest chapter order; slices are re-sorted by
     * level order so every group's ids follow the global campaign order.
     */
    public static synchronized void installForTesting(TdLevelJsonParser.Manifest manifest,
                                                      List<TdLevelDefinition> definitions) {
        if (manifest == null || definitions == null) {
            throw new IllegalArgumentException("TD test campaign is incomplete");
        }
        List<TdLevelDefinition> ordered = new ArrayList<>(definitions);
        ordered.sort(Comparator.comparingInt(level -> level.order));
        List<ChapterGroup> chapters = new ArrayList<>();
        int cursor = 0;
        for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
            int end = cursor + ref.levelCount;
            if (end > ordered.size()) {
                throw new IllegalArgumentException("TD manifest exceeds test definitions: " + ref.id);
            }
            List<String> groupIds = new ArrayList<>();
            for (TdLevelDefinition level : ordered.subList(cursor, end)) groupIds.add(level.id);
            chapters.add(new ChapterGroup(ref.id, ref.name, ref.nameEn, groupIds));
            cursor = end;
        }
        if (cursor != ordered.size()) {
            throw new IllegalArgumentException("TD test definitions do not cover the manifest");
        }
        install(definitions, manifest.contentVersion, chapters);
    }

    /**
     * JVM-only entry mirroring the production {@link #initialize} chapter resolution: every
     * manifest ref is matched to its parsed chapter object, display names go through the same
     * fallback chain (manifest entry first, then the chapter file's own Chinese name), and each
     * group carries that chapter's own order-sorted levels. Lets JVM tests exercise the exact
     * production grouping path without an {@link AssetManager}. Named distinctly from
     * {@link #installForTesting} because a {@code List<Chapter>} overload would clash with the
     * {@code List<TdLevelDefinition>} erasure.
     */
    static synchronized void installChaptersForTesting(TdLevelJsonParser.Manifest manifest,
                                                       List<TdLevelJsonParser.Chapter> chapters) {
        if (manifest == null || chapters == null || chapters.size() != manifest.chapters.size()) {
            throw new IllegalArgumentException("TD test chapters do not match the manifest");
        }
        Map<String, TdLevelJsonParser.Chapter> chapterById = new HashMap<>();
        for (TdLevelJsonParser.Chapter chapter : chapters) chapterById.put(chapter.id, chapter);
        List<TdLevelDefinition> definitions = new ArrayList<>();
        List<ChapterGroup> groups = new ArrayList<>();
        Set<String> chapterIds = new HashSet<>();
        for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
            TdLevelJsonParser.Chapter chapter = chapterById.get(ref.id);
            if (chapter == null) {
                throw new IllegalArgumentException(
                        "TD manifest chapter has no parsed chapter: " + ref.id);
            }
            groups.add(buildChapterGroup(ref, chapter, chapterIds));
            definitions.addAll(chapter.levels);
        }
        install(definitions, manifest.contentVersion, groups);
    }

    /**
     * Validates a manifest ref against its parsed chapter and builds the select-UI group.
     * Chapter display names: the manifest entry is the grouping source of truth; a manifest
     * that omits the localized names falls back to the chapter file's own Chinese name (and
     * to it again for English), mirroring the level name_en fallback chain.
     */
    private static ChapterGroup buildChapterGroup(TdLevelJsonParser.ChapterRef ref,
                                                  TdLevelJsonParser.Chapter chapter,
                                                  Set<String> chapterIds) {
        if (!ref.id.equals(chapter.id) || !chapterIds.add(chapter.id)
                || chapter.levels.size() != ref.levelCount) {
            throw new IllegalArgumentException("TD chapter manifest mismatch: " + ref.file);
        }
        String sourceName = ref.name != null ? ref.name : chapter.name;
        String sourceNameEn = ref.nameEn != null ? ref.nameEn : sourceName;
        List<TdLevelDefinition> chapterLevels = new ArrayList<>(chapter.levels);
        chapterLevels.sort(Comparator.comparingInt(level -> level.order));
        List<String> groupIds = new ArrayList<>();
        for (TdLevelDefinition level : chapterLevels) groupIds.add(level.id);
        return new ChapterGroup(ref.id, sourceName, sourceNameEn, groupIds);
    }

    public static List<String> levelIds() {
        return requireCatalog().ids;
    }

    /**
     * Chapter sections for the select UI, in campaign order. Empty when the catalog was
     * installed without manifest metadata (legacy {@code installForTesting(List)} path);
     * the select UI falls back to the flat layout in that case.
     */
    public static List<ChapterGroup> chapterGroups() {
        return requireCatalog().chapters;
    }

    public static boolean isKnownLevelId(String id) {
        return id != null && requireCatalog().byId.containsKey(canonicalId(id));
    }

    public static int contentVersion() {
        return requireCatalog().contentVersion;
    }

    /**
     * Kept source-compatible with the existing select UI; index is only a display fallback
     * (English neutral). Locale resolution mirrors host resources: only English locales read
     * the English fields, every other locale falls back to the Chinese source data.
     */
    public static String levelDisplayName(int index, String id) {
        TdLevelDefinition definition = requireCatalog().byId.get(canonicalId(id));
        if (definition == null) {
            return String.format(java.util.Locale.US, "Level %d", index + 1);
        }
        return preferSourceText() ? definition.name : definition.nameEn;
    }

    public static String levelSub(int index, String id) {
        TdLevelDefinition definition = requireCatalog().byId.get(canonicalId(id));
        if (definition == null) return "";
        return preferSourceText() ? definition.subtitle : definition.subtitleEn;
    }

    /** 开战前故事；无剧情时返回空串，调用方不弹故事面板。 */
    public static String levelStoryIntro(String id) {
        TdLevelDefinition definition = id == null ? null : requireCatalog().byId.get(canonicalId(id));
        if (definition == null) return "";
        return preferSourceText() ? definition.storyIntro : definition.storyIntroEn;
    }

    /** 胜利后尾声；无剧情时返回空串，结算面板不加故事行。 */
    public static String levelStoryOutro(String id) {
        TdLevelDefinition definition = id == null ? null : requireCatalog().byId.get(canonicalId(id));
        if (definition == null) return "";
        return preferSourceText() ? definition.storyOutro : definition.storyOutroEn;
    }

    /**
     * Chinese is the authoritative source text. Only explicit English locales read the
     * translated fields, so a third language (ja, ...) falls back to the source text exactly
     * like host resources fall back to the default values/ strings.
     */
    private static boolean preferSourceText() {
        return !"en".equals(Locale.getDefault().getLanguage());
    }

    /** Builds an isolated game session from immutable validated content. */
    public static TdGame buildLevel(String id) {
        TdLevelDefinition definition = requireCatalog().byId.get(canonicalId(id));
        if (definition == null) throw new IllegalArgumentException("unknown TD level: " + id);
        return definition.newGame();
    }

    /**
     * Builds an isolated game session with an explicit mode. ENDLESS keeps the level's map,
     * routes, starting coin and egg HP, but the session never ends in victory once the defined
     * waves run out — {@link TdEndlessWaveFactory} synthesizes follow-up waves instead.
     * A null mode safely keeps the default CAMPAIGN behaviour.
     */
    public static TdGame buildLevel(String id, TdGame.Mode mode) {
        TdGame game = buildLevel(id);
        game.setMode(mode);
        return game;
    }

    private static void install(List<TdLevelDefinition> definitions, int contentVersion,
                                List<ChapterGroup> chapters) {
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException("TD campaign has no levels");
        }
        List<TdLevelDefinition> ordered = new ArrayList<>(definitions);
        ordered.sort(Comparator.comparingInt(level -> level.order));
        // Runs before the duplicate rejection: an overlapping chapter range (only reachable
        // with a duplicated order through the slice-based path) deserves the specific
        // interleaving message over the generic duplicate one.
        requireMonotonicChapterOrders(chapters, ordered);
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
        catalog = new Catalog(contentVersion, ids, byId,
                chapters == null ? Collections.<ChapterGroup>emptyList() : chapters);
    }

    /**
     * The select UI maps a click inside a chapter to the global level index via chapter prefix
     * sums ({@code firstIdx} in TdModuleFragment), which is only correct when the chapter order
     * ranges are strictly increasing: every chapter's minimum order must exceed the previous
     * chapter's maximum order. Interleaved cross-chapter orders would render a misaligned select
     * layout, so installation fails closed. Chapters installed without metadata (legacy
     * {@link #installForTesting(List)} path) carry no groups and are not checked.
     */
    private static void requireMonotonicChapterOrders(List<ChapterGroup> chapters,
                                                      List<TdLevelDefinition> orderedLevels) {
        if (chapters == null || chapters.isEmpty()) return;
        Map<String, Integer> orderById = new HashMap<>();
        for (TdLevelDefinition level : orderedLevels) orderById.put(level.id, level.order);
        int previousMax = Integer.MIN_VALUE;
        for (ChapterGroup group : chapters) {
            if (group.levelIds().isEmpty()) {
                throw new IllegalArgumentException("TD chapter group has no levels: " + group.id);
            }
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (String levelId : group.levelIds()) {
                Integer order = orderById.get(levelId);
                if (order == null) {
                    throw new IllegalArgumentException(
                            "TD chapter group references unknown level: " + levelId);
                }
                min = Math.min(min, order);
                max = Math.max(max, order);
            }
            if (min <= previousMax) {
                throw new IllegalArgumentException("章节 order 跨章交错:章节 " + group.id
                        + " 的最小 order(" + min + ")未大于前一章最大 order(" + previousMax
                        + "),跨章 order 交错会导致选关分组点击位置错位,拒绝装载");
            }
            previousMax = max;
        }
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

    /**
     * Select-UI chapter section: stable chapter id, locale-resolved display name and its
     * ordered level ids. Locale resolution mirrors {@link #levelDisplayName(int, String)}:
     * only English locales read the English field, every other locale falls back to the
     * Chinese source data.
     */
    public static final class ChapterGroup {
        public final String id;
        /** Chinese source text (authoritative); null only when no localized name exists at all. */
        public final String name;
        public final String nameEn;
        private final List<String> levelIds;

        ChapterGroup(String id, String name, String nameEn, List<String> levelIds) {
            this.id = id;
            this.name = name;
            this.nameEn = nameEn != null ? nameEn : name;
            this.levelIds = Collections.unmodifiableList(new ArrayList<>(levelIds));
        }

        /** Display name for the current locale; the stable id is the last-resort neutral fallback. */
        public String displayName() {
            String value = preferSourceText() ? name : nameEn;
            return value != null ? value : id;
        }

        public List<String> levelIds() {
            return levelIds;
        }
    }

    private static final class Catalog {
        final int contentVersion;
        final List<String> ids;
        final Map<String, TdLevelDefinition> byId;
        final List<ChapterGroup> chapters;

        Catalog(int contentVersion, List<String> ids, Map<String, TdLevelDefinition> byId,
                List<ChapterGroup> chapters) {
            this.contentVersion = contentVersion;
            this.ids = Collections.unmodifiableList(new ArrayList<>(ids));
            this.byId = Collections.unmodifiableMap(new HashMap<>(byId));
            this.chapters = Collections.unmodifiableList(new ArrayList<>(chapters));
        }
    }
}
