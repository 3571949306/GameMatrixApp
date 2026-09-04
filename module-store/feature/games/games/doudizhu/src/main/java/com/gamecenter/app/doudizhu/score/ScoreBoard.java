package com.gamecenter.app.doudizhu.score;

/**
 * 斗地主倍数记分板（纯 Java，无 Android 依赖，可单测）。
 *
 * <p>承载一局中的全部倍数因子并负责最终结算：
 * 总倍数 = 底分系数 × 叫分 × 2^炸弹次数 ×（春天/反春 ×2）。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>纯值对象 + 显式状态推进方法（{@link #registerBomb} 等），不持有 View/Context，
 *       保证规则可脱离 Android 单测（对齐改造计划 P3 "倍数结算" 要求）</li>
 *   <li>炸弹与王炸统一按 ×2 计（王炸不再额外 ×2），与主流斗地主计分一致</li>
 *   <li>结算口径：底分 100。地主胜：地主 +2×底分×倍数，每名农民 −底分×倍数；
 *       农民胜：每名农民 +底分×倍数，地主 −2×底分×倍数。零和。</li>
 * </ul>
 */
public class ScoreBoard {

    /** 结算底分（倍数叠加前的基准分） */
    public static final int BASE_SCORE = 100;

    /** 叫分（1/2/3），地主确定后写入 */
    private int bidScore = 1;
    /** 炸弹/王炸触发次数 */
    private int bombCount = 0;
    /** 春天（地主胜且农民一张未出） */
    private boolean spring = false;
    /** 反春（农民胜且地主只出过首发一手） */
    private boolean antiSpring = false;

    /** 地主确定时写入叫分倍数（1~3）。 */
    public void setBidScore(int bidScore) {
        this.bidScore = Math.max(1, Math.min(3, bidScore));
    }

    public int getBidScore() {
        return bidScore;
    }

    public int getBombCount() {
        return bombCount;
    }

    public boolean isSpring() {
        return spring;
    }

    public boolean isAntiSpring() {
        return antiSpring;
    }

    /** 炸弹/王炸出牌时调用：倍数 ×2。 */
    public void registerBomb() {
        bombCount++;
    }

    /** 对局结束时写入春天/反春结论（互斥，后写覆盖先写）。 */
    public void applySpring(boolean spring, boolean antiSpring) {
        this.spring = spring;
        this.antiSpring = antiSpring;
    }

    /** 总倍数 = 叫分 × 2^炸弹数 ×（春天/反春 ×2）。 */
    public int totalMultiplier() {
        int springFactor = (spring || antiSpring) ? 2 : 1;
        return bidScore * (1 << bombCount) * springFactor;
    }

    /**
     * 结算人类玩家（座位 0）的得分变动。
     *
     * @param humanIsLandlord 人类是否为地主
     * @param landlordWon     地主是否获胜
     * @return 正为赢分，负为输分；地主身份按双倍份额结算
     */
    public int settleHumanDelta(boolean humanIsLandlord, boolean landlordWon) {
        int mult = totalMultiplier();
        int unit = BASE_SCORE * mult;
        if (humanIsLandlord) {
            return landlordWon ? unit * 2 : -unit * 2;
        }
        return landlordWon ? -unit : unit;
    }

    /**
     * 生成一局的结算结果快照。
     *
     * @param landlordSeat    地主座位（0/1/2）
     * @param humanIsLandlord 人类是否为地主
     * @param landlordWon     地主是否获胜
     * @param durationMs      对局耗时（毫秒，含存档恢复前的时间）
     * @return 结算结果值对象
     */
    public Settlement settle(int landlordSeat, boolean humanIsLandlord, boolean landlordWon,
                             long durationMs) {
        return new Settlement(landlordSeat, humanIsLandlord, landlordWon,
                bidScore, bombCount, spring, antiSpring, totalMultiplier(),
                settleHumanDelta(humanIsLandlord, landlordWon), durationMs);
    }

    /**
     * 对局结算结果（不可变值对象）。
     *
     * <p>承载结算弹窗需要的全部明细：胜负、地主、叫分、炸弹数、春天/反春、
     * 总倍数、人类得分变动与耗时。纯数据，可单测。</p>
     */
    public static final class Settlement {
        public final int landlordSeat;
        public final boolean humanIsLandlord;
        public final boolean landlordWon;
        public final int bidScore;
        public final int bombCount;
        public final boolean spring;
        public final boolean antiSpring;
        public final int multiplier;
        public final int humanScoreDelta;
        public final long durationMs;

        Settlement(int landlordSeat, boolean humanIsLandlord, boolean landlordWon,
                   int bidScore, int bombCount, boolean spring, boolean antiSpring,
                   int multiplier, int humanScoreDelta, long durationMs) {
            this.landlordSeat = landlordSeat;
            this.humanIsLandlord = humanIsLandlord;
            this.landlordWon = landlordWon;
            this.bidScore = bidScore;
            this.bombCount = bombCount;
            this.spring = spring;
            this.antiSpring = antiSpring;
            this.multiplier = multiplier;
            this.humanScoreDelta = humanScoreDelta;
            this.durationMs = durationMs;
        }

        /** 人类是否获胜（地主胜且人是地主，或农民胜且人是农民）。 */
        public boolean humanWon() {
            return humanIsLandlord == landlordWon;
        }

        /** 耗时格式化为 "m 分 ss 秒" 风格的紧凑串（纯逻辑可测）。 */
        public String formatDuration() {
            long totalSeconds = durationMs / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
        }
    }
}
