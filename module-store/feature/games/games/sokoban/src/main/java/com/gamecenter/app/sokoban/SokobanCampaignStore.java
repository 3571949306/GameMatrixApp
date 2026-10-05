package com.gamecenter.app.sokoban;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.gamecenter.app.core.common.ModuleScopedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 战役模式进度存储（模块作用域 SP）。
 *
 * <p>沿用 {@link SokobanCustomLevels} 的模块作用域偏好模式
 * （mod_sokoban__sokoban_campaign）：已通关关卡 key（{@code chapter-index}，
 * 如 {@code 1-2}）以逗号分隔存于 {@code cleared_levels}，已通关章 id 以
 * 逗号分隔存于 {@code cleared_chapters}。用 String 而非 StringSet，
 * 避免跨实例缓存问题（工程约定）。坏段（格式非法）丢弃并留日志，不影响其余数据。</p>
 */
final class SokobanCampaignStore {

    private static final String TAG = "SokobanCampaignStore";
    private static final String MODULE_ID = "sokoban";
    private static final String PREFS_NAME = "sokoban_campaign";
    private static final String KEY_CLEARED_LEVELS = "cleared_levels";
    private static final String KEY_CLEARED_CHAPTERS = "cleared_chapters";
    /** 关卡 key 格式：章 id 与章内序号均为非负整数，如 {@code 1-2}。 */
    private static final String LEVEL_KEY_PATTERN = "\\d+-\\d+";

    private SokobanCampaignStore() {
    }

    /** 从模块作用域 SP 重建战役进度（无数据时得到空进度）。 */
    static SokobanCampaignProgress load(Context context) {
        SharedPreferences prefs = preferences(context);
        return new SokobanCampaignProgress(
                splitLevelKeys(prefs.getString(KEY_CLEARED_LEVELS, "")),
                splitChapterIds(prefs.getString(KEY_CLEARED_CHAPTERS, "")));
    }

    /** 把进度导出的已通关关卡 key 与章 id 两个 Set 写回 SP。 */
    static void save(Context context, SokobanCampaignProgress progress) {
        preferences(context).edit()
                .putString(KEY_CLEARED_LEVELS, joinKeys(progress.clearedLevelKeys()))
                .putString(KEY_CLEARED_CHAPTERS, joinIds(progress.clearedChapterKeys()))
                .apply();
    }

    /** 解析逗号分隔的关卡 key 集合；格式非法的段丢弃并留日志。 */
    private static Set<String> splitLevelKeys(String raw) {
        Set<String> keys = new HashSet<>();
        if (raw == null || raw.isEmpty()) {
            return keys;
        }
        for (String part : raw.split(",")) {
            String key = part.trim();
            if (key.isEmpty()) {
                continue;
            }
            if (!key.matches(LEVEL_KEY_PATTERN)) {
                Log.w(TAG, "dropping malformed campaign level key: " + key);
                continue;
            }
            keys.add(key);
        }
        return keys;
    }

    /** 解析逗号分隔的章 id 集合；非数字段丢弃并留日志。 */
    private static Set<Integer> splitChapterIds(String raw) {
        Set<Integer> ids = new HashSet<>();
        if (raw == null || raw.isEmpty()) {
            return ids;
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException e) {
                // 棘轮门禁：catch 吞异常必须留痕，禁止静默 continue（verify_ratchet silent_return）。
                Log.w(TAG, "dropping malformed campaign chapter id: " + trimmed, e);
            }
        }
        return ids;
    }

    /** 关卡 key 集合转逗号分隔字符串（null 视为空集）。 */
    private static String joinKeys(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "";
        }
        return String.join(",", keys);
    }

    /** 章 id 集合转逗号分隔字符串（null 视为空集）。 */
    private static String joinIds(Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private static SharedPreferences preferences(Context context) {
        ModuleScopedPreferences.migrateFrom(context, MODULE_ID, PREFS_NAME);
        return ModuleScopedPreferences.get(context, MODULE_ID, PREFS_NAME);
    }
}
