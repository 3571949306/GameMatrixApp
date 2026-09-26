package com.gamecenter.app.chinesechess;

import android.content.Context;
import android.content.SharedPreferences;
import com.gamecenter.app.core.common.ModuleScopedPreferences;

/** 中国象棋模块自己的轻量 UI 偏好，避免动态模块依赖宿主全局设置实现。 */
final class ChineseChessUiPreferences {

    private static final String PREFS_NAME = "game_chinesechess_ui";
    /** 模块作用域 ID（必须与 catalog.json 中 chinesechess 模块 id 一致） */
    private static final String MODULE_ID = "chinesechess";
    private static final String KEY_BOARD_STYLE = "board_style_v1";
    private static final String STYLE_ENHANCED = "enhanced";
    private static final String STYLE_SIMPLE = "simple";
    /** 已通关残局 id 列表（逗号分隔 String，避免 StringSet 跨实例缓存问题） */
    private static final String KEY_ENDGAME_SOLVED = "endgame_solved_v1";

    private ChineseChessUiPreferences() {}

    static boolean isSimpleMode(Context context) {
        return STYLE_SIMPLE.equals(preferences(context).getString(
                KEY_BOARD_STYLE, STYLE_ENHANCED));
    }

    static void setSimpleMode(Context context, boolean enabled) {
        preferences(context).edit()
                .putString(KEY_BOARD_STYLE, enabled ? STYLE_SIMPLE : STYLE_ENHANCED)
                .apply();
    }

    /** 指定残局是否已通关。 */
    static boolean isEndgameSolved(Context context, int levelId) {
        String raw = preferences(context).getString(KEY_ENDGAME_SOLVED, "");
        if (raw == null || raw.isEmpty()) return false;
        String target = String.valueOf(levelId);
        for (String part : raw.split(",")) {
            if (target.equals(part)) return true;
        }
        return false;
    }

    /** 标记残局通关（幂等）。 */
    static void markEndgameSolved(Context context, int levelId) {
        if (isEndgameSolved(context, levelId)) return;
        String raw = preferences(context).getString(KEY_ENDGAME_SOLVED, "");
        String next = (raw == null || raw.isEmpty())
                ? String.valueOf(levelId) : raw + "," + levelId;
        preferences(context).edit()
                .putString(KEY_ENDGAME_SOLVED, next)
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        // Phase 3 数据隔离：迁移旧扁平 SP 并使用作用域 SP（mod_chinesechess__game_chinesechess_ui）
        ModuleScopedPreferences.migrateFrom(context, MODULE_ID, PREFS_NAME);
        return ModuleScopedPreferences.get(context, MODULE_ID, PREFS_NAME);
    }
}
