package com.gamecenter.app.doudizhu.score;

/**
 * 春天/反春天判定器（纯静态，无 Android 依赖，可单测）。
 *
 * <p>规则（对齐改造计划 P3）：
 * <ul>
 *   <li>春天：地主获胜，且两名农民从头到尾一张牌都没出过（各座位出牌次数为 0）→ 倍数 ×2</li>
 *   <li>反春天：农民获胜，且地主除开局首次出牌外再没出过牌（出牌次数为 1）→ 倍数 ×2</li>
 *   <li>两者互斥，其余情况无春天加成</li>
 * </ul>
 *
 * <p>出牌次数按"出牌手数"计（pass 不计），由对局控制器在每次
 * {@code commitPlay} 时累加并传入。手牌打完那一手同样计数。</p>
 */
public final class SpringDetector {

    private SpringDetector() {}

    /**
     * 判定是否春天：地主获胜且两名农民均未出过牌。
     *
     * @param winnerSeat   获胜座位
     * @param landlordSeat 地主座位
     * @param playCounts   各座位累计出牌手数（长度 ≥3）
     * @return true 表示春天
     */
    public static boolean isSpring(int winnerSeat, int landlordSeat, int[] playCounts) {
        return validate(winnerSeat, landlordSeat, playCounts)
                && winnerSeat == landlordSeat
                && countFarmerPlays(landlordSeat, playCounts) == 0;
    }

    /**
     * 判定是否反春天：农民获胜且地主只出过开局首发那一手。
     *
     * @param winnerSeat   获胜座位
     * @param landlordSeat 地主座位
     * @param playCounts   各座位累计出牌手数（长度 ≥3）
     * @return true 表示反春天
     */
    public static boolean isAntiSpring(int winnerSeat, int landlordSeat, int[] playCounts) {
        return validate(winnerSeat, landlordSeat, playCounts)
                && winnerSeat != landlordSeat
                && playCounts[landlordSeat] == 1;
    }

    private static boolean validate(int winnerSeat, int landlordSeat, int[] playCounts) {
        if (playCounts == null || playCounts.length < 3) return false;
        if (winnerSeat < 0 || winnerSeat >= 3) return false;
        if (landlordSeat < 0 || landlordSeat >= 3) return false;
        return true;
    }

    /** 两名农民（非地主座位）的出牌手数合计。 */
    private static int countFarmerPlays(int landlordSeat, int[] playCounts) {
        int sum = 0;
        for (int seat = 0; seat < 3; seat++) {
            if (seat != landlordSeat) {
                sum += playCounts[seat];
            }
        }
        return sum;
    }
}
