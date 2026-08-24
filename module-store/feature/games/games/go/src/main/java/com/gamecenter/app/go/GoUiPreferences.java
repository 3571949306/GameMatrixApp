// 同步声明：此实现与另一包中的 GoUiPreferences 保持同步；除包名外不得分叉。
package com.gamecenter.app.go;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent presentation preferences shared by every Go game surface. */
public final class GoUiPreferences {

    public static final String PREFERENCES_NAME = "game_go_ui";
    public static final String KEY_BOARD_STYLE = "board_style_v1";

    private GoUiPreferences() {}

    public static boolean isSimpleMode(Context context) {
        return preferences(context).getBoolean(KEY_BOARD_STYLE, false);
    }

    public static void setSimpleMode(Context context, boolean simpleMode) {
        preferences(context).edit().putBoolean(KEY_BOARD_STYLE, simpleMode).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
