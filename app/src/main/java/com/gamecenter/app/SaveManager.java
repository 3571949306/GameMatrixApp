package com.gamecenter.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 类名：SaveManager
 * 职责：通用存档管理器，为所有游戏提供统一的存档/读档/删除接口
 * 关联类：被各游戏 Activity 调用（SudokuActivity、KlotskiActivity、SokobanActivity、Game2048Activity）
 * 生命周期：单例模式，随应用进程存活
 * 注意事项：
 *   - key 格式为 "save_{gameId}_{slotKey}"，避免不同游戏存档冲突
 *   - 存储内容为 JSON 字符串，各游戏自行负责序列化/反序列化
 *   - 关卡进度使用独立的 key 格式 "progress_{gameId}"
 */
public final class SaveManager {

    private static final String PREFS_NAME = "gamecenter_saves";
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
                    instance = new SaveManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * 方法作用：保存游戏状态
     * @param gameId  游戏标识（如 "sudoku"、"klotski"、"sokoban"、"2048"）
     * @param slotKey 存档槽位（如 "auto"、"slot1"）
     * @param jsonState 序列化后的游戏状态 JSON 字符串
     * 调用时机：Activity.onPause() 中自动保存
     * 副作用：写入 SharedPreferences
     */
    public void save(String gameId, String slotKey, String jsonState) {
        prefs.edit().putString(buildSaveKey(gameId, slotKey), jsonState).apply();
    }

    /**
     * 方法作用：读取游戏状态
     * @param gameId  游戏标识
     * @param slotKey 存档槽位
     * @return 序列化的游戏状态 JSON 字符串；无存档返回 null
     * 调用时机：Activity.onCreate() 中检测是否有存档
     * 副作用：无
     */
    public String load(String gameId, String slotKey) {
        return prefs.getString(buildSaveKey(gameId, slotKey), null);
    }

    /**
     * 方法作用：检查是否存在存档
     * @param gameId  游戏标识
     * @param slotKey 存档槽位
     * @return true 表示存在存档
     * 调用时机：Activity.onCreate() 中判断是否弹出恢复对话框
     * 副作用：无
     */
    public boolean hasSave(String gameId, String slotKey) {
        return prefs.contains(buildSaveKey(gameId, slotKey));
    }

    /**
     * 方法作用：删除存档
     * @param gameId  游戏标识
     * @param slotKey 存档槽位
     * 调用时机：开始新游戏、游戏通关后
     * 副作用：从 SharedPreferences 中移除对应 key
     */
    public void deleteSave(String gameId, String slotKey) {
        prefs.edit().remove(buildSaveKey(gameId, slotKey)).apply();
    }

    /**
     * 方法作用：保存关卡进度（解锁关卡号 + 每关最佳记录）
     * @param gameId     游戏标识
     * @param jsonProgress 序列化后的进度 JSON 字符串
     * 调用时机：通关时保存
     * 副作用：写入 SharedPreferences
     */
    public void saveProgress(String gameId, String jsonProgress) {
        prefs.edit().putString(buildProgressKey(gameId), jsonProgress).apply();
    }

    /**
     * 方法作用：读取关卡进度
     * @param gameId 游戏标识
     * @return 序列化的进度 JSON 字符串；无进度返回 null
     * 调用时机：Activity.onCreate() 中恢复关卡进度
     * 副作用：无
     */
    public String loadProgress(String gameId) {
        return prefs.getString(buildProgressKey(gameId), null);
    }

    /**
     * 方法作用：检查是否存在关卡进度
     * @param gameId 游戏标识
     * @return true 表示存在进度记录
     * 副作用：无
     */
    public boolean hasProgress(String gameId) {
        return prefs.contains(buildProgressKey(gameId));
    }

    /**
     * 方法作用：删除关卡进度
     * @param gameId 游戏标识
     * 副作用：从 SharedPreferences 中移除对应 key
     */
    public void deleteProgress(String gameId) {
        prefs.edit().remove(buildProgressKey(gameId)).apply();
    }

    private String buildSaveKey(String gameId, String slotKey) {
        return KEY_PREFIX_SAVE + gameId + "_" + slotKey;
    }

    private String buildProgressKey(String gameId) {
        return KEY_PREFIX_PROGRESS + gameId;
    }
}
