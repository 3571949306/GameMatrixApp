package com.gamecenter.app.games.chinesechess;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.games.chinesechess.data.GameRecordDatabase;
import com.gamecenter.app.games.chinesechess.data.dao.GameRecordDao;
import com.gamecenter.app.games.chinesechess.data.entity.GameRecordEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 中国象棋对局记录管理器。
 *
 * <p>管理对局的完整生命周期：开始记录 → 记录走法/提示/错误 → 结束记录。
 * 对局数据持久化到 Room 数据库，支持查询历史和导出 PGN。</p>
 */
public class GameRecorder {

    private GameRecord currentRecord;
    private final GameRecordDao dao;
    private final ExecutorService executor;

    public GameRecorder(@NonNull Context context) {
        this.dao = GameRecordDatabase.getInstance(context).gameRecordDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 开始记录一局新对局。
     *
     * @param difficulty AI 难度等级（1-4）
     */
    public void startRecording(int difficulty) {
        currentRecord = new GameRecord();
        currentRecord.setStartTime(System.currentTimeMillis());
        currentRecord.setDifficulty(difficulty);
    }

    /**
     * 记录一步走法。
     *
     * @param move  走法坐标 [fromRow, fromCol, toRow, toCol]
     * @param score 走法评分
     */
    public void recordMove(int[] move, int score) {
        if (currentRecord == null) return;
        currentRecord.addMove(move);
        currentRecord.addScore(score);
    }

    /**
     * 记录一次 AI 提示。
     *
     * @param hint 提示结果
     */
    public void recordHint(HintResult hint) {
        if (currentRecord == null) return;
        currentRecord.addHint(hint);
    }

    /**
     * 记录一次错误分析。
     *
     * @param mistake 错误分析结果
     */
    public void recordMistake(MistakeResult mistake) {
        if (currentRecord == null) return;
        currentRecord.addMistake(mistake);
    }

    /**
     * 结束记录并持久化对局数据。
     *
     * @param result 对局结果
     */
    public void endRecording(GameResult result) {
        if (currentRecord == null) return;
        currentRecord.setEndTime(System.currentTimeMillis());
        currentRecord.setResult(result);

        final GameRecord record = currentRecord;
        executor.execute(() -> {
            GameRecordEntity entity = toEntity(record);
            dao.insert(entity);
        });

        currentRecord = null;
    }

    /**
     * 获取当前正在记录的对局（未结束时）。
     */
    @Nullable
    public GameRecord getCurrentRecord() {
        return currentRecord;
    }

    /**
     * 获取全部历史对局记录（同步读取，应在子线程调用）。
     */
    @NonNull
    public List<GameRecord> getHistory() {
        List<GameRecordEntity> entities = dao.getAll();
        List<GameRecord> records = new ArrayList<>();
        for (GameRecordEntity entity : entities) {
            records.add(fromEntity(entity));
        }
        return records;
    }

    /**
     * 按 gameId 查询单条对局记录（同步读取，应在子线程调用）。
     */
    @Nullable
    public GameRecord getRecord(String gameId) {
        GameRecordEntity entity = dao.getById(gameId);
        return entity != null ? fromEntity(entity) : null;
    }

    /**
     * 异步获取历史对局列表。
     */
    public void getHistoryAsync(@NonNull Callback<List<GameRecord>> callback) {
        executor.execute(() -> callback.onResult(getHistory()));
    }

    /**
     * 异步获取单条对局记录。
     */
    public void getRecordAsync(String gameId, @NonNull Callback<GameRecord> callback) {
        executor.execute(() -> callback.onResult(getRecord(gameId)));
    }

    // ==================== Entity 转换 ====================

    private static GameRecordEntity toEntity(GameRecord record) {
        GameRecordEntity entity = new GameRecordEntity();
        entity.gameId = record.getGameId();
        entity.startTime = record.getStartTime();
        entity.endTime = record.getEndTime();
        entity.result = record.getResult() != null ? record.getResult().name() : null;
        entity.difficulty = record.getDifficulty();
        entity.durationMs = record.getDuration();
        entity.movesJson = movesToJson(record.getMoves());
        entity.scoresJson = intListToJson(record.getScores());
        entity.hintsJson = hintsToJson(record.getHints());
        entity.mistakesJson = mistakesToJson(record.getMistakes());
        return entity;
    }

    private static GameRecord fromEntity(GameRecordEntity entity) {
        GameRecord record = new GameRecord(entity.gameId);
        record.setStartTime(entity.startTime);
        record.setEndTime(entity.endTime);
        record.setDifficulty(entity.difficulty);
        try {
            record.setResult(GameResult.valueOf(entity.result));
        } catch (Exception e) {
            record.setResult(null);
        }
        record.setMoves(jsonToMoves(entity.movesJson));
        record.setScores(jsonToIntList(entity.scoresJson));
        record.setHints(jsonToHints(entity.hintsJson));
        record.setMistakes(jsonToMistakes(entity.mistakesJson));
        return record;
    }

    // ==================== JSON 序列化 ====================

    private static String movesToJson(List<int[]> moves) {
        JSONArray arr = new JSONArray();
        for (int[] m : moves) {
            JSONArray inner = new JSONArray();
            for (int v : m) inner.put(v);
            arr.put(inner);
        }
        return arr.toString();
    }

    private static List<int[]> jsonToMoves(String json) {
        List<int[]> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONArray inner = arr.getJSONArray(i);
                int[] move = new int[inner.length()];
                for (int j = 0; j < inner.length(); j++) {
                    move[j] = inner.getInt(j);
                }
                list.add(move);
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    private static String intListToJson(List<Integer> list) {
        JSONArray arr = new JSONArray();
        for (int v : list) arr.put(v);
        return arr.toString();
    }

    private static List<Integer> jsonToIntList(String json) {
        List<Integer> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getInt(i));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    private static String hintsToJson(List<HintResult> hints) {
        JSONArray arr = new JSONArray();
        for (HintResult h : hints) {
            JSONArray inner = new JSONArray();
            if (h.getMove() != null) {
                JSONArray mv = new JSONArray();
                for (int v : h.getMove()) mv.put(v);
                inner.put(mv);
            } else {
                inner.put(JSONObject.NULL);
            }
            inner.put(h.getExplanation());
            inner.put(h.getComputeTimeMs());
            inner.put(h.getDifficulty());
            arr.put(inner);
        }
        return arr.toString();
    }

    private static List<HintResult> jsonToHints(String json) {
        List<HintResult> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONArray inner = arr.getJSONArray(i);
                int[] move = null;
                if (!inner.isNull(0)) {
                    JSONArray mvArr = inner.getJSONArray(0);
                    move = new int[mvArr.length()];
                    for (int j = 0; j < mvArr.length(); j++) {
                        move[j] = mvArr.getInt(j);
                    }
                }
                String explanation = inner.optString(1, "");
                long computeTimeMs = inner.optLong(2, 0);
                int difficulty = inner.optInt(3, 1);
                list.add(new HintResult(move, explanation, computeTimeMs, difficulty));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    private static String mistakesToJson(List<MistakeResult> mistakes) {
        JSONArray arr = new JSONArray();
        for (MistakeResult m : mistakes) {
            JSONArray inner = new JSONArray();
            inner.put(m.type != null ? m.type.name() : "NO_MISTAKE");
            inner.put(m.scoreDiff);
            inner.put(m.explanation);
            if (m.betterMove != null) {
                JSONArray bm = new JSONArray();
                for (int v : m.betterMove) bm.put(v);
                inner.put(bm);
            } else {
                inner.put(JSONObject.NULL);
            }
            arr.put(inner);
        }
        return arr.toString();
    }

    private static List<MistakeResult> jsonToMistakes(String json) {
        List<MistakeResult> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONArray inner = arr.getJSONArray(i);
                MistakeAnalyzer.MistakeType type;
                try {
                    type = MistakeAnalyzer.MistakeType.valueOf(inner.optString(0, "NO_MISTAKE"));
                } catch (IllegalArgumentException e) {
                    type = MistakeAnalyzer.MistakeType.NO_MISTAKE;
                }
                int scoreDiff = inner.optInt(1, 0);
                String explanation = inner.optString(2, "");
                int[] betterMove = null;
                if (!inner.isNull(3)) {
                    JSONArray bmArr = inner.getJSONArray(3);
                    betterMove = new int[bmArr.length()];
                    for (int j = 0; j < bmArr.length(); j++) {
                        betterMove[j] = bmArr.getInt(j);
                    }
                }
                list.add(new MistakeResult(type, scoreDiff, explanation, betterMove));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    // ==================== 回调接口 ====================

    public interface Callback<T> {
        void onResult(T result);
    }
}
