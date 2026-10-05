package com.gamecenter.app.sokoban;

import java.util.HashSet;
import java.util.Set;

/**
 * 推箱子战役进度状态机（纯 Java，无 Android 依赖）。
 *
 * <p>只维护「哪些关已清、哪些章已完」的状态与解锁规则，不含持久化：
 * UI 层在 {@link #recordLevelCleared} 后调用 {@link #clearedLevelKeys()}
 * 与 {@link #clearedChapterKeys()} 导出并落盘，重启时经构造函数回灌。</p>
 *
 * <p>解锁规则：章 1 恒开；章 N（N≥2）需章 N-1 全部关卡完成。
 * 关卡 key 格式为 {@code "章-关"}，如 {@code "1-2"} 表示第 1 章第 2 关。</p>
 */
public final class SokobanCampaignProgress {

    private final Set<Integer> clearedChapters;
    private final Set<String> clearedLevels;

    /**
     * @param clearedLevels   已完成关卡 key 集合（允许 {@code null}，按空处理）
     * @param clearedChapters 已完成章节 id 集合（允许 {@code null}，按空处理）
     */
    public SokobanCampaignProgress(Set<String> clearedLevels, Set<Integer> clearedChapters) {
        this.clearedLevels = new HashSet<>();
        if (clearedLevels != null) {
            this.clearedLevels.addAll(clearedLevels);
        }
        this.clearedChapters = new HashSet<>();
        if (clearedChapters != null) {
            this.clearedChapters.addAll(clearedChapters);
        }
    }

    /** 关卡 key：{@code chapter + "-" + index}。 */
    private static String key(int chapter, int index) {
        return chapter + "-" + index;
    }

    /** 指定关是否已完成。 */
    public boolean isLevelCleared(int chapter, int index) {
        return clearedLevels.contains(key(chapter, index));
    }

    /**
     * 指定章是否解锁：章 1 恒开；章 N 需章 N-1 全部关卡完成。
     * 章号不存在（含非法值）时返回 {@code false}。
     */
    public boolean isChapterUnlocked(int chapter) {
        if (chapter == 1) {
            return true;
        }
        if (SokobanCampaign.chapter(chapter) == null
                || SokobanCampaign.chapter(chapter - 1) == null) {
            return false;
        }
        return isChapterCleared(chapter - 1);
    }

    /** 指定章是否全部关卡完成（章号不存在返回 {@code false}）。 */
    public boolean isChapterCleared(int chapter) {
        SokobanCampaign.Chapter ch = SokobanCampaign.chapter(chapter);
        if (ch == null) {
            return false;
        }
        for (SokobanCampaign.CampaignLevel level : ch.levels) {
            if (!isLevelCleared(chapter, level.indexInChapter)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 记录关卡通关（幂等）；若该章关卡集齐则自动记入已完成章节。
     */
    public void recordLevelCleared(int chapter, int index) {
        clearedLevels.add(key(chapter, index));
        if (isChapterCleared(chapter)) {
            clearedChapters.add(chapter);
        }
    }

    /** 导出已完成关卡 key 集合（持久化用，返回副本）。 */
    public Set<String> clearedLevelKeys() {
        return new HashSet<>(clearedLevels);
    }

    /** 导出已完成章节 id 集合（持久化用，返回副本）。 */
    public Set<Integer> clearedChapterKeys() {
        return new HashSet<>(clearedChapters);
    }
}
