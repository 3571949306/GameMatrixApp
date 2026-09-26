package com.gamecenter.app.sokoban;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 推箱子每日挑战完成记录规则（纯 Java，无 Android 依赖）。
 *
 * <p>只含规则逻辑，不含持久化：UI 层持有「日期 key -> 当日最少步数」
 * 的 Map 并负责落盘，本类在其上提供完成判定、成绩刷新与最近 N 天
 * 完成状态查询。同日重复挑战仅保留更少步数（Wordle 模式），
 * 连续完成天数由 {@link #recentStreak} 供 UI 层推算。</p>
 */
public final class SokobanDailyRecord {

    /** 日期 key 长度（"yyyy-MM-dd" 恰好 10 字符）。 */
    private static final int DATE_KEY_LENGTH = 10;

    private SokobanDailyRecord() {
    }

    /**
     * 指定日期是否已完成（当日 map 中存在成绩即完成）。
     *
     * @param bestMovesByDate 日期 key -> 当日最少步数（未完成不在 map）；null 按空处理
     * @param dateKey         日期 key（yyyy-MM-dd）
     * @return 已完成返回 true
     */
    public static boolean isCompleted(Map<String, Integer> bestMovesByDate, String dateKey) {
        if (bestMovesByDate == null || dateKey == null) {
            return false;
        }
        return bestMovesByDate.containsKey(dateKey);
    }

    /**
     * 记录/刷新当日成绩。
     *
     * <ul>
     *   <li>未完成 → 记录并返回 true（首次）；</li>
     *   <li>已完成且新步数更少 → 更新并返回 true；</li>
     *   <li>否则返回 false（未刷新）。</li>
     * </ul>
     *
     * @param bestMovesByDate 日期 key -> 当日最少步数；null 时合法入参按首次
     *                        记录成功容错（内部容错防崩，UI 层保证传入有效 map）
     * @param dateKey         日期 key（yyyy-MM-dd）
     * @param moves           本局步数（<=0 非法，返回 false）
     * @return 本次是否记录/刷新
     */
    public static boolean record(Map<String, Integer> bestMovesByDate, String dateKey, int moves) {
        boolean valid = dateKey != null && dateKey.length() == DATE_KEY_LENGTH && moves > 0;
        if (bestMovesByDate == null) {
            return valid;
        }
        if (!valid) {
            return false;
        }
        Integer best = bestMovesByDate.get(dateKey);
        if (best == null || moves < best) {
            bestMovesByDate.put(dateKey, moves);
            return true;
        }
        return false;
    }

    /**
     * 最近 N 天（含今天，下标 0=今天）的完成状态列表。
     *
     * <p>元素为当日最少步数，未完成为 null；逐日 key 由
     * {@link SokobanDailyPuzzle#dateKeyDaysBack} 推算。</p>
     *
     * @param bestMovesByDate 日期 key -> 当日最少步数；null 按空处理（全 null）
     * @param todayKey        今天日期 key（yyyy-MM-dd）
     * @param days            天数（含今天）
     * @return 下标 0=今天、长度 days 的列表；todayKey 非法或 days<=0 时返回空列表
     */
    public static List<Integer> recentStreak(Map<String, Integer> bestMovesByDate,
            String todayKey, int days) {
        List<Integer> result = new ArrayList<>();
        if (todayKey == null || todayKey.length() != DATE_KEY_LENGTH || days <= 0) {
            return result;
        }
        // 借道 dateKeyDaysBack 做完整日期合法性校验（含 2026-99-99 之类越界值）
        if (SokobanDailyPuzzle.dateKeyDaysBack(todayKey, 0) == null) {
            return result;
        }
        for (int i = 0; i < days; i++) {
            String key = SokobanDailyPuzzle.dateKeyDaysBack(todayKey, i);
            result.add(key == null || bestMovesByDate == null ? null : bestMovesByDate.get(key));
        }
        return result;
    }
}
