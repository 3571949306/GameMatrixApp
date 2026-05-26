package com.gamecenter.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 游戏存档管理器（Java 版，用于子模块编译引用）。
 * 运行时由 Kotlin 版 SaveManager 提供实际实现（通过 Hilt 注入或静态单例）。
 */
public final class SaveManager {
    private static final String PREFS_NAME = "GameMatrix_saves";
    private static final String KEY_PREFIX_SAVE = "save_";
    private static final String KEY_PREFIX_PROGRESS = "progress_";

    private static volatile SaveManager instance;
    private final SharedPreferences prefs;

    private SaveManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static SaveManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SaveManager.class) {
                if (instance == null) {
                    instance = new SaveManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void save(String gameId, String slotKey, String jsonState) {
        prefs.edit().putString(KEY_PREFIX_SAVE + gameId + "_" + slotKey, jsonState).apply();
    }

    public String load(String gameId, String slotKey) {
        return prefs.getString(KEY_PREFIX_SAVE + gameId + "_" + slotKey, null);
    }

    public boolean hasSave(String gameId, String slotKey) {
        return prefs.contains(KEY_PREFIX_SAVE + gameId + "_" + slotKey);
    }

    public void deleteSave(String gameId, String slotKey) {
        prefs.edit().remove(KEY_PREFIX_SAVE + gameId + "_" + slotKey).apply();
    }

    public void saveProgress(String gameId, String jsonProgress) {
        prefs.edit().putString(KEY_PREFIX_PROGRESS + gameId, jsonProgress).apply();
    }

    public String loadProgress(String gameId) {
        return prefs.getString(KEY_PREFIX_PROGRESS + gameId, null);
    }

    public boolean hasProgress(String gameId) {
        return prefs.contains(KEY_PREFIX_PROGRESS + gameId);
    }

    public void deleteProgress(String gameId) {
        prefs.edit().remove(KEY_PREFIX_PROGRESS + gameId).apply();
    }
}
