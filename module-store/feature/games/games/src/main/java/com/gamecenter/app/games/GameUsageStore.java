package com.gamecenter.app.games;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 游戏使用数据持久化存储
 * <p>
 * 负责管理游戏中心的所有使用数据，包括收藏列表、最近游玩记录、
 * 游玩次数、最后游玩时间和详细统计数据（GameStats）。
 * 所有数据通过SharedPreferences持久化，统计对象通过Gson序列化为JSON存储。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用SharedPreferences而非数据库，因为数据量小、结构简单，避免引入额外依赖</li>
 *   <li>最近游玩列表使用逗号分隔的字符串存储，保持插入顺序，最多保留12条</li>
 *   <li>GameStats对象通过Gson序列化为JSON字符串存储，便于扩展字段</li>
 *   <li>使用ApplicationContext获取SharedPreferences，避免Activity生命周期导致的内存泄漏</li>
 *   <li>收藏列表使用LinkedHashSet返回，保持插入顺序</li>
 * </ul>
 * </p>
 */
public final class GameUsageStore {

    /** SharedPreferences文件名 */
    private static final String PREFS_NAME = "game_usage";
    /** 收藏列表的存储键 */
    private static final String KEY_FAVORITES = "favorites";
    /** 最近游玩游戏ID列表的存储键 */
    private static final String KEY_RECENT_IDS = "recent_ids";
    /** 游玩次数的键前缀，完整键为"play_count_{gameId}" */
    private static final String KEY_PLAY_COUNT_PREFIX = "play_count_";
    /** 最后游玩时间的键前缀，完整键为"last_played_{gameId}" */
    private static final String KEY_LAST_PLAYED_PREFIX = "last_played_";
    /** 统计数据JSON的键前缀，完整键为"stats_{gameId}" */
    private static final String KEY_STATS_PREFIX = "stats_";
    /** 最近游玩列表的最大保留数量 */
    private static final int MAX_RECENT_COUNT = 12;

    /** SharedPreferences实例 */
    private final SharedPreferences prefs;
    /** Gson实例，用于GameStats的序列化和反序列化 */
    private final Gson gson;

    /**
     * 构造函数
     * <p>
     * 使用ApplicationContext获取SharedPreferences，确保不会因Activity销毁而丢失引用。
     * </p>
     *
     * @param context 上下文对象，内部使用ApplicationContext
     */
    public GameUsageStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    /**
     * 记录游戏启动事件
     * <p>
     * 更新该游戏的游玩次数、最后游玩时间，并将该游戏ID移至最近游玩列表的头部。
     * 如果最近列表已满（超过MAX_RECENT_COUNT），则移除最旧的条目。
     * </p>
     *
     * @param gameId 游戏唯一标识符
     */
    public void recordLaunch(String gameId) {
        // 游玩次数加1
        int playCount = getPlayCount(gameId) + 1;
        // 从最近列表中移除已有记录（避免重复），然后插入到头部
        List<String> recentIds = getRecentIds(MAX_RECENT_COUNT);
        recentIds.remove(gameId);
        recentIds.add(0, gameId);
        // 超出上限时移除末尾最旧的条目
        while (recentIds.size() > MAX_RECENT_COUNT) {
            recentIds.remove(recentIds.size() - 1);
        }

        prefs.edit()
                .putInt(KEY_PLAY_COUNT_PREFIX + gameId, playCount)
                .putLong(KEY_LAST_PLAYED_PREFIX + gameId, System.currentTimeMillis())
                .putString(KEY_RECENT_IDS, encodeIds(recentIds))
                .apply();
    }

