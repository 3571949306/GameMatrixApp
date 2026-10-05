package com.gamecenter.app.td;

import java.util.ArrayList;
import java.util.List;

/**
 * 塔防「保卫蛋蛋」成就定义（纯数据枚举，无 Android 依赖，JVM 可测）。
 *
 * <p>条件全部可从既有游戏事件判定：通关（recordWin）、累计击杀（addKills）、
 * 无尽最佳波数（recordEndlessWaves）、简单与困难均通关（setEasyCleared/setHardCleared）。
 * 每个成就持久化为独立布尔键（{@link #saveKey}，td_achv_ 前缀，沿用模块 td_ 键约定），
 * 只增不减：解锁后不随任何状态回退而撤销，clearAll 一并清除。
 *
 * <p>判定分两条路径且共用同一份条件数据（kind/campaignLevelId/threshold），不另设语义：
 * <ul>
 *   <li>写入点（事件驱动）：TdSaveManager 的 recordWin/addKills/recordEndlessWaves/
 *       setEasyCleared/setHardCleared 后经 {@code matching*} 即时判定，支持解锁瞬间 UI 提示；</li>
 *   <li>补漏（幂等回溯）：TdSaveManager.syncAchievementsFromState 按当前持久化状态
 *       补齐「条件已满足但尚未落章」的成就（老存档升级 + 纵深兜底）。</li>
 * </ul>
 */
public enum TdAchievement {

    /** 初试啼声：通关 main_001 */
    FIRST_WIN(Kind.CAMPAIGN_CLEAR, "td_achv_first_win", "main_001", 0),
    /** 小试牛刀：通关第 1 章（终关 main_005） */
    CHAPTER1_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch1_cleared", "main_005", 0),
    /** 中流砥柱：通关第 2 章（终关 main_015） */
    CHAPTER2_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch2_cleared", "main_015", 0),
    /** 风暴之王：通关第 3 章（终关 main_025） */
    CHAPTER3_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch3_cleared", "main_025", 0),
    /** 熔灰终章：通关 main_035（第 4 章终关） */
    CHAPTER4_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch4_cleared", "main_035", 0),
    /** 余烬守望：通关 main_045（第 5 章终关） */
    CHAPTER5_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch5_cleared", "main_045", 0),
    /** 常春凯旋：通关 main_050（第 6 章「常青之春」终关） */
    CHAPTER6_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch6_cleared", "main_050", 0),
    /** 长夜破晓：通关 main_055（第 7 章「未醒之谷」终关） */
    CHAPTER7_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch7_cleared", "main_055", 0),
    /** 夏夜烟火：通关 main_060（第 8 章「夏潮彼岸」终关） */
    CHAPTER8_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch8_cleared", "main_060", 0),
    /** 秋实加冕：通关 main_065（第 9 章「秋实之夜」终关） */
    CHAPTER9_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch9_cleared", "main_065", 0),
    /** 冬至长明：通关 main_070（第 10 章「冬至长明」终关） */
    CHAPTER10_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch10_cleared", "main_070", 0),
    /** 夜巡之冠：通关 main_075（第 11 章「夜巡试炼」终关） */
    CHAPTER11_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch11_cleared", "main_075", 0),
    /** 烽灯归营：通关 main_080（第 12 章「烽灯连营」终关） */
    CHAPTER12_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch12_cleared", "main_080", 0),
    /** 雪峰终哨：通关 main_085（第 13 章「雪峰终哨」终关，全战役收束） */
    CHAPTER13_CLEARED(Kind.CAMPAIGN_CLEAR, "td_achv_ch13_cleared", "main_085", 0),
    /** 神射手：累计击杀 ≥ 100 */
    KILLS_100(Kind.TOTAL_KILLS, "td_achv_kills_100", null, 100),
    /** 杀神：累计击杀 ≥ 1000 */
    KILLS_1000(Kind.TOTAL_KILLS, "td_achv_kills_1000", null, 1000),
    /** 无尽新兵：任意难度无尽最佳 ≥ 10 波 */
    ENDLESS_10(Kind.ENDLESS_WAVES, "td_achv_endless_10", null, 10),
    /** 无尽战神：任意难度无尽最佳 ≥ 25 波 */
    ENDLESS_25(Kind.ENDLESS_WAVES, "td_achv_endless_25", null, 25),
    /** 双冠王：简单与困难难度均通关过 */
    DUAL_CROWN(Kind.DUAL_CLEAR, "td_achv_dual_crown", null, 0);

    /** 条件类型：决定解锁判定（写入点 matching* 与补漏 sync）走哪条路径 */
    enum Kind { CAMPAIGN_CLEAR, TOTAL_KILLS, ENDLESS_WAVES, DUAL_CLEAR }

    final Kind kind;
    /** 存档布尔键（完整键名，td_achv_ 前缀；各枚举必须唯一，见 TdSaveManagerTest） */
    final String saveKey;
    /** CAMPAIGN_CLEAR 的「已通关」判定关 id（章节终关）；其余类型为 null */
    final String campaignLevelId;
    /** TOTAL_KILLS / ENDLESS_WAVES 的阈值（≥ 即解锁）；其余类型为 0 */
    final int threshold;

    TdAchievement(Kind kind, String saveKey, String campaignLevelId, int threshold) {
        this.kind = kind;
        this.saveKey = saveKey;
        this.campaignLevelId = campaignLevelId;
        this.threshold = threshold;
    }

    /** 通关事件应触发的成就（纯函数，JVM 可测）：null/未知关 id 一律返回空表。 */
    static List<TdAchievement> matchingCampaignWin(String clearedLevelId) {
        List<TdAchievement> out = new ArrayList<>();
        if (clearedLevelId == null) return out;
        for (TdAchievement a : values()) {
            if (a.kind == Kind.CAMPAIGN_CLEAR && clearedLevelId.equals(a.campaignLevelId)) out.add(a);
        }
        return out;
    }

    /** 累计击杀事件应触发的成就（纯函数，JVM 可测）；一次大额累加跨多档时按枚举序全部返回。 */
    static List<TdAchievement> matchingTotalKills(int totalKills) {
        return matchingThreshold(Kind.TOTAL_KILLS, totalKills);
    }

    /** 无尽波数事件应触发的成就（纯函数，JVM 可测）：入参为各难度最佳波数的最大值。 */
    static List<TdAchievement> matchingEndlessWaves(int bestWavesAnyDifficulty) {
        return matchingThreshold(Kind.ENDLESS_WAVES, bestWavesAnyDifficulty);
    }

    /** 双难度通关事件应触发的成就（纯函数，JVM 可测）：简单与困难都通关才算。 */
    static List<TdAchievement> matchingDualClear(boolean easyCleared, boolean hardCleared) {
        List<TdAchievement> out = new ArrayList<>();
        if (easyCleared && hardCleared) {
            for (TdAchievement a : values()) {
                if (a.kind == Kind.DUAL_CLEAR) out.add(a);
            }
        }
        return out;
    }

    private static List<TdAchievement> matchingThreshold(Kind kind, int value) {
        List<TdAchievement> out = new ArrayList<>();
        for (TdAchievement a : values()) {
            if (a.kind == kind && value >= a.threshold) out.add(a);
        }
        return out;
    }
}
