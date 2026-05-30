package com.gamecenter.app.games;

/**
 * 游戏统计数据模型 —— 游戏的"成绩单"
 *
 * <p>你可以把这个类想象成一张成绩单，记录了某个游戏的所有表现数据：
 * 最高分多少、赢了几次、输了几次、玩了多久等等。
 * 每个游戏都有一张独立的成绩单，通过 gameId（游戏ID）来区分。</p>
 *
 * <p>这个类只负责"装数据"，不负责保存和读取。
 * 保存和读取的工作由 {@link GameUsageStore} 完成，就像成绩单由教务处保管一样。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>字段使用public修饰，简化数据访问，因为该类仅作为纯数据容器
 *       （就像成绩单上的分数，谁都可以看）</li>
 *   <li>提供无参构造函数以支持Gson反序列化（从JSON还原对象时需要）</li>
 *   <li>胜率计算仅基于胜/负场次，不包含平局</li>
 * </ul>
 * </p>
 */
public class GameStats {
    /** 游戏唯一标识符，与GameRegistry中的Entry.id对应，就像成绩单上的学号 */
    public String gameId;
    /** 最高得分，0表示尚未记录（还没考过试） */
    public int highScore;
    /** 总胜利次数 */
    public int totalWins;
    /** 总失败次数 */
    public int totalLosses;
    /** 总游戏次数（包含胜和负） */
    public int totalPlays;
    /** 最佳完成用时（毫秒），0表示尚未记录。越短越好，就像跑步计时 */
    public long bestTimeMs;
    /** 累计游玩总时长（毫秒），所有游戏时间加在一起 */
    public long totalPlayTimeMs;
    /** 最后游玩时间戳（毫秒），0表示从未游玩。记录的是"最后一次玩是什么时候" */

    public long lastPlayedAt;

    /**
     * 无参构造函数
     *
     * <p>供Gson反序列化使用（Gson从JSON字符串还原对象时需要无参构造函数），
     * 不应在业务代码中直接调用。就像成绩单模板，等Gson帮你填上数据。</p>
     */
    public GameStats() {}

    /**
     * 创建指定游戏ID的统计数据对象
     *
     * <p>所有统计字段初始化为0，表示该游戏尚无任何游玩记录，
     * 就像新生的空白成绩单。</p>
     *
     * @param gameId 游戏唯一标识符
     */
    public GameStats(String gameId) {
        this.gameId = gameId;
        this.highScore = 0;
        this.totalWins = 0;
        this.totalLosses = 0;
        this.totalPlays = 0;
        this.bestTimeMs = 0;
        this.totalPlayTimeMs = 0;
        this.lastPlayedAt = 0;
    }

    /**
     * 计算胜率百分比
     *
     * <p>胜率 = 胜利次数 ÷ (胜利次数 + 失败次数) × 100。
     * 当胜负总数为0时返回0，避免除以0导致程序崩溃。
     * 就像考试及格率 = 及格人数 ÷ 总人数 × 100。</p>
     *
     * @return 胜率百分比，范围0.0~100.0；无记录时返回0.0
     */
    public float getWinRate() {
        int total = totalWins + totalLosses;
        if (total == 0) return 0f;
        return (float) totalWins / total * 100f;
    }

    /**
     * 获取胜率的格式化文本
     *
     * <p>返回保留一位小数的百分比字符串，如"75.3%"。
     * 无记录时返回"无记录"。</p>
     *
     * @return 格式化的胜率文本
     */
    public String getWinRateText() {
        int total = totalWins + totalLosses;
        if (total == 0) return "无记录";
        return String.format("%.1f%%", getWinRate());
    }

    /**
     * 获取最佳用时的格式化文本
     *
     * <p>将毫秒转换为可读的时间格式。超过1分钟显示"X分X秒"，
     * 不足1分钟显示"X秒"。无记录时返回"无记录"。
     * 就像跑步成绩，把秒表数字变成"1分30秒"这样好读的格式。</p>
     *
     * @return 格式化的最佳用时文本
     */
    public String getBestTimeText() {
        if (bestTimeMs <= 0) return "无记录";
        // 毫秒转换为秒
        long seconds = bestTimeMs / 1000;
        // 秒转换为分钟
        long minutes = seconds / 60;
        // 剩余的秒数
        seconds = seconds % 60;
        if (minutes > 0) {
            return String.format("%d分%d秒", minutes, seconds);
        }
        return seconds + "秒";
    }

    /**
     * 获取总游玩时长的格式化文本
     *
     * <p>将毫秒转换为可读的时间格式，自动选择合适的单位：
     * <ul>
     *   <li>超过1小时：显示"X小时X分"</li>
     *   <li>超过1分钟：显示"X分X秒"</li>
     *   <li>不足1分钟：显示"X秒"</li>
     * </ul>
     * 无记录时返回"无记录"。</p>
     *
     * @return 格式化的总游玩时长文本
     */
    public String getTotalPlayTimeText() {
        if (totalPlayTimeMs <= 0) return "无记录";
        // 毫秒 → 秒 → 分 → 时，逐级换算
        long totalSeconds = totalPlayTimeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d小时%d分", hours, minutes);
        }
        if (minutes > 0) {
            return String.format("%d分%d秒", minutes, seconds);
        }
        return seconds + "秒";
    }
}
