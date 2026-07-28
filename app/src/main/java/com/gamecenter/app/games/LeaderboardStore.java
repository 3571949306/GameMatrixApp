package com.gamecenter.app.games;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 本地排行榜存储（P0-1）。
 * <p>
 * 复用 {@link GameUsageStore} 的 SharedPreferences（"game_usage"），避免数据分散，
 * 并自动被 {@code DataBackupHelper} 的导出/导入流程覆盖。
 * </p>
 * <p>
 * 每款游戏维护一个 Top-N 榜（默认 10 条），按分数降序排列。
 * 同时维护一个全局总分榜（跨游戏，按各游戏最高分之和排序）。
 * </p>
 */
public final class LeaderboardStore {
    private static final String PREFS_NAME = "game_usage";
    private static final String KEY_LEADERBOARD_PREFIX = "leaderboard_";
    private static final String KEY_GLOBAL_LEADERBOARD = "leaderboard_global";
    private static final int DEFAULT_MAX_ENTRIES = 10;

    private final SharedPreferences prefs;
    private final Gson gson;

    public LeaderboardStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    /** 排行榜条目 */
    public static final class Entry {
        public int score;
        public long timestamp;
        public int difficultyIndex;
        public String difficultyName;
        public long durationMs;

        public Entry() {}

        public Entry(int score, long timestamp, int difficultyIndex,
                     @NonNull String difficultyName, long durationMs) {
            this.score = score;
            this.timestamp = timestamp;
            this.difficultyIndex = difficultyIndex;
            this.difficultyName = difficultyName;
            this.durationMs = durationMs;
        }
    }

    /** 提交一局成绩，自动插入并保留 Top-N。
     * @return 入榜排名（1-based），未入榜返回 -1 */
    public int submitScore(@NonNull String gameId, int score, int difficultyIndex,
                           @NonNull String difficultyName, long durationMs) {
        if (gameId.isEmpty() || score <= 0) return -1;
        List<Entry> board = getLeaderboard(gameId);
        Entry newEntry = new Entry(score, System.currentTimeMillis(),
                difficultyIndex, difficultyName, durationMs);
        board.add(newEntry);
        // 按分数降序
        Collections.sort(board, (a, b) -> Integer.compare(b.score, a.score));
        // 截断到 max
        int max = DEFAULT_MAX_ENTRIES;
        if (board.size() > max) {
            board = new ArrayList<>(board.subList(0, max));
        }
        saveBoard(KEY_LEADERBOARD_PREFIX + gameId, board);

        // 计算排名
        int rank = -1;
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).timestamp == newEntry.timestamp
                    && board.get(i).score == newEntry.score) {
                rank = i + 1;
                break;
            }
        }
        return rank;
    }

    /** 获取某游戏 Top-N 榜（按分数降序） */
    @NonNull
    public List<Entry> getLeaderboard(@NonNull String gameId) {
        if (gameId.isEmpty()) return new ArrayList<>();
        return readBoard(KEY_LEADERBOARD_PREFIX + gameId);
    }

    /** 获取某游戏历史最高分（兼容 GameUsageStore.getHighScore） */
    public int getHighScore(@NonNull String gameId) {
        List<Entry> board = getLeaderboard(gameId);
        return board.isEmpty() ? 0 : board.get(0).score;
    }

    /** 获取所有游戏的总分榜（跨游戏，按各游戏最高分排序） */
    @NonNull
    public List<GlobalEntry> getGlobalLeaderboard() {
        List<GlobalEntry> result = new ArrayList<>();
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            if (e.getKey().startsWith(KEY_LEADERBOARD_PREFIX) && e.getValue() instanceof String) {
                String gameId = e.getKey().substring(KEY_LEADERBOARD_PREFIX.length());
                if (gameId.isEmpty() || gameId.equals("global")) continue;
                List<Entry> board = readBoard(e.getKey());
                if (board.isEmpty()) continue;
                int high = board.get(0).score;
                long lastTs = board.get(0).timestamp;
                int total = 0;
                for (Entry en : board) total += en.score;
                result.add(new GlobalEntry(gameId, high, total, board.size(), lastTs));
            }
        }
        Collections.sort(result, (a, b) -> Integer.compare(b.highScore, a.highScore));
        return result;
    }

    /** 清空某游戏榜单 */
    public void clearLeaderboard(@NonNull String gameId) {
        prefs.edit().remove(KEY_LEADERBOARD_PREFIX + gameId).apply();
    }

    /** 清空所有榜单 */
    public void clearAll() {
        Map<String, ?> all = prefs.getAll();
        SharedPreferences.Editor ed = prefs.edit();
        for (String key : all.keySet()) {
            if (key.startsWith(KEY_LEADERBOARD_PREFIX)) {
                ed.remove(key);
            }
        }
        ed.apply();
    }

    @NonNull
    private List<Entry> readBoard(@NonNull String key) {
        String json = prefs.getString(key, null);
        if (TextUtils.isEmpty(json)) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<Entry>>() {}.getType();
            List<Entry> list = gson.fromJson(json, type);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveBoard(@NonNull String key, @NonNull List<Entry> board) {
        try {
            prefs.edit().putString(key, gson.toJson(board)).apply();
        } catch (Exception ignored) {}
    }

    /** 全局榜条目 */
    public static final class GlobalEntry {
        public final String gameId;
        public final int highScore;
        public final int totalScore;
        public final int entriesCount;
        public final long lastPlayedTimestamp;

        public GlobalEntry(String gameId, int highScore, int totalScore,
                           int entriesCount, long lastPlayedTimestamp) {
            this.gameId = gameId;
            this.highScore = highScore;
            this.totalScore = totalScore;
            this.entriesCount = entriesCount;
            this.lastPlayedTimestamp = lastPlayedTimestamp;
        }
    }
}
