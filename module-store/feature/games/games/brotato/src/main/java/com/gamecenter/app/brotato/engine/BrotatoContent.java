package com.gamecenter.app.brotato.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Brotato 的只读内容表：manifest + enemies + waves 三段 JSON 一次装载成不可变对象。
 *
 * <p>纯 JVM、零 Android 依赖，可在单元测试里直接 {@code new String(Files.readAllBytes(...))}
 * 读生产资产后调用 {@link #load(String, String, String)}，不需要 AssetManager 或 Context。
 * 三段文本由装载方（{@code BrotatoModuleFragment} / 测试）提供，本类自己不碰文件系统。</p>
 *
 * <p>fail-closed 策略：内容缺失、字段越界、波次表引用了未定义的敌人 kind、wave 序列出现空洞、
 * gameId 不是 brotato —— 一律抛 {@link IllegalStateException} 并带上可读原因，
 * 绝不"跳过坏条目继续跑"。因此下游 {@link BrotatoArena} 可以无条件信任拿到的每个数值。</p>
 *
 * <p>口径：{@link #kinds()} 的 key 与 {@link EnemyKind#id()} 都是小写规范 id
 * （swarm / grunt / runner / zigzag / splitter / minion / tank / boss）；
 * {@link #levels()} 按 {@link WaveLevel#wave()} 升序且 1..{@link #totalWaves()} 连续。</p>
 */
public final class BrotatoContent {

    /** 内容 schema 版本（解析器只接受它）。 */
    public static final int SCHEMA_VERSION = BrotatoContentParser.SCHEMA_VERSION;
    /** 内容包必须声明的 gameId。 */
    public static final String GAME_ID = BrotatoContentParser.GAME_ID;

    private final Map<String, EnemyKind> kinds;
    private final List<WaveLevel> levels;
    private final int contentVersion;
    private final String enemiesFile;
    private final String wavesFile;

    private BrotatoContent(Map<String, EnemyKind> kinds, List<WaveLevel> levels, int contentVersion,
                           String enemiesFile, String wavesFile) {
        this.kinds = kinds;
        this.levels = levels;
        this.contentVersion = contentVersion;
        this.enemiesFile = enemiesFile;
        this.wavesFile = wavesFile;
    }

    /**
     * 装载整包内容。
     *
     * @param manifestJson manifest.json 文本（提供 contentVersion 与 files 映射）
     * @param enemiesJson enemies.json 文本
     * @param wavesJson   waves.json 文本
     * @return 不可变内容表
     * @throws IllegalStateException 任一段文本非法、缺失或自相矛盾（fail-closed）
     */
    public static BrotatoContent load(String manifestJson, String enemiesJson, String wavesJson) {
        try {
            BrotatoContentParser.Manifest manifest = BrotatoContentParser.parseManifest(manifestJson);
            Map<String, EnemyKind> kinds = BrotatoContentParser.parseEnemyKinds(enemiesJson);
            List<WaveLevel> levels = BrotatoContentParser.parseWaveLevels(wavesJson, kinds);
            return new BrotatoContent(kinds, levels, manifest.contentVersion(),
                    manifest.enemiesFile(), manifest.wavesFile());
        } catch (IllegalStateException exact) {
            throw exact; // 解析器已经带上了 fail-closed 原因，原样上抛
        } catch (RuntimeException broken) {
            throw BrotatoContentParser.fail("unexpected content failure: " + broken);
        }
    }

    /** 小写 id → 敌人定义；不可变，含全部已定义种类。 */
    public Map<String, EnemyKind> kinds() {
        return kinds;
    }

    /** 波表；不可变，按 wave 升序且 1..totalWaves 连续。 */
    public List<WaveLevel> levels() {
        return levels;
    }

    /** 总波数（与 {@link #levels()} 长度一致，来自 waves.json 的 totalWaves 声明）。 */
    public int totalWaves() {
        return levels.size();
    }

    /** 内容版本，来自 manifest.contentVersion。 */
    public int contentVersion() {
        return contentVersion;
    }

    /** manifest 声明的 enemies 文件路径（供装载方自检/日志）。 */
    public String enemiesFile() {
        return enemiesFile;
    }

    /** manifest 声明的 waves 文件路径。 */
    public String wavesFile() {
        return wavesFile;
    }

    /** 按 id 取敌人定义（大小写不敏感）；未定义返回 null。 */
    public EnemyKind kind(String id) {
        if (id == null) return null;
        return kinds.get(id.trim().toLowerCase(Locale.US));
    }

    /** 该 id 是否已定义。 */
    public boolean hasKind(String id) {
        return kind(id) != null;
    }

    /** 按波号取波表项（1 起）；越界返回 null。 */
    public WaveLevel levelForWave(int wave) {
        int index = wave - 1;
        if (index < 0 || index >= levels.size()) return null;
        return levels.get(index);
    }

    /** 是否存在可分裂的敌人且 minion 已定义（{@link BrotatoArena} 的分裂管线前置条件）。 */
    public boolean splitSupported() {
        if (!kinds.containsKey(EnemyKind.MINION_ID)) return false;
        for (EnemyKind kind : kinds.values()) {
            if (kind.split()) return true;
        }
        return false;
    }

    /** 全部已定义 id（升序），内容与测试排错用。 */
    public List<String> kindIds() {
        List<String> ids = new ArrayList<>(kinds.keySet());
        Collections.sort(ids);
        return ids;
    }

    @Override
    public String toString() {
        return "BrotatoContent(contentVersion=" + contentVersion + ",kinds=" + kinds.size()
                + ",waves=" + levels.size() + ")";
    }
}
