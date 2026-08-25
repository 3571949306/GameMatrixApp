package com.gamecenter.app.games.tetris;

/**
 * 俄罗斯方块纯计分规则（无 Android 依赖，便于单元测试）。
 *
 * <p>说明：Back-to-Back 1.5× 已在调用方（TetrisView.lockAndAdvance）预先处理进 baseScore，
 * 本类只负责 Combo 奖励、Perfect Clear 奖励与等级乘子的叠加。</p>
 */
public final class TetrisRules {

    private static final int[] PERFECT_CLEAR_BONUS = {0, 800, 1200, 1800, 2000};
    private static final int COMBO_PER_LEVEL = 50;

    private TetrisRules() {}

    /**
     * 计算一次消行获得的得分（不含 B2B，B2B 已并入 baseScore）。
     *
     * @param baseScore      基础分（已含 B2B 调整）
     * @param comboCount     当前连击数（1 表示首次消行，无 combo 奖励）
     * @param level          当前等级（>=1）
     * @param perfectClear   是否 Perfect Clear（清空整个棋盘）
     * @param cleared        本次消除的行数（1..4）
     * @return 最终得分增量
     */
    public static int score(int baseScore, int comboCount, int level, boolean perfectClear, int cleared) {
        int comboBonus = 0;
        if (comboCount > 1) {
            comboBonus = COMBO_PER_LEVEL * (comboCount - 1) * Math.max(1, level);
        }
        int pcBonus = 0;
        if (perfectClear) {
            pcBonus = PERFECT_CLEAR_BONUS[Math.min(cleared, 4)] * Math.max(1, level);
        }
        return baseScore * Math.max(1, level) + comboBonus + pcBonus;
    }
}
