// 同步声明：此实现与另一包中的 GoUiPreferences 保持同步；除包名外不得分叉。
package com.gamecenter.app.go;

import android.content.Context;
import android.content.SharedPreferences;
import com.gamecenter.app.core.common.ModuleScopedPreferences;

/** Persistent presentation preferences shared by every Go game surface. */
public final class GoUiPreferences {

    public static final String PREFERENCES_NAME = "game_go_ui";
    public static final String KEY_BOARD_STYLE = "board_style_v1";

    /** 模块作用域 ID（必须与 catalog.json 中 go 模块 id 一致） */
    private static final String MODULE_ID = "go";

    private GoUiPreferences() {}

    public static boolean isSimpleMode(Context context) {
        return preferences(context).getBoolean(KEY_BOARD_STYLE, false);
    }

    public static void setSimpleMode(Context context, boolean simpleMode) {
        preferences(context).edit().putBoolean(KEY_BOARD_STYLE, simpleMode).apply();
    }

    private static SharedPreferences preferences(Context context) {
        // Phase 3 数据隔离：迁移旧扁平 SP 并使用作用域 SP（mod_go__game_go_ui）
        ModuleScopedPreferences.migrateFrom(context, MODULE_ID, PREFERENCES_NAME);
        return ModuleScopedPreferences.get(context, MODULE_ID, PREFERENCES_NAME);
    }
}
