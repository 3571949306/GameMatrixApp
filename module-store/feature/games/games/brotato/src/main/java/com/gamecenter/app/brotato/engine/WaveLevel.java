package com.gamecenter.app.brotato.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 一波的调度表（waves.json 的一个 level）：纯数据，不带时间状态。
 *
 * <p>出怪计时由 {@link BrotatoArena} 持有，本类只描述"该出多少只、多久出一只、按权重出谁"，
 * 以及（可选）定点 Boss 编组。加权抽取写成 {@link #entryForRoll(int)} 这样的确定性取卡口，
 * 随机数由引擎的唯一随机源产生，因此本类自身无状态、可直接单测。</p>
 */
public final class WaveLevel {

    /** composition 的一项：某种敌人的小写 id + 抽取权重（weight ≥ 1）。 */
    public static final class CompEntry {
        private final String kind;
        private final int weight;

        public CompEntry(String kind, int weight) {
            this.kind = EnemyKind.normalizeId(kind);
            this.weight = weight;
        }

        /** 引用的敌人 id（小写规范形式，可直接喂给 {@link BrotatoContent#kinds()}）。 */
        public String kind() {
            return kind;
        }

        /** 抽取权重，加载期保证 ≥ 1。 */
        public int weight() {
            return weight;
        }

        @Override
        public String toString() {
            return kind + ":" + weight;
        }
    }

    /** 本波的 Boss 编组；{@link WaveLevel#bossGroup()} 返回 null 表示该波没有 Boss。 */
    public static final class BossGroup {
        private final String kind;
        private final int count;
        private final int intervalMs;
        private final int delayMs;

        public BossGroup(String kind, int count, int intervalMs, int delayMs) {
            this.kind = EnemyKind.normalizeId(kind);
            this.count = count;
            this.intervalMs = intervalMs;
            this.delayMs = delayMs;
        }

        /** Boss 的敌人 id（小写规范形式）。 */
        public String kind() {
            return kind;
        }

        /** 本波要出的 Boss 只数（≥ 1）。 */
        public int count() {
            return count;
        }

        /** 相邻两只 Boss 的间隔（ms，> 0）。 */
        public int intervalMs() {
            return intervalMs;
        }

        /** 第一只 Boss 相对波开始的延迟（ms，≥ 0）。 */
        public int delayMs() {
            return delayMs;
        }

        @Override
        public String toString() {
            return kind + "x" + count + "(delay=" + delayMs + "ms,every=" + intervalMs + "ms)";
        }
    }

    private final int wave;
    private final int spawnCount;
    private final int spawnIntervalMs;
    private final List<CompEntry> composition;
    private final BossGroup bossGroup;

    public WaveLevel(int wave, int spawnCount, int spawnIntervalMs, List<CompEntry> composition,
                     BossGroup bossGroup) {
        this.wave = wave;
        this.spawnCount = spawnCount;
        this.spawnIntervalMs = spawnIntervalMs;
        this.composition = composition == null || composition.isEmpty()
                ? Collections.<CompEntry>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(composition));
        this.bossGroup = bossGroup;
    }

    /** 波号，1 起；{@link BrotatoContent#levels()} 已保证按它升序且连续。 */
    public int wave() {
        return wave;
    }

    /** 本波按 composition 权重抽取的普通怪总数（不含 Boss 编组）。 */
    public int spawnCount() {
        return spawnCount;
    }

    /** 普通怪出怪间隔（ms，> 0）；引擎再按难度倍率缩放。 */
    public int spawnIntervalMs() {
        return spawnIntervalMs;
    }

    /** 只读刷怪构成（非空）。 */
    public List<CompEntry> composition() {
        return composition;
    }

    /** Boss 编组；该波没有 Boss 时为 null。 */
    public BossGroup bossGroup() {
        return bossGroup;
    }

    /** composition 权重总和；加载期保证 ≥ 1，可直接作为随机上界。 */
    public int totalWeight() {
        int sum = 0;
        for (CompEntry entry : composition) sum += entry.weight;
        return sum;
    }

    /**
     * 按权重取一项：给定 {@code roll}（0 ≤ roll < {@link #totalWeight()}）返回确定的条目，
     * 越界时就近夹到首/尾条目，绝不抛错（引擎随机源已经保证区间）。
     */
    public CompEntry entryForRoll(int roll) {
        if (composition.isEmpty()) return null;
        int cursor = roll;
        for (CompEntry entry : composition) {
            if (cursor < entry.weight) return entry;
            cursor -= entry.weight;
        }
        return composition.get(composition.size() - 1);
    }

    /** 本波应出的 Boss 只数（无 Boss 编组时为 0）。 */
    public int bossCount() {
        return bossGroup == null ? 0 : bossGroup.count;
    }

    /** 本波出怪的 kind id 集合（小写），供内容自检与 UI 预告使用。 */
    public List<String> kindIds() {
        List<String> ids = new ArrayList<>(composition.size());
        for (CompEntry entry : composition) ids.add(entry.kind());
        return ids;
    }

    @Override
    public String toString() {
        return "WaveLevel(wave=" + wave + ",spawnCount=" + spawnCount + ",intervalMs="
                + spawnIntervalMs + ",entries=" + composition + ",boss="
                + (bossGroup == null ? "-" : bossGroup) + ")";
    }

    /** 便于调试/日志的小写 id 归一（与 {@link EnemyKind#normalizeId(String)} 同口径）。 */
    static String lower(String id) {
        return id == null ? null : id.toLowerCase(Locale.US);
    }
}
