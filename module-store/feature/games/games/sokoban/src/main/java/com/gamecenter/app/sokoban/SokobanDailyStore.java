package com.gamecenter.app.sokoban;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.gamecenter.app.core.common.ModuleScopedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 每日关卡挑战记录存储（模块作用域 SP）。
 *
 * <p>沿用 {@link SokobanCampaignStore} 的模块作用域偏好模式
 * （mod_sokoban__sokoban_daily）：每日最佳步数表以行式文本存于
 * {@code best_moves}，每行一条 {@code 日期:步数}（如 {@code 2026-09-24:7}），
 * 行间以 \n 分隔。用 String 行式存储而非 StringSet，避免跨实例缓存问题
 * （工程约定）。坏行（日期/步数格式非法）丢弃并留日志，不影响其余数据。</p>
 *
 * <p>日期 key 口径与纯 Java 层 {@code SokobanDailyPuzzle} 一致：
 * {@code yyyy-MM-dd}（Locale.US）。本类只做持久化编解码，完成判定、
 * 纪录刷新与连击查询等规则由 {@code SokobanDailyRecord} 承载。</p>
 */
final class SokobanDailyStore {

    private static final String TAG = "SokobanDailyStore";
    private static final String MODULE_ID = "sokoban";
    private static final String PREFS_NAME = "sokoban_daily";
    private static final String KEY_BEST_MOVES = "best_moves";
    /** 日期 key 格式：yyyy-MM-dd（与每日纯 Java 层同口径）。 */
    private static final String DATE_KEY_PATTERN = "\\d{4}-\\d{2}-\\d{2}";

    private SokobanDailyStore() {
    }

    /**
     * 从模块作用域 SP 重建每日最佳步数表（无数据时得到空表）。
     *
     * <p>返回 {@link TreeMap}（按日期升序），save 输出行序确定。</p>
     */
    static Map<String, Integer> load(Context context) {
        Map<String, Integer> bestMoves = new TreeMap<>();
        String raw = preferences(context).getString(KEY_BEST_MOVES, "");
        if (raw == null || raw.isEmpty()) {
            return bestMoves;
        }
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int sep = trimmed.indexOf(':');
            if (sep <= 0) {
                Log.w(TAG, "dropping malformed daily record line: " + trimmed);
                continue;
            }
            String dateKey = trimmed.substring(0, sep);
            if (!dateKey.matches(DATE_KEY_PATTERN)) {
                Log.w(TAG, "dropping malformed daily record date: " + trimmed);
                continue;
            }
            try {
                int moves = Integer.parseInt(trimmed.substring(sep + 1));
                if (moves <= 0) {
                    Log.w(TAG, "dropping non-positive daily record moves: " + trimmed);
                    continue;
                }
                bestMoves.put(dateKey, moves);
            } catch (NumberFormatException e) {
                // 棘轮门禁：catch 吞异常必须留痕，禁止静默 continue（verify_ratchet silent_return）。
                Log.w(TAG, "dropping malformed daily record moves: " + trimmed, e);
            }
        }
        return bestMoves;
    }

    /** 把每日最佳步数表整体写回 SP（每行 {@code 日期:步数}，\n 分隔；null 视为空表）。 */
    static void save(Context context, Map<String, Integer> bestMovesByDate) {
        StringBuilder sb = new StringBuilder();
        if (bestMovesByDate != null) {
            for (Map.Entry<String, Integer> entry : bestMovesByDate.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(entry.getKey()).append(':').append(entry.getValue());
            }
        }
        preferences(context).edit().putString(KEY_BEST_MOVES, sb.toString()).apply();
    }

    /** 今日日期 key（yyyy-MM-dd，Locale.US）；面板与完成分发经它与每日纯 Java 层同口径取日期。 */
    static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static SharedPreferences preferences(Context context) {
        ModuleScopedPreferences.migrateFrom(context, MODULE_ID, PREFS_NAME);
        return ModuleScopedPreferences.get(context, MODULE_ID, PREFS_NAME);
    }
}
