package com.gamecenter.app.games.save;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

/**
 * 统一游戏存档管理器（2026-08-23 P2-1 中断续玩系统）。
 *
 * <p>为回合制/可暂停游戏提供按 gameId 的进度持久化能力：
 * 每局进行中的游戏在关键节点（落子/移动）调用 {@link #save} 保存状态 JSON，
 * 正常结束（胜/负/和）后调用 {@link #clear} 清除存档；
 * 再次进入游戏时通过 {@link #hasSave} 检测未完成对局，
 * 弹出"继续上局"对话框并经 {@link #load} 恢复。</p>
 *
 * <p>存储结构与项目内 ReplayRecorder 保持一致：SharedPreferences + org.json。
 * 外层包装统一元信息（保存时间戳），游戏特定状态放在 state 字段内：</p>
 * <pre>
 * {"savedAt": 1690000000000, "state": { ... 游戏自定义字段 ... }}
 * </pre>
 *
 * <p>设计约束：
 * <ul>
 *   <li>所有方法静默容错——存档失败不得影响游戏主流程</li>
 *   <li>单实例按 gameId 无状态读写，可任意线程调用（内部用 apply 异步落盘）</li>
 * </ul>
 * </p>
 */
public class GameSaveManager {

    private static final String TAG = "GameSaveManager";

    private static final String PREFS_NAME = "game_save_store";
    private static final String KEY_PREFIX = "save_";
    /** 外层元信息字段：保存时间戳 */
    private static final String FIELD_SAVED_AT = "savedAt";
    /** 游戏特定状态字段 */
    private static final String FIELD_STATE = "state";

    private final Context context;

    public GameSaveManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 保存游戏进度（state 由各游戏自行序列化为 JSONObject） */
    public void save(String gameId, JSONObject state) {
        if (gameId == null || gameId.isEmpty() || state == null) return;
        try {
            JSONObject wrapper = new JSONObject();
            wrapper.put(FIELD_SAVED_AT, System.currentTimeMillis());
            wrapper.put(FIELD_STATE, state);
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PREFIX + gameId, wrapper.toString())
                    .apply();
        } catch (Exception e) {
            Log.w(TAG, "save 失败 gameId=" + gameId + ": " + e.getMessage());
        }
    }

    /**
     * 加载游戏进度。
     *
     * @return 游戏状态 JSONObject；无存档或解析失败返回 null
     */
    public JSONObject load(String gameId) {
        if (gameId == null || gameId.isEmpty()) return null;
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + gameId, null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            JSONObject wrapper = new JSONObject(raw);
            return wrapper.optJSONObject(FIELD_STATE);
        } catch (Exception e) {
            Log.w(TAG, "load 解析失败 gameId=" + gameId + ": " + e.getMessage());
            return null;
        }
    }

    /** 是否存在未完成对局的存档 */
    public boolean hasSave(String gameId) {
        if (gameId == null || gameId.isEmpty()) return false;
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + gameId, null);
        return raw != null && !raw.isEmpty();
    }

    /** 存档保存时间（用于 UI 展示"上次进度"），无存档返回 0 */
    public long getSaveTime(String gameId) {
        if (gameId == null || gameId.isEmpty()) return 0;
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + gameId, null);
        if (raw == null || raw.isEmpty()) return 0;
        try {
            JSONObject wrapper = new JSONObject(raw);
            return wrapper.optLong(FIELD_SAVED_AT, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 清除存档（游戏正常结束后调用） */
    public void clear(String gameId) {
        if (gameId == null || gameId.isEmpty()) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PREFIX + gameId)
                .apply();
    }
}
