package com.gamecenter.app.chinesechess;

import android.content.Context;
import android.content.SharedPreferences;

/** 中国象棋模块自己的轻量 UI 偏好，避免动态模块依赖宿主全局设置实现。 */
final class ChineseChessUiPreferences {

    private static final String PREFS_NAME = "game_chinesechess_ui";
    private static final String KEY_BOARD_STYLE = "board_style_v1";
    private static final String STYLE_ENHANCED = "enhanced";
    private static final String STYLE_SIMPLE = "simple";

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

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
    }
}
