package com.gamecenter.app.games.replay;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * P2-7 (BOARD_REPLAY): 统一棋类对局录制器。
 *
 * 为五子棋/围棋/跳棋/井字棋等棋类提供统一的走法录制、持久化与回放能力。
 * （中国象棋已有独立的 GameRecorder，不纳入此基类，避免破坏现有功能。）
 *
 * 生命周期：
 * 1. {@link #startRecording(String, int)} — 开始录制，传入 gameId 和难度
 * 2. {@link #record(ReplayMove)} — 每次走棋后调用
 * 3. {@link #endRecording(String)} — 游戏结束时持久化，传入结果（"win"/"loss"/"draw"/"ongoing"）
 *
 * 持久化：SharedPreferences（key = "replay_" + gameId），JSON 格式。
 * 每个游戏仅保留最近 {@link #MAX_RECORDS_PER_GAME} 局。
 *
 * 回放：通过 {@link #loadReplay(String, int)} 加载历史记录，
 * 再用 {@link ReplayPlayer} 逐步播放。
 */
public class ReplayRecorder {

    private static final String TAG = "ReplayRecorder";
    private static final String PREFS_NAME = "board_replay_prefs";
    private static final String KEY_PREFIX = "replay_";
    private static final int MAX_RECORDS_PER_GAME = 10;

    public static final String RESULT_WIN = "win";
    public static final String RESULT_LOSS = "loss";
    public static final String RESULT_DRAW = "draw";
    public static final String RESULT_ONGOING = "ongoing";

    private final Context context;
    private final String gameId;
    private final List<ReplayMove> moves = new ArrayList<>();
    private long startTimeMs;
    private int difficulty;
    private boolean recording = false;

    public ReplayRecorder(Context context, String gameId) {
        this.context = context.getApplicationContext();
        this.gameId = gameId;
    }

    /** 开始录制新对局 */
    public void startRecording(int difficulty) {
        moves.clear();
        this.difficulty = difficulty;
        this.startTimeMs = System.currentTimeMillis();
        this.recording = true;
        Log.d(TAG, "[" + gameId + "] 开始录制，难度=" + difficulty);
    }

    /** 记录一步走法 */
    public void record(ReplayMove move) {
        if (!recording) return;
        moves.add(move);
    }

    /** 结束录制并持久化 */
    public void endRecording(String result) {
        if (!recording) return;
        recording = false;
        long endTimeMs = System.currentTimeMillis();
        saveRecord(result, endTimeMs);
        Log.d(TAG, "[" + gameId + "] 结束录制，结果=" + result + "，共 " + moves.size() + " 步");
    }

    /** 放弃当前录制（不保存） */
    public void discardRecording() {
        recording = false;
        moves.clear();
    }

    public boolean isRecording() {
        return recording;
    }

    public List<ReplayMove> getCurrentMoves() {
        return new ArrayList<>(moves);
    }

    // ============ 持久化 ============

    private void saveRecord(String result, long endTimeMs) {
        try {
            JSONArray movesArr = new JSONArray();
            for (ReplayMove m : moves) {
                movesArr.put(m.encode());
            }
            JSONObject record = new JSONObject();
            record.put("gameId", gameId);
            record.put("difficulty", difficulty);
            record.put("startTime", startTimeMs);
            record.put("endTime", endTimeMs);
            record.put("result", result);
            record.put("moves", movesArr);

            // 读取现有记录列表
            List<JSONObject> existing = loadRawRecords();
            // 头部插入新记录
            existing.add(0, record);
            // 限制数量
            while (existing.size() > MAX_RECORDS_PER_GAME) {
                existing.remove(existing.size() - 1);
            }
            // 写回
            JSONArray arr = new JSONArray();
            for (JSONObject o : existing) arr.put(o);
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PREFIX + gameId, arr.toString())
                    .apply();
        } catch (Exception e) {
            Log.w(TAG, "saveRecord 失败: " + e.getMessage());
        }
    }

    private List<JSONObject> loadRawRecords() {
        List<JSONObject> list = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PREFIX + gameId, null);
        if (raw == null || raw.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getJSONObject(i));
            }
        } catch (Exception e) {
            Log.w(TAG, "loadRawRecords 解析失败: " + e.getMessage());
        }
        return list;
    }

    /**
     * 加载该游戏的历史回放列表。
     * @return 回放记录列表（最新的在前）
     */
    public List<ReplayRecord> loadHistory() {
        List<ReplayRecord> result = new ArrayList<>();
        for (JSONObject o : loadRawRecords()) {
            try {
                result.add(ReplayRecord.fromJson(o));
            } catch (Exception e) {
                Log.w(TAG, "loadHistory 解析单条失败: " + e.getMessage());
            }
        }
        return result;
    }

    /** 获取最近一局回放（无则返回 null） */
    public ReplayRecord loadLatest() {
        List<ReplayRecord> list = loadHistory();
        return list.isEmpty() ? null : list.get(0);
    }

    /** 清空该游戏的全部回放记录 */
    public void clearHistory() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PREFIX + gameId)
                .apply();
    }

    /** 是否有回放记录 */
    public boolean hasHistory() {
        return !loadRawRecords().isEmpty();
    }
}
