package com.gamecenter.app.games.coin;

/**
 * 金币赚取规则引擎（纯 Java，无 Android 依赖）。
 *
 * <p>规则：胜局基础 {@value #WIN_BASE} 金币，按连续胜利天数加成
 * （每天 +{@value #STREAK_BONUS_PER_DAY}，加成封顶 {@value #STREAK_BONUS_MAX}）；
 * 败局安慰 {@value #LOSS_BASE} 金币；每日赚取上限 {@value #DAILY_CAP} 金币，
 * 由 {@link #clampToCap(int, long)} 在入账前统一裁剪。
 */
public class EarnRuleEngine {

    /** 胜局基础奖励。 */
    public static final int WIN_BASE = 10;
    /** 败局基础奖励。 */
    public static final int LOSS_BASE = 3;
    /** 每日挑战完成奖励，与对局奖励共用每日上限。 */
    public static final int CHALLENGE_REWARD = 20;
    /** 每连续胜利一天的额外加成。 */
    public static final int STREAK_BONUS_PER_DAY = 2;
    /** 连胜加成封顶值。 */
    public static final int STREAK_BONUS_MAX = 10;
    /** 每日赚取上限。 */
    public static final int DAILY_CAP = 200;

    /**
     * 胜局奖励：streakDays &lt; 1 时按 1 天计。
     * 奖励 = WIN_BASE + min((streakDays-1)*STREAK_BONUS_PER_DAY, STREAK_BONUS_MAX)，
     * 结果恒落在 10..20。
     */
    public int winReward(int streakDays) {
        if (streakDays < 1) {
            streakDays = 1;
        }
        int bonus = (streakDays - 1) * STREAK_BONUS_PER_DAY;
        if (bonus > STREAK_BONUS_MAX) {
            bonus = STREAK_BONUS_MAX;
        }
        return WIN_BASE + bonus;
    }

    /** 败局安慰奖励。 */
    public int lossReward() {
        return LOSS_BASE;
    }

    /** 每日挑战完成奖励，与对局奖励共用每日上限。 */
    public int challengeReward() {
        return CHALLENGE_REWARD;
    }

    /**
     * 按每日赚取上限裁剪待入账的正向收益。
     *
     * @param delta      待入账变动量；delta &lt;= 0 时原样返回（消费/零不受上限约束）
     * @param earnedToday 今日已赚取总额
     * @return 今日仍可入账的额度；已达/超上限时返回 0
     */
    public int clampToCap(int delta, long earnedToday) {
        if (delta <= 0) {
            return delta;
        }
        long remaining = DAILY_CAP - earnedToday;
        if (remaining < 0) {
            remaining = 0;
        }
        return (int) Math.min(delta, remaining);
    }
}