    /**
     * 切换游戏的收藏状态
     * <p>
     * 如果已收藏则取消收藏，未收藏则添加收藏。
     * 注意：必须先创建新的HashSet副本再修改，因为SharedPreferences.getStringSet()
     * 返回的Set不允许直接修改，否则会导致数据不一致。
     * </p>
     *
     * @param gameId 游戏唯一标识符
     * @return 切换后的收藏状态，true表示已收藏，false表示未收藏
     */
    public boolean toggleFavorite(String gameId) {
        // 必须创建副本，不能直接修改SharedPreferences返回的Set
        Set<String> favorites = new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
        boolean nowFavorite;
        if (favorites.contains(gameId)) {
            favorites.remove(gameId);
            nowFavorite = false;
        } else {
            favorites.add(gameId);
            nowFavorite = true;
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
        return nowFavorite;
    }

    /**
     * 检查游戏是否已收藏
     *
     * @param gameId 游戏唯一标识符
     * @return true表示已收藏，false表示未收藏
     */
    public boolean isFavorite(String gameId) {
        Set<String> favorites = prefs.getStringSet(KEY_FAVORITES, new HashSet<>());
        return favorites.contains(gameId);
    }

    /**
     * 获取游戏的游玩次数
     *
     * @param gameId 游戏唯一标识符
     * @return 游玩次数，未记录时返回0
     */
    public int getPlayCount(String gameId) {
        return prefs.getInt(KEY_PLAY_COUNT_PREFIX + gameId, 0);
    }

    /**
     * 获取游戏的最后游玩时间戳
     *
     * @param gameId 游戏唯一标识符
     * @return 最后游玩的时间戳（毫秒），未记录时返回0
     */
    public long getLastPlayedAt(String gameId) {
        return prefs.getLong(KEY_LAST_PLAYED_PREFIX + gameId, 0L);
    }

    /**
     * 获取最近游玩的游戏ID列表
     * <p>
     * 按最近游玩时间从新到旧排序，最多返回limit条记录。
     * </p>
     *
     * @param limit 最大返回数量
     * @return 最近游玩的游戏ID列表，按时间倒序排列
     */
    public List<String> getRecentIds(int limit) {
        List<String> ids = decodeIds(prefs.getString(KEY_RECENT_IDS, ""));
        if (ids.size() <= limit) {
            return ids;
        }
        return new ArrayList<>(ids.subList(0, limit));
    }

    /**
     * 获取所有收藏的游戏ID集合
     * <p>
     * 使用LinkedHashSet返回，保持收藏的插入顺序。
     * </p>
     *
     * @return 收藏的游戏ID集合
     */
    public Set<String> getFavoriteIds() {
        return new LinkedHashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    /**
     * 记录游戏得分，仅当新分数高于历史最高分时更新
     *
     * @param gameId 游戏唯一标识符
     * @param score  本次得分
     */
    public void recordScore(String gameId, int score) {
        GameStats stats = getStats(gameId);
        if (score > stats.highScore) {
            stats.highScore = score;
            saveStats(gameId, stats);
        }
    }

    /**
     * 记录游戏胜利
     * <p>
     * 同时更新胜利次数、总游戏次数和最后游玩时间。
     * </p>
     *
     * @param gameId 游戏唯一标识符
     */
    public void recordWin(String gameId) {
        GameStats stats = getStats(gameId);
        stats.totalWins++;
        stats.totalPlays++;
        stats.lastPlayedAt = System.currentTimeMillis();
        saveStats(gameId, stats);
    }

    /**
     * 记录游戏失败
     * <p>
     * 同时更新失败次数、总游戏次数和最后游玩时间。
     * </p>
     *
     * @param gameId 游戏唯一标识符
     */
    public void recordLoss(String gameId) {
        GameStats stats = getStats(gameId);
        stats.totalLosses++;
        stats.totalPlays++;
        stats.lastPlayedAt = System.currentTimeMillis();
        saveStats(gameId, stats);
    }

    /**
     * 记录游戏游玩时长
     * <p>
     * 累加到总游玩时间，并在以下条件更新最佳用时：
     * <ul>
     *   <li>首次记录（bestTimeMs <= 0）</li>
     *   <li>本次用时短于历史最佳用时</li>
     * </ul>
     * 注意：最佳用时指的是"最快完成时间"，越短越好。
     * </p>
     *
     * @param gameId     游戏唯一标识符
     * @param durationMs 本次游玩时长（毫秒）
     */
    public void recordPlayTime(String gameId, long durationMs) {
        GameStats stats = getStats(gameId);
        stats.totalPlayTimeMs += durationMs;
        // 首次记录或用时更短时更新最佳用时
        if (stats.bestTimeMs <= 0 || durationMs < stats.bestTimeMs) {
            stats.bestTimeMs = durationMs;
        }
        stats.lastPlayedAt = System.currentTimeMillis();
        saveStats(gameId, stats);
    }

    /**
     * 获取指定游戏的统计数据
     * <p>
     * 从SharedPreferences中读取JSON字符串并反序列化为GameStats对象。
     * 如果没有记录或JSON解析失败，返回一个初始化为0的空GameStats对象。
     * </p>
     *
     * @param gameId 游戏唯一标识符
     * @return 游戏统计数据，不会返回null
     */
    public GameStats getStats(String gameId) {
        String json = prefs.getString(KEY_STATS_PREFIX + gameId, null);
        if (json == null || json.isEmpty()) {
            return new GameStats(gameId);
        }
        try {
            return parseStatsJson(gameId, json);
        } catch (Exception e) {
            // JSON格式异常时返回空统计数据，避免崩溃
            return new GameStats(gameId);
        }
    }

    /**
     * 获取所有游戏的统计数据列表
     * <p>
     * 遍历SharedPreferences中所有以KEY_STATS_PREFIX开头的键，
     * 提取对应的GameStats对象。仅返回有实际游玩记录的数据
     * （至少有一项统计值大于0）。
     * 使用processedGameIds去重，防止同一游戏被重复添加。
     * </p>
     *
     * @return 所有有记录的游戏统计数据列表
     */
    public List<GameStats> getAllStats() {
        List<GameStats> allStats = new ArrayList<>();
        Set<String> allKeys = prefs.getAll().keySet();
        Set<String> processedGameIds = new HashSet<>();

        for (String key : allKeys) {
            // 过滤出统计数据键，排除以"_ids"结尾的非统计键
            if (key.startsWith(KEY_STATS_PREFIX) && !key.endsWith("_ids")) {
                String gameId = key.substring(KEY_STATS_PREFIX.length());
                // 去重：同一gameId只处理一次
                if (!processedGameIds.contains(gameId)) {
                    processedGameIds.add(gameId);
                    GameStats stats = getStats(gameId);
                    // 仅添加有实际记录的统计数据
                    if (stats.totalPlays > 0 || stats.highScore > 0 ||
                            stats.totalWins > 0 || stats.totalLosses > 0 ||
                            stats.totalPlayTimeMs > 0) {
                        allStats.add(stats);
                    }
                }
            }
        }

        return allStats;
    }

    /**
     * 获取所有游戏的总游玩时长
     *
     * @return 总游玩时长（毫秒）
     */
    public long getTotalPlayTimeMs() {
        long total = 0;
        for (GameStats stats : getAllStats()) {
            total += stats.totalPlayTimeMs;
        }
        return total;
    }

    /**
     * 获取所有游戏的总胜利次数
     *
     * @return 总胜利次数
     */
    public int getTotalWins() {
        int total = 0;
        for (GameStats stats : getAllStats()) {
            total += stats.totalWins;
        }
        return total;
    }

    /**
     * 获取所有游戏的总游玩次数
     *
     * @return 总游玩次数
     */
    public int getTotalPlays() {
        int total = 0;
        for (GameStats stats : getAllStats()) {
            total += stats.totalPlays;
        }
        return total;
    }

    /**
     * 获取指定游戏的胜利次数
     *
     * @param gameId 游戏唯一标识符
     * @return 该游戏的胜利次数
     */
    public int getWinCount(String gameId) {
        return getStats(gameId).totalWins;
    }

    /**
     * 获取指定游戏的失败次数
     *
     * @param gameId 游戏唯一标识符
     * @return 该游戏的失败次数
     */
    public int getLossCount(String gameId) {
        return getStats(gameId).totalLosses;
    }

    /**
     * 将GameStats对象序列化为JSON并保存到SharedPreferences
     *
     * @param gameId 游戏唯一标识符
     * @param stats  要保存的统计数据
     */
    private void saveStats(String gameId, GameStats stats) {
        prefs.edit().putString(KEY_STATS_PREFIX + gameId, gson.toJson(stats)).apply();
    }

    /**
     * 将JSON字符串反序列化为GameStats对象
     * <p>
     * 反序列化后强制设置gameId，因为Gson可能无法正确还原该字段。
     * 如果反序列化结果为null，返回一个空的GameStats对象。
     * </p>
     *
     * @param gameId 游戏唯一标识符
     * @param json   JSON字符串
     * @return 反序列化后的GameStats对象，不会返回null
     */
    private GameStats parseStatsJson(String gameId, String json) {
        GameStats stats = gson.fromJson(json, GameStats.class);
        if (stats == null) {
            return new GameStats(gameId);
        }
        // 强制设置gameId，确保与存储键一致
        stats.gameId = gameId;
        return stats;
    }

    /**
     * 将游戏ID列表编码为逗号分隔的字符串
     * <p>
     * 用于将最近游玩列表持久化到SharedPreferences的单个字符串字段中。
     * </p>
     *
     * @param ids 游戏ID列表
     * @return 逗号分隔的字符串，如"gomoku,snake,tetris"
     */
    private static String encodeIds(List<String> ids) {
        StringBuilder builder = new StringBuilder();
        for (String id : ids) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    /**
     * 将逗号分隔的字符串解码为游戏ID列表
     * <p>
     * 解码过程中会自动去除空白字符和重复项，确保列表的干净性。
     * 输入为null或空字符串时返回空列表。
     * </p>
     *
     * @param raw 逗号分隔的字符串
     * @return 游戏ID列表，不含空白和重复项
     */
    private static List<String> decodeIds(String raw) {
        List<String> ids = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return ids;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            String id = part.trim();
            // 跳过空白和重复的ID
            if (!id.isEmpty() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }
}
